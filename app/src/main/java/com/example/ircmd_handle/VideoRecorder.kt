package com.example.ircmd_handle

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VideoRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoRecorder"
        private const val MIME_TYPE = "video/avc" // H.264
        private const val FRAME_RATE = 30 // Will be updated based on camera config
        private const val I_FRAME_INTERVAL = 2 // seconds
        private const val TIMEOUT_USEC = 10000L // 10ms
        
        // Buffer management constants
        private const val MAX_INPUT_BUFFERS = 16
        private const val WARNING_BUFFER_THRESHOLD = 12
        private const val CRITICAL_BUFFER_THRESHOLD = 14
    }
    
    // Recording state
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val recordingStartTime = AtomicLong(0)
    private val pausedDuration = AtomicLong(0)
    private var lastPauseTime = 0L
    
    // MediaCodec components
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    
    // Track indices
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    
    // Configuration
    private var videoWidth = 256
    private var videoHeight = 192
    private var frameRate = 25
    private var bitrateMbps = 15
    private var deviceType = "256" // Will be updated based on camera config
    private var includeAudio = true
    
    // File management
    private var outputFile: File? = null
    private var currentFileName: String? = null
    
    // Buffer monitoring
    private var inputBufferCount = 0
    
    // Texture capture for recording
    private var captureJob: Job? = null
    private var textureView: TextureView? = null
    
    // Synchronization for encoder operations
    private val encoderLock = Any()
    
    // Fixed frame timing
    private var frameCounter = 0L
    private var frameDurationUs = 20000L // Will be calculated based on fps
    private var lastCapturedBitmap: Bitmap? = null
    
    // Direct recording mode support
    private var useDirectRecording = true // Enable by default for better performance
    
    // Native method declarations for direct recording
    private external fun nativeSetupDirectRecording()
    private external fun nativeStartDirectRecording()
    private external fun nativeStopDirectRecording()
    private external fun nativeCleanupDirectRecording()
    
    // Native callback for direct YUV frame data
    @Suppress("unused") // Called from native code
    private fun onNativeYUVFrame(yuvData: ByteArray, width: Int, height: Int, timestampUs: Long) {
        if (!isRecording.get() || isPaused.get()) {
            return
        }
        
        try {
            val encoder = videoEncoder
            if (encoder == null) {
                Log.w(TAG, "Video encoder not available for direct frame")
                return
            }
            
            // Get input buffer from MediaCodec
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null && yuvData.size <= inputBuffer.capacity()) {
                    inputBuffer.clear()
                    inputBuffer.put(yuvData)
                    
                    // Queue the input buffer with YUV420 data
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvData.size,
                        timestampUs,
                        0
                    )
                    
                    // Process encoder output
                    synchronized(encoderLock) {
                        try {
                            drainEncoder(false)
                        } catch (e: IllegalStateException) {
                            Log.w(TAG, "Encoder drain skipped due to concurrent operation")
                        }
                    }
                } else {
                    Log.w(TAG, "Input buffer too small or null: buffer capacity=${inputBuffer?.capacity()}, data size=${yuvData.size}")
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
                }
            } else {
                Log.w(TAG, "No input buffer available for direct frame")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing direct YUV frame", e)
        }
    }
    
    fun configure(width: Int, height: Int, fps: Int, deviceType: String, bitrateMbps: Int = 15, includeAudio: Boolean = true) {
        this.videoWidth = width
        this.videoHeight = height
        this.frameRate = fps
        this.deviceType = deviceType
        this.bitrateMbps = bitrateMbps
        this.includeAudio = includeAudio
        
        // Calculate fixed frame duration in microseconds
        this.frameDurationUs = 1000000L / fps // e.g., 20000μs for 50fps
        
        Log.i(TAG, "Configured recorder: ${width}x${height} @ ${fps}fps, ${bitrateMbps}Mbps, device: $deviceType")
        Log.i(TAG, "Frame interval: ${frameDurationUs}μs (${1000L / fps}ms)")
    }
    
    fun startRecording(textureView: TextureView? = null): Surface? {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return inputSurface
        }
        
        try {
            // Create output file
            createOutputFile()
            
            // Setup video encoder
            setupVideoEncoder()
            
            // Setup audio encoder if needed
            if (includeAudio) {
                setupAudioEncoder()
            }
            
            // Setup muxer
            setupMuxer()
            
            // Start encoding
            videoEncoder?.start()
            audioEncoder?.start()
            
            // Reset frame timing
            frameCounter = 0
            lastCapturedBitmap = null
            
            recordingStartTime.set(System.currentTimeMillis())
            pausedDuration.set(0)
            isRecording.set(true)
            isPaused.set(false)
            
            // Choose recording mode based on configuration
            if (useDirectRecording) {
                // Set up direct recording from native UVC callback
                try {
                    nativeSetupDirectRecording()
                    nativeStartDirectRecording()
                    Log.i(TAG, "🚀 Direct recording mode enabled")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Direct recording not available, falling back to TextureView capture")
                    useDirectRecording = false
                    if (textureView != null) {
                        startTextureCapture(textureView)
                    }
                }
            } else {
                // Fall back to TextureView capture mode
                if (textureView != null) {
                    startTextureCapture(textureView)
                }
            }
            
            Log.i(TAG, "📹 Recording started: $currentFileName (mode: ${if (useDirectRecording) "direct" else "textureview"})")
            return if (useDirectRecording) null else inputSurface // Return null for direct mode
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            cleanup()
            return null
        }
    }
    
    fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "Not currently recording")
            return
        }
        
        try {
            isRecording.set(false)
            isPaused.set(false)
            
            // Stop recording based on mode
            if (useDirectRecording) {
                try {
                    nativeStopDirectRecording()
                    Log.i(TAG, "🛑 Direct recording stopped")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Direct recording stop failed")
                }
            } else {
                // Stop texture capture first to prevent new frames
                stopTextureCapture()
            }
            
            // Give encoder time to finish processing current frames
            Thread.sleep(50)
            
            // Drain encoder and signal end of stream (with error handling)
            synchronized(encoderLock) {
                try {
                    drainEncoder(true)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Encoder drain conflict during shutdown - continuing with cleanup")
                }
            }
            
            // Stop encoders
            try {
                videoEncoder?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping video encoder: ${e.message}")
            }
            
            try {
                audioEncoder?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping audio encoder: ${e.message}")
            }
            
            // Stop muxer
            try {
                if (muxerStarted) {
                    mediaMuxer?.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping muxer: ${e.message}")
            }
            
            // Save to gallery
            saveToGallery()
            
            Log.i(TAG, "🛑 Recording stopped and saved: $currentFileName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            cleanup()
        }
    }
    
    fun pauseRecording() {
        if (!isRecording.get() || isPaused.get()) {
            Log.w(TAG, "Cannot pause - not recording or already paused")
            return
        }
        
        isPaused.set(true)
        lastPauseTime = System.currentTimeMillis()
        Log.i(TAG, "⏸️ Recording paused")
    }
    
    fun resumeRecording() {
        if (!isRecording.get() || !isPaused.get()) {
            Log.w(TAG, "Cannot resume - not recording or not paused")
            return
        }
        
        val pauseDuration = System.currentTimeMillis() - lastPauseTime
        pausedDuration.addAndGet(pauseDuration)
        isPaused.set(false)
        Log.i(TAG, "▶️ Recording resumed (paused for ${pauseDuration}ms)")
    }
    
    fun getRecordingDuration(): Long {
        if (!isRecording.get()) return 0
        
        val currentTime = System.currentTimeMillis()
        val totalTime = currentTime - recordingStartTime.get()
        val activeDuration = totalTime - pausedDuration.get()
        
        // Subtract current pause duration if paused
        return if (isPaused.get()) {
            activeDuration - (currentTime - lastPauseTime)
        } else {
            activeDuration
        }
    }
    
    fun isRecording(): Boolean = isRecording.get()
    fun isPaused(): Boolean = isPaused.get()
    
    private fun createOutputFile() {
        val timestamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFileName = "MINI2-${deviceType}_${timestamp}.mp4"
        
        // Create temporary file for recording
        outputFile = File(context.cacheDir, currentFileName!!)
        Log.i(TAG, "Output file: ${outputFile?.absolutePath}")
    }
    
    private fun setupVideoEncoder() {
        try {
            Log.i(TAG, "Setting up video encoder: ${videoWidth}x${videoHeight} @ ${frameRate}fps")
            
            val format = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateMbps * 1_000_000) // Convert Mbps to bps
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                
                // Buffer management for temporal accuracy
                val bufferSize = videoWidth * videoHeight * 3 / 2 // YUV420 size
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
                
                // Quality settings
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                
                // Additional timing and sync settings for proper playback speed
                setFloat(MediaFormat.KEY_CAPTURE_RATE, frameRate.toFloat())
                setInteger(MediaFormat.KEY_OPERATING_RATE, frameRate)
                
                // Ensure proper bitrate mode for consistent timing
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            
            Log.i(TAG, "Creating video encoder for MIME type: $MIME_TYPE")
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
            
            Log.i(TAG, "Configuring video encoder...")
            videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            Log.i(TAG, "✅ Video encoder configured: ${videoWidth}x${videoHeight} @ ${frameRate}fps, ${bitrateMbps}Mbps")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video encoder: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupAudioEncoder() {
        if (!includeAudio) return
        
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000) // 64 kbps for mono
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioEncoder?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        Log.i(TAG, "Audio encoder configured: 44.1kHz mono AAC")
    }
    
    private fun setupMuxer() {
        mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        
        Log.i(TAG, "Muxer setup complete")
    }
    
    private fun saveToGallery() {
        try {
            val resolver = context.contentResolver
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            
            val videoDetails = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, currentFileName)
                put(MediaStore.Video.Media.TITLE, "MINI2 Thermal Recording")
                put(MediaStore.Video.Media.DESCRIPTION, "Thermal camera recording from MINI2-$deviceType")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ThermalCamera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            
            val videoUri = resolver.insert(videoCollection, videoDetails)
            if (videoUri != null) {
                resolver.openOutputStream(videoUri)?.use { outputStream ->
                    outputFile?.inputStream()?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Mark as not pending (for Android Q+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    videoDetails.clear()
                    videoDetails.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(videoUri, videoDetails, null, null)
                }
                
                Log.i(TAG, "✅ Video saved to gallery: $currentFileName")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video to gallery", e)
        } finally {
            // Clean up temporary file
            outputFile?.delete()
        }
    }
    
    private fun cleanup() {
        try {
            // Clean up direct recording native resources first
            if (useDirectRecording) {
                try {
                    nativeCleanupDirectRecording()
                    Log.i(TAG, "🧹 Direct recording native cleanup complete")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Direct recording cleanup not available")
                }
            }
            
            inputSurface?.release()
            inputSurface = null
            
            videoEncoder?.release()
            videoEncoder = null
            
            audioEncoder?.release()
            audioEncoder = null
            
            mediaMuxer?.release()
            mediaMuxer = null
            
            // Clean up bitmap resources
            lastCapturedBitmap?.recycle()
            lastCapturedBitmap = null
            
            muxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
            inputBufferCount = 0
            frameCounter = 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    // Monitor encoder performance
    private fun monitorEncoderPerformance() {
        when {
            inputBufferCount >= CRITICAL_BUFFER_THRESHOLD -> {
                Log.w(TAG, "🚨 Critical: Encoder falling behind (${inputBufferCount}/${MAX_INPUT_BUFFERS} buffers)")
            }
            inputBufferCount >= WARNING_BUFFER_THRESHOLD -> {
                Log.w(TAG, "⚠️ Warning: Encoder load high (${inputBufferCount}/${MAX_INPUT_BUFFERS} buffers)")
            }
        }
    }
    
    private fun startTextureCapture(textureView: TextureView) {
        this.textureView = textureView
        
        val frameIntervalMs = 1000L / frameRate // Fixed interval in milliseconds (e.g., 20ms for 50fps)
        
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "📹 Starting fixed-interval capture: ${frameIntervalMs}ms intervals (${frameRate}fps)")
            var frameCount = 0
            val startTime = System.currentTimeMillis()
            
            while (isRecording.get()) {
                if (!isPaused.get()) {
                    try {
                        val frameStartTime = System.currentTimeMillis()
                        
                        // Capture frame from TextureView (or reuse last if capture fails)
                        val bitmap = withContext(Dispatchers.Main) {
                            textureView.getBitmap(videoWidth, videoHeight)
                        }
                        
                        if (bitmap != null) {
                            // Successfully captured new frame
                            lastCapturedBitmap?.recycle() // Clean up previous bitmap
                            lastCapturedBitmap = bitmap
                            encodeBitmapFrame(bitmap)
                        } else {
                            // Camera not ready - reuse last captured frame to maintain timing
                            lastCapturedBitmap?.let { lastBitmap ->
                                Log.v(TAG, "Reusing last frame (camera not ready)")
                                encodeBitmapFrame(lastBitmap)
                            } ?: run {
                                Log.w(TAG, "No frame available for encoding")
                            }
                        }
                        
                        frameCount++
                        
                        // Log frame rate every 50 frames to verify actual capture rate
                        if (frameCount % 50 == 0) {
                            val elapsedTime = System.currentTimeMillis() - startTime
                            val actualFps = (frameCount * 1000.0) / elapsedTime
                            Log.i(TAG, "📊 Capture rate check: ${frameCount} frames in ${elapsedTime}ms = %.2f fps (target: ${frameRate}fps)".format(actualFps))
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error capturing frame: ${e.message}")
                        // Continue with timing even on error
                    }
                }
                
                // Fixed interval delay - maintains consistent frame rate regardless of capture success
                delay(frameIntervalMs)
            }
        }
        
        Log.i(TAG, "📹 Started texture capture at ${frameRate}fps with ${frameIntervalMs}ms fixed intervals")
    }
    
    private fun encodeBitmapFrame(bitmap: Bitmap) {
        try {
            val encoder = videoEncoder
            if (encoder == null) {
                Log.w(TAG, "Video encoder not available")
                return
            }
            
            // Convert bitmap to YUV420 and feed to encoder
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    // Convert ARGB bitmap to YUV420 format
                    val yuvData = convertBitmapToYUV420(bitmap)
                    inputBuffer.clear()
                    inputBuffer.put(yuvData)
                    
                    // Fixed presentation time based on frame number
                    val presentationTimeUs = frameCounter * frameDurationUs
                    frameCounter++
                    
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvData.size,
                        presentationTimeUs,
                        0
                    )
                    
                    inputBufferCount++
                    
                    // Process encoder output immediately (with synchronization)
                    synchronized(encoderLock) {
                        try {
                            drainEncoder(false)
                        } catch (e: IllegalStateException) {
                            Log.w(TAG, "Encoder drain skipped due to concurrent operation")
                        }
                    }
                    
                    // Reduce input buffer count after processing
                    inputBufferCount = maxOf(0, inputBufferCount - 1)
                    
                    monitorEncoderPerformance()
                } else {
                    Log.w(TAG, "Could not get input buffer")
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap frame", e)
        }
    }
    
    private fun convertBitmapToYUV420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        
        // Optimize: Scale down if too large
        val targetBitmap = if (width > videoWidth || height > videoHeight) {
            Bitmap.createScaledBitmap(bitmap, videoWidth, videoHeight, false)
        } else {
            bitmap
        }
        
        val actualWidth = targetBitmap.width
        val actualHeight = targetBitmap.height
        val pixels = IntArray(actualWidth * actualHeight)
        targetBitmap.getPixels(pixels, 0, actualWidth, 0, 0, actualWidth, actualHeight)
        
        val yuvSize = actualWidth * actualHeight * 3 / 2 // YUV420 format
        val yuv = ByteArray(yuvSize)
        
        var yIndex = 0
        var uvIndex = actualWidth * actualHeight
        
        // Optimized RGB to YUV conversion
        for (j in 0 until actualHeight) {
            for (i in 0 until actualWidth) {
                val pixel = pixels[j * actualWidth + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Convert RGB to Y (luminance) - optimized formula
                val y = (77 * r + 150 * g + 29 * b) shr 8
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                
                // Convert RGB to UV (chrominance) for every 2x2 block
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = 128 + ((-43 * r - 85 * g + 128 * b) shr 8)
                    val v = 128 + ((128 * r - 107 * g - 21 * b) shr 8)
                    
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        
        // Clean up scaled bitmap if we created one
        if (targetBitmap != bitmap) {
            targetBitmap.recycle()
        }
        
        return yuv
    }
    
    private fun drainEncoder(endOfStream: Boolean) {
        try {
            val encoder = videoEncoder ?: return
            val muxer = mediaMuxer ?: return
            
            // For ByteBuffer input, we signal end of stream by queuing an empty buffer
            if (endOfStream) {
                val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (inputBufferIndex >= 0) {
                    // Use the next frame timestamp for end of stream
                    val endStreamTimeUs = frameCounter * frameDurationUs
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        endStreamTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }
            
            val bufferInfo = MediaCodec.BufferInfo()
            var encoderOutputAvailable = true
            
            while (encoderOutputAvailable) {
                val encoderOutputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                
                when {
                    encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        encoderOutputAvailable = false
                    }
                    encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) {
                            Log.e(TAG, "Format changed after muxer started")
                        } else {
                            val newFormat = encoder.outputFormat
                            videoTrackIndex = muxer.addTrack(newFormat)
                            
                            if (!includeAudio || audioTrackIndex >= 0) {
                                muxer.start()
                                muxerStarted = true
                                Log.i(TAG, "Muxer started with video track")
                            }
                        }
                    }
                    encoderOutputBufferIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(encoderOutputBufferIndex)
                        if (encodedData != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            
                            if (bufferInfo.size > 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            }
                        }
                        
                        encoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderOutputAvailable = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error draining encoder", e)
        }
    }
    
    private fun stopTextureCapture() {
        captureJob?.cancel()
        captureJob = null
        textureView = null
        Log.i(TAG, "🛑 Stopped texture capture")
    }
}
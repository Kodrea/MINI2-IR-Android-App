# PRD - Video Recording Feature

## Goal
Enable users to capture and save thermal video recordings directly from the camera stream to device storage.

## Key Classes / Files
### Android/Kotlin Files
- `CameraActivity.kt` - Add recording controls and status indicators
- `VideoRecordingManager.kt` - NEW - Manage recording lifecycle and file operations
- `IrcmdManager.kt` - Extend with recording state management
- `DeviceConfig.kt` - Add video format preferences

### Native/C++ Files
- `uvc_manager.h` - Add video recording frame callback interface
- `uvc_manager.cpp` - Implement frame capture and format conversion
- `video_encoder.h` - NEW - Video encoding wrapper
- `video_encoder.cpp` - NEW - Handle video compression and file writing

### UI/Resources
- `activity_camera.xml` - Add recording button and progress indicator
- `dialog_recording_settings.xml` - NEW - Recording format and quality options
- `strings.xml` - Recording-related strings and messages

### Other Files
- `CMakeLists.txt` - Add video encoding libraries (ffmpeg or MediaCodec)
- `build.gradle.kts` - Add MediaRecorder and file permissions

## Steps
### Phase 1: Basic Recording Infrastructure
- [ ] Create `VideoRecordingManager` class with start/stop functionality
- [ ] Add recording permissions (WRITE_EXTERNAL_STORAGE, CAMERA)
- [ ] Implement basic UI controls (record button, status indicator)
- [ ] Add file path generation and storage management
- [ ] Integrate with existing camera permission flow

### Phase 2: Frame Capture Integration
- [ ] Extend UVC manager to provide frame callback for recording
- [ ] Implement frame buffer management for recording pipeline
- [ ] Add frame format conversion (YUV to encoder-compatible format)
- [ ] Create recording state machine (idle, recording, stopping)
- [ ] Add error handling for recording failures

### Phase 3: Video Encoding & File Output
- [ ] Integrate Android MediaRecorder or native video encoder
- [ ] Implement video compression with configurable quality settings
- [ ] Add metadata embedding (timestamp, device info, thermal parameters)
- [ ] Create recording settings dialog (format, quality, duration limits)
- [ ] Implement file management (naming convention, storage location)

## Testing
- [ ] Unit tests for VideoRecordingManager state transitions
- [ ] Integration tests for frame capture pipeline
- [ ] Manual testing on device with various recording durations
- [ ] Performance validation (memory usage, CPU impact, storage speed)

## Acceptance Criteria
- [ ] User can start/stop recording with single button press
- [ ] Recordings save to device storage with thermal metadata
- [ ] Recording does not interfere with live camera stream
- [ ] Maximum file size limits prevent storage exhaustion
- [ ] Recording quality settings persist between sessions
- [ ] Clear visual feedback shows recording status and duration

## Dependencies
- Android MediaRecorder API or native video encoding library
- Sufficient device storage space for video files
- Frame capture integration with existing UVC streaming pipeline
- File permissions granted by user

## Risks & Mitigation
- **Risk**: Recording may impact live stream performance
  - **Mitigation**: Use separate thread for encoding, optimize frame copying
- **Risk**: Large video files may fill device storage
  - **Mitigation**: Implement file size limits and storage space checks
- **Risk**: Thermal metadata may be lost in standard video formats
  - **Mitigation**: Use custom metadata fields or separate data files

## Claude Status
Created initial PRD structure and identified key implementation phases.

---
**Created**: 2025-01-08  
**Last Updated**: 2025-01-08  
**Assignee**: Claude  
**Status**: Planned
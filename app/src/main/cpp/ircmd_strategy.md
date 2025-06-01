# IRCMD SDK Integration Strategy

## File Structure

### Phase 1 Files
- `app/src/main/cpp/ircmd_manager.h` - New header file for `IrcmdManager` class
- `app/src/main/cpp/ircmd_manager.cpp` - New implementation file for `IrcmdManager`
- `app/src/main/java/com/example/ircmd_handle/CameraActivity.kt` - Modify existing activity
- `app/src/main/cpp/CMakeLists.txt` - Update to include SDK headers and libraries
- `app/src/main/AndroidManifest.xml` - Verify USB permissions

### Phase 2 Files
- `app/src/main/cpp/ircmd_handle.h` - New header file for handle wrapper class
- `app/src/main/cpp/ircmd_handle.cpp` - New implementation file for handle wrapper
- `app/src/main/cpp/ircmd_manager.h` - Update to include handle management
- `app/src/main/cpp/ircmd_manager.cpp` - Update with handle implementation
- `app/src/main/java/com/example/ircmd_handle/CameraActivity.kt` - Update to manage handle lifecycle

### Phase 3 Files
- `app/src/main/cpp/ircmd_manager.h` - Add FFC function declarations
- `app/src/main/cpp/ircmd_manager.cpp` - Implement FFC functionality
- `app/src/main/java/com/example/ircmd_handle/CameraActivity.kt` - Add FFC UI controls
- `app/src/main/res/layout/activity_camera.xml` - Add FFC button to layout
- `app/src/main/res/values/strings.xml` - Add FFC-related strings

### Common Files
- `app/src/main/cpp/ircmd_log.h` - New header for logging constants
- `app/src/main/cpp/ircmd_log.cpp` - New implementation for logging functions
- `app/src/main/cpp/ircmd_error.h` - New header for error code handling
- `app/src/main/cpp/ircmd_error.cpp` - New implementation for error handling

## Phase 1: SDK Interface Setup & Permissions

### Initial Setup
- Add SDK header files to the project
- Create a new class (e.g., `IrcmdManager`) to handle SDK operations
- Add logging constants for SDK operations (separate from UVC logging)

### Permission & Interface Handling
- Verify current USB permission handling in `CameraActivity` works for SDK
- Add logging to track USB interface selection
- Add verification that we're connecting to the correct interface for SDK
- Log all USB device details to confirm we're targeting the right device

### Verification Steps
- Log when USB device is attached/detached
- Log interface details (numbers, classes, etc.)
- Verify UVC streaming still works after SDK interface setup
- Add error logging for any permission/interface issues

## Phase 2: IrcmdHandle Implementation

### Handle Structure
- Create a wrapper class for `MySdk_IrcmdHandle_t`
- Add proper initialization and cleanup methods
- Implement RAII pattern for handle management

### Handle Lifecycle
- Add logging for handle creation/destruction
- Track handle state (valid/invalid)
- Add error logging for handle operations
- Implement proper cleanup on activity destruction

### Verification Steps
- Log successful handle creation
- Verify handle remains valid across activity lifecycle
- Log any handle-related errors
- Verify UVC streaming continues to work

## Phase 3: FFC Implementation

### FFC Function Integration
- Add FFC command method to `IrcmdManager`
- Implement 500ms delay requirement
- Add proper error handling and logging
- Add UI feedback for FFC status

### Command Execution
- Log FFC command attempts
- Log command completion or failure
- Add error code translation to human-readable messages
- Implement proper timing between commands

### Verification Steps
- Log FFC command execution timing
- Verify FFC commands complete successfully
- Monitor for any interference with UVC streaming
- Verify error handling works as expected

## Logging Strategy

### Log Tags
- `IrcmdManager` - SDK operations
- `IrcmdHandle` - handle management
- `IrcmdFFC` - FFC-specific operations

### Log Levels
- **INFO**: Normal operations, command execution
- **WARN**: Non-critical errors, timing issues
- **ERROR**: Critical failures, handle issues
- **DEBUG**: Detailed operation timing, state changes

### Log Content
- Command execution attempts and results
- Error codes and their meanings
- Timing information for commands
- Handle state changes
- USB interface details
- Permission status
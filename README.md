# Eternal Journey

An Android app that automatically plays audio files when Bluetooth devices connect, perfect for car audio systems or other Bluetooth-enabled devices.

## Features

- 🎵 **Automatic Audio Playback**: Automatically starts playing audio when a selected Bluetooth device connects
- 📱 **Bluetooth Device Management**: Select and manage multiple Bluetooth devices for auto-play
- 🎧 **Audio File Management**: 
  - Bundled audio files included with the app
  - Import your own audio files
  - Set default audio file for auto-play
- 🔄 **Seamless Audio Routing**: Audio continues playing on phone speaker when Bluetooth disconnects
- 📊 **Real-time Connection Status**: See which Bluetooth devices are currently connected
- 🎮 **Playback Controls**: Play, pause, stop, and control audio playback
- 🔔 **Notifications**: Toast notifications for critical events (connections, playback, etc.)

## Requirements

- Android 7.0 (API level 24) or higher
- Bluetooth support
- Audio playback permissions

## Setup

1. Clone the repository:
```bash
git clone https://github.com/hihanifm/EternalJourney.git
```

2. Open the project in Android Studio

3. Build and run the app on your device or emulator

## Usage

### Initial Setup

1. **Pair Bluetooth Device**: First, pair your Bluetooth device (e.g., car audio system) in Android Settings

2. **Select Devices**: 
   - Open the app
   - Go to the "Devices" tab
   - Select the Bluetooth devices you want to enable auto-play for

3. **Set Default Audio**:
   - Go to the "Audio" tab
   - The first bundled audio file is automatically set as default
   - You can change it by tapping "Set Default" on any audio file
   - Or import your own audio files

4. **Enable Auto-play**:
   - Go to the "Playback" tab
   - Toggle "Auto-play on Bluetooth connect" ON

### How It Works

1. When a selected Bluetooth device connects, the app automatically:
   - Detects the connection
   - Starts playing the default audio file
   - Shows a notification

2. When the Bluetooth device disconnects:
   - Audio continues playing on your phone speaker
   - Connection status updates in real-time

3. You can manually control playback:
   - Use the Playback Control screen
   - Use the notification controls
   - Tap audio files to play them with external apps

## Project Structure

```
app/src/main/java/com/hanifm/eternaljourney/
├── audio/
│   └── AudioFileManager.kt          # Manages audio files (bundled and user)
├── bluetooth/
│   ├── BluetoothDeviceManager.kt    # Manages Bluetooth device selection
│   └── BluetoothConnectionStateManager.kt  # Tracks connection state
├── data/
│   └── PreferencesManager.kt        # SharedPreferences wrapper
├── navigation/
│   └── NavGraph.kt                  # Navigation setup
├── receiver/
│   └── BluetoothConnectionReceiver.kt  # Handles BT connection events
├── service/
│   └── AudioPlaybackService.kt     # Media playback service
├── ui/
│   ├── AudioFilesScreen.kt         # Audio file management UI
│   ├── DeviceSelectionScreen.kt    # Bluetooth device selection UI
│   ├── MainScreen.kt                # Main navigation screen
│   ├── PlaybackControlScreen.kt    # Playback controls UI
│   └── viewmodel/
│       ├── AudioFilesViewModel.kt
│       ├── DeviceSelectionViewModel.kt
│       └── PlaybackViewModel.kt
└── util/
    └── Constants.kt                 # App constants (LOG_TAG, etc.)
```

## Logging

The app uses conditional logging that is only enabled in debug builds. Logs use the tag prefix `EJ-` for easy filtering:

```bash
# View all app logs (debug builds only)
adb logcat | grep "EJ-"
```

## Permissions

The app requires the following permissions:

- `BLUETOOTH` - Basic Bluetooth functionality
- `BLUETOOTH_ADMIN` - Bluetooth device management
- `BLUETOOTH_CONNECT` - Connect to Bluetooth devices (Android 12+)
- `MODIFY_AUDIO_SETTINGS` - Audio routing control
- `FOREGROUND_SERVICE` - Background audio playback
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - Media playback service
- `READ_MEDIA_AUDIO` - Access audio files (Android 13+)
- `READ_EXTERNAL_STORAGE` - Access audio files (Android 12 and below)

## Technologies Used

- **Kotlin** - Programming language
- **Jetpack Compose** - Modern UI toolkit
- **Media3 ExoPlayer** - Audio playback
- **Navigation Compose** - Navigation between screens
- **ViewModel & StateFlow** - State management
- **Coroutines** - Asynchronous operations

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Building for Release

To build a release APK:

```bash
./gradlew assembleRelease
```

The release APK will be located at `app/build/outputs/apk/release/app-release.apk`

## Privacy

This app:
- Stores preferences locally on your device
- Does not collect or transmit any personal data
- Does not require internet connection
- All audio files are stored locally

## License

This project is open source and available for use.

## Support

For issues, questions, or suggestions, please open an issue on GitHub.

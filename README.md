# VisionBoard

**Transform your transparent MacBook keyboard cover into a fully functional Bluetooth keyboard using only your Android phone's camera.**

VisionBoard uses computer vision, hand tracking, and Bluetooth HID to turn any flat surface with a transparent keyboard cover into a real typing input device. No desktop software required.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           app (Application)                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │  splash   │ │onboarding│ │calibration│ │  typing  │ │ pairing  │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ │
│       │             │            │             │            │        │
│  ┌────┴─────────────┴────────────┴─────────────┴────────────┴────┐  │
│  │                     ui/theme + ui/components                  │  │
│  └───────────────────────────┬───────────────────────────────────┘  │
├──────────────────────────────┼──────────────────────────────────────┤
│  domain (Use Cases)          │                                      │
│  ┌───────────┐ ┌────────────┐│┌───────────┐ ┌──────────────────┐   │
│  │calibration │ │ tracking   │││ bluetooth  │ │    settings      │   │
│  └─────┬─────┘ └─────┬──────┘│└─────┬─────┘ └────────┬─────────┘   │
├────────┼──────────────┼───────┼──────┼─────────────────┼─────────────┤
│  repository           │       │      │                 │             │
│  ┌─────────┐ ┌────────┘  ┌────┘ ┌────┘          ┌─────┘             │
│  │calibration│ │settings  │stats │               │                   │
│  └─────┬───┘ └────┬──────┘└──┬──┘               │                   │
├────────┼──────────┼──────────┼───────────────────┼───────────────────┤
│  core modules     │          │                   │                   │
│  ┌─────┴───┐ ┌────┴──┐ ┌────┴───┐ ┌──────────┐ ┌┴──────────┐       │
│  │ camera  │ │ vision│ │tracking│ │ geometry │ │ bluetooth  │       │
│  │(CameraX)│ │(OpenCV│ │(Kalman │ │(Homograp │ │(HID Device)│       │
│  │         │ │MediaP)│ │Filter) │ │phy)      │ │            │       │
│  └─────────┘ └───────┘ └────────┘ └──────────┘ └────────────┘       │
│  ┌─────────┐ ┌───────┐ ┌────────┐ ┌──────────┐ ┌────────────┐       │
│  │network  │ │database│ │settings│ │  utils   │ │     di     │       │
│  │(WebSocket│ │(Room)  │ │(DataSt)│ │(Pool,Mntr│ │  (Hilt)   │       │
│  └─────────┘ └───────┘ └────────┘ └──────────┘ └────────────┘       │
├──────────────────────────────────────────────────────────────────────┤
│  core/common (Models, Extensions, Constants, Utilities)              │
└──────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| IDE | Android Studio |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt (Dagger) |
| Camera | CameraX |
| CV | OpenCV Android 4.9.0 |
| Hand Tracking | MediaPipe Tasks Vision |
| Geometry | Custom homography, perspective transforms |
| Storage | Room + DataStore |
| Concurrency | Coroutines + Flow |
| Bluetooth | Android Bluetooth HID Device API |
| Network | WebSocket (TCP) |

## Modules

| Module | Purpose |
|--------|---------|
| `app` | Application entry, navigation, manifest |
| `core:common` | Models, constants, extensions, utilities |
| `core:camera` | CameraX integration, frame analysis |
| `core:vision` | OpenCV detection, MediaPipe hands, keyboard layout |
| `core:tracking` | Kalman filter, finger tracking, touch detection, gestures |
| `core:geometry` | Homography, perspective transforms, feature matching, calibration |
| `core:bluetooth` | HID report descriptor, Bluetooth HID service |
| `core:network` | WebSocket server for Wi-Fi keyboard mode |
| `core:database` | Room database, DAOs, entities |
| `core:domain` | Use cases (calibration, tracking, bluetooth, settings) |
| `core:repository` | Data repositories (calibration, settings, stats) |
| `core:di` | Hilt dependency injection modules |
| `core:settings` | DataStore-based settings manager |
| `core:utils` | Battery optimizer, memory guard, logging, object pools |
| `ui:theme` | Material 3 theme, colors, typography |
| `ui:components` | Reusable composables (overlays, indicators, guides) |
| `ui:splash` | Animated splash screen |
| `ui:onboarding` | Multi-page onboarding flow |
| `ui:calibration` | Camera-based calibration with guided steps |
| `ui:typing` | Main typing screen with live camera |
| `ui:preview` | Standalone camera preview |
| `ui:pairing` | Bluetooth device pairing |
| `ui:settings` | App settings screen |
| `ui:developer` | Developer mode with live debug overlays |

## How It Works

### 1. Calibration
1. User places transparent keyboard cover on flat surface
2. App guides through 3-5 image captures from different angles
3. ORB features extracted and matched across images
4. Pairwise homographies computed and fused
5. Master keyboard layout with key polygons saved permanently

### 2. Live Typing
1. CameraX sends upright frames to the preview and the on-device model.
2. MediaPipe returns fingertip landmarks for up to two hands.
3. OpenCV estimates the keyboard outline, or the user selects its four corners.
4. A homography maps fingertip positions to the keyboard layout while each hand's motion is tracked independently.
5. The touch detector scores taps and resolves key polygons.
6. The app displays the last recognized key and sends it to a connected Bluetooth HID host.

### 3. Bluetooth HID
- Phone appears as a standard Bluetooth keyboard
- Real USB HID report descriptor (6-key rollover)
- Works with Windows, macOS, Linux, Android tablets
- No desktop companion software required

## Touch Detection Algorithm

The app does NOT simply check if a fingertip is inside a key polygon. It uses a multi-factor probability scoring system:

| Factor | Weight | Description |
|--------|--------|-------------|
| Velocity | 0.25 | Downward motion detected |
| Acceleration | 0.20 | Negative Y velocity increasing |
| Proximity | 0.20 | Distance to key centroid |
| Stability | 0.15 | Finger velocity below threshold for N frames |
| Dwell | 0.10 | Time spent near key |
| History | 0.10 | Motion trajectory analysis |

**Hysteresis**: Different thresholds for press (0.65) vs release (0.35) to prevent oscillation.

**Debouncing**: Configurable minimum time between same-key presses (default 150ms).

## Performance Targets

| Metric | Target |
|--------|--------|
| Frame Rate | 60 FPS |
| End-to-End Latency | < 40ms |
| Memory Usage | < 200 MB |
| Battery | Optimized with adaptive frame skipping |

## Key Features

- **Self-correcting homography** - Refines perspective transform continuously during typing
- **Intelligent occlusion handling** - Predicts keyboard location when partially hidden
- **Two-handed typing** - Tracks up to 2 hands simultaneously
- **Gesture shortcuts** - Thumb tap (space), pinch (backspace), two-finger tap (enter), swipes
- **Typing statistics** - WPM, accuracy, heatmaps
- **Developer mode** - Live overlays for camera feed, detections, homography grid, finger skeletons, velocity vectors, FPS, CPU/memory
- **Wi-Fi mode** - TCP/WebSocket fallback for devices without Bluetooth HID support
- **Calibration export/import** - Backup and share calibration data

## Hand tracking model

The app uses Google's [MediaPipe Hand Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker). Gradle downloads the official float16 v1 task bundle, verifies its SHA-256, and packages it in the APK. Inference runs on the phone and works offline after installation.

### Try live tracking

1. Open VisionBoard, grant camera access, and reach the typing screen. You can skip the guided capture to test live tracking.
2. Point the rear camera at your hands. Fingertip dots should follow your fingers even before you start a typing session.
3. Place an ANSI MacBook keyboard cover on a flat surface and fix the phone at an angle that shows both the full cover and finger motion. If the automatic outline misses it, tap **Set corners** and select top-left, top-right, bottom-right, and bottom-left.
4. Tap **Start typing**. The app shows the last recognized key. For Bluetooth output, tap the Bluetooth icon, allow visibility, then add the phone from the host computer's Bluetooth settings while the app stays open.

## Building

Use JDK 17 or 21 with Android SDK Platform 35 installed. Set `ANDROID_HOME` or let Android Studio generate `local.properties`.

```bash
# Clone and open in Android Studio
# Or build from command line:
./gradlew :app:assembleDebug

# Run tests
./gradlew :app:testDebugUnitTest

# Run instrumented tests
./gradlew :app:connectedDebugAndroidTest
```

## Requirements

- Android 9.0 (API 28) or higher
- Rear camera
- Bluetooth support
- Transparent MacBook keyboard cover (or similar)

## License

MIT License

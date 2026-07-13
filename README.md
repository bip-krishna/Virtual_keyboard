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
| Bluetooth | HID Device Profile (hidden API) |
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
1. CameraX captures frames at target FPS
2. Keyboard cover detected via contour detection (ORB fallback)
3. Camera pose estimated via homography
4. Frame warped into calibration space
5. MediaPipe Hands detects hand landmarks
6. Fingertips tracked via Kalman filter + temporal smoothing
7. Touch detected via 6-factor probability scoring (velocity, acceleration, dwell, stability, proximity, history)
8. Key pressed mapped via point-in-polygon
9. HID keycode generated and sent over Bluetooth

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

## Building

```bash
# Clone and open in Android Studio
# Or build from command line:
./gradlew assembleDebug

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Requirements

- Android 9.0 (API 28) or higher
- Rear camera
- Bluetooth support
- Transparent MacBook keyboard cover (or similar)

## License

MIT License

package com.visionboard.common.constants

import android.view.KeyEvent

object AppConstants {

    // ── Bluetooth UUIDs (BLUVID service) ──────────────────────────────────────

    object Bluetooth {
        val SERVICE_UUID = java.util.UUID.fromString("0000FE95-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_INPUT_UUID = java.util.UUID.fromString("00002A3D-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_OUTPUT_UUID = java.util.UUID.fromString("00002A3E-0000-1000-8000-00805F9B34FB")
        val CLIENT_CONFIG_DESCRIPTOR = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val DEVICE_NAME_PREFIX = "BLUVID"
        val RECONNECT_DELAY_MS = 3000L
        val CONNECTION_TIMEOUT_MS = 10_000L
        val MAX_RECONNECT_ATTEMPTS = 5
        val SCAN_TIMEOUT_MS = 12_000L
        val MTU_SIZE = 512
    }

    // ── Camera defaults ───────────────────────────────────────────────────────

    object Camera {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 30
        const val LOW_WIDTH = 640
        const val LOW_HEIGHT = 480
        const val MEDIUM_WIDTH = 1280
        const val MEDIUM_HEIGHT = 720
        const val HIGH_WIDTH = 1920
        const val HIGH_HEIGHT = 1080
        const val MAX_WIDTH = 2560
        const val MAX_HEIGHT = 1440
        const val CAMERA_FACING_BACK = 0
        const val CAMERA_FACING_FRONT = 1
        const val AUTO_EXPOSURE_TARGET = 150
        const val FOCUS_DISTANCE_METERS = 0.3f
        const val FRAME_FORMAT_NV21 = 17
    }

    // ── Touch thresholds ──────────────────────────────────────────────────────

    object Touch {
        const val TAP_MAX_DURATION_MS = 300L
        const val DOUBLE_TAP_MAX_INTERVAL_MS = 350L
        const val LONG_PRESS_MIN_DURATION_MS = 600L
        const val SWIPE_MIN_DISTANCE_PX = 80f
        const val SWIPE_MAX_DURATION_MS = 250L
        const val PINCH_MIN_DISTANCE_RATIO = 0.15f
        const val TAP_CONFIDENCE_THRESHOLD = 0.65f
        const val TIP_TRACKING_CONFIDENCE_THRESHOLD = 0.5f
        const val MAX_FINGERTIP_VELOCITY = 2000f
        const val TAP_RADIUS_PX = 30f
        const val NEIGHBOR_PROXIMITY_PX = 60f
    }

    // ── Kalman filter parameters ──────────────────────────────────────────────

    object Kalman {
        const val PROCESS_NOISE = 0.03f
        const val MEASUREMENT_NOISE = 0.1f
        const val ESTIMATION_ERROR = 1.0f
        const val STATE_DIMENSION = 4
        const val MEASUREMENT_DIMENSION = 2
        const val DELTA_TIME = 1.0f / 30f
        const val MIN_CONFIDENCE_FOR_UPDATE = 0.3f
        const val MAX_FRAMES_WITHOUT_DETECTION = 5
    }

    // ── HID keycodes – full US keyboard layout ────────────────────────────────

    object HIDKeycodes {
        val CHAR_MAP: Map<Char, Int> = buildMap {
            // Letters (lowercase → KeyEventCODE_* lowercase)
            put('a', KeyEvent.KEYCODE_A)
            put('b', KeyEvent.KEYCODE_B)
            put('c', KeyEvent.KEYCODE_C)
            put('d', KeyEvent.KEYCODE_D)
            put('e', KeyEvent.KEYCODE_E)
            put('f', KeyEvent.KEYCODE_F)
            put('g', KeyEvent.KEYCODE_G)
            put('h', KeyEvent.KEYCODE_H)
            put('i', KeyEvent.KEYCODE_I)
            put('j', KeyEvent.KEYCODE_J)
            put('k', KeyEvent.KEYCODE_K)
            put('l', KeyEvent.KEYCODE_L)
            put('m', KeyEvent.KEYCODE_M)
            put('n', KeyEvent.KEYCODE_N)
            put('o', KeyEvent.KEYCODE_O)
            put('p', KeyEvent.KEYCODE_P)
            put('q', KeyEvent.KEYCODE_Q)
            put('r', KeyEvent.KEYCODE_R)
            put('s', KeyEvent.KEYCODE_S)
            put('t', KeyEvent.KEYCODE_T)
            put('u', KeyEvent.KEYCODE_U)
            put('v', KeyEvent.KEYCODE_V)
            put('w', KeyEvent.KEYCODE_W)
            put('x', KeyEvent.KEYCODE_X)
            put('y', KeyEvent.KEYCODE_Y)
            put('z', KeyEvent.KEYCODE_Z)

            // Uppercase (shifted)
            put('A', KeyEvent.KEYCODE_A)
            put('B', KeyEvent.KEYCODE_B)
            put('C', KeyEvent.KEYCODE_C)
            put('D', KeyEvent.KEYCODE_D)
            put('E', KeyEvent.KEYCODE_E)
            put('F', KeyEvent.KEYCODE_F)
            put('G', KeyEvent.KEYCODE_G)
            put('H', KeyEvent.KEYCODE_H)
            put('I', KeyEvent.KEYCODE_I)
            put('J', KeyEvent.KEYCODE_J)
            put('K', KeyEvent.KEYCODE_K)
            put('L', KeyEvent.KEYCODE_L)
            put('M', KeyEvent.KEYCODE_M)
            put('N', KeyEvent.KEYCODE_N)
            put('O', KeyEvent.KEYCODE_O)
            put('P', KeyEvent.KEYCODE_P)
            put('Q', KeyEvent.KEYCODE_Q)
            put('R', KeyEvent.KEYCODE_R)
            put('S', KeyEvent.KEYCODE_S)
            put('T', KeyEvent.KEYCODE_T)
            put('U', KeyEvent.KEYCODE_U)
            put('V', KeyEvent.KEYCODE_V)
            put('W', KeyEvent.KEYCODE_W)
            put('X', KeyEvent.KEYCODE_X)
            put('Y', KeyEvent.KEYCODE_Y)
            put('Z', KeyEvent.KEYCODE_Z)

            // Digits
            put('0', KeyEvent.KEYCODE_0)
            put('1', KeyEvent.KEYCODE_1)
            put('2', KeyEvent.KEYCODE_2)
            put('3', KeyEvent.KEYCODE_3)
            put('4', KeyEvent.KEYCODE_4)
            put('5', KeyEvent.KEYCODE_5)
            put('6', KeyEvent.KEYCODE_6)
            put('7', KeyEvent.KEYCODE_7)
            put('8', KeyEvent.KEYCODE_8)
            put('9', KeyEvent.KEYCODE_9)

            // Space & punctuation
            put(' ', KeyEvent.KEYCODE_SPACE)
            put('.', KeyEvent.KEYCODE_PERIOD)
            put(',', KeyEvent.KEYCODE_COMMA)
            put(';', KeyEvent.KEYCODE_SEMICOLON)
            put(':', KeyEvent.KEYCODE_COLON)
            put('!', KeyEvent.KEYCODE_EXCLAMATION)
            put('?', KeyEvent.KEYCODE_QUESTION_MARK)
            put('\'', KeyEvent.KEYCODE_APOSTROPHE)
            put('"', KeyEvent.KEYCODE_APOSTROPHE)
            put('-', KeyEvent.KEYCODE_MINUS)
            put('_', KeyEvent.KEYCODE_MINUS)
            put('+', KeyEvent.KEYCODE_EQUALS)
            put('=', KeyEvent.KEYCODE_EQUALS)
            put('/', KeyEvent.KEYCODE_SLASH)
            put('\\', KeyEvent.KEYCODE_BACKSLASH)
            put('(', KeyEvent.KEYCODE_9)
            put(')', KeyEvent.KEYCODE_0)
            put('[', KeyEvent.KEYCODE_LEFT_BRACKET)
            put(']', KeyEvent.KEYCODE_RIGHT_BRACKET)
            put('{', KeyEvent.KEYCODE_LEFT_BRACKET)
            put('}', KeyEvent.KEYCODE_RIGHT_BRACKET)
            put('@', KeyEvent.KEYCODE_AT)
            put('#', KeyEvent.KEYCODE_POUND)
            put('$', KeyEvent.KEYCODE_DOLLAR)
            put('%', KeyEvent.KEYCODE_PERCENT)
            put('^', KeyEvent.KEYCODE_STAR)
            put('&', KeyEvent.KEYCODE_AMPERSAND)
            put('*', KeyEvent.KEYCODE_STAR)
            put('~', KeyEvent.KEYCODE_GRAVE)
            put('`', KeyEvent.KEYCODE_GRAVE)
            put('<', KeyEvent.KEYCODE_COMMA)
            put('>', KeyEvent.KEYCODE_PERIOD)
            put('|', KeyEvent.KEYCODE_BACKSLASH)
            put('\t', KeyEvent.KEYCODE_TAB)
            put('\n', KeyEvent.KEYCODE_ENTER)
        }

        val SHIFTED_CHARS: Set<Char> = setOf(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '+',
            '{', '}', '|', ':', '"', '<', '>', '?'
        )

        const val SPECIAL_KEY_SPACE = KeyEvent.KEYCODE_SPACE
        const val SPECIAL_KEY_BACKSPACE = KeyEvent.KEYCODE_DEL
        const val SPECIAL_KEY_ENTER = KeyEvent.KEYCODE_ENTER
        const val SPECIAL_KEY_TAB = KeyEvent.KEYCODE_TAB
        const val SPECIAL_KEY_SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
        const val SPECIAL_KEY_CTRL = KeyEvent.KEYCODE_CTRL_LEFT
        const val SPECIAL_KEY_ALT = KeyEvent.KEYCODE_ALT_LEFT
        const val SPECIAL_KEY_ESC = KeyEvent.KEYCODE_ESCAPE
        const val SPECIAL_KEY_DELETE = KeyEvent.KEYCODE_FORWARD_DEL
        const val SPECIAL_KEY_CAPS_LOCK = KeyEvent.KEYCODE_CAPS_LOCK
        const val SPECIAL_KEY_UP = KeyEvent.KEYCODE_DPAD_UP
        const val SPECIAL_KEY_DOWN = KeyEvent.KEYCODE_DPAD_DOWN
        const val SPECIAL_KEY_LEFT = KeyEvent.KEYCODE_DPAD_LEFT
        const val SPECIAL_KEY_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT
    }

    // ── Debounce times ────────────────────────────────────────────────────────

    object Debounce {
        const val KEY_PRESS_MS = 50L
        const val KEY_REPEAT_MS = 400L
        const val KEY_REPEAT_INTERVAL_MS = 50L
        const val SETTINGS_CHANGE_MS = 300L
        const val BLUETOOTH_SCAN_MS = 2000L
        const val CALIBRATION_CAPTURE_MS = 800L
        const val UI_UPDATE_MS = 16L
        const val FEATURE_DETECTION_MS = 100L
    }

    // ── Feature detection parameters ──────────────────────────────────────────

    object FeatureDetection {
        const val MIN_FEATURES_DETECTED = 8
        const val GOOD_MATCH_RATIO = 0.75f
        const val RANSAC_THRESHOLD = 5.0f
        const val MIN_MATCH_COUNT = 10
        const val ORB_FEATURES = 500
        const val FAST_THRESHOLD = 20
        const val ORB_SCALE_FACTOR = 1.2f
        const val ORB_PYRAMID_LEVELS = 3
        const val BRUTE_FORCE_MATCHER_NORM = 6 // NORM_HAMMING for ORB
        const val FLANN_INDEX_KDTREE = 1
        const val FLANN_TREES = 5
        const val FLANN_CHECKS = 50
        const val MAX_KEYPOINT_SIZE = 2000
        const val MIN_CONTOUR_AREA = 500.0
        const val APPROX_POLYGON_EPSILON_RATIO = 0.02
    }

    // ── Calibration ───────────────────────────────────────────────────────────

    object Calibration {
        val IMAGE_COUNT_RANGE = 3..5
        const val DEFAULT_IMAGE_COUNT = 4
        const val CHECKERBOARD_COLS = 9
        const val CHECKERBOARD_ROWS = 6
        const val SQUARE_SIZE_MM = 25.0f
        const val HOMOGRAPHY_METHOD = 8 // RANSAC
        const val MIN_CORNERS_FOR_HOMOGRAPHY = 4
        const val CORNER_SUBPIX_WINDOW_SIZE = 11
        const val CORNER_SUBPIX_ZERO_ZONE = -1
        const val CORNER_SUBPIX_CRITERIA_MAX_ITER = 30
        const val CORNER_SUBPIX_CRITERIA_EPSILON = 0.001
        const val CONFIDENCE_THRESHOLD = 0.85f
    }

    // ── Homography ────────────────────────────────────────────────────────────

    object Homography {
        const val MAX_ITERATIONS = 50
        const val CONVERGENCE_THRESHOLD = 1e-6f
        const val MIN_INLIERS = 4
        const val REPROJECTION_THRESHOLD = 5.0f
    }

    // ── FPS targets ───────────────────────────────────────────────────────────

    object FPS {
        const val TARGET_FPS = 30
        const val MIN_ACCEPTABLE_FPS = 15
        const val HIGH_PERFORMANCE_FPS = 60
        const val LOW_POWER_FPS = 15
        const val FPS_UPDATE_INTERVAL_MS = 500L
        const val LATENCY_WARNING_MS = 50f
        const val LATENCY_CRITICAL_MS = 100f
        const val SMOOTHING_FACTOR = 0.1f
    }

    // ── Memory limits ─────────────────────────────────────────────────────────

    object Memory {
        const val MAX_MAT_POOL_SIZE = 30
        const val MAX_POINTF_POOL_SIZE = 200
        const val MAX_RECT_POOL_SIZE = 50
        const val FRAME_BUFFER_POOL_SIZE = 5
        const val MAX_FRAME_CACHE_SIZE = 3
        const val GC_PRESSURE_THRESHOLD_MB = 150L
        const val LOW_MEMORY_THRESHOLD_MB = 100L
        const val CRITICAL_MEMORY_THRESHOLD_MB = 50L
        const val MEMORY_CHECK_INTERVAL_MS = 5000L
        const val MAX_BITMAP_CACHE_SIZE_MB = 64L
        const val IMAGE_BUFFER_SIZE_BYTES = 1024 * 1024
    }

    // ── Networking / WiFi mode ────────────────────────────────────────────────

    object Network {
        const val SERVER_PORT = 8765
        const val CLIENT_PORT = 8766
        const val CONNECTION_TIMEOUT_MS = 5000L
        const val READ_TIMEOUT_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 10_000L
        const val HEARTBEAT_INTERVAL_MS = 2000L
        const val MAX_RETRY_COUNT = 3
        const val BUFFER_SIZE = 4096
    }

    // ── Hand tracking ─────────────────────────────────────────────────────────

    object HandTracking {
        const val MAX_HANDS = 2
        const val FINGERTIP_COUNT = 5
        const val HAND_LANDMARK_COUNT = 21
        const val SMOOTHING_WINDOW_SIZE = 5
        const val VELOCITY_WINDOW_SIZE = 3
        const val MIN_TRACKING_CONFIDENCE = 0.5f
        const val MIN_DETECTION_CONFIDENCE = 0.6f
        const val MODEL_COMPLEXITY = 1
    }

    // ── UI constants ──────────────────────────────────────────────────────────

    object UI {
        const val OVERLAY_ALPHA = 0.35f
        const val KEY_HIGHLIGHT_ALPHA = 0.6f
        const val ANIMATION_DURATION_MS = 200L
        const val CALIBRATION_GUIDE_ALPHA = 0.8f
        const val MIN_TOUCH_TARGET_SIZE_DP = 48
        const val KEY_CORNER_RADIUS_DP = 8
    }
}

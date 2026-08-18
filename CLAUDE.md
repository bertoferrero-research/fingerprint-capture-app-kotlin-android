# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

Android research app (Kotlin) used to build a **BLE RSSI fingerprinting dataset with ground-truth position derived from ArUco/ChArUco marker vision**. It is not a production app — it's a data-collection and offline-processing tool for a doctoral research project. Two ground-truth strategies exist:

- **Offline capture**: BLE RSSI is logged continuously while the operator manually walks to known, fixed (x, y, z) waypoints entered by hand — no camera involved. Fast/cheap but coarse ground truth.
- **Online capture**: BLE RSSI and camera frames are logged simultaneously while moving freely; position is computed later by detecting ArUco markers with known world coordinates and back-projecting the camera pose. Slower to process but gives continuous, precise ground truth.

## Build, lint, test

Standard Gradle Android project (single module `:app`). From the repo root, use the wrapper:

```
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.bertoferrero.fingerprintcaptureapp.ExampleUnitTest"   # single test
./gradlew connectedAndroidTest   # instrumented tests (app/src/androidTest), needs a device/emulator
./gradlew lint                   # Android lint
```

On Windows use `gradlew.bat` instead of `./gradlew`. There are essentially no real tests yet (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt` are template stubs) — the "Development Tests" screens inside the app (see below) are the actual validation mechanism for detection/positioning algorithms, run manually on-device.

Requires the OpenCV Android SDK dependency (`org.opencv:opencv:4.11.0`, AAR from Maven) and a local Android SDK configured via `local.properties` (`sdk.dir`, machine-specific, not checked in). `minSdk = 24`, `targetSdk = compileSdk = 35`, Kotlin `2.1.0`, AGP `8.9.1`, Java 11 bytecode target.

## App navigation structure

Navigation uses **Voyager** (`Screen` classes + `Navigator`/`FadeTransition`), no Jetpack Navigation graph. `MainActivity` just hosts a `Navigator(MainScreen())`. `MainScreen` fans out into 5 sections, each its own `Screen`:

- **Data Capture** → `OfflineCaptureScreen`, `OnlineCaptureScreen`
- **Data Processing** → `BatchArucoProcessingScreen`, `OnlineSamplePostprocessingScreen`
- **Tools** (`ToolsScreen`) → `CameraSamplerScreen` (ChArUco calibration frame sampler), `TestRssiMonitorScreen` (live BLE RSSI viewer), `MatPhotoCompressionToolScreen`
- **Configuration** (`ConfigurationScreen`) → camera calibration parameters, ArUco/detection settings (backed by `SettingsParametersManager`)
- **Development Tests** (`TestsScreen`) → manual on-device validation screens for distance estimation, trilateration, and ArUco pose/rotation — this is where algorithm changes should be sanity-checked visually since there's no automated test coverage

Screens under `views/**` are thin Compose UI; state and logic live in matching `viewmodels/**` (AndroidViewModel-based, no Hilt — DI was attempted and abandoned, see comment in `OfflineCaptureViewModel`). Business logic that isn't UI state lives one layer deeper in `controllers/**`.

## Core pipelines

### 1. BLE capture (`lib/BleScanner.kt`, `services/RssiCaptureService.kt`)

`RssiCaptureService` is a **foreground `Service`** (not tied to any Activity/Compose lifecycle) so capture survives backgrounding — it acquires a `PARTIAL_WAKE_LOCK` and requests battery-optimization exemption. It drives a `BleScanner`, which wraps Android's `BluetoothLeScanner` configured for research-grade capture: `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, `reportDelay = 0` (no batching — every advertisement is reported). Filtering has two tiers: exact MAC addresses go through Android's **hardware `ScanFilter`** (cheap, chip-level, capped at ~8-16 filters); MAC *prefixes* (e.g. manufacturer OUIs in `BleScanner.MacPrefixes`) are filtered in software in `onResultReceived`. See `docs/HARDWARE_FILTERING_GUIDE.md` for the rationale and the CSV schema.

Each RSSI sample is written immediately (no in-memory buffering) to **two CSV files per capture session**: a combined `..._all.csv` and one per-MAC file, both under the user-chosen `DocumentFile` output folder (SAF, not raw file paths — this app writes to user-granted tree URIs throughout). Columns: `timestamp,time,mac_address,rssi,tx_power,pos_x,pos_y,pos_z`. In offline mode `pos_x/y/z` are the fixed waypoint the operator entered; in online mode they're placeholders filled in later by postprocessing.

### 2. Camera capture & the `.matphoto` format (`controllers/capture/OnlineCaptureController.kt`, `lib/opencv/MatFileInputOutput.kt`)

Online capture samples OpenCV `Mat` frames from the camera preview at a configurable frequency and persists each one as a custom **`.matphoto`** file: a GZIP stream wrapping a `"GZIP"` marker + raw rows/cols/OpenCV-type header + raw pixel bytes (see `MatToFile`/`MatFromFile`). This preserves the exact `Mat`, not a lossy re-encode. A JPEG preview is saved alongside for human inspection but isn't used in processing. Saving happens off the capture thread through a bounded-concurrency (`Semaphore(2)`) coroutine queue (`OnlineCaptureController`) so disk I/O never blocks frame capture; on shutdown the queue is drained synchronously before the controller finishes.

Camera rendering goes through OpenCV's Android camera bridge (`FixedFocusJavaCamera2View`, wrapped by `views/components/OpenCvCamera.kt`), not CameraX — CameraX (`views/components/OpenCvCamera.kt`'s `RenderNativeCameraView`) exists in the code but is unused/dead, kept from an earlier experiment. Screens implement `ICameraController` (`initProcess`/`finishProcess`/`processFrame`) and get frames pushed into them by the camera bridge each frame; controllers exist per use case (calibration, batch calibration, online capture, distance testing, positioning/rotation testing, trilateration testing).

### 3. ArUco detection & pose (`lib/markers/MarkersDetector.kt`, `lib/positioning/GlobalPositioner.kt`)

`MarkersDetector` wraps OpenCV's `ArucoDetector` with detection parameters tuned specifically to reduce false positives and pose ambiguity at long range (>5m) — see `docs/ARUCO_DETECTION_PROPOSAL.md` (original recommendations) and `docs/ARUCO_DETECTION_IMPLEMENTATION.md` (what was actually implemented, with an open checklist) for the full rationale. Key points for anyone touching this file:

- Pose is estimated with `solvePnPGeneric` (`SOLVEPNP_IPPE_SQUARE`) to get *both* geometrically valid planar-marker solutions, then disambiguated by (a) discarding negative-Z (behind-camera) solutions and (b) picking the lowest **manually recomputed** reprojection error (`calculateReprojectionError`) rather than trusting OpenCV's own error output — this was found to be more reliable.
- Pose is then refined with `solvePnPRefineLM`, falling back to `solvePnPRefineVVS`, falling back to leaving it unrefined, wrapped in try/catch since availability varies by OpenCV build.
- Additional rejection filters (only applied when the caller passes thresholds — `markerMaxAngle`, `markerMinPixelSize`): viewing-angle cheirality (reject markers seen at >75° off-normal) and minimum pixel size (reject markers whose detected width is too small to trust the pose).
- `detectMarkers.kt`'s free-function `detectMarkers(...)` is a **deprecated compatibility wrapper** around `MarkersDetector` — new code should instantiate `MarkersDetector` directly.

`GlobalPositioner` takes per-frame detected markers plus the world-space `MarkerDefinition` config (position + roll/pitch/yaw in degrees) and inverts marker-relative camera pose into world-frame camera position: `R_cam_marker = R_marker_cam.transpose()`, then `t_cam_world = R_marker_world * t_cam_marker + t_marker_world`. When multiple markers are visible, candidate positions go through **adaptive-threshold RANSAC** (`ransacFilterPositions` in `RansacFilter.kt`) — if no consensus is found at the initial threshold, the threshold is relaxed in steps up to an optional max — then combined via `MultipleMarkersBehaviour` (`CLOSEST`, `AVERAGE`/`WEIGHTED_AVERAGE`, `MEDIAN`/`WEIGHTED_MEDIAN`). Positions with any negative world coordinate are discarded as invalid. All of this logs verbosely via `android.util.Log` at each stage — that's intentional for offline debugging of position jumps, not leftover noise to clean up.

### 4. Offline postprocessing (`viewmodels/processing/*`, `controllers/processing/ArucoProcessingController.kt`)

- **Batch ArUco Processing**: standalone tool to run marker detection over a folder of images and dump a position CSV — used to validate detection/calibration independent of a capture session.
- **Online Sample Postprocessing** (`OnlineSamplePostprocessingViewModel`) is the pipeline that turns an online-capture session into a labeled dataset: it reads the RSSI `..._all.csv`, and for each RSSI sample looks at all `.matphoto` images whose timestamp falls in the preceding `timeWindow` milliseconds, runs them all through `ArucoProcessingController.processImageFiles` (pooling markers detected across every image in the window before computing one global position — more markers → better RANSAC/averaging), and writes the resolved `pos_x/y/z` back into the RSSI row. Position results are **cached and reused** across RSSI rows that share the same image window (cache key = min/max image timestamp in the window) to avoid recomputing identical detections. A parallel `all_processing_stats.csv` records per-image and per-marker diagnostics (RANSAC population, exclusions, thresholds actually used) for later analysis of why a position did or didn't resolve.

### 5. Camera calibration (`controllers/cameracontroller/CalibrationCameraController.kt`, `BatchCalibrationController.kt`)

Calibration uses a **ChArUco board** (not a plain checkerboard), detected via `CharucoDetector`. Calibration parameters (camera matrix + distortion coefficients) are serialized (`MatSerialization`) into `SharedPreferences` (`CameraCalibrationParameters`), not files — they're a persistent per-device setting, exportable/importable as a map for backup. `MarkerDefinition`/marker size, ArUco dictionary, and ChArUco board geometry defaults live in `SettingsParametersManager`.

## Conventions to know before editing

- **All file I/O for capture/processing output uses `DocumentFile`/SAF tree URIs**, not `java.io.File` paths — the user grants a folder via the system picker. Keep new file-writing code consistent with this (see `openCsvFile`, `saveImageAsync`).
- **Comments and logs in this codebase are mostly Spanish**; UI strings and identifiers are English. Match the existing convention of whichever file you're editing rather than switching languages within a file.
- Positioning/detection code is deliberately defensive (try/catch around OpenCV calls that may not exist in every build, e.g. `solvePnPGeneric`/`solvePnPRefineLM`) because the app has been developed against evolving `4.11.0`-era OpenCV APIs — don't remove these fallbacks without checking they're truly dead.
- `MultipleMarkersBehaviour`, RANSAC thresholds, marker max-angle/min-pixel-size, and time-window are all exposed as tunable parameters through the UI (postprocessing/test screens) specifically so they can be swept experimentally — when adding a new detection/positioning knob, prefer wiring it through the same pattern (ViewModel state → controller constructor/update method) rather than hardcoding.
- No dependency injection framework is used (Hilt was tried and abandoned — see the comment in `OfflineCaptureViewModel.kt`); `AndroidViewModel` + manual construction is the pattern throughout.

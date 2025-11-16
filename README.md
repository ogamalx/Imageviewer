# ImgArchiveViewer (Android)

Small Android app to inspect and convert Android sparse `.img` files.

## Compatibility

- **Minimum Android Version:** Android 8.0 (API 26)
- **Target Android Version:** Android 15 (API 35)
- **Tested on:** Android 15, Samsung Galaxy A26 5G (SM-A26)

## Features

- Open `.img` files from a file picker or via "Open with" in file managers.
- Detect whether an image is:
  - Android **sparse** image, or
  - plain **raw** image.
- Show basic info:
  - Size
  - Block size
  - Total blocks
  - Total chunks
  - Calculated raw size
- Convert sparse images to **raw** images and save as `<original>.raw.img`.

## Usage

1. Install the APK (see *Build & CI* below).
2. Launch the app.
3. Tap **Open .img** and pick a file.
4. If it’s a sparse image, press **Convert Sparse → RAW** to generate a raw `.img`.

You can also open images directly from a file manager via "Open with ImgArchiveViewer".

## Build & CI

### Local build (Android Studio)

1. Open this repo in Android Studio (Giraffe or later).
2. Let it sync Gradle.
3. Run the `app` configuration on a device or emulator.

### Command line

```bash
./gradlew assembleDebug
```

Resulting APK: `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions

The workflow at [`.github/workflows/android.yml`](.github/workflows/android.yml):

- Runs on pushes and pull requests to `main`.
- Builds the `assembleDebug` APK.
- Uploads it as the `app-debug-apk` artifact.

You can download the latest build from the **Actions** tab.
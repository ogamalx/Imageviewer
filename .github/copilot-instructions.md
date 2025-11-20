# AI Coding Instructions for ImgArchiveViewer

## Project Overview
Android app for inspecting and converting Android sparse `.img` files to raw format. Built for personal use on rooted Samsung Galaxy A26 5G. Single-activity architecture with no fragments or navigation components.

## Architecture & Patterns

### Single-File Activity Pattern
- **All logic in `MainActivity.kt`** (~350 lines): Activity, parser, and data model in one file
- No separate ViewModel, Repository, or UseCase layers
- Direct coroutine launches from `lifecycleScope` for I/O operations
- `SparseImageParser` object for stateless parsing utilities
- Data class `SparseImageInfo` for header metadata

### Streaming I/O Architecture
- Uses Android Storage Access Framework (SAF) with `DocumentFile` and `ContentResolver`
- **Never loads entire files into memory** - streams chunk-by-chunk using 1MB buffer (`1 shl 20`)
- Persistent URI permissions via `takePersistableUriPermission()` for file access
- Progress callbacks during conversion: `suspend (Long) -> Unit` for bytes written

### Binary Format Handling
- Little-endian ByteBuffer operations for Android sparse image format
- Magic number: `0xED26FF3A`
- Chunk types: RAW (`0xCAC1`), FILL (`0xCAC2`), DONT_CARE (`0xCAC3`), CRC32 (`0xCAC4`)
- See `SparseImageParser.convertToRaw()` for chunk processing logic

## Build & Environment

### Gradle Configuration
- **Java 17** target (not 11 or 21) - set in `app/build.gradle.kts`
- Kotlin 1.9.24, Android Gradle Plugin 8.4.2
- ViewBinding enabled (no findViewById except in `onCreate`)
- Min SDK 26, Target/Compile SDK 35

### Build Commands (Windows PowerShell)
```powershell
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK (unsigned)
./gradlew clean            # Clean build artifacts
```

### GitHub Actions CI
- Workflow: `.github/workflows/android.yml`
- Generates Gradle wrapper on CI (not committed to repo)
- Java 17 Temurin distribution
- Uploads `app-debug-apk` artifact

## Code Conventions

### Coroutines & Lifecycle
- Use `lifecycleScope.launch` for UI-triggered I/O (button clicks)
- Wrap I/O in `withContext(Dispatchers.IO)` - see `convertSparseToRawInternal()`
- Progress updates via `withContext(Dispatchers.Main)` for TextView updates

### Activity Result APIs
- `registerForActivityResult` for file picking (`OpenDocument`) and saving (`CreateDocument`)
- Store pending URIs in nullable properties (e.g., `pendingSparseUri`) between picker and result

### Intent Filters
- Handles `ACTION_VIEW` for `.img` files from file managers
- Pattern: `.*\\.img` with MIME type `*/*` (broad matcher)

### UI State Management
- Direct TextView text updates (no LiveData/StateFlow for this simple app)
- Button visibility toggling: `btnConvert.visibility = Button.VISIBLE/GONE`
- Disable buttons during long operations: `btnConvert.isEnabled = false`

## Dependencies
- AndroidX Core KTX, AppCompat, Material Design
- `androidx.documentfile:documentfile` for SAF
- `androidx.lifecycle:lifecycle-runtime-ktx` for coroutines
- No Hilt, Room, Retrofit, or Compose - keep it minimal

## Testing
- No tests currently exist in codebase
- If adding: Use JUnit 4 + Android instrumentation tests with `AndroidJUnitRunner`

## When Making Changes
- **Explain why** if modifying sparse image parsing logic - format is tricky
- Test with actual sparse `.img` files (system.img, vendor.img, etc.)
- Verify file size calculations: `blockSize * totalBlocks` for raw size
- Ensure streaming I/O still works - don't load entire files into memory

# Building the APK

## Prerequisites

### Java 17 JDK Required
This project requires Java 17 to build. Install one of:

1. **Adoptium Temurin 17** (Recommended)
   - Download: https://adoptium.net/temurin/releases/?version=17
   - Install and add to PATH

2. **Oracle JDK 17**
   - Download: https://www.oracle.com/java/technologies/downloads/#java17

3. **Android Studio** (easiest option)
   - Includes bundled JDK
   - Just open project and build

### After Installing Java
Set JAVA_HOME environment variable:
```powershell
# Windows PowerShell (as Administrator)
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot', 'Machine')
```

Or add it to your user environment variables via System Properties.

## Build Commands

```powershell
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK (unsigned)
./gradlew clean            # Clean build
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Icon Assets
The app uses vector drawable icons that work across all density buckets. If you need custom PNG icons, replace the files in:
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.xml`

This directory contains fastlane metadata for the Google Play Store, managed via `supply`.

See https://docs.fastlane.tools/actions/supply/ for full documentation.

## Directory Structure

```
metadata/android/en-US/
├── title.txt              # App title (max 30 chars)
├── short_description.txt  # Play Store short description (max 80 chars)
├── full_description.txt   # Play Store full description (max 4000 chars, HTML supported)
├── images/
│   ├── icon.png           # App icon (512x512)
│   ├── featureGraphic.png # Feature graphic (1024x500)
│   └── phoneScreenshots/  # Phone screenshots (1-8)
└── changelogs/
    └── {versionCode}.txt  # Per-version release notes (max 500 chars)
```

## Usage

Upload metadata to Play Store:
```
fastlane supply --skip_upload_apk --skip_upload_aab
```

Upload metadata with an APK:
```
fastlane supply --apk path/to/app-release.apk
```

Initialize/download existing metadata:
```
fastlane supply init
```

## Changelogs

Changelogs are named by `versionCode` from `app/build.gradle`. The build script at `app/build.gradle` copies the changelog for the current versionCode into `app/build/changelog.txt` for use in GitHub Releases.

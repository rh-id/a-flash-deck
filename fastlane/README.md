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

## Supported Locales

| Locale | Language |
|--------|----------|
| en-US  | English |
| id     | Indonesian |
| de-DE  | German |
| fr-FR  | French |
| it-IT  | Italian |
| nb-NO  | Norwegian Bokmål |
| nn-NO  | Norwegian Nynorsk |
| is-IS  | Icelandic |
| et     | Estonian |
| rm     | Romansh |
| zh-CN  | Chinese (Simplified) |

Each locale contains `title.txt`, `short_description.txt`, `full_description.txt`, changelogs, and images.

## Screenshots

Each locale directory contains 7 phone screenshots (1080x1920), captured from
an AOSP emulator loaded with demo data, with the system locale set so the app
UI appears translated for that locale. The shots show, in order:

1. Home menu ("Study due cards")
2. Deck list
3. Test question card
4. Test answer card with grade buttons
5. Statistics page with review history
6. Gemini deck-generation page with a topic entered
7. Review reminder notification (expanded, with the card's picture)

When refreshing screenshots, keep the set at 7 files with stable numbering so
existing documentation and release notes stay valid. The notification shot
(7.png) should be checked visually — a broken capture can look structurally
identical to a good one.

## Graphics Sources

`graphics/launcher.svg` is the source design of the launcher/store icon
(export to 512x512 PNG for `icon.png`), and `graphics/featuredGraphic.svg` is
the source of the 1024x500 feature graphic. Export them with Inkscape; the
feature graphic embeds rasterized app screenshots, so refresh it when the
screenshots change significantly.

## Changelogs

Changelogs are named by `versionCode` from `app/build.gradle`. The build script at `app/build.gradle` copies the changelog for the current versionCode into `app/build/changelog.txt` for use in GitHub Releases.

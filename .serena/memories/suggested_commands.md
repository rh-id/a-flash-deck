# Suggested commands (Windows host, Git Bash default shell)

## Build / test
- Full build incl. lint + JVM unit tests (what CI runs on push/PR to master): `./gradlew build`
- Debug APK: `./gradlew assembleDebug`
- Instrumented tests (needs running emulator; CI matrix api 23 & 29): `./gradlew connectedCheck`
- One module's unit tests: `./gradlew :base:testDebugUnitTest`
- On cmd/PowerShell use `gradlew.bat` instead of `./gradlew`.

## Deck tooling (native import format)
- Build importable deck zip from CSV/TSV (needs `question`/`answer` columns) or authoring JSON: `python scripts/build_deck.py build --input cards.csv --name "Deck name" --output Decks.zip`
- Minify pretty deck JSON to the required single-line form: `python scripts/build_deck.py fix --input Decks-pretty.json --output Decks.json`
- Zip must contain exactly `Decks.json` (case-sensitive), single-line JSON; schema reference: `docs/deck-json-schema.md`.

## Release sequence
1. Bump versionCode/versionName in `app/build.gradle`.
2. Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for ALL 11 locales: de-DE, en-US, et, fr-FR, id, is-IS, it-IT, nb-NO, nn-NO, rm, zh-CN.
3. Commit (conventional style), tag `vX.Y.Z`, push tag → `android-release.yml` builds, signs, attaches `app-debug.apk`, `app-release.apk`, and changelog (copied from en-US changelog by `app/build.gradle` afterEvaluate) to a GitHub release.
- Play Store metadata upload only: `fastlane supply --skip_upload_apk --skip_upload_aab`.

## Hazards
- Repo-root file `nul` is a Windows reserved-name artifact (gitignored but tracked). Never try to read/delete it casually on Windows.
- Never hand-edit `app/src/main/assets/licenses.html` — generated from `app/licenses.yml`.

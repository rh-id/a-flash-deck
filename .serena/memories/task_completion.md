# Task completion checklist

- `./gradlew build` passes — this is CI's gate on master (lint + JVM unit tests). Minimum bar for any change.
- Room changes (entity/DAO/column): bump `AppDatabase` version, add auto-migration, commit updated exported schema under `base/schemas/` (or `bot/schemas/`), then run `./gradlew connectedCheck` on an emulator — `DbMigrationTest` depends on the exported schemas.
- Anki import/export changes: run `:app` androidTest (`AnkiRoundTripTest`, `AnkiImporterTest`, `AnkiExporterTest`, `ApkgParserTest`) via `./gradlew :app:connectedCheck`.
- New/changed user-visible strings: add to `values/` and all `values-*` locale folders; Play Store text changes to all 11 `fastlane/metadata/android/<locale>/` folders.
- Dependency/toolchain changes: update ext pins in root `build.gradle`; keep Gradle wrapper pin, daemon JDK 21 pin, and desugaring intact.
- Releases only via the full sequence in `mem:suggested_commands` (versionCode bump + changelog for all 11 locales + `v*` tag).

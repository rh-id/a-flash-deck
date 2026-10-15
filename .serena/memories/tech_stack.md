# Tech stack

## Language / toolchain
- 100% Java (zero .kt files); source/target compatibility 8 + core-library desugaring (`desugar_jdk_libs:2.1.5`) in every module — do not use Java-9+ APIs or Kotlin.
- Gradle 9.8.0 wrapper (pinned distributionSha256Sum), AGP 9.4.1, daemon JDK pinned to 21 via `gradle/gradle-daemon-jvm.properties` (JetBrains vendor, foojay URLs). CI also uses JDK 21; needs a recent Android Studio.
- SDK: minSdk 23, targetSdk/compileSdk 37. applicationId `m.co.rh.id.a_flash_deck`. versionCode/versionName live in `app/build.gradle`.
- `gradle.properties`: `-Xmx2048m`, `android.useAndroidX=true`, `android.nonTransitiveRClass=false`, parallel disabled.

## Versions (version catalog `gradle/libs.versions.toml`)
- a-navigator v0.0.73, a-provider v0.0.27, a-logger v0.0.4, rx-utils v0.0.3, concurrent-utils v0.0.4 (all JitPack, exposed from `:base`), room 2.8.5, work 2.11.2, markwon 4.6.2.

## Frameworks / libraries
- DI + navigation: JitPack libs by the same author — `com.github.rh-id:a-provider` (service-locator DI container), `a-navigator` (StatefulView stack), `a-logger`, `rx-utils` exposed `api` from `:base`; `concurrent-utils` via `implementation`.
- Async: RxJava 3 + RxAndroid; WorkManager 2.11.2 for background jobs.
- DB: Room 2.8.5 via annotationProcessor. Two databases: `AppDatabase` (`:base`, version 17 (12→13 auto; 14→15→16→17 manual in DbMigration)) and `BotDatabase` (`:bot`). Room schemas exported to `base/schemas/` and `bot/schemas/` — required by migration tests.
- UI: Views/XML + Material Components, RecyclerView, ConstraintLayout, DrawerLayout, SwipeRefreshLayout, PhotoView. Markdown: Markwon 4.6.2 (core, ext-tables, ext-strikethrough, ext-tasklist, linkify, ext-latex, inline-parser) wrapped by `MarkdownRenderer`.
- Tests: JUnit 4.13.2, androidx.test ext-junit, Espresso, Mockito 5.24 (androidTest only), Room room-testing. LeakCanary plumber-android. org.json for JSON.
- `app/build.gradle` applies `gradle/license-html-generator.gradle`, which regenerates `app/src/main/assets/licenses.html` from `app/licenses.yml`.

## Repos / signing
- `settings.gradle`: FAIL_ON_PROJECT_REPOS; repos google(), mavenCentral(), jitpack.
- Release signing only when `SIGNING_KEY` env var (Base64 keystore) is present — local release builds are unsigned, which is expected.

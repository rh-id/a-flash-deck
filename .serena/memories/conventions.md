# Conventions

## Architecture pattern (project-specific, non-obvious)
- No MVP/MVVM, no ViewModels, no LiveData, no Fragments, no Hilt/Dagger. Custom stack:
  - `Provider` (a-provider) = service-locator DI container assembled from `ProviderModule` implementations; accessed via `getProvider()`.
  - `INavigator` (a-navigator) = stack navigation; screens are `StatefulView` custom views — pages, dialogs, and list items are all views pushed on the stack. Only 2 activities exist (`MainActivity` hosts everything).
- Business logic lives in Rx command objects `*Cmd.java` (return Completable/Single), repositories, and `*Coordinator.java` classes.
- UI reactivity via RxJava subjects in `*ChangeNotifier.java` classes (e.g. `SuggestedCardChangeNotifier`) — not LiveData/Flow.

## Naming
- Pages `*Page.java`, dialogs `*SVDialog.java`, list/item views `*SV.java`, commands `*Cmd.java`, WorkManager jobs `*Worker.java`, notifiers `*ChangeNotifier.java`, state modifiers `*Modifier.java`.

## Code style
- GPL-3 header on source files; match surrounding style. No .editorconfig, no lint config; minify disabled; proguard-rules.pro is the untouched template.
- Conventional commits (`feat:`, `fix:`, `chore:`, `docs:`); single long-lived `master` branch; releases via `v*` tags only.

## Data
- Room entities/DAOs per module (`base/entity` + `bot/entity`); DB version bumps need auto-migration + updated exported schema in module `schemas/` dir (migration tests load from there).
- Test sessions are Java-serialized to files (not Room) — keep serialization compatibility in mind for `TestState`.

## i18n
- User-visible strings must exist in BOTH `app/src/main/res/values-*` (note `values-in` = Indonesian) AND `fastlane/metadata/android/<locale>/` (11 locales). Keep the two locale sets in sync.

## AI cards
- Gemini output is stored with Anki-style math delimiters `\(...\)` / `\[...\]` (converted from Markdown LaTeX) — rendering expects these.

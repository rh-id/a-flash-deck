# a-flash-deck — core map

Android flash-card study app (GPL-3, repo `rh-id/a-flash-deck`, package `m.co.rh.id.a_flash_deck`, v2.3.0 / versionCode 63). Java-only Android app built on the author's own DI/navigation libraries, not mainstream stacks.

## Module map (Gradle, Groovy DSL)
- `:app` — application module: all main UI pages, Anki `.apkg` import/export, test/quizzing flow, notification handling, home-screen shortcuts. Depends on all modules below. Class map: `mem:app/core`.
- `:base` — foundation: Room DB, entities/DAOs/repositories, DI provider modules, routes/constants, common UI, Markdown+LaTeX renderer, audio, SharedPreferences. No project deps; everything builds on it. Class map: `mem:base/core`.
- `:timer-notification` — periodic study-reminder notification timers (WorkManager) with configurable start/end time window. Depends on `:base`. Class map: `mem:timer-notification/core`.
- `:bot` — "Flash bot" usage-analytics engine: scores card interactions and suggests cards to test; has its own Room DB. Depends on `:base`. Class map: `mem:bot/core`.
- `:ai` — Google Gemini deck generation (from topic / image / existing deck / single card) with encrypted API-key storage. Depends on `:base`. Class map: `mem:ai/core`.

## Entry points
- `app/src/main/java/m/co/rh/id/a_flash_deck/app/MainActivity.java` — single activity hosting the entire navigator page stack.
- `app/.../app/CardShowActivity.java` — home-screen shortcut: shows a random card.

## Key cross-cutting pieces
- SM-2-lite spaced-repetition scheduling: `base/.../base/model/ReviewScheduler.java`; state persisted in Room table `card_review_state` (ease, interval, repetitions, lapses, dueDateTime, suspended).
- Study analytics: append-only `review_log` table (DB v17 adds per-answer timing columns) written by `StudyRepository`, aggregated by `ReviewStatsCalculator`, surfaced in `:app` `StatisticsPage`/`StatsCmd`; per-deck due counts via `DeckDueCount`.
- Quiz engine + resume-after-restart: `app/.../app/provider/modifier/TestStateModifier.java` (Java-serializes `TestState` to a file referenced by `Test.stateFileLocation`).
- Deck JSON import/export format (`Decks.json` inside a zip; multi-line JSON accepted since 2.1.0, releases ≤2.0.0 need a single line): `docs/deck-json-schema.md`; tooling in `mem:suggested_commands`.

## Invariants
- No Kotlin anywhere; Java 8 source/target with core-library desugaring in every module.
- Classic Views/XML UI. No Compose, no Fragments, no ViewModels/LiveData — RxJava 3 + custom Provider/Navigator/StatefulView pattern (see `mem:conventions` before writing UI or business logic).
- Language/framework/toolchain versions and constraints: read `mem:tech_stack` before touching Gradle files or upgrading dependencies.
- Build/test/release/deck-tooling commands: `mem:suggested_commands`.
- Definition of done for coding tasks: `mem:task_completion`.

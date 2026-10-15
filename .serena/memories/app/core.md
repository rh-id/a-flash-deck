# :app module core

Application module; depends on `:base`, `:timer-notification`, `:bot`, `:ai`. Package `m.co.rh.id.a_flash_deck.app`. Only two activities exist in the whole app.

- `MainApplication.java` — creates `Provider`, WorkManager configuration, crash handler.
- `MainActivity.java` — hosts the entire navigator page stack. `CardShowActivity.java` — shortcut entry: random card.
- `anki/` — `ApkgParser`/`ApkgGenerator` + `anki/model/Anki*` for Anki `.apkg` import/export (Basic cards with images/audio).
- `provider/command/` — Rx commands: `NewCardCmd`, `UpdateDeckCmd`, `SuspendCardCmd`, `ExportImportCmd`, `PagedDeckItemsCmd`, `StatsCmd`, etc.
- `provider/component/` — `AnkiImporter`/`AnkiExporter`/`ExportImportCoordinator`; `TestWorkflowCoordinator` (orchestrates test flow, consumes bot suggestions); `AppNotificationHandler` (notification channels, voice playback; implements base `IAppNotificationHandler`); `AppShortcutHandler`.
- `provider/modifier/TestStateModifier.java` — quizzing engine: builds card selection, persists/resumes `TestState` (Java-serialized file at `Test.stateFileLocation`), applies grades via `StudyRepository` (returns `GradeResult`; cards graded "Again" are re-queued in the same session; end-of-test `TestSummarySVDialog` shows total answers + elapsed time).
- `ui/page/` — `HomePage`, `DeckListPage` (per-deck due badges + per-deck study-due flow), `DeckSelectPage`, `CardListPage`, `CardDetailPage`, `TestPage` + `TestSummarySVDialog`, `StatisticsPage`, `SettingsPage`, `SplashPage`, `NotificationTimerListPage`, `DonationsPage`, `CardShowHomePage`/`CardShowPage`; `ui/component/` — reusable `*SV` views (`CardItemSV`, `DeckListSV`, `OngoingTestBannerSV` for resumed tests, `stats/BarChartSV`, `settings/ThemeMenuSV` with the Material You dynamic-colors toggle).
- `receiver/` — `NotificationPlayVoiceReceiver`, `NotificationDeleteReceiver` (notification action buttons).
- androidTest: `AnkiExporterTest`, `AnkiImporterTest`, `AnkiRoundTripTest`, `ApkgParserTest`, `ExportImportCmdTest`; helpers `AnkiTestDataHelper`, `TestDatabaseProviderModule`.

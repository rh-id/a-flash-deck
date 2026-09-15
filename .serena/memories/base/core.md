# :base module core

Foundation library; no project dependencies; all other modules depend on it. Package `m.co.rh.id.a_flash_deck.base`.

- `BaseApplication.java` — abstract app class exposing `getProvider()` / `getNavigator()`.
- `entity/` — Room entities: `Deck`, `Card`, `Test`, `CardReviewState` (SM-2 state: ease, interval, repetitions, lapses, dueDateTime, suspended), `NotificationTimer`, `AndroidNotification`.
- `dao/` — `DeckDao`, `CardDao`, `TestDao`, `StudyDao`, `CardReviewStateDao`, `NotificationTimerDao`; `room/AppDatabase.java` (version 15, auto-migrations; schemas exported to `base/schemas/`), `room/DbMigration.java`.
- `repository/` — `DeckCardRepository` (deck/card CRUD + JSON import), `StudyRepository` (spaced repetition + grading), `AndroidNotificationRepository`.
- `model/ReviewScheduler.java` — SM-2-lite scheduler (subject of the only JVM unit test, `ReviewSchedulerTest`); `model/TestState.java` — in-test state (card list + index).
- `component/` — `AppSharedPreferences`, `AudioPlayer`/`AudioRecorder`, `MarkdownRenderer` (Markwon + LaTeX), `IAppNotificationHandler` (interface implemented in `:app`).
- `provider/` — `DatabaseProviderModule`, `CardMediaStore` (card image/voice files on disk), `FileHelper`, `ImageHelper`, `CommonNavConfig`; `provider/notifier/` — change notifiers.
- `ui/component/common/` — shared dialogs (`VoiceRecordSVDialog`, `TimePickerSVDialog`, ...).
- androidTest: `DbMigrationTest`, `JsonModelSerializeTest`, `component/MarkdownRendererTest`, `dao/CardDaoBatchQueryTest`.

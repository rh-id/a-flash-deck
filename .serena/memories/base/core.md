# :base module core

Foundation library; no project dependencies; all other modules depend on it. Package `m.co.rh.id.a_flash_deck.base`.

- `BaseApplication.java` — abstract app class exposing `getProvider()` / `getNavigator()`.
- `entity/` — Room entities: `Deck`, `Card`, `Test`, `CardReviewState` (SM-2 state: ease, interval, repetitions, lapses, dueDateTime, suspended), `ReviewLog` (append-only graded-review history: card, deck, grade, createdDateTime, timeBeforeRevealMs, timeAfterRevealMs, reversed), `NotificationTimer`, `AndroidNotification`.
- `dao/` — `DeckDao`, `CardDao`, `TestDao`, `StudyDao` (due/new queries, incl. per-deck), `CardReviewStateDao`, `ReviewLogDao`, `NotificationTimerDao`, `AndroidNotificationDao`, `DaoBatchQueryUtil`.
- `room/` — `AppDatabase.java` (**version 17**; auto-migration 12→13, manual migrations 14→15→16→17 in `DbMigration.java`; schemas exported to `base/schemas/` and required by `DbMigrationTest`), `DbMigration.java`, `converter/`.
- `repository/` — `DeckCardRepository` (deck/card CRUD + JSON import), `StudyRepository` (spaced repetition + grading, writes `ReviewLog` incl. response times), `AndroidNotificationRepository`.
- `model/` — `ReviewScheduler` (SM-2-lite scheduler), `TestState` (in-test state: card list, current index, grade counts, session/elapsed timing; Java-serialized to a file at `Test.stateFileLocation` for resume), `ReviewStatsCalculator`/`ReviewStats` (statistics page math), `DeckDueCount` (per-deck due/new badges), plus event models (`DeckModel`, `CardSuspendStateChangedEvent`, ...).
- `component/` — `AppSharedPreferences` (theme, notification time window, Material You dynamic-colors flag — default ON), `AudioPlayer`/`AudioRecorder`, `MarkdownRenderer` (Markwon + LaTeX), `IAppNotificationHandler` (interface implemented in `:app`).
- `provider/` — `DatabaseProviderModule`, `CardMediaStore` (card image/voice files on disk), `FileHelper`, `ImageHelper`, `CommonNavConfig`; `provider/notifier/` — `DeckChangeNotifier`, `TestChangeNotifier`, `NotificationTimerChangeNotifier`, `NotificationTimeChangeNotifier` (bot/AI notifiers live in their own modules).
- `ui/component/common/` — shared dialogs (`VoiceRecordSVDialog`, `TimePickerSVDialog`, ...).
- Tests — JVM (`base/src/test`): `ReviewSchedulerTest`, `ReviewStatsCalculatorTest`, `TestStateTest`. androidTest: `DbMigrationTest`, `JsonModelSerializeTest`, `component/MarkdownRendererTest`, `dao/CardDaoBatchQueryTest`, `StudyDaoDueQueryTest`.

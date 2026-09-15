# :bot module core

"Flash bot" = local card-suggestion analytics engine, NOT a chat bot. Depends on `:base`; has its own Room database (`BotDatabase`, file `a-flash-deck.bot.db`, schemas in `bot/schemas/`).

- `provider/component/BotAnalytics.java` — logs card interactions (`ACTION_OPEN_NOTIFICATION`, `ACTION_DELETE_NOTIFICATION`, `ACTION_OPEN_TEST_ANSWER`) and schedules periodic workers.
- `workmanager/BotAnalyzeWorker.java` — scores cards from the last 3 days of logs, excludes suspended cards from analysis/suggestions, writes `SuggestedCard` rows. `workmanager/BotLogCleanerWorker.java` — log cleanup.
- `entity/` — `CardLog`, `SuggestedCard`; `dao/` — `CardLogDao`, `SuggestedCardDao`.
- `provider/notifier/SuggestedCardChangeNotifier.java` — consumed by `TestWorkflowCoordinator` in `:app` to surface suggested cards.

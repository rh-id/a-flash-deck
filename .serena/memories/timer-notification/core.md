# :timer-notification module core

Periodic study-reminder "notification timers". Depends on `:base`.

- `provider/command/NewNotificationTimerCmd.java` — enqueues periodic WorkManager work.
- `workmanager/NotificationTimerWorker.java` — picks a random card from the selected decks, respects the configured start/end time window (from `AppSharedPreferences`), notifies via `IAppNotificationHandler`.
- `ui/page/NotificationTimerDetailSVDialog.java` + timer list components; the list page (`NotificationTimerListPage`) lives in `:app`.
- Entities/DAOs live in `:base` (`NotificationTimer` entity, `NotificationTimerDao`) — this module contains no Room database of its own.

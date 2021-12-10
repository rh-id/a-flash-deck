package m.co.rh.id.a_flash_deck.app.component;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.MainActivity;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.model.TimerNotificationEvent;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler implements IAppNotificationHandler {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<AndroidNotificationDao> mAndroidNotificationDao;
    private final ProviderValue<NotificationTimerDao> mTimerNotificationDao;
    private final ProviderValue<DeckDao> mDeckDao;
    private final BehaviorSubject<TimerNotificationEvent> mTimerNotificationSubject;

    public AppNotificationHandler(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mAndroidNotificationDao = provider.lazyGet(AndroidNotificationDao.class);
        mTimerNotificationDao = provider.lazyGet(NotificationTimerDao.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mTimerNotificationSubject = BehaviorSubject.create();
    }

    @Override
    public void postNotificationTimer(NotificationTimer notificationTimer, Card selectedCard) {
        createTestNotificationChannel();
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_TIMER_NOTIFICATION;
        androidNotification.refId = notificationTimer.id;
        mAndroidNotificationDao.get().insertNotification(androidNotification);
        Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
        receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlag = PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                intentFlag);
        Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
        deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                intentFlag);
        String title = mAppContext.getString(R.string.notification_title_flash_question);
        String content = selectedCard.question;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_TIMER_NOTIFICATION)
                .setSmallIcon(R.drawable.ic_notification_launcher)
                .setColorized(true)
                .setColor(mAppContext.getResources().getColor(R.color.teal_custom))
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_TIMER_NOTIFICATION)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_TIMER_NOTIFICATION,
                androidNotification.requestId,
                builder.build());
    }

    private void createTestNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_timer_notification_name);
            String description = mAppContext.getString(R.string.notification_timer_notification_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_TIMER_NOTIFICATION,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = mAppContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void removeNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() ->
                    mAndroidNotificationDao.get().deleteByRequestId((Integer) serializable));
        }
    }

    @Override
    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                AndroidNotification androidNotification =
                        mAndroidNotificationDao.get().findByRequestId((int) serializable);
                if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_TIMER_NOTIFICATION)) {
                    NotificationTimer notificationTimer = mTimerNotificationDao.get().findById(androidNotification.refId);
                    Card card = mDeckDao.get().getCardByCardId(notificationTimer.currentCardId);
                    mTimerNotificationSubject.onNext(new TimerNotificationEvent(notificationTimer, card));
                }
            });
        }
    }

    @Override
    public Flowable<TimerNotificationEvent> getTimerNotificationEventFlow() {
        return Flowable.fromObservable(mTimerNotificationSubject, BackpressureStrategy.BUFFER);
    }
}

/*
 *     Copyright (C) 2021 Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import m.co.rh.id.a_flash_deck.app.CardShowActivity;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.model.NotificationTimerEvent;
import m.co.rh.id.a_flash_deck.base.repository.AndroidNotificationRepo;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler implements IAppNotificationHandler {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<AndroidNotificationRepo> mAndroidNotificationRepo;
    private final ProviderValue<NotificationTimerDao> mTimerNotificationDao;
    private final ProviderValue<DeckDao> mDeckDao;
    private BehaviorSubject<NotificationTimerEvent> mTimerNotificationSubject;

    public AppNotificationHandler(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mAndroidNotificationRepo = provider.lazyGet(AndroidNotificationRepo.class);
        mTimerNotificationDao = provider.lazyGet(NotificationTimerDao.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mTimerNotificationSubject = BehaviorSubject.create();
    }

    @Override
    public void postNotificationTimer(NotificationTimer notificationTimer, Card selectedCard) {
        createTestNotificationChannel();
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_NOTIFICATION_TIMER;
        androidNotification.refId = notificationTimer.id;
        mAndroidNotificationRepo.get().insertNotification(androidNotification);
        Intent receiverIntent = new Intent(mAppContext, CardShowActivity.class);
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_NOTIFICATION_TIMER)
                .setSmallIcon(R.drawable.ic_notification_launcher)
                .setColorized(true)
                .setColor(mAppContext.getResources().getColor(R.color.teal_custom))
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_NOTIFICATION_TIMER)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_NOTIFICATION_TIMER,
                androidNotification.requestId,
                builder.build());
    }

    private void createTestNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_timer_notification_name);
            String description = mAppContext.getString(R.string.notification_timer_notification_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_NOTIFICATION_TIMER,
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
                    mAndroidNotificationRepo.get().deleteNotificationByRequestId((Integer) serializable));
        }
    }

    @Override
    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                    NotificationTimer notificationTimer = mTimerNotificationDao.get().findById(androidNotification.refId);
                    Card card = mDeckDao.get().getCardByCardId(notificationTimer.currentCardId);
                    mTimerNotificationSubject.onNext(new NotificationTimerEvent(notificationTimer, card));
                    // delete after process notification
                    mAndroidNotificationRepo.get().deleteNotification(androidNotification);
                }
            });
        }
    }

    @Override
    public Flowable<NotificationTimerEvent> getTimerNotificationEventFlow() {
        return Flowable.fromObservable(mTimerNotificationSubject, BackpressureStrategy.BUFFER);
    }

    @Override
    public void clearEvent() {
        mTimerNotificationSubject.onComplete();
        mTimerNotificationSubject = BehaviorSubject.create();
    }

    @Override
    public void cancelNotificationSync(NotificationTimer notificationTimer) {
        AndroidNotification androidNotification = mAndroidNotificationRepo.get().findByGroupTagAndRefId(GROUP_KEY_NOTIFICATION_TIMER, notificationTimer.id);
        if (androidNotification != null) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
            notificationManagerCompat.cancel(GROUP_KEY_NOTIFICATION_TIMER,
                    androidNotification.requestId);
            mAndroidNotificationRepo.get().deleteNotification(androidNotification);
        }
    }
}

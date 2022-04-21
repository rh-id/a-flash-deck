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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.CardShowActivity;
import m.co.rh.id.a_flash_deck.app.MainActivity;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationPlayVoiceReceiver;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.model.NotificationTimerEvent;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.repository.AndroidNotificationRepo;
import m.co.rh.id.a_flash_deck.bot.provider.component.BotAnalytics;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler implements IAppNotificationHandler {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<AndroidNotificationRepo> mAndroidNotificationRepo;
    private final ProviderValue<NotificationTimerDao> mNotificationTimerDao;
    private final ProviderValue<DeckDao> mDeckDao;
    private final ProviderValue<FileHelper> mFileHelper;
    private final ProviderValue<AudioPlayer> mAudioPlayer;
    private final ProviderValue<BotAnalytics> mBotAnalytics;
    private BehaviorSubject<Optional<NotificationTimerEvent>> mNotificationTimerSubject;
    private ReentrantLock mLock;

    public AppNotificationHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mAndroidNotificationRepo = provider.lazyGet(AndroidNotificationRepo.class);
        mNotificationTimerDao = provider.lazyGet(NotificationTimerDao.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mFileHelper = provider.lazyGet(FileHelper.class);
        mAudioPlayer = provider.lazyGet(AudioPlayer.class);
        mBotAnalytics = provider.lazyGet(BotAnalytics.class);
        mNotificationTimerSubject = BehaviorSubject.createDefault(Optional.empty());
        mLock = new ReentrantLock();
    }

    @Override
    public void postNotificationTimer(NotificationTimer notificationTimer, Card selectedCard) {
        mLock.lock();
        createNotificationTimerNotificationChannel();
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
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_NOTIFICATION_TIMER)
                .setAutoCancel(true);
        if (selectedCard.questionImage != null) {
            File questionImageFile = mFileHelper.get().getCardQuestionImage(selectedCard.questionImage);
            File questionImageThumbnailFile = mFileHelper.get().getCardQuestionImageThumbnail(selectedCard.questionImage);
            Bitmap questionImage = BitmapFactory.decodeFile(questionImageFile.getAbsolutePath());
            Bitmap questionImageThumbnail = BitmapFactory.decodeFile(questionImageThumbnailFile.getAbsolutePath());
            builder.setLargeIcon(questionImageThumbnail);
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                    .setSummaryText(content)
                    .bigPicture(questionImage));
        }
        if (selectedCard.questionVoice != null) {
            Intent questionVoiceIntent = new Intent(mAppContext, NotificationPlayVoiceReceiver.class);
            questionVoiceIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent questionVoicePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, questionVoiceIntent,
                    intentFlag);
            builder.addAction(R.drawable.ic_keyboard_voice_black, mAppContext.getString(R.string.play_voice), questionVoicePendingIntent);
        }
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_NOTIFICATION_TIMER,
                androidNotification.requestId,
                builder.build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_NOTIFICATION_TIMER)
                    .setSmallIcon(R.drawable.ic_notification_launcher)
                    .setColorized(true)
                    .setColor(mAppContext.getColor(R.color.teal_custom))
                    .setContentTitle(mAppContext.getString(R.string.notification_title_flash_question))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setGroup(GROUP_KEY_NOTIFICATION_TIMER)
                    .setGroupSummary(true);
            notificationManagerCompat.notify(GROUP_KEY_NOTIFICATION_TIMER, GROUP_SUMMARY_ID_NOTIFICATION_TIMER, summaryBuilder.build());
        }
        mLock.unlock();
    }

    private void createNotificationTimerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_name_notification_timer);
            String description = mAppContext.getString(R.string.notification_description_notification_timer);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_NOTIFICATION_TIMER,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = mAppContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void postGeneralMessage(String title, String content) {
        mLock.lock();
        createGeneralMessageNotificationChannel();
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_GENERAL_MESSAGE;
        mAndroidNotificationRepo.get().insertNotification(androidNotification);
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_GENERAL_MESSAGE)
                .setSmallIcon(R.drawable.ic_notification_launcher)
                .setColorized(true)
                .setColor(mAppContext.getResources().getColor(R.color.teal_custom))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_GENERAL_MESSAGE)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_GENERAL_MESSAGE,
                androidNotification.requestId,
                builder.build());
        mLock.unlock();
    }

    private void createGeneralMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_name_general_message);
            String description = mAppContext.getString(R.string.notification_description_general_message);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_GENERAL_MESSAGE,
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
            {
                mLock.lock();
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                if (androidNotification != null) {
                    if (androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                        NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                        mBotAnalytics.get().trackDeleteNotification(notificationTimer.currentCardId);
                    }
                }
                mAndroidNotificationRepo.get().deleteNotificationByRequestId((Integer) serializable);
                mLock.unlock();
            });
        }
    }

    @Override
    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                if (androidNotification != null) {
                    if (androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                        NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                        mBotAnalytics.get().trackOpenNotification(notificationTimer.currentCardId);
                        Card card = mDeckDao.get().getCardByCardId(notificationTimer.currentCardId);
                        mNotificationTimerSubject.onNext(Optional.of(new NotificationTimerEvent(notificationTimer, card)));
                    }
                    // delete after process notification
                    mAndroidNotificationRepo.get().deleteNotification(androidNotification);
                }
                mLock.unlock();
            });
        }
    }

    @Override
    public Flowable<Optional<NotificationTimerEvent>> getTimerNotificationEventFlow() {
        return Flowable.fromObservable(mNotificationTimerSubject, BackpressureStrategy.BUFFER);
    }

    // FIXME: there should be some way to create queue with RX,
    //  rather than manually clean subject everytime item is processed
    @Override
    public void clearEvent() {
        mNotificationTimerSubject.onNext(Optional.empty());
    }

    @Override
    public void cancelNotificationSync(NotificationTimer notificationTimer) {
        mLock.lock();
        AndroidNotification androidNotification = mAndroidNotificationRepo.get().findByGroupTagAndRefId(GROUP_KEY_NOTIFICATION_TIMER, notificationTimer.id);
        if (androidNotification != null) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
            notificationManagerCompat.cancel(GROUP_KEY_NOTIFICATION_TIMER,
                    androidNotification.requestId);
            mAndroidNotificationRepo.get().deleteNotification(androidNotification);
        }
        mLock.unlock();
    }

    @Override
    public void playVoice(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                    NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                    Card card = mDeckDao.get().getCardByCardId(notificationTimer.currentCardId);
                    if (card.questionVoice != null) {
                        mAudioPlayer.get().play(Uri.fromFile(mFileHelper.get().getCardQuestionVoice(card.questionVoice)));
                    }
                }
                mLock.unlock();
            });
        }
    }
}

/*
 *     Copyright (C) 2021-present Ruby Hartono
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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import co.rh.id.lib.rx3_utils.subject.QueueSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.CardShowActivity;
import m.co.rh.id.a_flash_deck.app.MainActivity;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationGradeReceiver;
import m.co.rh.id.a_flash_deck.app.receiver.NotificationPlayVoiceReceiver;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.model.NotificationTimerEvent;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.repository.AndroidNotificationRepository;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.a_flash_deck.bot.provider.component.BotAnalytics;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler implements IAppNotificationHandler {
    private static final String TAG = AppNotificationHandler.class.getName();

    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<AndroidNotificationRepository> mAndroidNotificationRepo;
    private final ProviderValue<NotificationTimerDao> mNotificationTimerDao;
    private final ProviderValue<DeckDao> mDeckDao;
    private final ProviderValue<CardDao> mCardDao;
    private final ProviderValue<CardMediaStore> mCardMediaStore;
    private final ProviderValue<AudioPlayer> mAudioPlayer;
    private final ProviderValue<BotAnalytics> mBotAnalytics;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<StudyRepository> mStudyRepository;
    private final ProviderValue<MarkdownRenderer> mMarkdownRenderer;
    private final QueueSubject<NotificationTimerEvent> mNotificationTimerSubject;
    private final QueueSubject<Deck> mDeckMessageSubject;
    private final ReentrantLock mLock;

    public AppNotificationHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mAndroidNotificationRepo = provider.lazyGet(AndroidNotificationRepository.class);
        mNotificationTimerDao = provider.lazyGet(NotificationTimerDao.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mCardDao = provider.lazyGet(CardDao.class);
        mCardMediaStore = provider.lazyGet(CardMediaStore.class);
        mAudioPlayer = provider.lazyGet(AudioPlayer.class);
        mBotAnalytics = provider.lazyGet(BotAnalytics.class);
        mLogger = provider.lazyGet(ILogger.class);
        mStudyRepository = provider.lazyGet(StudyRepository.class);
        mMarkdownRenderer = provider.lazyGet(MarkdownRenderer.class);
        mNotificationTimerSubject = new QueueSubject<>();
        mDeckMessageSubject = new QueueSubject<>();
        mLock = new ReentrantLock();
    }

    @Override
    public void postNotificationTimer(NotificationTimer notificationTimer, Card selectedCard) {
        if (ActivityCompat.checkSelfPermission(mAppContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Bitmap questionImage = null;
        Bitmap questionImageThumbnail = null;
        mLock.lock();
        try {
            createNotificationTimerNotificationChannel();
            AndroidNotification androidNotification = new AndroidNotification();
            androidNotification.groupKey = GROUP_KEY_NOTIFICATION_TIMER;
            androidNotification.refId = notificationTimer.id;
            mAndroidNotificationRepo.get().insertNotification(androidNotification);
            Intent receiverIntent = new Intent(mAppContext, CardShowActivity.class);
            receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);

            int intentFlag = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                    intentFlag);
            Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
            deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                    intentFlag);
            String title = mAppContext.getString(R.string.notification_title_flash_question);
            // Notifications render plain text only — strip Markdown/LaTeX syntax
            // (e.g. **bold** -> bold, \(x^2\) -> readable text) so users don't
            // see raw markup in the notification body.
            String content = mMarkdownRenderer.get().toPlainText(selectedCard.question);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_NOTIFICATION_TIMER)
                    .setSmallIcon(R.drawable.ic_notification_launcher)
                    .setColorized(true)
                    .setColor(mAppContext.getResources().getColor(R.color.teal_custom, mAppContext.getTheme()))
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
                File questionImageFile = mCardMediaStore.get().getCardQuestionImage(selectedCard.questionImage);
                File questionImageThumbnailFile = mCardMediaStore.get().getCardQuestionImageThumbnail(selectedCard.questionImage);
                questionImage = BitmapFactory.decodeFile(questionImageFile.getAbsolutePath());
                questionImageThumbnail = BitmapFactory.decodeFile(questionImageThumbnailFile.getAbsolutePath());
                builder.setLargeIcon(questionImageThumbnail);
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .setSummaryText(content)
                        .bigPicture(questionImage));
            }
            // Grade actions (Again / Good / Hard) allow grading the card
            // directly from the notification shade, persisting through the
            // same grading path as the in-app test flow. Voice stays last.
            addGradeAction(builder, ReviewScheduler.GRADE_AGAIN, androidNotification, intentFlag);
            addGradeAction(builder, ReviewScheduler.GRADE_GOOD, androidNotification, intentFlag);
            addGradeAction(builder, ReviewScheduler.GRADE_HARD, androidNotification, intentFlag);
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
        } finally {
            if (questionImage != null) {
                questionImage.recycle();
            }
            if (questionImageThumbnail != null) {
                questionImageThumbnail.recycle();
            }
            mLock.unlock();
        }
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

    /**
     * Adds a shade grade action (Again / Good / Hard) for the card shown by
     * the given timer notification.
     * <p>
     * Android dedupes PendingIntents by requestCode + Intent.filterEquals and
     * does NOT compare extras, so the grade intent gets a filterEquals-distinct
     * action per grade ({@link NotificationGradeReceiver#buildGradeAction})
     * plus a requestCode derived from the notification's own requestId to make
     * silent collapse into the wrong grade impossible. The grade value itself
     * is also carried as the {@link #KEY_INT_GRADE} extra.
     * <p>
     * requestId increments once per notification, so {@code requestId * 10 +
     * grade} yields a unique requestCode for every (notification, grade) pair.
     */
    private void addGradeAction(NotificationCompat.Builder builder, int grade,
                                AndroidNotification androidNotification, int intentFlag) {
        int titleRes;
        switch (grade) {
            case ReviewScheduler.GRADE_AGAIN:
                titleRes = R.string.grade_again;
                break;
            case ReviewScheduler.GRADE_HARD:
                titleRes = R.string.grade_hard;
                break;
            case ReviewScheduler.GRADE_GOOD:
                titleRes = R.string.grade_good;
                break;
            default:
                // Unsupported grade — do not add the action
                return;
        }
        Intent gradeIntent = new Intent(mAppContext, NotificationGradeReceiver.class);
        gradeIntent.setAction(NotificationGradeReceiver.buildGradeAction(grade));
        gradeIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        gradeIntent.putExtra(KEY_INT_GRADE, grade);
        // requestCode = requestId * 10 + grade: notification and grade are
        // both encoded into the requestCode, so (notification, grade) pairs
        // never share a PendingIntent even though extras are ignored by the
        // dedup logic.
        PendingIntent gradePendingIntent = PendingIntent.getBroadcast(mAppContext,
                androidNotification.requestId * 10 + grade, gradeIntent, intentFlag);
        // icon = 0 (no existing drawable fits Again/Good/Hard): Android 7.0
        // (API 24) removed action icons from the notification shade, so with
        // minSdk 23 only the rare API 23 device that also shows the icon'd
        // voice action would render plain icon-less grade buttons — harmless.
        builder.addAction(0, mAppContext.getString(titleRes), gradePendingIntent);
    }

    @Override
    public void postGeneralMessage(String title, String content) {
        if (ActivityCompat.checkSelfPermission(mAppContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLock.lock();
        try {
            createGeneralMessageNotificationChannel();
            AndroidNotification androidNotification = new AndroidNotification();
            androidNotification.groupKey = GROUP_KEY_GENERAL_MESSAGE;
            mAndroidNotificationRepo.get().insertNotification(androidNotification);
            Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
            receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);

            int intentFlag = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                    intentFlag);
            Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
            deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                    intentFlag);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_GENERAL_MESSAGE)
                    .setSmallIcon(R.drawable.ic_notification_launcher)
                    .setColorized(true)
                    .setColor(mAppContext.getResources().getColor(R.color.teal_custom, mAppContext.getTheme()))
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
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void postDeckMessage(String title, String content, long deckId) {
        if (ActivityCompat.checkSelfPermission(mAppContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLock.lock();
        try {
            createGeneralMessageNotificationChannel();
            AndroidNotification androidNotification = new AndroidNotification();
            androidNotification.groupKey = GROUP_KEY_DECK_MESSAGE;
            androidNotification.refId = deckId;
            mAndroidNotificationRepo.get().insertNotification(androidNotification);
            Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
            receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);

            int intentFlag = PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                    intentFlag);
            Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
            deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
            PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                    intentFlag);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_GENERAL_MESSAGE)
                    .setSmallIcon(R.drawable.ic_notification_launcher)
                    .setColorized(true)
                    .setColor(mAppContext.getResources().getColor(R.color.teal_custom, mAppContext.getTheme()))
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(deletePendingIntent)
                    .setGroup(GROUP_KEY_DECK_MESSAGE)
                    .setAutoCancel(true);
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
            notificationManagerCompat.notify(GROUP_KEY_DECK_MESSAGE,
                    androidNotification.requestId,
                    builder.build());
        } finally {
            mLock.unlock();
        }
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
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null) {
                        if (androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                            NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                            if (notificationTimer != null && notificationTimer.currentCardId != null) {
                                mBotAnalytics.get().trackDeleteNotification(notificationTimer.currentCardId);
                            }
                        }
                        deleteNotificationRecordAndCancel(androidNotification);
                    }
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    @Override
    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null) {
                        if (androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                            NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                            if (notificationTimer != null && notificationTimer.currentCardId != null) {
                                mBotAnalytics.get().trackOpenNotification(notificationTimer.currentCardId);
                                Card card = mCardDao.get().getCardByCardId(notificationTimer.currentCardId);
                                if (card != null) {
                                    mNotificationTimerSubject.onNext(new NotificationTimerEvent(notificationTimer, card));
                                }
                            }
                        } else if (androidNotification.groupKey.equals(GROUP_KEY_DECK_MESSAGE)) {
                            Deck deck = mDeckDao.get().getDeckById(androidNotification.refId);
                            if (deck != null) {
                                mDeckMessageSubject.onNext(deck);
                            }
                        }
                        // delete after process notification
                        mAndroidNotificationRepo.get().deleteNotification(androidNotification);
                        // Dismiss the system notification. The child is normally
                        // auto-cancelled by the system on tap; this is a safety net
                        // for OEM ROMs, and is required for the timer group, whose
                        // summary (ID 0) has no content intent and would otherwise
                        // linger as a blank notification after answering/exiting.
                        cancelSystemNotifications(androidNotification.groupKey, androidNotification.requestId);
                    }
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    @Override
    public Flowable<NotificationTimerEvent> getTimerNotificationEventFlow() {
        return Flowable.fromObservable(mNotificationTimerSubject, BackpressureStrategy.BUFFER);
    }

    @Override
    public Flowable<Deck> getDeckMessageEventFlow() {
        return Flowable.fromObservable(mDeckMessageSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Deletes the {@code android_notification} record and dismisses its
     * system notification, including the timer group summary when it is the
     * last timer child (see {@link #cancelSystemNotifications(String, int)}).
     * Shared by the swipe-dismiss path ({@link #removeNotification(Intent)})
     * and the shade-grade path ({@link #gradeNotificationCard(Intent)}).
     * Unlike the dismiss path, shade grades carry no delete analytics.
     * Must be called while holding {@link #mLock}.
     */
    private void deleteNotificationRecordAndCancel(AndroidNotification androidNotification) {
        mAndroidNotificationRepo.get().deleteNotification(androidNotification);
        // Dismiss the system notification. The child is normally
        // removed by the swipe itself; this is a safety net for OEM
        // ROMs, and is required for the timer group, whose summary
        // (ID 0) has no content intent and would otherwise linger.
        cancelSystemNotifications(androidNotification.groupKey, androidNotification.requestId);
    }

    /**
     * Cancel the child system notification identified by {@code groupKey} and
     * {@code requestId}. For the timer group (the only one that posts a group
     * summary with no content intent), also cancel the summary
     * (ID {@link #GROUP_SUMMARY_ID_NOTIFICATION_TIMER}) when no timer
     * notifications remain, so it does not linger as a blank notification.
     * <p>
     * Cancelling the child explicitly is a safety net for {@code setAutoCancel}
     * not firing reliably on some OEM ROMs; for groups with no summary it is
     * otherwise redundant with the system's own dismissal.
     * <p>
     * Must be called after the corresponding {@code android_notification}
     * record has been deleted so the count reflects the post-delete state.
     * Safe to call from any thread.
     */
    private void cancelSystemNotifications(String groupKey, int requestId) {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.cancel(groupKey, requestId);
        // The timer group posts a summary (ID 0) with no content intent that is
        // never auto-dismissed. Cancel it only when no timer children remain, so
        // other still-live timers keep their grouping. Cancelling a summary that
        // isn't posted (e.g. when dismissing a non-timer notification) is a no-op.
        if (mAndroidNotificationRepo.get().countByGroupKey(GROUP_KEY_NOTIFICATION_TIMER) == 0) {
            notificationManagerCompat.cancel(GROUP_KEY_NOTIFICATION_TIMER, GROUP_SUMMARY_ID_NOTIFICATION_TIMER);
        }
    }

    @Override
    public void cancelNotificationSync(NotificationTimer notificationTimer) {
        mLock.lock();
        try {
            AndroidNotification androidNotification = mAndroidNotificationRepo.get().findByGroupTagAndRefId(GROUP_KEY_NOTIFICATION_TIMER, notificationTimer.id);
            if (androidNotification != null) {
                mAndroidNotificationRepo.get().deleteNotification(androidNotification);
                cancelSystemNotifications(GROUP_KEY_NOTIFICATION_TIMER, androidNotification.requestId);
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void playVoice(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId((int) serializable);
                    if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                        NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                        if (notificationTimer != null && notificationTimer.currentCardId != null) {
                            Card card = mCardDao.get().getCardByCardId(notificationTimer.currentCardId);
                            if (card != null && card.questionVoice != null) {
                                mAudioPlayer.get().play(Uri.fromFile(mCardMediaStore.get().getCardQuestionVoice(card.questionVoice)));
                            }
                        }
                    }
                } finally {
                    mLock.unlock();
                }
            });
        }
    }

    @Override
    public void gradeNotificationCard(Intent intent) {
        Serializable requestIdSerializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        Serializable gradeSerializable = intent.getSerializableExtra(KEY_INT_GRADE);
        if (requestIdSerializable instanceof Integer && gradeSerializable instanceof Integer) {
            int grade = (int) gradeSerializable;
            if (grade < ReviewScheduler.GRADE_AGAIN || grade > ReviewScheduler.GRADE_EASY) {
                mLogger.get().w(TAG, "Ignoring grade out of range from notification: " + grade);
                return;
            }
            int requestId = (int) requestIdSerializable;
            mExecutorService.get().execute(() -> {
                mLock.lock();
                try {
                    AndroidNotification androidNotification =
                            mAndroidNotificationRepo.get().findByRequestId(requestId);
                    if (androidNotification == null) {
                        // Record already cleaned up (double tap or expiry) — a
                        // no-op keeps the receiver idempotent.
                        return;
                    }
                    boolean graded = false;
                    if (androidNotification.groupKey.equals(GROUP_KEY_NOTIFICATION_TIMER)) {
                        NotificationTimer notificationTimer = mNotificationTimerDao.get().findById(androidNotification.refId);
                        if (notificationTimer != null && notificationTimer.currentCardId != null) {
                            Card card = mCardDao.get().getCardByCardId(notificationTimer.currentCardId);
                            if (card != null) {
                                // Grade through the same path as the in-app test
                                // flow: updates CardReviewState and appends a
                                // ReviewLog row so statistics stay consistent.
                                // Shade grades have no reveal timing, and
                                // worker-path cards are never reversed.
                                mStudyRepository.get().applyGrade(card.id, card.deckId, grade,
                                        new Date(), 0, 0, false);
                                graded = true;
                            }
                        }
                    }
                    if (!graded) {
                        // Timer or card already gone — no analytics event for the
                        // grade, just clean the stale notification up as the
                        // dismiss path would.
                        mLogger.get().d(TAG, "Grade action found no card, requestId=" + requestId);
                    }
                    deleteNotificationRecordAndCancel(androidNotification);
                } finally {
                    mLock.unlock();
                }
            });
        } else {
            mLogger.get().d(TAG, "Grade action missing request id or grade extra");
        }
    }
}

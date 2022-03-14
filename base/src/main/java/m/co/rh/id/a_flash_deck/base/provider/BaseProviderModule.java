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

package m.co.rh.id.a_flash_deck.base.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.work.WorkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.component.AudioRecorder;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimeChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.CompositeLogger;
import m.co.rh.id.alogger.FileLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.alogger.ToastLogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for base configuration
 */
public class BaseProviderModule implements ProviderModule {
    private static final String TAG = BaseProviderModule.class.getName();

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new DatabaseProviderModule());
        // thread pool to be used throughout this app lifecycle
        providerRegistry.registerAsync(ExecutorService.class, () -> {
            ThreadPoolExecutor threadPoolExecutor =
                    new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                            Integer.MAX_VALUE,
                            10, TimeUnit.SECONDS, new SynchronousQueue<>());
            threadPoolExecutor.allowCoreThreadTimeOut(true);
            threadPoolExecutor.prestartAllCoreThreads();
            return threadPoolExecutor;
        });
        providerRegistry.register(ScheduledExecutorService.class, Executors.newSingleThreadScheduledExecutor());
        providerRegistry.register(Handler.class, new Handler(Looper.getMainLooper()));
        providerRegistry.registerAsync(ILogger.class, () -> {
            ILogger defaultLogger = new AndroidLogger(ILogger.ERROR);
            List<ILogger> loggerList = new ArrayList<>();
            loggerList.add(defaultLogger);
            try {
                int logLevel = ILogger.DEBUG;
                ILogger fileLogger = new FileLogger(logLevel,
                        provider.get(FileHelper.class).getLogFile());
                loggerList.add(fileLogger);
            } catch (IOException e) {
                defaultLogger.e(TAG, "Error creating file logger", e);
            }
            try {
                ILogger toastLogger = new ToastLogger(ILogger.INFO, context);
                loggerList.add(toastLogger);
            } catch (Throwable throwable) {
                defaultLogger.e(TAG, "Error creating toast logger", throwable);
            }

            return new CompositeLogger(loggerList);
        });
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));

        providerRegistry.register(FileHelper.class, new FileHelper(provider, context));
        providerRegistry.register(CommonNavConfig.class, new CommonNavConfig());
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerLazy(AudioRecorder.class, () -> new AudioRecorder(context, provider));
        providerRegistry.registerLazy(AudioPlayer.class, () -> new AudioPlayer(context, provider));
        providerRegistry.registerLazy(DeckChangeNotifier.class, DeckChangeNotifier::new);
        providerRegistry.registerLazy(TestChangeNotifier.class, TestChangeNotifier::new);
        providerRegistry.registerLazy(NotificationTimerChangeNotifier.class, NotificationTimerChangeNotifier::new);
        providerRegistry.registerLazy(NotificationTimeChangeNotifier.class, NotificationTimeChangeNotifier::new);
        // clean up undeleted file
        providerRegistry.registerAsync(FileCleanUpTask.class, () -> new FileCleanUpTask(provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        ILogger iLogger = provider.get(ILogger.class);
        ExecutorService executorService = provider.get(ExecutorService.class);
        ScheduledExecutorService scheduledExecutorService = provider.get(ScheduledExecutorService.class);
        try {
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(1500, TimeUnit.MILLISECONDS);
            iLogger.d(TAG, "ExecutorService shutdown? " + terminated);
        } catch (Throwable throwable) {
            iLogger.e(TAG, "Failed to shutdown ExecutorService", throwable);
        }
        try {
            scheduledExecutorService.shutdown();
            boolean terminated = scheduledExecutorService.awaitTermination(1500, TimeUnit.MILLISECONDS);
            iLogger.d(TAG, "ScheduledExecutorService shutdown? " + terminated);
        } catch (Throwable throwable) {
            iLogger.e(TAG, "Failed to shutdown ScheduledExecutorService", throwable);
        }
    }
}

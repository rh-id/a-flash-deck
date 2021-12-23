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

import androidx.room.Room;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.repository.AndroidNotificationRepo;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;
import m.co.rh.id.a_flash_deck.base.room.DbMigration;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = context.getApplicationContext();
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(appContext,
                        AppDatabase.class, "a-flash-deck.db")
                        .addMigrations(DbMigration.getAllMigrations())
                        .build());
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(DeckDao.class, () ->
                provider.get(AppDatabase.class).deckDao());
        providerRegistry.registerAsync(TestDao.class, () ->
                provider.get(AppDatabase.class).testDao());
        providerRegistry.registerAsync(AndroidNotificationRepo.class, () ->
                {
                    // ensure that this is initialized in background thread due to SQLite logic
                    Future<AndroidNotificationRepo> androidNotificationRepoFuture = provider.get(ExecutorService.class)
                            .submit(() -> new AndroidNotificationRepo(provider.get(AppDatabase.class).androidNotificationDao()));
                    try {
                        return androidNotificationRepoFuture.get();
                    } catch (Exception e) {
                        provider.get(ILogger.class).e("DatabaseProviderModule", "failed to init AndroidNotificationRepo", e);
                        throw new RuntimeException(e);
                    }
                }
        );
        providerRegistry.registerLazy(NotificationTimerDao.class, () ->
                provider.get(AppDatabase.class).timerNotificationDao());
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}

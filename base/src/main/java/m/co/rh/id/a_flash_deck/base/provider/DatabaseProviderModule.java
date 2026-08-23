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

import androidx.annotation.NonNull;
import androidx.room.Room;

import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.NotificationTimerDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.repository.AndroidNotificationRepository;
import m.co.rh.id.a_flash_deck.base.repository.DeckCardRepository;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;
import m.co.rh.id.a_flash_deck.base.room.DbMigration;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(AppDatabase.class,
                getAppDatabaseProviderValue(provider.getContext()));
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(DeckDao.class, () ->
                provider.get(AppDatabase.class).deckDao());
        providerRegistry.registerAsync(CardDao.class, () ->
                provider.get(AppDatabase.class).cardDao());
        providerRegistry.registerAsync(TestDao.class, () ->
                provider.get(AppDatabase.class).testDao());
        providerRegistry.registerAsync(AndroidNotificationRepository.class, () ->
                new AndroidNotificationRepository(provider.getContext(),
                        provider.get(AppDatabase.class).androidNotificationDao())
        );
        providerRegistry.registerAsync(DeckCardRepository.class, () -> {
            AppDatabase db = provider.get(AppDatabase.class);
            return new DeckCardRepository(db, db.deckDao(), db.cardDao());
        });
        providerRegistry.registerLazy(NotificationTimerDao.class, () ->
                provider.get(AppDatabase.class).timerNotificationDao());
    }

    @NonNull
    protected ProviderValue<AppDatabase> getAppDatabaseProviderValue(Context appContext) {
        return () ->
                Room.databaseBuilder(appContext,
                        AppDatabase.class, "a-flash-deck.db")
                        .addMigrations(DbMigration.getAllMigrations())
                        .build();
    }
}

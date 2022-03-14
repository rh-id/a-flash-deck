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

package m.co.rh.id.a_flash_deck.bot.provider;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import m.co.rh.id.a_flash_deck.bot.dao.CardLogDao;
import m.co.rh.id.a_flash_deck.bot.dao.SuggestedCardDao;
import m.co.rh.id.a_flash_deck.bot.provider.component.BotAnalytics;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.a_flash_deck.bot.room.BotDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Provider module for database configuration
 */
public class BotProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = context.getApplicationContext();
        providerRegistry.registerAsync(BotDatabase.class, getDatabaseProviderValue(appContext));
        providerRegistry.registerAsync(CardLogDao.class, () ->
                provider.get(BotDatabase.class).cardLogDao());
        providerRegistry.registerAsync(SuggestedCardDao.class, () ->
                provider.get(BotDatabase.class).suggestedCardDao());

        providerRegistry.registerAsync(SuggestedCardChangeNotifier.class, () -> new SuggestedCardChangeNotifier(provider));
        providerRegistry.registerAsync(BotAnalytics.class, () -> new BotAnalytics(provider));
    }

    @NonNull
    protected ProviderValue<BotDatabase> getDatabaseProviderValue(Context appContext) {
        return () ->
                Room.databaseBuilder(appContext,
                        BotDatabase.class, "a-flash-deck.bot.db")
                        .build();
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}

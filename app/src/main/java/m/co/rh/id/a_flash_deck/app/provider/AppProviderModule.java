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

package m.co.rh.id.a_flash_deck.app.provider;

import android.app.Application;
import android.content.Context;

import androidx.work.WorkManager;

import m.co.rh.id.a_flash_deck.app.component.AppNotificationHandler;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.provider.BaseProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.RxProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimeChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());
        providerRegistry.registerModule(new RxProviderModule());

        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));

        providerRegistry.registerPool(IStatefulViewProvider.class, () -> new StatefulViewProvider(provider));
        providerRegistry.registerLazy(DeckChangeNotifier.class, DeckChangeNotifier::new);
        providerRegistry.registerLazy(TestChangeNotifier.class, TestChangeNotifier::new);
        providerRegistry.registerLazy(NotificationTimeChangeNotifier.class, NotificationTimeChangeNotifier::new);
        providerRegistry.registerLazy(TestStateModifier.class, () -> new TestStateModifier(context, provider));
        providerRegistry.registerAsync(AppNotificationHandler.class, () -> new AppNotificationHandler(context, provider));

        // clean up undeleted file
        providerRegistry.registerAsync(FileCleanUpTask.class, () -> new FileCleanUpTask(provider));

        // Timer notification
        providerRegistry.registerLazy(NotificationTimerChangeNotifier.class, NotificationTimerChangeNotifier::new);
        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(NavigatorProvider.class, new NavigatorProvider(mApplication, provider));
    }


    @Override
    public void dispose(Context context, Provider provider) {
        mApplication = null;
    }
}

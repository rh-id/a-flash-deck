/*
 *     Copyright (C) 2021-2026 Ruby Hartono
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

import m.co.rh.id.a_flash_deck.app.provider.component.AnkiExporter;
import m.co.rh.id.a_flash_deck.app.provider.component.AnkiImporter;
import m.co.rh.id.a_flash_deck.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_flash_deck.app.provider.component.AppShortcutHandler;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.provider.BaseProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.RxProviderModule;
import m.co.rh.id.a_flash_deck.bot.provider.BotProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());
        providerRegistry.registerModule(new RxProviderModule());
        providerRegistry.registerModule(new BotProviderModule());

        providerRegistry.registerPool(IStatefulViewProvider.class, () -> new StatefulViewProvider(provider));
        providerRegistry.registerLazy(TestStateModifier.class, () -> new TestStateModifier(provider));
        providerRegistry.registerAsync(AppNotificationHandler.class, () -> new AppNotificationHandler(provider));
        providerRegistry.registerAsync(AppShortcutHandler.class, () -> new AppShortcutHandler(provider));
        providerRegistry.registerLazy(AnkiImporter.class, () -> new AnkiImporter(provider));
        providerRegistry.registerLazy(AnkiExporter.class, () -> new AnkiExporter(provider));

        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(NavigatorProvider.class, () -> new NavigatorProvider(mApplication, provider));
    }
}

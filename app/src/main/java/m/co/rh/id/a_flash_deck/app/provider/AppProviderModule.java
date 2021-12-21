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

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.work.WorkManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_flash_deck.app.MainActivity;
import m.co.rh.id.a_flash_deck.app.component.AppNotificationHandler;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.app.ui.page.CardDetailPage;
import m.co.rh.id.a_flash_deck.app.ui.page.CardListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.CardShowPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckDetailSVDialog;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckSelectSVDialog;
import m.co.rh.id.a_flash_deck.app.ui.page.NotificationTimerListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.SettingsPage;
import m.co.rh.id.a_flash_deck.app.ui.page.SplashPage;
import m.co.rh.id.a_flash_deck.app.ui.page.TestPage;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.BaseProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.RxProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimeChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.a_flash_deck.base.ui.component.common.BooleanSVDialog;
import m.co.rh.id.a_flash_deck.base.ui.component.common.MessageSVDialog;
import m.co.rh.id.a_flash_deck.base.ui.component.common.TimePickerSVDialog;
import m.co.rh.id.a_flash_deck.timer.ui.page.NotificationTimerDetailSVDialog;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;
    private Navigator mNavigator;

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

        // Timer notification
        providerRegistry.registerLazy(NotificationTimerChangeNotifier.class, NotificationTimerChangeNotifier::new);
        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(INavigator.class, getNavigator(provider));
    }

    private Navigator getNavigator(Provider provider) {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        navMap.put(Routes.CARD_DETAIL_PAGE, (args, activity) -> new CardDetailPage());
        navMap.put(Routes.CARD_SHOW_PAGE, (args, activity) -> new CardShowPage());
        navMap.put(Routes.DECK_DETAIL_DIALOG, (args, activity) -> new DeckDetailSVDialog());
        navMap.put(Routes.DECK_SELECT_DIALOG, (args, activity) -> new DeckSelectSVDialog());
        navMap.put(Routes.DECKS, (args, activity) -> new DeckListPage());
        navMap.put(Routes.CARDS, (args, activity) -> new CardListPage());
        navMap.put(Routes.TEST, (args, activity) -> new TestPage());
        navMap.put(Routes.NOTIFICATION_TIMERS, (args, activity) -> new NotificationTimerListPage());
        navMap.put(Routes.NOTIFICATION_TIMER_DETAIL_DIALOG, (args, activity) -> new NotificationTimerDetailSVDialog());
        navMap.put(Routes.COMMON_BOOLEAN_DIALOG, (args, activity) -> new BooleanSVDialog());
        navMap.put(Routes.COMMON_MESSAGE_DIALOG, (args, activity) -> new MessageSVDialog());
        navMap.put(Routes.COMMON_TIMEPICKER_DIALOG, (args, activity) -> new TimePickerSVDialog());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setSaveStateFile(new File(mApplication.getCacheDir(),
                "anavigator/Navigator.state"));
        navBuilder.setRequiredComponent(provider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mNavigator = navigator;
        mApplication.registerActivityLifecycleCallbacks(mNavigator);
        mApplication.registerComponentCallbacks(mNavigator);
        return navigator;
    }

    @Override
    public void dispose(Context context, Provider provider) {
        mApplication.unregisterActivityLifecycleCallbacks(mNavigator);
        mApplication.unregisterComponentCallbacks(mNavigator);
        mApplication = null;
    }
}

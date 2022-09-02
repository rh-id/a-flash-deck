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
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.CardShowActivity;
import m.co.rh.id.a_flash_deck.app.MainActivity;
import m.co.rh.id.a_flash_deck.app.ui.page.CardDetailPage;
import m.co.rh.id.a_flash_deck.app.ui.page.CardListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.CardShowHomePage;
import m.co.rh.id.a_flash_deck.app.ui.page.CardShowPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckDetailSVDialog;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckSelectSVDialog;
import m.co.rh.id.a_flash_deck.app.ui.page.DonationsPage;
import m.co.rh.id.a_flash_deck.app.ui.page.HomePage;
import m.co.rh.id.a_flash_deck.app.ui.page.NotificationTimerListPage;
import m.co.rh.id.a_flash_deck.app.ui.page.SettingsPage;
import m.co.rh.id.a_flash_deck.app.ui.page.SplashPage;
import m.co.rh.id.a_flash_deck.app.ui.page.TestPage;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.timer.ui.page.NotificationTimerDetailSVDialog;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

@SuppressWarnings("rawtypes")
public class NavigatorProvider implements ProviderDisposable {
    private Application mApplication;
    private Provider mProvider;
    private CommonNavConfig mCommonNavConfig;
    private Map<Class<? extends Activity>, Navigator> mActivityNavigatorMap;
    private ThreadPoolExecutor mThreadPoolExecutor;
    private View mLoadingView;

    public NavigatorProvider(Application application, Provider provider) {
        mApplication = application;
        mProvider = provider;
        mActivityNavigatorMap = new LinkedHashMap<>();
        mCommonNavConfig = mProvider.get(CommonNavConfig.class);
        int maxThread = Runtime.getRuntime().availableProcessors();
        mThreadPoolExecutor = new ThreadPoolExecutor(maxThread, maxThread, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        mThreadPoolExecutor.allowCoreThreadTimeOut(true);
        mThreadPoolExecutor.prestartAllCoreThreads();
        mLoadingView = LayoutInflater.from(mProvider.getContext())
                .inflate(R.layout.page_splash, null);
        setupMainActivityNavigator();
        setupCardShowActivityNavigator();
    }

    public INavigator getNavigator(Activity activity) {
        return mActivityNavigatorMap.get(activity.getClass());
    }

    @SuppressWarnings("unchecked")
    private Navigator setupMainActivityNavigator() {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.SPLASH_PAGE, (args, activity) -> new SplashPage(Routes.HOME_PAGE));
        navMap.put(Routes.HOME_PAGE, (args, activity) -> new HomePage());
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        navMap.put(Routes.DONATIONS_PAGE, (args, activity) -> new DonationsPage());
        navMap.put(Routes.CARD_DETAIL_PAGE, (args, activity) -> new CardDetailPage());
        navMap.put(Routes.CARD_SHOW_PAGE, (args, activity) -> new CardShowPage());
        navMap.put(Routes.DECK_DETAIL_DIALOG, (args, activity) -> new DeckDetailSVDialog());
        navMap.put(Routes.DECK_SELECT_DIALOG, (args, activity) -> new DeckSelectSVDialog());
        navMap.put(Routes.DECKS, (args, activity) -> new DeckListPage());
        navMap.put(Routes.CARDS, (args, activity) -> new CardListPage());
        navMap.put(Routes.TEST, (args, activity) -> new TestPage());
        navMap.put(Routes.NOTIFICATION_TIMERS, (args, activity) -> new NotificationTimerListPage());
        navMap.put(Routes.NOTIFICATION_TIMER_DETAIL_DIALOG, (args, activity) -> new NotificationTimerDetailSVDialog());
        navMap.putAll(mCommonNavConfig.getNavMap());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.SPLASH_PAGE, navMap);
        navBuilder.setRequiredComponent(mProvider);
        navBuilder.setMainHandler(mProvider.get(Handler.class));
        navBuilder.setThreadPoolExecutor(mThreadPoolExecutor);
        navBuilder.setLoadingView(mLoadingView);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mActivityNavigatorMap.put(MainActivity.class, navigator);
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @SuppressWarnings("unchecked")
    private Navigator setupCardShowActivityNavigator() {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> new CardShowHomePage());
        navMap.put(Routes.CARD_DETAIL_PAGE, (args, activity) -> new CardDetailPage());
        navMap.put(Routes.CARD_SHOW_PAGE, (args, activity) -> new CardShowPage());
        navMap.put(Routes.DECK_SELECT_DIALOG, (args, activity) -> new DeckSelectSVDialog());
        navMap.putAll(mCommonNavConfig.getNavMap());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setRequiredComponent(mProvider);
        navBuilder.setMainHandler(mProvider.get(Handler.class));
        navBuilder.setThreadPoolExecutor(mThreadPoolExecutor);
        navBuilder.setLoadingView(mLoadingView);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(CardShowActivity.class, navConfiguration);
        mActivityNavigatorMap.put(CardShowActivity.class, navigator);
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Context context) {
        if (mActivityNavigatorMap != null && !mActivityNavigatorMap.isEmpty()) {
            for (Map.Entry<Class<? extends Activity>, Navigator> navEntry : mActivityNavigatorMap.entrySet()) {
                Navigator navigator = navEntry.getValue();
                mApplication.unregisterActivityLifecycleCallbacks(navigator);
                mApplication.unregisterComponentCallbacks(navigator);
            }
            mActivityNavigatorMap.clear();
            mThreadPoolExecutor.shutdown();
        }
        mActivityNavigatorMap = null;
        mProvider = null;
        mApplication = null;
        mThreadPoolExecutor = null;
        mLoadingView = null;
    }
}

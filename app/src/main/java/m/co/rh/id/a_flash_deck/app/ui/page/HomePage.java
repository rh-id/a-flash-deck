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

package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements NavOnBackPressed<Activity>, View.OnClickListener, DrawerLayout.DrawerListener {
    private static final String TAG = HomePage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient Provider mProvider;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;
    private transient Provider mSvProvider;
    private transient DrawerLayout mDrawerLayout;
    private transient BehaviorSubject<Optional<TestState>> mTestStateSubject;

    public HomePage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        if (mTestStateSubject == null) {
            mTestStateSubject = BehaviorSubject.create();
        }
        View view = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        View menuDecks = view.findViewById(R.id.menu_decks);
        menuDecks.setOnClickListener(this);
        View menuCards = view.findViewById(R.id.menu_cards);
        menuCards.setOnClickListener(this);
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuNotificationTimers = view.findViewById(R.id.menu_notification_timers);
        menuNotificationTimers.setOnClickListener(this);
        mDrawerLayout = view.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.home));
        mAppBarSV.setNavigationOnClick(this);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        Button addDeckButton = view.findViewById(R.id.button_add_deck);
        Button addCardButton = view.findViewById(R.id.button_add_card);
        Button startTestButton = view.findViewById(R.id.button_start_test);
        Button addNotificationButton = view.findViewById(R.id.button_add_notification);
        Button exportDeckButton = view.findViewById(R.id.button_export_deck);
        addDeckButton.setOnClickListener(this);
        addCardButton.setOnClickListener(this);
        startTestButton.setOnClickListener(this);
        addNotificationButton.setOnClickListener(this);
        exportDeckButton.setOnClickListener(this);
        ViewGroup cardOnGoingTest = view.findViewById(R.id.container_card_ongoing_test);
        cardOnGoingTest.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onGoingTest",
                        mTestStateSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testStateOptional -> {
                                    if (testStateOptional.isPresent()) {
                                        TestState testState = testStateOptional.get();
                                        String totalCards = (testState.getCurrentCardIndex() + 1) + " / " + testState.getTotalCards();
                                        cardOnGoingTest.setVisibility(View.VISIBLE);
                                        TextView textTotalCard = cardOnGoingTest.findViewById(R.id.text_total_cards);
                                        textTotalCard.setText(totalCards);
                                    } else {
                                        cardOnGoingTest.setVisibility(View.GONE);
                                    }
                                }));
        mSvProvider.get(RxDisposer.class)
                .add("createView_loadActiveTest",
                        mSvProvider.get(TestStateModifier.class).getActiveTest()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((testStateOptional, throwable) -> {
                                    Context svContext = mSvProvider.getContext();
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class).e(TAG, svContext.getString(R.string.error_loading_test), throwable);
                                    } else {
                                        mTestStateSubject.onNext(testStateOptional);
                                    }
                                }));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onStartTest",
                        mSvProvider.get(TestChangeNotifier.class).getStartTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent ->
                                        mTestStateSubject.onNext(Optional.of(testEvent.getTestState())))
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onStopTest",
                        mSvProvider.get(TestChangeNotifier.class).getStopTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent -> mTestStateSubject.onNext(Optional.empty()))
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onTestStateChanged",
                        mSvProvider.get(TestChangeNotifier.class).getTestStateChangeFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testState -> mTestStateSubject.onNext(Optional.of(testState)))
                );
        return view;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_deck) {
            mNavigator.push(Routes.DECK_DETAIL_DIALOG);
        } else if (id == R.id.button_add_card) {
            // Check if deck empty then new card
            mSvProvider.get(RxDisposer.class)
                    .add("onClick_addNewCard",
                            mSvProvider.get(NewCardCmd.class)
                                    .countDeck().observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((integer, throwable) -> {
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class).e(TAG,
                                                    mSvProvider.getContext().getString(R.string.error_count_deck), throwable);
                                        } else {
                                            if (integer == 0) {
                                                mNavigator.push(Routes.DECK_DETAIL_DIALOG,
                                                        (navigator, navRoute, activity, currentView) ->
                                                        {
                                                            Serializable serializable = navRoute.getRouteResult();
                                                            if (serializable != null) {
                                                                navigator.push(Routes.CARD_DETAIL_PAGE,
                                                                        CardDetailPage.Args.withDeck(
                                                                                DeckDetailSVDialog.Result.of(serializable)
                                                                                        .getDeck()
                                                                        ));
                                                            }
                                                        });
                                            } else {
                                                mNavigator.push(Routes.DECK_SELECT_DIALOG,
                                                        (navigator, navRoute, activity, currentView) -> {
                                                            DeckSelectSVDialog.Result result =
                                                                    DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                                                            if (result != null) {
                                                                if (!result.getSelectedDeck().isEmpty()) {
                                                                    Deck deck = result.getSelectedDeck().get(0);
                                                                    navigator.push(Routes.CARD_DETAIL_PAGE,
                                                                            CardDetailPage.Args.withDeck(
                                                                                    deck
                                                                            ));
                                                                } else {
                                                                    Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                                                                    String title = provider.getContext().getString(R.string.title_error);
                                                                    String content = provider.getContext().getString(R.string.error_no_deck_selected);
                                                                    navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                                            provider.get(CommonNavConfig.class).args_commonMessageDialog(title,
                                                                                    content));
                                                                }
                                                            }
                                                        });
                                            }
                                        }
                                    })
                    );
        } else if (id == R.id.button_start_test) {
            mSvProvider.get(RxDisposer.class)
                    .add("onClick_startTest",
                            mSvProvider.get(TestStateModifier.class).getActiveTest()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((testStateOpt, throwable) -> {
                                        CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
                                        if (throwable != null) {
                                            String title = mSvProvider.getContext().getString(R.string.title_error);
                                            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    commonNavConfig.args_commonMessageDialog(title, throwable.getMessage()));
                                            mSvProvider.get(ILogger.class).e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            if (testStateOpt.isPresent()) {
                                                Context svContext = mSvProvider.getContext();
                                                String title = svContext.getString(R.string.title_confirm);
                                                String content = svContext.getString(R.string.test_session_exist_confirm_start_new);
                                                mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                                                        commonNavConfig.args_commonBooleanDialog(title, content),
                                                        (navigator, navRoute, activity, currentView) -> {
                                                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                                                            CommonNavConfig commonNavConfig1 = provider.get(CommonNavConfig.class);
                                                            if (commonNavConfig1.result_commonBooleanDialog(navRoute)) {
                                                                CompositeDisposable compositeDisposable = new CompositeDisposable();
                                                                compositeDisposable.add(provider.get(TestStateModifier.class).stopActiveTest()
                                                                        .observeOn(AndroidSchedulers.mainThread())
                                                                        .subscribe((testState, throwable1) -> {
                                                                            if (throwable1 != null) {
                                                                                String title1 = provider.getContext().getString(R.string.title_error);
                                                                                navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                                                        commonNavConfig1.args_commonMessageDialog(title1, throwable1.getMessage()));
                                                                                provider.get(ILogger.class).e(TAG, throwable1.getMessage(), throwable1);
                                                                            } else {
                                                                                startTestWorkflow(navigator);
                                                                            }
                                                                            compositeDisposable.dispose();
                                                                        })
                                                                );
                                                            }
                                                        });
                                            } else {
                                                startTestWorkflow(mNavigator);
                                            }
                                        }
                                    })
                    );

        } else if (id == R.id.button_add_notification) {
            NotificationTimerListPage.addNewNotificationTimerWorkflow(mNavigator);
        } else if (id == R.id.button_export_deck) {
            mNavigator.push(Routes.DECK_SELECT_DIALOG, DeckSelectSVDialog.Args.multiSelectMode(),
                    (navigator, navRoute, activity, currentView) -> {
                        DeckSelectSVDialog.Result result = DeckSelectSVDialog.Result.of(navRoute);
                        if (result != null) {
                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                            Context context = provider.getContext();
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(ExportImportCmd.class)
                                    .exportFile(result.getSelectedDeck())
                                    .subscribe((file, throwable) -> {
                                        if (throwable != null) {
                                            if (throwable.getCause() instanceof ValidationException) {
                                                String title = context.getString(R.string.error);
                                                navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                        provider.get(CommonNavConfig.class).args_commonMessageDialog(title,
                                                                throwable.getCause().getMessage()));
                                            } else {
                                                provider.get(ILogger.class)
                                                        .e(TAG, throwable.getMessage(), throwable);
                                            }
                                        } else {
                                            provider.get(ILogger.class)
                                                    .d(TAG, "File exported: " + file.getAbsolutePath());
                                            UiUtils.shareFile(context, file, file.getName());
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_decks) {
            mNavigator.push(Routes.DECKS);
        } else if (id == R.id.menu_cards) {
            mNavigator.push(Routes.CARDS);
        } else if (id == R.id.menu_notification_timers) {
            mNavigator.push(Routes.NOTIFICATION_TIMERS);
        } else if (id == R.id.container_card_ongoing_test) {
            mNavigator.push(Routes.TEST);
        } else {
            // if not match other ids, this is toolbar internal button id onclick: mAppBarSV.setNavigationOnClick(this);
            if (!mDrawerLayout.isOpen()) {
                mDrawerLayout.open();
            }
        }
    }

    private void startTestWorkflow(INavigator navigatorInstance) {
        navigatorInstance.push(Routes.DECK_SELECT_DIALOG, DeckSelectSVDialog.Args.multiSelectMode(),
                (navigator, navRoute, activity, currentView) -> {
                    DeckSelectSVDialog.Result result = DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                    if (result != null) {
                        Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                        ArrayList<Deck> deckArrayList = result.getSelectedDeck();
                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                        compositeDisposable.add(provider.get(TestStateModifier.class)
                                .startTest(deckArrayList)
                                .subscribe((testState, throwable) -> {
                                    if (throwable != null) {
                                        Context context = provider.getContext();
                                        if (throwable.getCause() instanceof ValidationException) {
                                            String title = context.getString(R.string.title_error);
                                            CommonNavConfig commonNavConfig = provider.get(CommonNavConfig.class);
                                            navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    commonNavConfig.args_commonMessageDialog(title,
                                                            throwable.getCause().getMessage()),
                                                    (navigator1, navRoute1, activity1, currentView1) -> startTestWorkflow(navigator1));
                                        } else {
                                            provider.get(ILogger.class).e(TAG,
                                                    context.getString(R.string.error_starting_test), throwable);
                                        }
                                    } else {
                                        navigator.push(Routes.TEST);
                                    }
                                })
                        );
                    }
                });
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mProvider = null;
        mDrawerLayout = null;
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (mDrawerLayout.isOpen()) {
            mDrawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                mSvProvider
                        .get(ILogger.class)
                        .i(TAG, activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        // Leave blank
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        // Leave blank
    }
}

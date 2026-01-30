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
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
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
import m.co.rh.id.a_flash_deck.bot.entity.SuggestedCard;
import m.co.rh.id.a_flash_deck.bot.provider.command.DeleteSuggestedCardCmd;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, View.OnClickListener, DrawerLayout.DrawerListener, NavOnActivityResult<Activity> {
    private static final String TAG = HomePage.class.getName();
    private static final int REQUEST_CODE_IMPORT_DECK = 1;

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient TestStateModifier mTestStateModifier;
    private transient TestChangeNotifier mTestChangeNotifier;
    private transient SuggestedCardChangeNotifier mSuggestedCardChangeNotifier;
    private transient CommonNavConfig mCommonNavConfig;
    private transient NewCardCmd mNewCardCmd;
    private transient DeleteSuggestedCardCmd mDeleteSuggestedCardCmd;
    private transient ExportImportCmd mExportImportCmd;
    private transient DrawerLayout mDrawerLayout;
    private transient BehaviorSubject<Optional<TestState>> mTestStateSubject;

    public HomePage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mTestStateModifier = mSvProvider.get(TestStateModifier.class);
        mTestChangeNotifier = mSvProvider.get(TestChangeNotifier.class);
        mSuggestedCardChangeNotifier = mSvProvider.get(SuggestedCardChangeNotifier.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        mDeleteSuggestedCardCmd = mSvProvider.get(DeleteSuggestedCardCmd.class);
        mExportImportCmd = mSvProvider.get(ExportImportCmd.class);
        mTestStateSubject = BehaviorSubject.create();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        View menuDecks = rootLayout.findViewById(R.id.menu_decks);
        menuDecks.setOnClickListener(this);
        View menuCards = rootLayout.findViewById(R.id.menu_cards);
        menuCards.setOnClickListener(this);
        View menuSettings = rootLayout.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuDonations = rootLayout.findViewById(R.id.menu_donations);
        menuDonations.setOnClickListener(this);
        View menuNotificationTimers = rootLayout.findViewById(R.id.menu_notification_timers);
        menuNotificationTimers.setOnClickListener(this);
        mDrawerLayout = rootLayout.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.home));
        mAppBarSV.setNavigationOnClick(this);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        Button addDeckButton = rootLayout.findViewById(R.id.button_add_deck);
        Button addCardButton = rootLayout.findViewById(R.id.button_add_card);
        Button startTestButton = rootLayout.findViewById(R.id.button_start_test);
        Button addNotificationButton = rootLayout.findViewById(R.id.button_add_notification);
        Button exportDeckButton = rootLayout.findViewById(R.id.button_export_deck);
        Button importDeckButton = rootLayout.findViewById(R.id.button_import_deck);
        addDeckButton.setOnClickListener(this);
        addCardButton.setOnClickListener(this);
        startTestButton.setOnClickListener(this);
        addNotificationButton.setOnClickListener(this);
        exportDeckButton.setOnClickListener(this);
        importDeckButton.setOnClickListener(this);
        ViewGroup cardOnGoingTest = rootLayout.findViewById(R.id.container_card_ongoing_test);
        cardOnGoingTest.setOnClickListener(this);
        View flashBotContainer = rootLayout.findViewById(R.id.container_card_flash_bot);
        Button flashBotAcceptButton = rootLayout.findViewById(R.id.button_flash_bot_accept);
        flashBotAcceptButton.setOnClickListener(this);
        mRxDisposer
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
        mRxDisposer
                .add("createView_loadActiveTest",
                        mTestStateModifier.getActiveTest()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((testStateOptional, throwable) -> {
                                    Context svContext = mSvProvider.getContext();
                                    if (throwable != null) {
                                        mLogger.e(TAG, svContext.getString(R.string.error_loading_test), throwable);
                                    } else {
                                        mTestStateSubject.onNext(testStateOptional);
                                    }
                                }));
        mRxDisposer
                .add("createView_onStartTest",
                        mTestChangeNotifier.getStartTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent ->
                                        mTestStateSubject.onNext(Optional.of(testEvent.getTestState())))
                );
        mRxDisposer
                .add("createView_onStopTest",
                        mTestChangeNotifier.getStopTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent -> mTestStateSubject.onNext(Optional.empty()))
                );
        mRxDisposer
                .add("createView_onTestStateChanged",
                        mTestChangeNotifier.getTestStateChangeFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testState -> mTestStateSubject.onNext(Optional.of(testState)))
                );
        mRxDisposer
                .add("createView_onSuggestedCardChanged",
                        mSuggestedCardChangeNotifier
                                .getSuggestedCardFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(suggestedCards -> {
                                    if (!suggestedCards.isEmpty()) {
                                        flashBotContainer.setVisibility(View.VISIBLE);
                                    } else {
                                        flashBotContainer.setVisibility(View.GONE);
                                    }
                                }));
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_deck) {
            mNavigator.push(Routes.DECK_DETAIL_DIALOG);
        } else if (id == R.id.button_add_card) {
            // Check if deck empty then new card
            mRxDisposer
                    .add("onClick_addNewCard",
                            mNewCardCmd
                                    .countDeck().observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((integer, throwable) -> {
                                        if (throwable != null) {
                                            mLogger.e(TAG,
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
            mRxDisposer
                    .add("onClick_startTest",
                            mTestStateModifier.getActiveTest()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((testStateOpt, throwable) -> {
                                        if (throwable != null) {
                                            String title = mSvProvider.getContext().getString(R.string.title_error);
                                            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    mCommonNavConfig.args_commonMessageDialog(title, throwable.getMessage()));
                                            mLogger.e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            if (testStateOpt.isPresent()) {
                                                Context svContext = mSvProvider.getContext();
                                                String title = svContext.getString(R.string.title_confirm);
                                                String content = svContext.getString(R.string.test_session_exist_confirm_start_new);
                                                mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                                                        mCommonNavConfig.args_commonBooleanDialog(title, content),
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
        } else if (id == R.id.button_import_deck) {
            Activity activity = mNavigator.getActivity();
            String chooserMessage = activity.getString(R.string.title_import_deck);
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            String[] mimeTypes = {"application/zip", "application/octet-stream", "application/vnd.anki.apkg"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent = Intent.createChooser(intent, chooserMessage);
            activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_DECK);
        } else if (id == R.id.button_flash_bot_accept) {
            mRxDisposer
                    .add("onClick_flashBot_startTest",
                            mTestStateModifier.getActiveTest()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((testStateOpt, throwable) -> {
                                        if (throwable != null) {
                                            String title = mSvProvider.getContext().getString(R.string.title_error);
                                            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    mCommonNavConfig.args_commonMessageDialog(title, throwable.getMessage()));
                                            mLogger.e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            if (testStateOpt.isPresent()) {
                                                Context svContext = mSvProvider.getContext();
                                                String title = svContext.getString(R.string.title_confirm);
                                                String content = svContext.getString(R.string.test_session_exist_confirm_start_new);
                                                mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                                                        mCommonNavConfig.args_commonBooleanDialog(title, content),
                                                        (navigator, navRoute, activity, currentView) -> {
                                                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                                                            CommonNavConfig commonNavConfig1 = provider.get(CommonNavConfig.class);
                                                            if (commonNavConfig1.result_commonBooleanDialog(navRoute)) {
                                                                Context context = provider.getContext();
                                                                CompositeDisposable compositeDisposable = new CompositeDisposable();
                                                                ExecutorService executorService = provider.get(ExecutorService.class);
                                                                compositeDisposable.add(
                                                                        Single.fromFuture(
                                                                                executorService.submit(() -> {
                                                                                    provider.get(TestStateModifier.class).stopActiveTest().blockingGet();
                                                                                    List<SuggestedCard> suggestedCardList = provider.get(SuggestedCardChangeNotifier.class)
                                                                                            .getSuggestedCard();
                                                                                    List<Long> cardIds = new ArrayList<>();
                                                                                    if (!suggestedCardList.isEmpty()) {
                                                                                        for (SuggestedCard suggestedCard : suggestedCardList) {
                                                                                            cardIds.add(suggestedCard.cardId);
                                                                                        }
                                                                                    }
                                                                                    return provider.get(TestStateModifier.class).startTestWithCardIds(cardIds).blockingGet();
                                                                                })
                                                                        ).observeOn(AndroidSchedulers.mainThread()).subscribe((testState, throwable1) -> {
                                                                            if (throwable1 != null) {
                                                                                Throwable cause1 = throwable1.getCause();
                                                                                if (cause1 == null)
                                                                                    cause1 = throwable1;
                                                                                provider.get(ILogger.class).e(TAG,
                                                                                        context.getString(R.string.error_starting_test), cause1);
                                                                            } else {
                                                                                navigator.push(Routes.TEST);
                                                                                provider.get(DeleteSuggestedCardCmd.class)
                                                                                        .executeDeleteAll();
                                                                            }
                                                                            compositeDisposable.dispose();
                                                                        })
                                                                );
                                                            }
                                                        });
                                            } else {
                                                List<SuggestedCard> suggestedCardList = mSuggestedCardChangeNotifier
                                                        .getSuggestedCard();
                                                List<Long> cardIds = new ArrayList<>();
                                                if (!suggestedCardList.isEmpty()) {
                                                    for (SuggestedCard suggestedCard : suggestedCardList) {
                                                        cardIds.add(suggestedCard.cardId);
                                                    }
                                                }
                                                mRxDisposer
                                                        .add("onClick_flashBot_startTest_withCardIds",
                                                                mTestStateModifier.startTestWithCardIds(cardIds)
                                                                        .observeOn(AndroidSchedulers.mainThread())
                                                                        .subscribe((testState, throwable1) -> {
                                                                            if (throwable1 != null) {
                                                                                Throwable cause1 = throwable1.getCause();
                                                                                if (cause1 == null)
                                                                                    cause1 = throwable1;
                                                                                mLogger.e(TAG,
                                                                                        mSvProvider.getContext()
                                                                                                .getString(R.string.error_starting_test), cause1);
                                                                            } else {
                                                                                mNavigator.push(Routes.TEST);
                                                                                mDeleteSuggestedCardCmd
                                                                                        .executeDeleteAll();
                                                                            }
                                                                        })
                                                        );
                                            }
                                        }
                                    })
                    );
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_donations) {
            mNavigator.push(Routes.DONATIONS_PAGE);
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
                mLogger
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

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_IMPORT_DECK) {
            if (resultCode == Activity.RESULT_OK) {
                Context context = activity.getApplicationContext();
                mRxDisposer.add("onActivityResult_importFile"
                        , mExportImportCmd.importFile(data.getData())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((deckModels, throwable) -> {
                                    if (throwable != null) {
                                        mLogger
                                                .e(TAG, context.getString(R.string.error_failed_to_open_file)
                                                        , throwable);
                                    } else {
                                        mLogger.i(TAG,
                                                context.getString(R.string.success_import_file, deckModels.size()));
                                    }
                                }));
            }
        }
    }
}

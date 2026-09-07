/*
 *     Copyright (C) 2021-present Ruby Hartono
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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.ai.ui.page.GenerateDeckFromExistingPage;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.component.ExportImportCoordinator;
import m.co.rh.id.a_flash_deck.app.provider.component.TestWorkflowCoordinator;
import m.co.rh.id.a_flash_deck.app.ui.component.OngoingTestBannerSV;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
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

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient CommonNavConfig mCommonNavConfig;
    private transient NewCardCmd mNewCardCmd;
    private transient IAppNotificationHandler mAppNotificationHandler;
    private transient DrawerLayout mDrawerLayout;
    @NavInject
    private OngoingTestBannerSV mOngoingTestBannerSV;
    private transient TestWorkflowCoordinator mTestWorkflowCoordinator;
    private transient ExportImportCoordinator mExportImportCoordinator;
    private transient SuggestedCardChangeNotifier mSuggestedCardChangeNotifier;
    private transient ExecutorService mExecutorService;
    private transient StudyRepository mStudyRepository;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient TestChangeNotifier mTestChangeNotifier;
    private transient Button mStudyDueCardsButton;
    private transient boolean mStudyDueCardsShown;

    public HomePage() {
        mAppBarSV = new AppBarSV();
        mOngoingTestBannerSV = new OngoingTestBannerSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        mAppNotificationHandler = mSvProvider.get(IAppNotificationHandler.class);
        mSuggestedCardChangeNotifier = mSvProvider.get(SuggestedCardChangeNotifier.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mStudyRepository = mSvProvider.get(StudyRepository.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
        mTestChangeNotifier = mSvProvider.get(TestChangeNotifier.class);
        mTestWorkflowCoordinator = new TestWorkflowCoordinator(mSvProvider, mRxDisposer);
        mExportImportCoordinator = new ExportImportCoordinator(mSvProvider, mRxDisposer);
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
        Button studyDueCardsButton = rootLayout.findViewById(R.id.button_study_due_cards);
        studyDueCardsButton.setVisibility(View.GONE);
        mStudyDueCardsButton = studyDueCardsButton;
        mStudyDueCardsShown = false;
        Button addNotificationButton = rootLayout.findViewById(R.id.button_add_notification);
        Button exportDeckButton = rootLayout.findViewById(R.id.button_export_deck);
        Button exportAnkiButton = rootLayout.findViewById(R.id.button_export_anki);
        Button importDeckButton = rootLayout.findViewById(R.id.button_import_deck);
        Button generateDeckAiButton = rootLayout.findViewById(R.id.button_generate_deck_ai);
        Button generateDeckFromExistingAiButton = rootLayout.findViewById(R.id.button_generate_deck_from_existing_ai);
        Button generateDeckFromImageAiButton = rootLayout.findViewById(R.id.button_generate_deck_from_image_ai);
        addDeckButton.setOnClickListener(this);
        addCardButton.setOnClickListener(this);
        startTestButton.setOnClickListener(this);
        studyDueCardsButton.setOnClickListener(this);
        addNotificationButton.setOnClickListener(this);
        exportDeckButton.setOnClickListener(this);
        exportAnkiButton.setOnClickListener(this);
        importDeckButton.setOnClickListener(this);
        generateDeckAiButton.setOnClickListener(this);
        generateDeckFromExistingAiButton.setOnClickListener(this);
        generateDeckFromImageAiButton.setOnClickListener(this);

        // Mount ongoing test banner into container
        ViewGroup ongoingTestContainer = rootLayout.findViewById(R.id.container_card_ongoing_test);
        ongoingTestContainer.addView(mOngoingTestBannerSV.buildView(activity, ongoingTestContainer));

        View flashBotContainer = rootLayout.findViewById(R.id.container_card_flash_bot);
        Button flashBotAcceptButton = rootLayout.findViewById(R.id.button_flash_bot_accept);
        flashBotAcceptButton.setOnClickListener(this);

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
        mRxDisposer.add("createView_deckNotification",
                mAppNotificationHandler.getDeckMessageEventFlow()
                        .delay(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .subscribe(deck ->
                                mNavigator.push(Routes.CARDS, CardListPage.Args.withDeck(deck))
                        ));

        refreshDueCardCount(activity);
        mRxDisposer.add("createView_dueCardCount_cardAdded",
                mDeckChangeNotifier.getAddedCardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(card -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_cardUpdated",
                mDeckChangeNotifier.getUpdatedCardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(card -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_cardDeleted",
                mDeckChangeNotifier.getDeletedCardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(card -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_cardMoved",
                mDeckChangeNotifier.getMovedCardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(moveCardEvent -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_cardSuspendStateChanged",
                mDeckChangeNotifier.getSuspendStateChangedFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(cardSuspendStateChangedEvent -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_deckAdded",
                mDeckChangeNotifier.getAddedDeckFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_deckDeleted",
                mDeckChangeNotifier.getDeletedDeckFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> refreshDueCardCount(activity)));
        mRxDisposer.add("createView_dueCardCount_testStopped",
                mTestChangeNotifier.getStopTestEventFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(testEvent -> refreshDueCardCount(activity)));
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
                                                mNavigator.push(Routes.DECK_SELECT_PAGE,
                                                        (navigator, navRoute, activity, currentView) -> {
                                                            DeckSelectPage.Result result =
                                                                    DeckSelectPage.Result.of(navRoute.getRouteResult());
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
            mTestWorkflowCoordinator.startTestFlow(mNavigator);
        } else if (id == R.id.button_study_due_cards) {
            mTestWorkflowCoordinator.startDueTestFlow(mNavigator);
        } else if (id == R.id.button_add_notification) {
            NotificationTimerListPage.addNewNotificationTimerWorkflow(mNavigator);
        } else if (id == R.id.button_export_deck) {
            mExportImportCoordinator.exportFlow(mNavigator, false);
        } else if (id == R.id.button_export_anki) {
            mExportImportCoordinator.exportFlow(mNavigator, true);
        } else if (id == R.id.button_import_deck) {
            mExportImportCoordinator.importFlow(mNavigator.getActivity());
        } else if (id == R.id.button_generate_deck_ai) {
            pushAiRouteIfConfigured(Routes.AI_GENERATE_DECK_PAGE);
        } else if (id == R.id.button_generate_deck_from_existing_ai) {
            pushAiRouteIfConfiguredWithDeckSelect();
        } else if (id == R.id.button_generate_deck_from_image_ai) {
            pushAiRouteIfConfigured(Routes.AI_GENERATE_DECK_FROM_IMAGE_PAGE);
        } else if (id == R.id.button_flash_bot_accept) {
            mTestWorkflowCoordinator.startTestWithSuggestionsFlow(mNavigator);
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
        } else {
            // if not match other ids, this is toolbar internal button id onclick: mAppBarSV.setNavigationOnClick(this);
            if (!mDrawerLayout.isOpen()) {
                mDrawerLayout.open();
            }
        }
    }

    /**
     * Re-fetches the due/new card count and updates the study due cards button.
     * Reusing the same RxDisposer tag cancels any previous in-flight count request.
     */
    private void refreshDueCardCount(Activity activity) {
        mRxDisposer.add("createView_dueCardCount",
                Single.fromCallable(() -> mStudyRepository.getDueCardCount())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(integer ->
                                        updateDueCardCount(activity, integer),
                                throwable ->
                                        mLogger.e(TAG, "Failed to count due and new cards", throwable)));
    }

    private void updateDueCardCount(Activity activity, int count) {
        if (mStudyDueCardsButton == null) {
            return;
        }
        if (count > 0) {
            mStudyDueCardsButton.setText(activity.getString(R.string.study_due_cards_with_count, count));
            if (!mStudyDueCardsShown) {
                mStudyDueCardsShown = true;
                mStudyDueCardsButton.setVisibility(View.VISIBLE);
                playPopAnimation(mStudyDueCardsButton);
            }
        } else {
            mStudyDueCardsButton.setVisibility(View.GONE);
            mStudyDueCardsShown = false;
        }
    }

    /**
     * One-shot scale pop animation when the study due cards button appears.
     * The fade-in comes from the parent LinearLayout's layout transition
     * (animateLayoutChanges), so only scale is animated here.
     * Skipped when the system reduced motion setting (animator duration scale) is off.
     */
    private void playPopAnimation(Button button) {
        float animatorDurationScale = Settings.Global.getFloat(
                button.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        if (animatorDurationScale == 0.0f) {
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(button, View.SCALE_X, 0.85f, 1.0f),
                ObjectAnimator.ofFloat(button, View.SCALE_Y, 0.85f, 1.0f));
        animatorSet.setDuration(220);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    /**
     * AI gating helper method that checks if Gemini API is configured before pushing a route.
     */
    private void pushAiRouteIfConfigured(String routeId) {
        GeminiService geminiService = mSvProvider.get(GeminiService.class);
        if (geminiService.isConfigured()) {
            mNavigator.push(routeId);
        } else {
            String title = mSvProvider.getContext().getString(R.string.title_error);
            String content = mSvProvider.getContext().getString(R.string.error_api_key_not_configured);
            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                    mCommonNavConfig.args_commonMessageDialog(title, content));
        }
    }

    /**
     * AI gating helper for generate deck from existing AI.
     * Checks configuration BEFORE pushing DECK_SELECT_PAGE, preserving exact behavior.
     */
    private void pushAiRouteIfConfiguredWithDeckSelect() {
        GeminiService geminiService = mSvProvider.get(GeminiService.class);
        if (geminiService.isConfigured()) {
            mNavigator.push(Routes.DECK_SELECT_PAGE, DeckSelectPage.Args.multiSelectMode(),
                    (navigator, navRoute, activity, currentView) -> {
                        DeckSelectPage.Result result = DeckSelectPage.Result.of(navRoute.getRouteResult());
                        if (result != null && !result.getSelectedDeck().isEmpty()) {
                            ArrayList<Long> selectedDeckIds = new ArrayList<>();
                            for (Deck deck : result.getSelectedDeck()) {
                                selectedDeckIds.add(deck.id);
                            }
                            navigator.push(Routes.AI_GENERATE_DECK_FROM_EXISTING_PAGE,
                                    GenerateDeckFromExistingPage.Args.with(selectedDeckIds));
                        }
                    });
        } else {
            String title = mSvProvider.getContext().getString(R.string.title_error);
            String content = mSvProvider.getContext().getString(R.string.error_api_key_not_configured);
            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                    mCommonNavConfig.args_commonMessageDialog(title, content));
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mOngoingTestBannerSV != null) {
            mOngoingTestBannerSV.dispose(activity);
            mOngoingTestBannerSV = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mDrawerLayout = null;
        mStudyDueCardsButton = null;
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
        if (requestCode == ExportImportCoordinator.REQUEST_CODE_IMPORT_DECK) {
            if (resultCode == Activity.RESULT_OK) {
                mExportImportCoordinator.onImportResult(data.getData());
            }
        }
    }
}

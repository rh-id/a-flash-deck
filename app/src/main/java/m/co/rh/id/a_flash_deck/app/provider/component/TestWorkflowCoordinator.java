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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckSelectPage;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.bot.entity.SuggestedCard;
import m.co.rh.id.a_flash_deck.bot.provider.command.DeleteSuggestedCardCmd;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

/**
 * Coordinator for test-related workflows.
 * Plain Java class (NOT a StatefulView).
 */
public class TestWorkflowCoordinator {
    private static final String TAG = TestWorkflowCoordinator.class.getName();

    private final Provider mProvider;
    private final CommonNavConfig mCommonNavConfig;
    private final RxDisposer mRxDisposer;

    public TestWorkflowCoordinator(Provider provider, RxDisposer rxDisposer) {
        mProvider = provider;
        mCommonNavConfig = provider.get(CommonNavConfig.class);
        mRxDisposer = rxDisposer;
    }

    /**
     * Starts the test flow with deck selection.
     */
    public void startTestFlow(INavigator navigator) {
        mRxDisposer.add("onClick_startTest",
                mProvider.get(TestStateModifier.class)
                        .getActiveTest()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((testStateOpt, throwable) -> {
                    if (throwable != null) {
                        String title = mProvider.getContext().getString(R.string.title_error);
                        navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                mCommonNavConfig.args_commonMessageDialog(title, throwable.getMessage()));
                        mProvider.get(ILogger.class).e(
                                TAG,
                                throwable.getMessage(), throwable);
                    } else {
                        if (testStateOpt.isPresent()) {
                            Context svContext = mProvider.getContext();
                            String title = svContext.getString(R.string.title_confirm);
                            String content = svContext.getString(R.string.test_session_exist_confirm_start_new);
                            navigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                                    mCommonNavConfig.args_commonBooleanDialog(title, content),
                                    (navigator1, navRoute, activity, currentView) -> {
                                        Provider provider = (Provider) navigator1.getNavConfiguration().getRequiredComponent();
                                        CommonNavConfig commonNavConfig1 = provider.get(CommonNavConfig.class);
                                        if (commonNavConfig1.result_commonBooleanDialog(navRoute)) {
                                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                                            compositeDisposable.add(
                                                    provider.get(TestStateModifier.class)
                                                            .stopActiveTest()
                                                            .observeOn(AndroidSchedulers.mainThread())
                                                            .subscribe((testState, throwable1) -> {
                                                                if (throwable1 != null) {
                                                                    String title1 = provider.getContext().getString(R.string.title_error);
                                                                    navigator1.push(Routes.COMMON_MESSAGE_DIALOG,
                                                                            commonNavConfig1.args_commonMessageDialog(title1, throwable1.getMessage()));
                                                                    provider.get(ILogger.class).e(
                                                                            TAG,
                                                                            throwable1.getMessage(), throwable1);
                                                                } else {
                                                                    startDeckSelectTestFlow(navigator1);
                                                                }
                                                                compositeDisposable.dispose();
                                                            })
                                            );
                                        }
                                    });
                        } else {
                            startDeckSelectTestFlow(navigator);
                        }
                    }
                })
        );
    }

    /**
     * Starts the test flow with suggested cards (flash bot).
     */
    public void startTestWithSuggestionsFlow(INavigator navigator) {
        mRxDisposer.add("onClick_flashBot_startTest",
                mProvider.get(TestStateModifier.class)
                        .getActiveTest()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((testStateOpt, throwable) -> {
                    if (throwable != null) {
                        String title = mProvider.getContext().getString(R.string.title_error);
                        navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                mCommonNavConfig.args_commonMessageDialog(title, throwable.getMessage()));
                        mProvider.get(ILogger.class).e(
                                TAG,
                                throwable.getMessage(), throwable);
                    } else {
                        if (testStateOpt.isPresent()) {
                            Context svContext = mProvider.getContext();
                            String title = svContext.getString(R.string.title_confirm);
                            String content = svContext.getString(R.string.test_session_exist_confirm_start_new);
                            navigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                                    mCommonNavConfig.args_commonBooleanDialog(title, content),
                                    (navigator1, navRoute, activity, currentView) -> {
                                        Provider provider = (Provider) navigator1.getNavConfiguration().getRequiredComponent();
                                        CommonNavConfig commonNavConfig1 = provider.get(CommonNavConfig.class);
                                        if (commonNavConfig1.result_commonBooleanDialog(navRoute)) {
                                            Context context = provider.getContext();
                                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                                            ExecutorService executorService = provider.get(ExecutorService.class);
                                            compositeDisposable.add(
                                                    provider.get(TestStateModifier.class)
                                                            .stopActiveTest()
                                                            .subscribeOn(Schedulers.from(executorService))
                                                            .flatMap(testState1 -> {
                                                                List<SuggestedCard> suggestedCardList = provider.get(SuggestedCardChangeNotifier.class)
                                                                        .getSuggestedCard();
                                                                List<Long> cardIds = new ArrayList<>();
                                                                if (!suggestedCardList.isEmpty()) {
                                                                    for (SuggestedCard suggestedCard : suggestedCardList) {
                                                                    cardIds.add(suggestedCard.cardId);
                                                                }
                                                                }
                                                                return provider.get(TestStateModifier.class)
                                                                        .startTestWithCardIds(cardIds);
                                                            })
                                                            .observeOn(AndroidSchedulers.mainThread())
                                                            .subscribe((testState, throwable1) -> {
                                                                if (throwable1 != null) {
                                                                    Throwable cause1 = throwable1.getCause();
                                                                    if (cause1 == null)
                                                                        cause1 = throwable1;
                                                                    provider.get(ILogger.class).e(
                                                                            TAG,
                                                                            context.getString(R.string.error_starting_test), cause1);
                                                                } else {
                                                                    navigator1.push(Routes.TEST);
                                                                    provider.get(DeleteSuggestedCardCmd.class)
                                                                            .executeDeleteAll();
                                                                }
                                                                compositeDisposable.dispose();
                                                            })
                                            );
                                        }
                                    });
                        } else {
                            List<SuggestedCard> suggestedCardList = mProvider.get(SuggestedCardChangeNotifier.class)
                                    .getSuggestedCard();
                            List<Long> cardIds = new ArrayList<>();
                            if (!suggestedCardList.isEmpty()) {
                                for (SuggestedCard suggestedCard : suggestedCardList) {
                                    cardIds.add(suggestedCard.cardId);
                                }
                            }
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(
                                    mProvider.get(TestStateModifier.class)
                                            .startTestWithCardIds(cardIds)
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe((testState, throwable1) -> {
                                                if (throwable1 != null) {
                                                    Throwable cause1 = throwable1.getCause();
                                                    if (cause1 == null)
                                                        cause1 = throwable1;
                                                    mProvider.get(ILogger.class).e(
                                                            TAG,
                                                            mProvider.getContext().getString(R.string.error_starting_test), cause1);
                                                } else {
                                                    navigator.push(Routes.TEST);
                                                    mProvider.get(DeleteSuggestedCardCmd.class)
                                                            .executeDeleteAll();
                                                }
                                                compositeDisposable.dispose();
                                            })
                            );
                        }
                    }
                })
        );
    }

    /**
     * Internal method to handle deck selection and test start.
     */
    private void startDeckSelectTestFlow(INavigator navigator) {
        navigator.push(Routes.DECK_SELECT_PAGE, DeckSelectPage.Args.multiSelectMode(),
                (navigator1, navRoute, activity, currentView) -> {
                    DeckSelectPage.Result result = DeckSelectPage.Result.of(navRoute.getRouteResult());
                    if (result != null) {
                        Provider provider = (Provider) navigator1.getNavConfiguration().getRequiredComponent();
                        ArrayList<Deck> deckArrayList = result.getSelectedDeck();
                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                        compositeDisposable.add(
                                provider.get(TestStateModifier.class)
                                        .startTest(deckArrayList)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe((testState, throwable) -> {
                                            if (throwable != null) {
                                                Context context = provider.getContext();
                                                if (throwable.getCause() instanceof ValidationException) {
                                                    String title = context.getString(R.string.title_error);
                                                    CommonNavConfig commonNavConfig = provider.get(CommonNavConfig.class);
                                                    navigator1.push(Routes.COMMON_MESSAGE_DIALOG,
                                                            commonNavConfig.args_commonMessageDialog(title,
                                                                    throwable.getCause().getMessage()),
                                                            (navigator2, navRoute1, activity1, currentView1) ->
                                                                    startDeckSelectTestFlow(navigator2));
                                                } else {
                                                    provider.get(ILogger.class).e(
                                                            TAG,
                                                            context.getString(R.string.error_starting_test), throwable);
                                                }
                                            } else {
                                                navigator1.push(Routes.TEST);
                                            }
                                            compositeDisposable.dispose();
                                        })
                        );
                    }
                });
    }
}

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

package m.co.rh.id.a_flash_deck.app.ui.component;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.TestChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Child StatefulView component for ongoing test banner.
 * Follows AppBarSV/DeckItemSV pattern.
 */
public class OngoingTestBannerSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = OngoingTestBannerSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient TestStateModifier mTestStateModifier;
    private transient TestChangeNotifier mTestChangeNotifier;
    private transient BehaviorSubject<Optional<TestState>> mTestStateSubject;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mTestStateModifier = mSvProvider.get(TestStateModifier.class);
        mTestChangeNotifier = mSvProvider.get(TestChangeNotifier.class);
        mTestStateSubject = BehaviorSubject.create();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.component_ongoing_test, container, false);
        rootLayout.setOnClickListener(this);

        // Set up subscription to test state changes
        mRxDisposer
                .add("createView_onGoingTest",
                        mTestStateSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testStateOptional -> {
                                    if (mSvProvider == null) {
                                        return;
                                    }
                                    if (testStateOptional.isPresent()) {
                                        TestState testState = testStateOptional.get();
                                        String totalCards = (testState.getCurrentCardIndex() + 1) + " / " + testState.getTotalCards();
                                        rootLayout.setVisibility(View.VISIBLE);
                                        TextView textTotalCard = rootLayout.findViewById(R.id.text_total_cards);
                                        textTotalCard.setText(totalCards);
                                    } else {
                                        rootLayout.setVisibility(View.GONE);
                                    }
                                }));

        // Load active test
        mRxDisposer
                .add("createView_loadActiveTest",
                        mTestStateModifier.getActiveTest()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((testStateOptional, throwable) -> {
                                    if (mSvProvider == null) {
                                        return;
                                    }
                                    Context svContext = mSvProvider.getContext();
                                    if (throwable != null) {
                                        mSvProvider.get(ILogger.class)
                                                .e(TAG, svContext.getString(R.string.error_loading_test), throwable);
                                    } else {
                                        mTestStateSubject.onNext(testStateOptional);
                                    }
                                }));

        // Subscribe to test start events
        mRxDisposer
                .add("createView_onStartTest",
                        mTestChangeNotifier.getStartTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent ->
                                        mTestStateSubject.onNext(Optional.of(testEvent.getTestState())))
                );

        // Subscribe to test stop events
        mRxDisposer
                .add("createView_onStopTest",
                        mTestChangeNotifier.getStopTestEventFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testEvent -> mTestStateSubject.onNext(Optional.empty()))
                );

        // Subscribe to test state changes
        mRxDisposer
                .add("createView_onTestStateChanged",
                        mTestChangeNotifier.getTestStateChangeFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(testState -> mTestStateSubject.onNext(Optional.of(testState)))
                );

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mTestStateSubject != null) {
            mTestStateSubject.onComplete();
            mTestStateSubject = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        mNavigator.push(Routes.TEST);
    }
}

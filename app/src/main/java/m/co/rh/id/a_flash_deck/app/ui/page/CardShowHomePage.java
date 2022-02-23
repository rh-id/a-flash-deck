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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_flash_deck.app.provider.component.AppShortcutHandler;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class CardShowHomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient AppNotificationHandler mAppNotificationHandler;
    private transient AppShortcutHandler mAppShortcutHandler;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mAppNotificationHandler = mSvProvider.get(AppNotificationHandler.class);
        mAppShortcutHandler = mSvProvider.get(AppShortcutHandler.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_card_show_home, container, false);
        Button exitButton = rootLayout.findViewById(R.id.button_exit);
        exitButton.setOnClickListener(this);
        mRxDisposer.add("createView_onCardShowActionDispatch",
                mAppShortcutHandler.getCardFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(cardOptional -> cardOptional.ifPresent(card -> {
                            showCard(card);
                            mAppShortcutHandler.clearEvent();
                        })));
        mRxDisposer
                .add("createView_onTimerNotificationEvent",
                        mSvProvider.get(AppNotificationHandler.class)
                                .getTimerNotificationEventFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(timerNotificationEventOpt ->
                                        timerNotificationEventOpt.ifPresent(notificationTimerEvent ->
                                        {
                                            showCard(notificationTimerEvent.getSelectedCard());
                                            mAppNotificationHandler.clearEvent();
                                        })
                                )
                );
        return rootLayout;
    }

    private void showCard(Card card) {
        mNavigator.push(Routes.CARD_SHOW_PAGE,
                CardShowPage.Args.withCard
                        (card.clone()),
                (navigator, navRoute, activity1, currentView) ->
                        onBackPressed(currentView, activity1, navigator));
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_exit) {
            mNavigator.finishActivity();
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (navigator.isInitialRoute()) {
            navigator.pop();
        }
    }
}

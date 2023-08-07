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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.ui.component.card.CardListSV;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class CardListPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private CardListSV mCardListSV;

    public CardListPage() {
        mAppBarSV = new AppBarSV(R.menu.page_card_list);
        mCardListSV = new CardListSV();
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mCardListSV.setDeckId(args.getDeck().id);
        } else {
            mCardListSV.setDeckId(Long.MIN_VALUE);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_card_list, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_card_list));
        mAppBarSV.setMenuItemClick(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mCardListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            Deck deck = null;
            Args args = Args.of(mNavRoute);
            if (args != null) {
                deck = args.getDeck();
            }
            if (deck != null) {
                mNavigator.push(Routes.CARD_DETAIL_PAGE, CardDetailPage.Args.withDeck(deck));
            } else {
                mNavigator.push(Routes.DECK_SELECT_DIALOG,
                        (navigator, navRoute, activity, currentView) -> {
                            DeckSelectSVDialog.Result result =
                                    DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                            if (result != null &&
                                    !result.getSelectedDeck().isEmpty()) {
                                Deck selectedDeck = result.getSelectedDeck().get(0);
                                navigator.push(Routes.CARD_DETAIL_PAGE,
                                        CardDetailPage.Args.withDeck(
                                                selectedDeck
                                        ));
                            }
                        });
            }
            return true;
        }
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mCardListSV != null) {
            mCardListSV.dispose(activity);
            mCardListSV = null;
        }
        mNavigator = null;
        mNavRoute = null;
    }

    public static class Args implements Serializable {
        public static Args withDeck(Deck deck) {
            Args args = new Args();
            args.mDeck = deck;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Deck mDeck;

        public Deck getDeck() {
            return mDeck;
        }
    }
}

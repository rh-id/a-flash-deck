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

import java.io.Serializable;
import java.util.ArrayList;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.ui.component.deck.DeckListSV;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class DeckSelectSVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private DeckListSV mDeckListSV;

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        DeckListSV.ListMode listMode;
        Args args = Args.of(mNavRoute);
        if (args != null && args.mSelectMode == Args.MULTI_SELECT_MODE) {
            listMode = DeckListSV.ListMode.multiSelectMode();
        } else {
            listMode = DeckListSV.ListMode.selectMode();
        }
        mDeckListSV = new DeckListSV(listMode);
        mNavigator.injectRequired(this, mDeckListSV);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.dialog_deck_select, container, false);
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mDeckListSV.buildView(activity, rootLayout));
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_cancel) {
            getNavigator().pop();
        } else if (viewId == R.id.button_ok) {
            ArrayList<Deck> selectedDeck = mDeckListSV.getSelectedDeck();
            getNavigator().pop(Result.selectedDeck(selectedDeck));
        }
    }

    public static class Args implements Serializable {
        public static Args multiSelectMode() {
            Args args = new Args();
            args.mSelectMode = 1;
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

        private static byte SELECT_MODE = 0;
        private static byte MULTI_SELECT_MODE = 1;

        private byte mSelectMode;
    }

    /**
     * Result of this dialog
     */
    public static class Result implements Serializable {
        public static Result selectedDeck(ArrayList<Deck> selectedDeck) {
            Result result = new Result();
            result.mSelectedDeck = selectedDeck;
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private ArrayList<Deck> mSelectedDeck;

        private Result() {
        }

        public ArrayList<Deck> getSelectedDeck() {
            return mSelectedDeck;
        }
    }
}

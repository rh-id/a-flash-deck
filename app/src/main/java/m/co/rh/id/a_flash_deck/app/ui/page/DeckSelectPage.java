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

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.Serializable;
import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedDeckItemsCmd;
import m.co.rh.id.a_flash_deck.app.ui.component.deck.DeckListSV;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class DeckSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = DeckSelectPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private DeckListSV mDeckListSV;

    private transient ILogger mLogger;
    private transient CompositeDisposable mCompositeDisposable;

    public DeckSelectPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
        if (mCompositeDisposable == null) {
            mCompositeDisposable = new CompositeDisposable();
        }
        if (mDeckListSV == null) {
            DeckListSV.ListMode listMode;
            Args args = Args.of(mNavRoute);
            if (args != null && args.mSelectMode == Args.MULTI_SELECT_MODE) {
                listMode = DeckListSV.ListMode.multiSelectMode();
            } else {
                listMode = DeckListSV.ListMode.selectMode();
            }
            mDeckListSV = new DeckListSV(listMode);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_deck_select, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_select_deck));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));

        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mDeckListSV.buildView(activity, rootLayout));

        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);

        Button buttonSelectAll = rootLayout.findViewById(R.id.button_select_all);

        Args args = Args.of(mNavRoute);
        if (args != null && args.mSelectMode == Args.MULTI_SELECT_MODE) {
            buttonSelectAll.setVisibility(View.VISIBLE);
            buttonSelectAll.setOnClickListener(this);

            PagedDeckItemsCmd pagedDeckItemsCmd = mDeckListSV.getPagedDeckItemsCmd();
            if (pagedDeckItemsCmd != null) {
                mCompositeDisposable.add(pagedDeckItemsCmd.getSelectedDeckIdsFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(selectedDeckIds -> updateSelectionTitleAndButton(activity, buttonSelectAll, pagedDeckItemsCmd))
                );
                mCompositeDisposable.add(pagedDeckItemsCmd.getTotalCountFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(total -> updateSelectionTitleAndButton(activity, buttonSelectAll, pagedDeckItemsCmd))
                );
            }
        }

        return rootLayout;
    }

    private void updateSelectionTitleAndButton(Activity activity, Button buttonSelectAll, PagedDeckItemsCmd pagedDeckItemsCmd) {
        int selectedCount = pagedDeckItemsCmd.getSelectedCount();
        int totalCount = pagedDeckItemsCmd.getTotalCount();
        mAppBarSV.setTitle(activity.getString(R.string.title_select_deck_count, selectedCount, totalCount));
        if (selectedCount >= totalCount && totalCount > 0) {
            buttonSelectAll.setText(R.string.deselect_all);
        } else {
            buttonSelectAll.setText(R.string.select_all);
        }
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_cancel) {
            mNavigator.pop();
        } else if (viewId == R.id.button_ok) {
            mCompositeDisposable.add(mDeckListSV.getSelectedDeck()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(selectedDeck -> {
                        if (!selectedDeck.isEmpty()) {
                            mNavigator.pop(Result.selectedDeck(selectedDeck));
                        } else {
                            mLogger.i(TAG, view.getContext().getString(R.string.error_please_select_deck));
                        }
                    }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable))
            );
        } else if (viewId == R.id.button_select_all) {
            PagedDeckItemsCmd pagedDeckItemsCmd = mDeckListSV.getPagedDeckItemsCmd();
            if (pagedDeckItemsCmd != null) {
                int selectedCount = pagedDeckItemsCmd.getSelectedCount();
                int totalCount = pagedDeckItemsCmd.getTotalCount();
                if (selectedCount > 0 && selectedCount >= totalCount && totalCount > 0) {
                    mDeckListSV.unselectAll();
                } else {
                    mDeckListSV.selectAll();
                }
            }
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mDeckListSV != null) {
            mDeckListSV.dispose(activity);
            mDeckListSV = null;
        }
        if (mCompositeDisposable != null) {
            mCompositeDisposable.dispose();
            mCompositeDisposable = null;
        }
        mNavigator = null;
        mNavRoute = null;
    }

    public static class Args implements Serializable {
        public static Args multiSelectMode() {
            Args args = new Args();
            args.mSelectMode = MULTI_SELECT_MODE;
            return args;
        }

        public static Args selectMode() {
            Args args = new Args();
            args.mSelectMode = SELECT_MODE;
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

        private static final byte SELECT_MODE = 0;
        private static final byte MULTI_SELECT_MODE = 1;

        private byte mSelectMode;
    }

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

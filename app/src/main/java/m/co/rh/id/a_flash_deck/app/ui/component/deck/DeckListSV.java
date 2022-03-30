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

package m.co.rh.id.a_flash_deck.app.ui.component.deck;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedDeckItemsCmd;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class DeckListSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = DeckListSV.class.getName();

    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient PagedDeckItemsCmd mPagedDeckItemsCmd;

    private transient PublishSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient DeckRecyclerViewAdapter mDeckRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    private ListMode mListMode;

    public DeckListSV() {
    }

    public DeckListSV(ListMode listMode) {
        mListMode = listMode;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
        mPagedDeckItemsCmd = mSvProvider.get(PagedDeckItemsCmd.class);
        mPagedDeckItemsCmd.refresh();
        if (mSearchStringSubject == null) {
            mSearchStringSubject = PublishSubject.create();
        }
        if (mSearchTextWatcher == null) {
            mSearchTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mSearchStringSubject.onNext(editable.toString());
                }
            };
        }
        DeckItemSV.ListMode listMode = null;
        if (mListMode != null) {
            if (mListMode.mSelectMode == ListMode.SELECT_MODE) {
                listMode = DeckItemSV.ListMode.selectMode();
            } else if (mListMode.mSelectMode == ListMode.MULTI_SELECT_MODE) {
                listMode = DeckItemSV.ListMode.multiSelectMode();
            }
        }
        mDeckRecyclerViewAdapter = new DeckRecyclerViewAdapter(
                mPagedDeckItemsCmd,
                mNavigator, this,
                listMode);
        mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedDeckItemsCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_deck, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mDeckRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnScrollListener);
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mPagedDeckItemsCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedDeckItemsCmd.getDecksFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mDeckRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onItemAdded",
                        mDeckChangeNotifier.getAddedDeckFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mDeckRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer
                .add("createView_onItemUpdated",
                        mDeckChangeNotifier.getUpdatedDeckFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mDeckRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mPagedDeckItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onItemDeleted",
                        mDeckChangeNotifier
                                .getDeletedDeckFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mDeckRecyclerViewAdapter::notifyItemDeleted));

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mSearchStringSubject != null) {
            mSearchStringSubject.onComplete();
            mSearchStringSubject = null;
        }
        mSearchTextWatcher = null;
        if (mDeckRecyclerViewAdapter != null) {
            mDeckRecyclerViewAdapter.dispose(activity);
            mDeckRecyclerViewAdapter = null;
        }
        mOnScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedDeckItemsCmd.refresh();
    }

    public ArrayList<Deck> getSelectedDeck() {
        return mPagedDeckItemsCmd.getSelectedDecks();
    }

    public static class ListMode implements Serializable {
        public static ListMode selectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = SELECT_MODE;
            return listMode;
        }

        public static ListMode multiSelectMode() {
            ListMode listMode = new ListMode();
            listMode.mSelectMode = MULTI_SELECT_MODE;
            return listMode;
        }

        /**
         * One selection only
         */
        private static final byte SELECT_MODE = 0;
        private static final byte MULTI_SELECT_MODE = 1;

        private byte mSelectMode;

        private ListMode() {
        }

        public int getSelectMode() {
            return mSelectMode;
        }
    }
}

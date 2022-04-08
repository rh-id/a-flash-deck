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

package m.co.rh.id.a_flash_deck.app.ui.component.card;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedCardItemsCmd;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.rx.SerialBehaviorSubject;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class CardListSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = CardListSV.class.getName();

    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient PagedCardItemsCmd mPagedCardItemsCmd;

    private transient PublishSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient CardRecyclerViewAdapter mCardRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    private SerialBehaviorSubject<Long> mDeckId;

    public CardListSV() {
        mDeckId = new SerialBehaviorSubject<>();
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
        mPagedCardItemsCmd = mSvProvider.get(PagedCardItemsCmd.class);
        mPagedCardItemsCmd.refresh();

        mSearchStringSubject = PublishSubject.create();
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
        mOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedCardItemsCmd.loadNextPage();
                }
            }
        };
        mCardRecyclerViewAdapter = new CardRecyclerViewAdapter(
                mPagedCardItemsCmd,
                mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_card, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mCardRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mOnScrollListener);
        mRxDisposer.add("createView_onDeckIdChanged",
                mDeckId.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aLong -> {
                            mPagedCardItemsCmd.setDeckId(aLong);
                            mPagedCardItemsCmd.refresh();
                        }));
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mPagedCardItemsCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedCardItemsCmd.getCardsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mCardRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onItemAdded",
                        mDeckChangeNotifier.getAddedCardFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(card -> {
                                    mCardRecyclerViewAdapter.notifyItemAdded(card);
                                    recyclerView.scrollToPosition(0);
                                }));
        mRxDisposer
                .add("createView_onItemUpdated",
                        mDeckChangeNotifier.getUpdatedCardFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mCardRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mPagedCardItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onItemDeleted",
                        mDeckChangeNotifier
                                .getDeletedCardFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mCardRecyclerViewAdapter::notifyItemDeleted));

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
        if (mCardRecyclerViewAdapter != null) {
            mCardRecyclerViewAdapter.dispose(activity);
            mCardRecyclerViewAdapter = null;
        }
        mOnScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedCardItemsCmd.refresh();
    }

    public void setDeckId(long deckId) {
        mDeckId.onNext(deckId);
    }
}

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

package m.co.rh.id.a_flash_deck.timer.ui.component.timer;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.provider.command.PagedNotificationTimerItemsCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimerListSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener {

    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient NotificationTimerChangeNotifier mNotificationTimerChangeNotifier;
    private transient PagedNotificationTimerItemsCmd mPagedNotificationTimerItemsCmd;
    private SerialBehaviorSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient NotificationTimerRecyclerViewAdapter mNotificationTimerRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    public NotificationTimerListSV() {
        mSearchStringSubject = new SerialBehaviorSubject<>();
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
        mNotificationTimerChangeNotifier = mSvProvider.get(NotificationTimerChangeNotifier.class);
        mPagedNotificationTimerItemsCmd = mSvProvider.get(PagedNotificationTimerItemsCmd.class);
        mPagedNotificationTimerItemsCmd.refresh();
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
                    mPagedNotificationTimerItemsCmd.loadNextPage();
                }
            }
        };
        mNotificationTimerRecyclerViewAdapter = new NotificationTimerRecyclerViewAdapter(
                mPagedNotificationTimerItemsCmd,
                mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_timer_notification, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mNotificationTimerRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnScrollListener);
        mRxDisposer
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .getSubject()
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mExecutorService))
                                .subscribe(searchString -> mPagedNotificationTimerItemsCmd
                                        .search(searchString))
                );
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedNotificationTimerItemsCmd.getNotificationTimersFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mNotificationTimerRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer
                .add("createView_onItemAdded",
                        mNotificationTimerChangeNotifier.getAddedTimerNotificationFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(notificationTimer -> {
                                    mNotificationTimerRecyclerViewAdapter.notifyItemAdded(notificationTimer);
                                    recyclerView.scrollToPosition(0);
                                }));
        mRxDisposer
                .add("createView_onItemUpdated",
                        mNotificationTimerChangeNotifier.getUpdatedTimerNotificationFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mNotificationTimerRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer
                .add("createView_onLoadingChanged",
                        mPagedNotificationTimerItemsCmd.getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mRxDisposer
                .add("createView_onItemDeleted",
                        mNotificationTimerChangeNotifier
                                .getDeletedTimerNotificationFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mNotificationTimerRecyclerViewAdapter::notifyItemDeleted));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mSearchTextWatcher = null;
        if (mNotificationTimerRecyclerViewAdapter != null) {
            mNotificationTimerRecyclerViewAdapter.dispose(activity);
            mNotificationTimerRecyclerViewAdapter = null;
        }
        mOnScrollListener = null;
    }

    @Override
    public void onRefresh() {
        mPagedNotificationTimerItemsCmd.refresh();
    }
}

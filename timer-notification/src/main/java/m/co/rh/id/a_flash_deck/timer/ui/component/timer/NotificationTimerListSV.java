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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.NotificationTimerChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.provider.command.PagedNotificationTimerItemsCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimerListSV extends StatefulView<Activity> implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = NotificationTimerListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient PublishSubject<String> mSearchStringSubject;
    private transient TextWatcher mSearchTextWatcher;
    private transient NotificationTimerRecyclerViewAdapter mNotificationTimerRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mOnScrollListener;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mSvProvider.get(PagedNotificationTimerItemsCmd.class).refresh();
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
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.list_timer_notification, container, false);
        EditText editTextSearch = rootLayout.findViewById(R.id.edit_text_search);
        editTextSearch.addTextChangedListener(mSearchTextWatcher);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        mNotificationTimerRecyclerViewAdapter = new NotificationTimerRecyclerViewAdapter(
                mSvProvider.get(PagedNotificationTimerItemsCmd.class),
                mNavigator, this);
        if (mOnScrollListener == null) {
            mOnScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mSvProvider.get(PagedNotificationTimerItemsCmd.class).loadNextPage();
                    }
                }
            };
        }
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mNotificationTimerRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        recyclerView.addOnScrollListener(mOnScrollListener);
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemSearched",
                        mSearchStringSubject
                                .debounce(700, TimeUnit.MILLISECONDS)
                                .observeOn(Schedulers.from(mSvProvider.get(ExecutorService.class)))
                                .subscribe(searchString -> mSvProvider.get(PagedNotificationTimerItemsCmd.class)
                                        .search(searchString))
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemRefreshed",
                        mSvProvider.get(PagedNotificationTimerItemsCmd.class).getNotificationTimersFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(decks -> mNotificationTimerRecyclerViewAdapter.notifyItemRefreshed())
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemAdded",
                        mSvProvider.get(NotificationTimerChangeNotifier.class).getAddedTimerNotificationFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mNotificationTimerRecyclerViewAdapter::notifyItemAdded));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemUpdated",
                        mSvProvider.get(NotificationTimerChangeNotifier.class).getUpdatedTimerNotificationFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(mNotificationTimerRecyclerViewAdapter::notifyItemUpdated));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onLoadingChanged",
                        mSvProvider.get(PagedNotificationTimerItemsCmd.class).getLoadingFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(swipeRefreshLayout::setRefreshing)
                );
        mSvProvider.get(RxDisposer.class)
                .add("createView_onItemDeleted",
                        mSvProvider.get(NotificationTimerChangeNotifier.class)
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
        mProvider = null;
        if (mSearchStringSubject != null) {
            mSearchStringSubject.onComplete();
            mSearchStringSubject = null;
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
        mSvProvider.get(PagedNotificationTimerItemsCmd.class).refresh();
    }
}

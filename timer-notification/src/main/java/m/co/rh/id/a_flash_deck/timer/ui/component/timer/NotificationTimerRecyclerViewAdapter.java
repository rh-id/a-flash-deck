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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.provider.command.PagedNotificationTimerItemsCmd;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

public class NotificationTimerRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_DECK_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedNotificationTimerItemsCmd mPagedNotificationTimerItemsCmd;
    private INavigator mNavigator;
    private StatefulView mParentStatefulView;
    private List<StatefulView> mCreatedSvList;

    public NotificationTimerRecyclerViewAdapter(PagedNotificationTimerItemsCmd pagedNotificationTimerItemsCmd,
                                                INavigator navigator, StatefulView parentStatefulView) {
        mPagedNotificationTimerItemsCmd = pagedNotificationTimerItemsCmd;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = UiUtils.getActivity(parent).getLayoutInflater().inflate(R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            Activity activity = UiUtils.getActivity(parent);
            NotificationTimerItemSV notificationTimerItemSV = new NotificationTimerItemSV();
            mNavigator.injectRequired(mParentStatefulView, notificationTimerItemSV);
            View view = notificationTimerItemSV.buildView(activity, parent);
            mCreatedSvList.add(notificationTimerItemSV);
            return new ItemViewHolder(view, notificationTimerItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<NotificationTimer> timerArrayList = mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems();
            NotificationTimer item = timerArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            NotificationTimer itemHolder = itemViewHolder.getItem();
            if (itemHolder == null || !itemHolder.equals(item)) {
                itemViewHolder.setItem(item);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_DECK_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedNotificationTimerItemsCmd == null) {
            return true;
        }
        return mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems().size() == 0;
    }

    public void notifyItemAdded(NotificationTimer notificationTimer) {
        int existingIdx = findDeck(notificationTimer);
        if (existingIdx == -1) {
            mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems()
                    .add(0, notificationTimer);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(NotificationTimer notificationTimer) {
        int existingIdx = findDeck(notificationTimer);
        if (existingIdx != -1) {
            ArrayList<NotificationTimer> timers = mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems();
            timers.remove(existingIdx);
            timers.add(existingIdx, notificationTimer);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(NotificationTimer notificationTimer) {
        int removedIdx = findDeck(notificationTimer);
        if (removedIdx != -1) {
            mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems()
                    .remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
    }

    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (StatefulView sv : mCreatedSvList) {
                sv.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    private int findDeck(NotificationTimer notificationTimer) {
        ArrayList<NotificationTimer> timers =
                mPagedNotificationTimerItemsCmd.getAllTimerNotificationItems();
        int size = timers.size();
        int resultIdx = -1;
        for (int i = 0; i < size; i++) {
            if (notificationTimer.id.equals(timers.get(i).id)) {
                resultIdx = i;
                break;
            }
        }
        return resultIdx;
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private NotificationTimerItemSV mNotificationTimerItemSV;

        public ItemViewHolder(@NonNull View itemView, NotificationTimerItemSV notificationTimerItemSV) {
            super(itemView);
            mNotificationTimerItemSV = notificationTimerItemSV;
        }

        public void setItem(NotificationTimer notificationTimer) {
            mNotificationTimerItemSV.setTimerNotification(notificationTimer);
        }

        public NotificationTimer getItem() {
            return mNotificationTimerItemSV.getTimerNotification();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

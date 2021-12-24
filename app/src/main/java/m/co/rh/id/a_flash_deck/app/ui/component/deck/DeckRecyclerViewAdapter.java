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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedDeckItemsCmd;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

public class DeckRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_DECK_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedDeckItemsCmd mPagedDeckItemsCmd;
    private INavigator mNavigator;
    private StatefulView mParentStatefulView;
    private DeckItemSV.ListMode mDeckItemListMode;
    private List<DeckItemSV> mCreatedSvList;

    public DeckRecyclerViewAdapter(PagedDeckItemsCmd pagedDeckItemsCmd,
                                   INavigator navigator, StatefulView parentStatefulView,
                                   DeckItemSV.ListMode listMode) {
        mPagedDeckItemsCmd = pagedDeckItemsCmd;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mDeckItemListMode = listMode;
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
            DeckItemSV deckItemSV = new DeckItemSV(mDeckItemListMode);
            mNavigator.injectRequired(mParentStatefulView, deckItemSV);
            View view = deckItemSV.buildView(activity, parent);
            mCreatedSvList.add(deckItemSV);
            return new ItemViewHolder(view, deckItemSV, mPagedDeckItemsCmd, mCreatedSvList);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<Deck> itemArrayList = mPagedDeckItemsCmd.getAllDeckItems();
            Deck item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Deck itemHolder = itemViewHolder.getItem();
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
        return mPagedDeckItemsCmd.getAllDeckItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_DECK_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedDeckItemsCmd == null) {
            return true;
        }
        return mPagedDeckItemsCmd.getAllDeckItems().size() == 0;
    }

    public void notifyItemAdded(Deck deck) {
        int existingIdx = findDeck(deck);
        if (existingIdx == -1) {
            mPagedDeckItemsCmd.getAllDeckItems()
                    .add(0, deck);
            notifyItemInserted(0);
        }
    }

    public void notifyItemUpdated(Deck deck) {
        int existingIdx = findDeck(deck);
        if (existingIdx != -1) {
            ArrayList<Deck> decks = mPagedDeckItemsCmd.getAllDeckItems();
            decks.remove(existingIdx);
            decks.add(existingIdx, deck);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(Deck deck) {
        int removedIdx = findDeck(deck);
        if (removedIdx != -1) {
            mPagedDeckItemsCmd.getAllDeckItems()
                    .remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
    }

    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (DeckItemSV deckItemSV : mCreatedSvList) {
                deckItemSV.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    private int findDeck(Deck deck) {
        ArrayList<Deck> decks =
                mPagedDeckItemsCmd.getAllDeckItems();
        int size = decks.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (deck.id.equals(decks.get(i).id)) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder implements DeckItemSV.OnItemSelectListener {
        private DeckItemSV mDeckItemSV;
        private PagedDeckItemsCmd mPagedDeckItemsCmd;
        private List<DeckItemSV> mCreatedDeckItemSvList;

        public ItemViewHolder(@NonNull View itemView, DeckItemSV deckItemSV, PagedDeckItemsCmd pagedDeckItemsCmd, List<DeckItemSV> createdDeckItemSvList) {
            super(itemView);
            mDeckItemSV = deckItemSV;
            mPagedDeckItemsCmd = pagedDeckItemsCmd;
            mCreatedDeckItemSvList = createdDeckItemSvList;
            mDeckItemSV.setOnSelectListener(this);
        }

        public void setItem(Deck deck) {
            mDeckItemSV.setDeck(deck);
            if (mPagedDeckItemsCmd.isSelected(deck)) {
                select();
            } else {
                unSelect();
            }
        }

        public Deck getItem() {
            return mDeckItemSV.getDeck();
        }

        public void select() {
            mDeckItemSV.select();
        }

        public void unSelect() {
            mDeckItemSV.unSelect();
        }

        @Override
        public void onItemSelect(Deck deck, boolean selected) {
            boolean shouldClearSelection = false;
            if (mDeckItemSV.getListMode() != null) {
                shouldClearSelection = mDeckItemSV.getListMode().shouldClearOtherSelection();
            }
            if (selected) {
                mPagedDeckItemsCmd.selectDeck(
                        deck,
                        shouldClearSelection);
            } else {
                mPagedDeckItemsCmd.unSelectDeck(deck);
            }
            if (shouldClearSelection && !mCreatedDeckItemSvList.isEmpty()) {
                for (DeckItemSV deckItemSV : mCreatedDeckItemSvList) {
                    Deck deck1 = deckItemSV.getDeck();
                    if (deck1 != null && !deck1.id.equals(deck.id)) {
                        deckItemSV.unSelect();
                    }
                }
            }
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

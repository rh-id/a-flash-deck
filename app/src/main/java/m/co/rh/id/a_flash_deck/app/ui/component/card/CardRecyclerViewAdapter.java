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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedCardItemsCmd;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

public class CardRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_DECK_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedCardItemsCmd mPagedCardItemsCmd;
    private INavigator mNavigator;
    private StatefulView mParentStatefulView;
    private List<CardItemSV> mCreatedSvList;

    public CardRecyclerViewAdapter(PagedCardItemsCmd pagedCardItemsCmd,
                                   INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedCardItemsCmd = pagedCardItemsCmd;
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
            CardItemSV cardItemSV = new CardItemSV();
            mNavigator.injectRequired(mParentStatefulView, cardItemSV);
            View view = cardItemSV.buildView(activity, parent);
            mCreatedSvList.add(cardItemSV);
            return new ItemViewHolder(view, cardItemSV, mPagedCardItemsCmd);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<Card> deckArrayList = mPagedCardItemsCmd.getAllCardItems();
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(deckArrayList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedCardItemsCmd.getAllCardItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_DECK_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedCardItemsCmd == null) {
            return true;
        }
        return mPagedCardItemsCmd.getAllCardItems().size() == 0;
    }

    public void notifyItemAdded(Card card) {
        int existingIdx = findDeck(card);
        if (existingIdx == -1) {
            Long deckId = mPagedCardItemsCmd.getDeckId();
            if (deckId == null || deckId.equals(card.deckId)) {
                mPagedCardItemsCmd.getAllCardItems()
                        .add(0, card);
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(Card card) {
        int existingIdx = findDeck(card);
        if (existingIdx != -1) {
            ArrayList<Card> cards = mPagedCardItemsCmd.getAllCardItems();
            cards.remove(existingIdx);
            cards.add(existingIdx, card);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(Card card) {
        int removedIdx = findDeck(card);
        if (removedIdx != -1) {
            mPagedCardItemsCmd.getAllCardItems()
                    .remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
    }

    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (CardItemSV cardItemSV : mCreatedSvList) {
                cardItemSV.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    private int findDeck(Card card) {
        ArrayList<Card> cards =
                mPagedCardItemsCmd.getAllCardItems();
        int size = cards.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (card.id.equals(cards.get(i).id)) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private CardItemSV mCardItemSV;
        private PagedCardItemsCmd mPagedCardItemsCmd;

        public ItemViewHolder(@NonNull View itemView, CardItemSV cardItemSV, PagedCardItemsCmd pagedCardItemsCmd) {
            super(itemView);
            mCardItemSV = cardItemSV;
            mPagedCardItemsCmd = pagedCardItemsCmd;
        }

        public void setItem(Card card) {
            mCardItemSV.setCard(card);
        }

        public Card getItem() {
            return mCardItemSV.getCard();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

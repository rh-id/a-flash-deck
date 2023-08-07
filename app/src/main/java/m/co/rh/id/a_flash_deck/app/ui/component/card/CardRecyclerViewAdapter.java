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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

public class CardRecyclerViewAdapter extends ListAdapter<Card, RecyclerView.ViewHolder> {

    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<CardItemSV> mCreatedSvList;

    public CardRecyclerViewAdapter(
            INavigator navigator, StatefulView parentStatefulView
    ) {
        super(new ItemCallback());
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = UiUtils.getActivity(parent);
        CardItemSV cardItemSV = new CardItemSV();
        mNavigator.injectRequired(mParentStatefulView, cardItemSV);
        View view = cardItemSV.buildView(activity, parent);
        mCreatedSvList.add(cardItemSV);
        return new ItemViewHolder(view, cardItemSV);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            Card item = getItem(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Card itemFromHolder = itemViewHolder.getItem();
            if (itemFromHolder == null || !itemFromHolder.equals(item)) {
                itemViewHolder.setItem(item);
            }
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

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final CardItemSV mCardItemSV;

        public ItemViewHolder(@NonNull View itemView, CardItemSV cardItemSV) {
            super(itemView);
            mCardItemSV = cardItemSV;
        }

        public void setItem(Card card) {
            mCardItemSV.setCard(card);
        }

        public Card getItem() {
            return mCardItemSV.getCard();
        }
    }

    protected static class ItemCallback extends DiffUtil.ItemCallback<Card> {

        @Override
        public boolean areItemsTheSame(@NonNull Card oldItem, @NonNull Card newItem) {
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Card oldItem, @NonNull Card newItem) {
            return oldItem.equals(newItem);
        }
    }
}

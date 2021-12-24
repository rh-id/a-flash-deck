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

package m.co.rh.id.a_flash_deck.base.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

public class DeckModel implements Serializable {
    private static final long serialVersionUID = -8121772616636312403L;
    private Deck mDeck;
    private ArrayList<Card> mCardList;

    public DeckModel() {
        mCardList = new ArrayList<>();
    }

    public DeckModel(Deck deck, List<Card> cardList) {
        mDeck = deck;
        mCardList = new ArrayList<>(cardList);
    }

    public Deck getDeck() {
        return mDeck;
    }

    public ArrayList<Card> getCardList() {
        return mCardList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeckModel deckModel = (DeckModel) o;
        return Objects.equals(mDeck, deckModel.mDeck)
                && Objects.equals(mCardList, deckModel.mCardList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDeck, mCardList);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("serialVersionUID", serialVersionUID);
        jsonObject.put("deck", mDeck.toJson());
        if (!mCardList.isEmpty()) {
            JSONArray jsonArray = new JSONArray();
            for (Card card : mCardList) {
                jsonArray.put(card.toJson());
            }
            jsonObject.put("cardList", jsonArray);
        }
        return jsonObject;
    }

    public void fromJson(JSONObject jsonObject) throws JSONException {
        JSONObject deckJsonObject = jsonObject.getJSONObject("deck");
        mDeck = new Deck();
        mDeck.fromJson(deckJsonObject);
        JSONArray cardListJson = jsonObject.getJSONArray("cardList");
        if (cardListJson.length() > 0) {
            int size = cardListJson.length();
            for (int i = 0; i < size; i++) {
                JSONObject cardJsonObj = cardListJson.getJSONObject(i);
                Card card = new Card();
                card.fromJson(cardJsonObj);
                mCardList.add(card);
            }
        }
    }
}

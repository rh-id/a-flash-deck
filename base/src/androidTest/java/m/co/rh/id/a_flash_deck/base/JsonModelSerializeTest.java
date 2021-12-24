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

package m.co.rh.id.a_flash_deck.base;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;

public class JsonModelSerializeTest {
    @Test
    public void serialize_deserialize_deck() throws JSONException {
        Date date = new Date();

        Deck deck = new Deck();
        deck.id = 1L;
        deck.name = "test deck";
        deck.createdDateTime = date;
        deck.updatedDateTime = date;

        JSONObject jsonObject = deck.toJson();

        Deck deserializedDeck = new Deck();
        deserializedDeck.fromJson(jsonObject);

        assertEquals(deck, deserializedDeck);
    }

    @Test
    public void serialize_deserialize_card() throws JSONException {
        Card card = new Card();
        card.id = 1L;
        card.deckId = 2L;
        card.ordinal = 1;
        card.question = "this is question";
        card.answer = "this is answer";

        JSONObject jsonObject = card.toJson();

        Card deserializedCard = new Card();
        deserializedCard.fromJson(jsonObject);

        assertEquals(card, deserializedCard);
    }

    @Test
    public void serialize_deserialize_deckModel() throws JSONException {
        Date date = new Date();

        Deck deck = new Deck();
        deck.id = 1L;
        deck.name = "test deck";
        deck.createdDateTime = date;
        deck.updatedDateTime = date;

        Card card = new Card();
        card.id = 1L;
        card.deckId = 1L;
        card.ordinal = 1;
        card.question = "this is question";
        card.answer = "this is answer";

        Card card2 = new Card();
        card2.id = 2L;
        card2.deckId = 1L;
        card2.ordinal = 2;
        card2.question = "this is question 2";
        card2.answer = "this is answer 2";

        DeckModel deckModel = new DeckModel(deck, Arrays.asList(card, card2));

        JSONObject jsonObject = deckModel.toJson();
        DeckModel deserializedDeckModel = new DeckModel();
        deserializedDeckModel.fromJson(jsonObject);

        assertEquals(deckModel, deserializedDeckModel);
    }
}

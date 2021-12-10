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

package m.co.rh.id.a_flash_deck.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Objects;


@Entity(tableName = "card")
public class Card implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Deck ID that refers to Deck.id
     */
    @ColumnInfo(name = "deck_id")
    public Long deckId;

    /**
     * Ordinal/order start from 0
     */
    @ColumnInfo(name = "ordinal")
    public int ordinal;

    /**
     * Question part of card, usually at the top or front side card
     */
    @ColumnInfo(name = "question")
    public String question;

    /**
     * Answer part of card, usually at the bottom or back side of card
     */
    @ColumnInfo(name = "answer")
    public String answer;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return ordinal == card.ordinal && Objects.equals(id, card.id)
                && Objects.equals(deckId, card.deckId)
                && Objects.equals(question, card.question)
                && Objects.equals(answer, card.answer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, deckId, ordinal, question, answer);
    }

    @Override
    public Card clone() {
        try {
            return (Card) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}

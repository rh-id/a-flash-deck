/*
 *     Copyright (C) 2021-present Ruby Hartono
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

import androidx.room.ColumnInfo;

/**
 * Room query projection: the latest review timestamp of a single card.
 * Used as boundary data for the true-retention computation, which needs
 * the previous review time of a card even when that review lies before
 * the statistics window.
 */
public class CardLastReview {

    /**
     * Card ID that refers to Card.id; boxed because the review_log card_id
     * column is nullable (a null row cannot be attributed and is skipped
     * by consumers)
     */
    @ColumnInfo(name = "cardId")
    public Long cardId;

    /**
     * Epoch ms timestamp of the card's latest review covered by the query
     */
    @ColumnInfo(name = "lastDateTime")
    public long lastDateTime;
}

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

package m.co.rh.id.a_flash_deck.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_flash_deck.base.room.converter.Converter;

/**
 * Review history entry, one row per graded review.
 * Unlike CardReviewState (which only keeps the latest state), this log is
 * append-only and powers the statistics page (daily counts, streak,
 * retention).
 */
@Entity(tableName = "review_log",
        indices = {@Index("card_id"), @Index("created_date_time")})
public class ReviewLog implements Serializable {

    /**
     * Auto-generated id of the review log entry
     */
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Card ID that refers to Card.id
     */
    @ColumnInfo(name = "card_id")
    public Long cardId;

    /**
     * Deck ID that refers to Deck.id at review time
     */
    @ColumnInfo(name = "deck_id")
    public Long deckId;

    /**
     * Applied grade (ReviewScheduler.GRADE_AGAIN .. ReviewScheduler.GRADE_EASY)
     */
    @ColumnInfo(name = "grade")
    public int grade;

    /**
     * Date time of the review
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;
}

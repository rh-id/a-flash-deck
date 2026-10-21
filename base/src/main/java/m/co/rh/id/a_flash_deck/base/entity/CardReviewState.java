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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_flash_deck.base.room.converter.Converter;

/**
 * Spaced-repetition review state of a card, one row per card.
 * Absent row means the card is new/never studied.
 * <p>
 * The row is dual-written: both schedulers are advanced on every grade
 * (see StudyRepository.applyGrade) and both are deterministic functions of
 * the grade and time history, so the stored states stay correct across
 * scheduler-mode switches without any re-seeding. Column ownership:
 * <ul>
 *     <li>SM-2-lite (ReviewScheduler) owns interval_days and ease_factor;
 *     they are written on every grade even while FSRS is the active mode</li>
 *     <li>FSRS-6 (FsrsScheduler) owns stability and difficulty;
 *     0.0 is the uninitialized/new-card sentinel</li>
 *     <li>repetitions and lapses are shared and keep SM-2 semantics
 *     (SM-2 resets repetitions to 0 on AGAIN); FsrsScheduler never reads
 *     or writes them</li>
 *     <li>last_review_date_time and suspended are shared;
 *     due_date_time is written by the active scheduler only</li>
 * </ul>
 */
@Entity(tableName = "card_review_state")
public class CardReviewState implements Serializable {

    /**
     * Card ID that refers to Card.id
     */
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    public Long cardId;

    /**
     * Next due date time of the card review
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "due_date_time")
    public Date dueDateTime;

    /**
     * Current review interval in days
     */
    @ColumnInfo(name = "interval_days")
    public double intervalDays;

    /**
     * SM-2 ease factor
     */
    @ColumnInfo(name = "ease_factor")
    public double easeFactor;

    /**
     * Number of consecutive successful reviews
     */
    @ColumnInfo(name = "repetitions")
    public int repetitions;

    /**
     * Number of times the card was failed
     */
    @ColumnInfo(name = "lapses")
    public int lapses;

    /**
     * Last review date time, null if the card is new/never studied
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "last_review_date_time")
    public Date lastReviewDateTime;

    /**
     * Anki-style suspend flag: when true the card is excluded from due-card
     * selection and from the study due count, but remains browsable, editable
     * and exportable.
     * Note: suspending a never-studied card lazily creates its row (with
     * dueDateTime still null until the first grade).
     */
    @ColumnInfo(name = "suspended")
    public boolean suspended = false;

    /**
     * FSRS-6 memory stability in days (FsrsScheduler-owned); the estimated
     * number of days until recall probability drops to 90%.
     * 0.0 is the sentinel for not yet initialized (new card, or card with
     * only SM-2-lite history that is seeded from interval_days on its next
     * FSRS grade)
     */
    @ColumnInfo(name = "stability")
    public double stability;

    /**
     * FSRS-6 memory difficulty (FsrsScheduler-owned), clamped to
     * [FsrsParams.DIFFICULTY_MIN, FsrsParams.DIFFICULTY_MAX].
     * 0.0 is the sentinel for not yet initialized (see stability)
     */
    @ColumnInfo(name = "difficulty")
    public double difficulty;
}

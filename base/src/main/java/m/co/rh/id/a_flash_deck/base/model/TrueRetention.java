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

import java.io.Serializable;

/**
 * "True retention" of the review window: the share of reviews answered
 * correctly, bucketed by the number of whole days between the review and
 * the previous review of the same card (a review a long time after the
 * previous one SHOULD be harder to recall, so each bucket shows how well
 * the material was retained for that gap). Reviews whose card has no
 * previous review (first-ever reviews) are counted in
 * {@link #newCardCount} and excluded from the buckets, because they
 * measure nothing about retention.
 *
 * Bucket indexes are ordered shortest gap first:
 * 0 = less than 1 day, 1 = 1 to 7 days, 2 = 8 to 30 days,
 * 3 = more than 30 days
 */
public class TrueRetention implements Serializable {

    /**
     * Count of reviews graded not-Again per bucket (correct recalls),
     * indexed 0..3 as documented on the class
     */
    public int[] bucketCorrectCounts;

    /**
     * Total count of reviews per bucket, correct and incorrect together
     * (bucket sample size), indexed 0..3 as documented on the class
     */
    public int[] bucketTotalCounts;

    /**
     * Count of first-ever reviews seen in the window (the card had no
     * earlier review); informative only — shown as its own "New cards"
     * row on the statistics page instead of a percentage, because there
     * is no previous review to measure a retention gap against
     */
    public int newCardCount;
}

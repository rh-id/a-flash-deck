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

/**
 * Selection of the active spaced-repetition scheduler, stored as an int
 * shared preference.
 */
public final class SchedulerMode {

    /**
     * Classic SM-2-lite scheduler (ReviewScheduler), the default
     */
    public static final int SM2 = 0;

    /**
     * FSRS-6 scheduler (FsrsScheduler)
     */
    public static final int FSRS = 1;

    private SchedulerMode() {
    }
}

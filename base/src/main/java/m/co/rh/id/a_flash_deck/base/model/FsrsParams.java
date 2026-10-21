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
 * FSRS-6 (Free Spaced Repetition Scheduler) default parameters and bounds,
 * ported from py-fsrs v6.0.1 (fsrs/fsrs.py, MIT license).
 * Used by {@link FsrsScheduler} and the scheduler settings.
 */
public final class FsrsParams {

    /**
     * Default FSRS-6 optimizer weights (w0..w20), the shipped
     * default parameter set of py-fsrs v6.0.1
     */
    public static final double[] DEFAULT_WEIGHTS = {
            0.2172, 1.1771, 3.2602, 16.1507, 7.0114, 0.57, 2.0966,
            0.0069, 1.5261, 0.112, 1.0178, 1.849, 0.1133, 0.3127,
            2.2934, 0.2191, 3.0004, 0.7536, 0.3332, 0.1437, 0.2
    };

    /**
     * Lower bound of the FSRS memory stability in days
     */
    public static final double STABILITY_MIN = 0.001;

    /**
     * Minimum FSRS memory difficulty
     */
    public static final double DIFFICULTY_MIN = 1.0;

    /**
     * Maximum FSRS memory difficulty
     */
    public static final double DIFFICULTY_MAX = 10.0;

    /**
     * Maximum FSRS interval in days (the SM-2-lite cap is much lower, see
     * {@link ReviewScheduler#INTERVAL_CAP_DAYS})
     */
    public static final int MAX_INTERVAL_DAYS = 36500;

    /**
     * Minimum stability in days used when seeding FSRS state from an
     * SM-2-lite history with a zero (or smaller) interval
     */
    public static final double SEED_STABILITY_FLOOR = 0.5;

    /**
     * Default desired retention, the target recall probability FSRS
     * schedules for
     */
    public static final double DEFAULT_DESIRED_RETENTION = 0.9;

    /**
     * Minimum selectable desired retention
     */
    public static final double DESIRED_RETENTION_MIN = 0.7;

    /**
     * Maximum selectable desired retention
     */
    public static final double DESIRED_RETENTION_MAX = 0.98;

    private FsrsParams() {
    }

    /**
     * @return the given desired retention clamped to the supported range
     */
    public static double clampDesiredRetention(double desiredRetention) {
        return Math.max(DESIRED_RETENTION_MIN,
                Math.min(DESIRED_RETENTION_MAX, desiredRetention));
    }
}

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
 * Selection of the active test mode, stored as an int shared preference
 * and in the serialized TestState.
 */
public final class TestMode {

    /**
     * Classic self-graded test: reveal the answer, then grade
     * (Again / Hard / Good / Easy), the default
     */
    public static final int SELF_GRADE = 0;

    /**
     * Multiple-choice test: pick the correct answer from generated options
     */
    public static final int MULTIPLE_CHOICE = 1;

    private TestMode() {
    }

    /**
     * @return the given mode when it is a known TestMode constant,
     * {@link #SELF_GRADE} for anything else
     */
    public static int normalize(int mode) {
        return mode != SELF_GRADE && mode != MULTIPLE_CHOICE
                ? SELF_GRADE : mode;
    }
}

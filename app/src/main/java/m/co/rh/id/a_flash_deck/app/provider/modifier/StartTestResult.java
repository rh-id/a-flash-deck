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

package m.co.rh.id.a_flash_deck.app.provider.modifier;

import m.co.rh.id.a_flash_deck.base.model.TestState;

/**
 * Result of starting a deck-select test: the new test state plus how many
 * suspended cards were excluded from it.
 */
public class StartTestResult {
    public final TestState testState;
    public final int skippedSuspendedCount;

    public StartTestResult(TestState testState, int skippedSuspendedCount) {
        this.testState = testState;
        this.skippedSuspendedCount = skippedSuspendedCount;
    }
}

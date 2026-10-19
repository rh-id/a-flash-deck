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

package m.co.rh.id.a_flash_deck.app.anki.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of an Anki .apkg import: what was imported and what had to be
 * skipped. Used to build the user visible import summary dialog and logs.
 */
public class AnkiImportSummary {

    /**
     * Number of decks created by the import.
     */
    public int decksImported;
    /**
     * Number of app cards created by the import.
     */
    public int cardsImported;
    /**
     * Number of Anki notes that were turned into app cards.
     */
    public int notesImported;
    /**
     * Number of notes skipped because an identical note was already imported
     * (its guid exists in the database or the guid repeats within the
     * imported file itself).
     */
    public int duplicateNotesSkipped;
    /**
     * Notetypes whose notes were not imported (cloze or unsupported notetypes).
     */
    public final List<SkippedNotetype> skippedNotetypes = new ArrayList<>();
    /**
     * Number of media items dropped because only the first image/audio per
     * card side is supported.
     */
    public int droppedMediaCount;

    /**
     * Returns true when the import had to skip content (notetypes or media),
     * false when everything in the package was imported cleanly. Duplicates
     * alone do not count as a warning: they are expected on re-import and are
     * only reported in the summary dialog.
     */
    public boolean hasWarnings() {
        return !skippedNotetypes.isEmpty()
                || droppedMediaCount > 0;
    }

    /**
     * A notetype whose notes were not imported.
     */
    public static class SkippedNotetype {
        /**
         * Name of the skipped notetype as defined in the Anki collection.
         */
        public final String name;
        /**
         * Number of notes of this notetype that were skipped.
         */
        public final int noteCount;
        /**
         * Machine readable reason for skipping, e.g. "cloze" or "unsupported".
         */
        public final String reason;

        public SkippedNotetype(String name, int noteCount, String reason) {
            this.name = name;
            this.noteCount = noteCount;
            this.reason = reason;
        }
    }
}

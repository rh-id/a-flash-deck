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

package m.co.rh.id.a_flash_deck.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiImportSummary;
import m.co.rh.id.a_flash_deck.app.anki.model.AnkiImportSummary.SkippedNotetype;
import m.co.rh.id.a_flash_deck.base.model.DeckImportPayload;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;

/**
 * Result of an import file operation: the imported decks plus a summary of
 * what was imported and what was skipped.
 */
public class ImportResult {

    /**
     * The imported decks with their Anki note guids (guids empty for native
     * imports), empty when nothing was imported.
     */
    public final List<DeckImportPayload> payloads;
    /**
     * The deck models of {@link #payloads}, in the same order. Convenience
     * access for UI and tests.
     */
    public final List<DeckModel> decks;
    /**
     * Details about the import, never null.
     */
    public final AnkiImportSummary summary;
    /**
     * True when the summary should be shown to the user as a dialog: always
     * for .apkg imports, for other imports only when warnings exist.
     */
    public final boolean showSummaryDialog;

    public ImportResult(List<DeckImportPayload> payloads, AnkiImportSummary summary, boolean showSummaryDialog) {
        this.payloads = payloads == null ? Collections.emptyList() : payloads;
        List<DeckModel> deckModels = new ArrayList<>();
        for (DeckImportPayload payload : this.payloads) {
            if (payload != null && payload.getDeckModel() != null) {
                deckModels.add(payload.getDeckModel());
            }
        }
        this.decks = Collections.unmodifiableList(deckModels);
        this.summary = summary == null ? new AnkiImportSummary() : summary;
        this.showSummaryDialog = showSummaryDialog;
    }

    /**
     * Wraps deck models without Anki note guids (native app format imports).
     */
    public static ImportResult withoutGuids(List<DeckModel> decks, AnkiImportSummary summary,
                                            boolean showSummaryDialog) {
        List<DeckImportPayload> payloads = new ArrayList<>();
        if (decks != null) {
            for (DeckModel deckModel : decks) {
                payloads.add(new DeckImportPayload(deckModel, Collections.emptyList()));
            }
        }
        return new ImportResult(payloads, summary, showSummaryDialog);
    }

    /**
     * Builds the user visible import summary message: imported deck/card
     * counts followed by one line per warning (skipped notetypes, dropped
     * media) and the duplicate note count.
     */
    public String buildSummaryMessage(Context context) {
        StringBuilder message = new StringBuilder();
        message.append(context.getString(R.string.import_summary_result,
                summary.decksImported, summary.cardsImported));
        for (SkippedNotetype skippedNotetype : summary.skippedNotetypes) {
            message.append('\n')
                    .append(context.getString(R.string.import_summary_skipped_notetype,
                            skippedNotetype.name, skippedNotetype.noteCount));
        }
        if (summary.droppedMediaCount > 0) {
            message.append('\n')
                    .append(context.getString(R.string.import_summary_dropped_media,
                            summary.droppedMediaCount));
        }
        if (summary.duplicateNotesSkipped > 0) {
            message.append('\n')
                    .append(context.getString(R.string.import_summary_duplicates_skipped,
                            summary.duplicateNotesSkipped));
        }
        return message.toString();
    }
}

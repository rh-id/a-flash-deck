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

package m.co.rh.id.a_flash_deck.app.ui.component.card;

import java.io.Serializable;

/**
 * Plain Java class that manages ONE markdown field (question or answer).
 * Serializable pure state holder: binding, async rendering and focus
 * handling are owned by CardDetailPage.
 */
public class MarkdownEditField implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String mFieldName;

    // Raw markdown source, the surviving editor state, never null
    private String mCurrentText = "";

    /**
     * Internal constructor for MarkdownEditField
     */
    private MarkdownEditField(String fieldName) {
        mFieldName = fieldName;
    }

    /**
     * Create a MarkdownEditField for the question
     */
    public static MarkdownEditField forQuestion() {
        return new MarkdownEditField("question");
    }

    /**
     * Create a MarkdownEditField for the answer
     */
    public static MarkdownEditField forAnswer() {
        return new MarkdownEditField("answer");
    }

    /**
     * Get the field name, used by CardDetailPage for unique RxDisposer tags
     *
     * @return The field name
     */
    public String getFieldName() {
        return mFieldName;
    }

    /**
     * Get the current raw markdown source
     *
     * @return The raw markdown source, never null (empty when unset)
     */
    public String getText() {
        return mCurrentText;
    }

    /**
     * Set the raw markdown source
     *
     * @param text The raw markdown source, null is treated as empty
     */
    public void setText(String text) {
        mCurrentText = text != null ? text : "";
    }
}

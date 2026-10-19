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
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Objects;

/**
 * Links an imported card to the guid of the Anki note it was created from.
 * Used to detect and skip duplicate notes when the same .apkg file is
 * imported again.
 */
@Entity(tableName = "anki_note_guid",
        foreignKeys = {@ForeignKey(entity = Card.class,
                parentColumns = "id", childColumns = "card_id",
                onDelete = ForeignKey.CASCADE)},
        indices = {@Index("guid"), @Index("card_id")})
public class AnkiNoteGuid implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Card ID that refers to Card.id
     */
    @ColumnInfo(name = "card_id")
    public long cardId;

    /**
     * Original Anki note guid this card was imported from
     */
    @NonNull
    @ColumnInfo(name = "guid")
    public String guid;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnkiNoteGuid that = (AnkiNoteGuid) o;
        return cardId == that.cardId &&
                Objects.equals(id, that.id) &&
                Objects.equals(guid, that.guid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cardId, guid);
    }
}

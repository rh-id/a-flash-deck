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

package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.AnkiNoteGuid;

/**
 * DAO that handles the Anki note guid entity
 */
@Dao
public abstract class AnkiNoteGuidDao {

    @Query("SELECT guid FROM anki_note_guid WHERE guid IN (:guids)")
    abstract List<String> getExistingGuids(List<String> guids);

    /**
     * Returns all of the given guids that already exist in the database
     * (queried in batches; tolerates null/empty input).
     */
    public List<String> findExistingGuids(List<String> guids) {
        return DaoBatchQueryUtil.queryInBatches(guids, this::getExistingGuids);
    }

    @Query("SELECT * FROM anki_note_guid WHERE card_id = :cardId")
    public abstract AnkiNoteGuid findByCardId(long cardId);

    @Insert
    public abstract void insert(AnkiNoteGuid ankiNoteGuid);
}

/*
 *     Copyright (C) 2021 Ruby Hartono
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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import m.co.rh.id.a_flash_deck.base.room.converter.Converter;


@Entity(tableName = "deck")
public class Deck implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * User defined deck name
     */
    @ColumnInfo(name = "name")
    public String name;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deck deck = (Deck) o;
        return Objects.equals(id, deck.id) &&
                Objects.equals(name, deck.name) &&
                Objects.equals(createdDateTime, deck.createdDateTime) &&
                Objects.equals(updatedDateTime, deck.updatedDateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, createdDateTime, updatedDateTime);
    }

    @Override
    public Deck clone() {
        try {
            return (Deck) super.clone();
        } catch (CloneNotSupportedException cloneNotSupportedException) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Deck{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", updatedDateTime=" + updatedDateTime +
                '}';
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id);
        jsonObject.put("name", name);
        String createdDateTimeStr = "";
        if (createdDateTime != null) {
            createdDateTimeStr = Long.toString(createdDateTime.getTime());
        }
        String updatedDateTimeStr = "";
        if (updatedDateTime != null) {
            updatedDateTimeStr = Long.toString(updatedDateTime.getTime());
        }
        jsonObject.put("createdDateTime", createdDateTimeStr);
        jsonObject.put("updatedDateTime", updatedDateTimeStr);
        return jsonObject;
    }

    public void fromJson(JSONObject jsonObject) throws JSONException {
        id = jsonObject.getLong("id");
        name = jsonObject.getString("name");
        String createdDateTimeMilis = jsonObject.getString("createdDateTime");
        if (!createdDateTimeMilis.isEmpty()) {
            createdDateTime = new Date(Long.parseLong(createdDateTimeMilis));
        }
        String updatedDateTimeMilis = jsonObject.getString("updatedDateTime");
        if (!updatedDateTimeMilis.isEmpty()) {
            updatedDateTime = new Date(Long.parseLong(updatedDateTimeMilis));
        }
    }
}

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

package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.Test;

/**
 * DAO that handles deck and cards
 */
@Dao
public abstract class TestDao {

    @Query("SELECT * FROM test")
    public abstract Test getCurrentTest();

    @Transaction
    public void insertTest(Test test) {
        if (test == null) {
            return;
        }
        Date currentDate = new Date();
        if (test.createdDateTime == null) {
            test.createdDateTime = currentDate;
        }
        test.id = insert(test);
    }

    @Delete
    public abstract void delete(Test test);

    @Insert
    protected abstract long insert(Test test);

    @Query("SELECT * FROM test where id=:testId")
    public abstract Test getTestById(long testId);
}

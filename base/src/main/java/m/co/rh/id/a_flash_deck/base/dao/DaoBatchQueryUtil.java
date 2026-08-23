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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class for batching queries to avoid SQLite parameter binding limits
 */
final class DaoBatchQueryUtil {

    private DaoBatchQueryUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Executes an IN-clause query in chunks below SQLite's 999 bind-variable limit (older Android versions).
     * Returns all rows matching the given ids; safe for null, empty, or duplicate id inputs (ids are deduplicated).
     *
     * @param ids    List of IDs to query
     * @param query  Function that executes the query for a batch of IDs
     * @param <T>    Return type of the query
     * @return       Combined results from all batches, or empty list for null/empty input
     */
    static <T> List<T> queryInBatches(List<Long> ids, Function<List<Long>, List<T>> query) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        // Deduplicate while preserving first-occurrence order
        List<Long> deduplicatedIds = new ArrayList<>(new LinkedHashSet<>(ids));
        int size = deduplicatedIds.size();
        int maxQuerySize = 500;
        if (size <= maxQuerySize) {
            return query.apply(deduplicatedIds);
        }
        List<T> result = new ArrayList<>();
        for (int i = 0, i2 = maxQuerySize;
             size > 0;
             size -= maxQuerySize,
                     i = i2,
                     i2 += Math.min(size, maxQuerySize)) {
            result.addAll(query.apply(deduplicatedIds.subList(i, i2)));
        }
        return result;
    }
}

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

package m.co.rh.id.a_flash_deck.app.anki;

/**
 * Anki note guid helpers. Anki does not validate the guid format, only that
 * it is non-empty; this produces the same format a native Anki client would.
 */
public final class AnkiGuids {

    /**
     * Anki's guid alphabet: letters, digits and 29 safe punctuation
     * characters (91 in total, one per base91 digit).
     */
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    + "!#$%&()*+,-./:;<=>?@[]^_`{|}~";

    private AnkiGuids() {
        // utility class
    }

    /**
     * Encodes a non-negative value exactly the way Anki encodes note guids:
     * big-endian base91 over {@link #ALPHABET}. Deterministic, so the same
     * card id always yields the same guid (e.g. 1 encodes to "b").
     *
     * @param value non-negative value to encode, typically a card id
     * @return the base91 representation, never empty
     */
    public static String base91FromLong(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        long remaining = value;
        StringBuilder encoded = new StringBuilder();
        do {
            encoded.append(ALPHABET.charAt((int) (remaining % ALPHABET.length())));
            remaining /= ALPHABET.length();
        } while (remaining > 0);
        return encoded.reverse().toString();
    }
}

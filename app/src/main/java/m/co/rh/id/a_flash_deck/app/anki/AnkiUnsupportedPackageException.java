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

import java.io.IOException;

/**
 * Thrown when an .apkg file is structurally a zip package but its collection
 * content cannot be imported by this app.
 * The reason is used by the importer to pick a distinct user facing message.
 */
public class AnkiUnsupportedPackageException extends IOException {

    /**
     * Why the package content is not supported.
     */
    public enum Reason {
        /**
         * Modern Anki package (collection.anki21b) whose zstd payload could
         * not be decompressed or whose collection schema is unrecognized,
         * i.e. the package is corrupted or unreadable.
         */
        MODERN_ZSTD,
        /**
         * Legacy collection.anki2 that is only the decoy stub embedded in
         * modern packages ("Please update to the latest Anki version").
         */
        DECOY
    }

    public final Reason reason;

    public AnkiUnsupportedPackageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AnkiUnsupportedPackageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}

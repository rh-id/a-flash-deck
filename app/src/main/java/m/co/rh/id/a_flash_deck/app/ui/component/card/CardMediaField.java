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

import java.io.File;
import java.io.Serializable;
import java.util.Optional;

/**
 * Plain Java class that manages ONE attachment slot (image or voice).
 * Serializable pure state holder: browsing, camera capture and the UI
 * updates on file changes are handled by CardDetailPage.
 */
public class CardMediaField implements Serializable {
    private static final long serialVersionUID = 1L;

    // Request codes for image browsing and camera capture
    private static final int BROWSE_FOR_QUESTION_IMAGE = 1;
    private static final int BROWSE_FOR_ANSWER_IMAGE = 2;
    private static final int CAMERA_FOR_QUESTION_IMAGE = 3;
    private static final int CAMERA_FOR_ANSWER_IMAGE = 4;
    private static final int NO_REQUEST_CODE = 0;

    private final int mBrowseRequestCode;
    private final int mCameraRequestCode;

    private File mFile;
    private File mTempCameraFile;

    /**
     * Internal constructor for CardMediaField
     */
    private CardMediaField(int browseRequestCode, int cameraRequestCode) {
        mBrowseRequestCode = browseRequestCode;
        mCameraRequestCode = cameraRequestCode;
    }

    /**
     * Create a CardMediaField for question image
     */
    public static CardMediaField forQuestionImage() {
        return new CardMediaField(BROWSE_FOR_QUESTION_IMAGE, CAMERA_FOR_QUESTION_IMAGE);
    }

    /**
     * Create a CardMediaField for answer image
     */
    public static CardMediaField forAnswerImage() {
        return new CardMediaField(BROWSE_FOR_ANSWER_IMAGE, CAMERA_FOR_ANSWER_IMAGE);
    }

    /**
     * Create a CardMediaField for question voice
     */
    public static CardMediaField forQuestionVoice() {
        return new CardMediaField(NO_REQUEST_CODE, NO_REQUEST_CODE);
    }

    /**
     * Create a CardMediaField for answer voice
     */
    public static CardMediaField forAnswerVoice() {
        return new CardMediaField(NO_REQUEST_CODE, NO_REQUEST_CODE);
    }

    /**
     * Get the current file value
     *
     * @return Optional containing the file, or empty if none
     */
    public Optional<File> getValue() {
        return Optional.ofNullable(mFile);
    }

    /**
     * Set the file value
     *
     * @param file The file to set, or null to clear
     */
    public void setFile(File file) {
        mFile = file;
    }

    /**
     * Get the request code used for image browsing
     *
     * @return The browse request code
     */
    public int getBrowseRequestCode() {
        return mBrowseRequestCode;
    }

    /**
     * Get the request code used for camera capture
     *
     * @return The camera request code
     */
    public int getCameraRequestCode() {
        return mCameraRequestCode;
    }

    /**
     * Get the temp file created for a pending camera capture
     *
     * @return The temp camera file, or null if none
     */
    public File getTempCameraFile() {
        return mTempCameraFile;
    }

    /**
     * Set the temp camera file
     *
     * @param tempCameraFile The temp camera file
     */
    public void setTempCameraFile(File tempCameraFile) {
        mTempCameraFile = tempCameraFile;
    }
}

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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.util.UiUtils;

/**
 * Plain Java class that manages ONE attachment slot (image or voice).
 * Handles browsing, camera capture, voice recording, and playback.
 */
public class CardMediaField {
    private static final String TAG = CardMediaField.class.getName();

    // Request codes for image browsing and camera capture
    private static final int BROWSE_FOR_QUESTION_IMAGE = 1;
    private static final int BROWSE_FOR_ANSWER_IMAGE = 2;
    private static final int CAMERA_FOR_QUESTION_IMAGE = 3;
    private static final int CAMERA_FOR_ANSWER_IMAGE = 4;
    private static final int NO_REQUEST_CODE = 0;

    private final FileHelper mFileHelper;
    private final AudioPlayer mAudioPlayer; // nullable for image-only fields
    private final ILogger mLogger;
    private final RxDisposer mRxDisposer;
    private final int mBrowseRequestCode;
    private final int mCameraRequestCode;

    private BehaviorSubject<Optional<File>> mFileSubject;
    private File mTempCameraFile;

    /**
     * Internal constructor for CardMediaField
     */
    private CardMediaField(FileHelper fileHelper, AudioPlayer audioPlayer, ILogger logger,
                          RxDisposer rxDisposer, int browseRequestCode, int cameraRequestCode) {
        mFileHelper = fileHelper;
        mAudioPlayer = audioPlayer;
        mLogger = logger;
        mRxDisposer = rxDisposer;
        mBrowseRequestCode = browseRequestCode;
        mCameraRequestCode = cameraRequestCode;
    }

    /**
     * Create a CardMediaField for question image
     */
    public static CardMediaField forQuestionImage(FileHelper fileHelper, ILogger logger, RxDisposer rxDisposer) {
        return new CardMediaField(fileHelper, null, logger, rxDisposer, BROWSE_FOR_QUESTION_IMAGE, CAMERA_FOR_QUESTION_IMAGE);
    }

    /**
     * Create a CardMediaField for answer image
     */
    public static CardMediaField forAnswerImage(FileHelper fileHelper, ILogger logger, RxDisposer rxDisposer) {
        return new CardMediaField(fileHelper, null, logger, rxDisposer, BROWSE_FOR_ANSWER_IMAGE, CAMERA_FOR_ANSWER_IMAGE);
    }

    /**
     * Create a CardMediaField for question voice
     */
    public static CardMediaField forQuestionVoice(FileHelper fileHelper, AudioPlayer audioPlayer, ILogger logger, RxDisposer rxDisposer) {
        return new CardMediaField(fileHelper, audioPlayer, logger, rxDisposer, NO_REQUEST_CODE, NO_REQUEST_CODE);
    }

    /**
     * Create a CardMediaField for answer voice
     */
    public static CardMediaField forAnswerVoice(FileHelper fileHelper, AudioPlayer audioPlayer, ILogger logger, RxDisposer rxDisposer) {
        return new CardMediaField(fileHelper, audioPlayer, logger, rxDisposer, NO_REQUEST_CODE, NO_REQUEST_CODE);
    }

    /**
     * Get the current file value
     *
     * @return Optional containing the file, or empty if none
     */
    public Optional<File> getValue() {
        if (mFileSubject == null) {
            return Optional.empty();
        }
        return mFileSubject.getValue();
    }

    /**
     * Set the file value
     *
     * @param file The file to set, or null to clear
     */
    public void setFile(File file) {
        if (mFileSubject == null) {
            mFileSubject = BehaviorSubject.createDefault(Optional.ofNullable(file));
        } else {
            mFileSubject.onNext(Optional.ofNullable(file));
        }
    }

    /**
     * Get the BehaviorSubject for UI subscriptions
     *
     * @return The BehaviorSubject for file changes
     */
    public BehaviorSubject<Optional<File>> getFileSubject() {
        return mFileSubject;
    }

    /**
     * Browse for an image file
     *
     * @param activity    The activity
     */
    public void browseImage(Activity activity) {
        UiUtils.browseImage(activity, mBrowseRequestCode);
    }

    /**
     * Take a photo from camera
     *
     * @param activity    The activity
     * @return The temp file created for the camera, or null if failed
     */
    public File takePhotoFromCamera(Activity activity) {
        try {
            mTempCameraFile = mFileHelper.createImageTempFile();
            UiUtils.takeImageFromCamera(activity, mCameraRequestCode, mTempCameraFile);
            return mTempCameraFile;
        } catch (Exception e) {
            mLogger.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Play voice recording
     */
    public void playVoice() {
        if (mAudioPlayer != null) {
            File file = getValue().orElse(null);
            if (file != null) {
                mAudioPlayer.play(Uri.fromFile(file));
            }
        }
    }

    /**
     * Handle activity result from browsing or camera
     *
     * @param requestCode The request code
     * @param resultCode  The result code
     * @param data        The intent data
     * @return true if a camera-capture result was applied (result code OK for this field's camera request)
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == mBrowseRequestCode && resultCode == Activity.RESULT_OK && data != null) {
            Uri fullPhotoUri = data.getData();
            handleImageUri(fullPhotoUri);
            return false;
        } else if (requestCode == mCameraRequestCode && resultCode == Activity.RESULT_OK) {
            if (mTempCameraFile != null) {
                Uri cameraUri = Uri.fromFile(mTempCameraFile);
                handleImageUri(cameraUri);
                mTempCameraFile = null;
            }
            return true;
        }
        return false;
    }

    /**
     * Handle image URI processing
     *
     * @param imageUri  The image URI
     */
    private void handleImageUri(Uri imageUri) {
        String tag = "handleImageUri_" + mBrowseRequestCode + "_" + mCameraRequestCode;
        mRxDisposer.add(tag,
                Single.fromCallable(() -> mFileHelper.createImageTempFile(imageUri))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(resultFile -> setFile(resultFile),
                                e -> mLogger.e(TAG, e.getMessage(), e)));
    }

    /**
     * Set the temp camera file (restored by the page after process death)
     *
     * @param tempCameraFile The temp camera file
     */
    public void setTempCameraFile(File tempCameraFile) {
        mTempCameraFile = tempCameraFile;
    }

    /**
     * Dispose resources
     */
    public void dispose() {
        if (mFileSubject != null) {
            mFileSubject.onComplete();
            mFileSubject = null;
        }
        mTempCameraFile = null;
    }
}
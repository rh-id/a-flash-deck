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

package m.co.rh.id.a_flash_deck.ai.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromImageCmd;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;

public class GenerateDeckFromImagePage extends BaseGenerateDeckPage
        implements NavOnActivityResult<Activity> {

    private static final String TAG = GenerateDeckFromImagePage.class.getName();
    private static final int REQUEST_CAMERA = 601;
    private static final int REQUEST_GALLERY = 602;
    private static final int MAX_IMAGES = 10;

    private transient GenerateDeckFromImageCmd mGenerateCmd;
    private transient FileHelper mFileHelper;

    private transient MaterialAutoCompleteTextView mEditTextPrompt;
    private transient EditText mEditTextMaxCards;
    private transient LinearLayout mContainerImages;
    private transient TextView mTextNoImages;

    /** Temp file for camera output (preserved across state saves). */
    private File mTempCameraFile;
    /** Ordered list of image file paths selected by the user (preserved across state saves). */
    private ArrayList<String> mImagePaths;

    public GenerateDeckFromImagePage() {
        super();
    }

    @Override
    protected void initProviders() {
        super.initProviders();
        mGenerateCmd = mSvProvider.get(GenerateDeckFromImageCmd.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initProviders();
        if (mImagePaths == null) {
            mImagePaths = new ArrayList<>();
        }

        View view = activity.getLayoutInflater().inflate(R.layout.page_generate_deck_from_image, container, false);

        // Setup AppBar
        mAppBarSV.setTitle(mSvProvider.getContext().getString(R.string.title_generate_deck_from_image));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));

        mTextSelectedModel = view.findViewById(R.id.text_selected_model);
        TextInputLayout layoutSelectedModel = view.findViewById(R.id.input_layout_selected_model);
        mContainerImages = view.findViewById(R.id.container_images);
        mTextNoImages = view.findViewById(R.id.text_no_images);
        mEditTextPrompt = view.findViewById(R.id.edit_text_prompt);
        mEditTextMaxCards = view.findViewById(R.id.edit_text_max_cards);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        Button buttonGenerate = view.findViewById(R.id.button_generate);
        Button buttonTakePhoto = view.findViewById(R.id.button_take_photo);
        Button buttonPickGallery = view.findViewById(R.id.button_pick_gallery);

        mTextSelectedModel.setText(mApiKeyManager.getSelectedModel());
        mTextSelectedModel.setOnClickListener(v -> showModelSelectionDialog(activity));
        if (layoutSelectedModel != null) {
            layoutSelectedModel.setOnClickListener(v -> showModelSelectionDialog(activity));
            layoutSelectedModel.setEndIconOnClickListener(v -> showModelSelectionDialog(activity));
        }

        String[] suggestions = mSvProvider.getContext().getResources().getStringArray(R.array.ai_image_prompt_suggestions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, suggestions);
        mEditTextPrompt.setAdapter(adapter);

        mEditTextMaxCards.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
                mGenerateCmd.valid(mImagePaths, maxCards);
            }
        });

        buttonTakePhoto.setOnClickListener(v -> onTakePhotoClicked(activity));
        buttonPickGallery.setOnClickListener(v -> onPickGalleryClicked(activity));
        buttonCancel.setOnClickListener(this);
        buttonGenerate.setOnClickListener(this);

        setupValidationObserver(view.findViewById(R.id.text_validation), mGenerateCmd.getValidationFlowable());
        fetchModels();
        refreshImageList(activity);

        return view;
    }

    private void onTakePhotoClicked(Activity activity) {
        if (mImagePaths != null && mImagePaths.size() >= MAX_IMAGES) {
            return;
        }
        try {
            mTempCameraFile = mFileHelper.createImageTempFile();
            UiUtils.takeImageFromCamera(activity, REQUEST_CAMERA, mTempCameraFile);
        } catch (Exception e) {
            mSvProvider.get(ILogger.class).e(TAG, "Failed to launch camera", e);
        }
    }

    private void onPickGalleryClicked(Activity activity) {
        if (mImagePaths != null && mImagePaths.size() >= MAX_IMAGES) {
            return;
        }
        UiUtils.browseImage(activity, REQUEST_GALLERY);
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator,
                                 int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) return;

        if (requestCode == REQUEST_CAMERA) {
            if (mTempCameraFile != null && mTempCameraFile.exists()) {
                addImagePath(activity, mTempCameraFile.getAbsolutePath());
                mTempCameraFile = null;
            }
        } else if (requestCode == REQUEST_GALLERY) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String disposerKey = "copyGalleryImage_" + UUID.randomUUID().toString();
                mSvProvider.get(RxDisposer.class).add(disposerKey,
                        Single.fromCallable(() -> mFileHelper.createImageTempFile(uri))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        resultFile -> addImagePath(activity, resultFile.getAbsolutePath()),
                                        e -> mSvProvider.get(ILogger.class).e(TAG, "Failed to copy gallery image", e)));
            }
        }
    }

    private void addImagePath(Activity activity, String absolutePath) {
        if (mImagePaths == null) {
            mImagePaths = new ArrayList<>();
        }
        if (mImagePaths.size() >= MAX_IMAGES) return;
        mImagePaths.add(absolutePath);
        refreshImageList(activity);
    }

    private void removeImagePath(Activity activity, int index) {
        if (mImagePaths != null && index >= 0 && index < mImagePaths.size()) {
            mImagePaths.remove(index);
        }
        refreshImageList(activity);
    }

    private void refreshImageList(Activity activity) {
        if (mContainerImages == null || mTextNoImages == null || mImagePaths == null) return;
        mContainerImages.removeAllViews();
        mTextNoImages.setVisibility(mImagePaths.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(activity != null ? activity : mContainerImages.getContext());
        for (int i = 0; i < mImagePaths.size(); i++) {
            final int index = i;
            final String path = mImagePaths.get(i);
            View itemView = inflater.inflate(R.layout.item_image_thumbnail, mContainerImages, false);
            ImageView thumbnail = itemView.findViewById(R.id.image_thumbnail);
            TextView nameView = itemView.findViewById(R.id.text_image_name);
            Button removeBtn = itemView.findViewById(R.id.button_remove_image);

            String disposerKey = "loadThumb_" + path.hashCode();
            mSvProvider.get(RxDisposer.class).add(disposerKey,
                    Single.fromCallable(() -> loadThumbnail(path))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    bmp -> thumbnail.setImageBitmap(bmp),
                                    e -> mSvProvider.get(ILogger.class).e(TAG, "Failed to load thumbnail", e)));

            nameView.setText(new File(path).getName());
            removeBtn.setOnClickListener(v -> removeImagePath(activity, index));
            mContainerImages.addView(itemView);
        }
        int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
        mGenerateCmd.valid(mImagePaths, maxCards);
    }

    private Bitmap loadThumbnail(String path) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4;
        return BitmapFactory.decodeFile(path, opts);
    }

    @Override
    protected void generateDeck() {
        int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
        if (!mGenerateCmd.valid(mImagePaths, maxCards)) return;
        String prompt = mEditTextPrompt != null ? mEditTextPrompt.getText().toString().trim() : "";
        String modelId = mApiKeyManager.getSelectedModel();
        mGenerateCmd.execute(new ArrayList<>(mImagePaths), prompt, maxCards, modelId);
        mSvProvider.get(ILogger.class).i(TAG,
                mSvProvider.getContext().getString(R.string.ai_generate_from_image_started));
        mNavigator.pop();
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mGenerateCmd = null;
        mFileHelper = null;
        mEditTextPrompt = null;
        mEditTextMaxCards = null;
        mContainerImages = null;
        mTextNoImages = null;
        mTempCameraFile = null;
        mImagePaths = null;
    }
}

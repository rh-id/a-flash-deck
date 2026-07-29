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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromImageCmd;
import m.co.rh.id.a_flash_deck.ai.model.AvailableModel;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.aprovider.Provider;

public class GenerateDeckFromImagePage extends StatefulView<Activity>
        implements NavOnActivityResult<Activity>, View.OnClickListener {

    private static final String TAG = GenerateDeckFromImagePage.class.getName();
    private static final int REQUEST_CAMERA = 601;
    private static final int REQUEST_GALLERY = 602;
    private static final int MAX_IMAGES = 10;

    @NavInject
    private transient Provider mProvider;
    @NavInject
    private INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient GeminiService mGeminiService;
    private transient ApiKeyManager mApiKeyManager;
    private transient GenerateDeckFromImageCmd mGenerateCmd;
    private transient FileHelper mFileHelper;
    private transient List<AvailableModel> mAvailableModels;

    private transient TextView mTextSelectedModel;
    private transient MaterialAutoCompleteTextView mEditTextPrompt;
    private transient EditText mEditTextMaxCards;
    private transient LinearLayout mContainerImages;
    private transient TextView mTextNoImages;

    /** Temp file for camera output (preserved across state saves). */
    private File mTempCameraFile;
    /** Ordered list of image file paths selected by the user (preserved across state saves). */
    private ArrayList<String> mImagePaths;

    public GenerateDeckFromImagePage() {
        mAppBarSV = new AppBarSV();
    }

    private void initProviders() {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mGeminiService = mSvProvider.get(GeminiService.class);
        mApiKeyManager = mSvProvider.get(ApiKeyManager.class);
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
        com.google.android.material.textfield.TextInputLayout layoutSelectedModel = view.findViewById(R.id.input_layout_selected_model);
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

    private void fetchModels() {
        if (!mGeminiService.isConfigured()) return;
        mSvProvider.get(RxDisposer.class).add("fetchModels",
                mGeminiService.fetchAvailableModels()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((models, throwable) -> {
                            if (throwable != null) {
                                mSvProvider.get(ILogger.class).e(TAG,
                                        mSvProvider.getContext().getString(R.string.error_fetching_models),
                                        throwable);
                            } else if (models != null) {
                                mAvailableModels = models;
                            }
                        }));
    }

    private void showModelSelectionDialog(Activity activity) {
        if (mAvailableModels == null || mAvailableModels.isEmpty()) {
            mSvProvider.get(RxDisposer.class).add("fetchModelsForDialog",
                    mGeminiService.fetchAvailableModels()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((models, throwable) -> {
                                if (throwable == null && models != null && !models.isEmpty()) {
                                    mAvailableModels = models;
                                    showModelSelectionDialog(activity);
                                }
                            }));
            return;
        }

        String[] displayNames = new String[mAvailableModels.size()];
        String currentModel = mApiKeyManager.getSelectedModel();
        int selectedIndex = -1;
        for (int i = 0; i < mAvailableModels.size(); i++) {
            displayNames[i] = mAvailableModels.get(i).displayName;
            if (mAvailableModels.get(i).id.equals(currentModel)) {
                selectedIndex = i;
            }
        }

        int finalSelectedIndex = selectedIndex >= 0 ? selectedIndex : 0;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.title_select_model)
                .setSingleChoiceItems(displayNames, finalSelectedIndex, (dialog, which) -> {
                    AvailableModel selected = mAvailableModels.get(which);
                    mApiKeyManager.saveSelectedModel(selected.id);
                    mTextSelectedModel.setText(selected.id);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupValidationObserver(TextView textValidation, Flowable<String> validationFlowable) {
        mSvProvider.get(RxDisposer.class).add("validation",
                validationFlowable
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s.isEmpty()) {
                                textValidation.setVisibility(View.GONE);
                            } else {
                                textValidation.setVisibility(View.VISIBLE);
                                textValidation.setText(s);
                            }
                        }));
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
                        io.reactivex.rxjava3.core.Single.fromCallable(() -> mFileHelper.createImageTempFile(uri))
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
                    io.reactivex.rxjava3.core.Single.fromCallable(() -> loadThumbnail(path))
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

    private int parseEditTextInt(EditText editText, int defaultValue) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_generate) {
            generateDeck();
        } else if (id == R.id.button_cancel) {
            mNavigator.pop();
        }
    }

    private void generateDeck() {
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
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mGeminiService = null;
        mApiKeyManager = null;
        mGenerateCmd = null;
        mFileHelper = null;
        mAvailableModels = null;
        mTextSelectedModel = null;
        mEditTextPrompt = null;
        mEditTextMaxCards = null;
        mContainerImages = null;
        mTextNoImages = null;
        mTempCameraFile = null;
        mImagePaths = null;
    }
}

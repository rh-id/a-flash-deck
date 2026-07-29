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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.model.AvailableModel;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public abstract class BaseGenerateDeckPage extends StatefulView<Activity>
        implements View.OnClickListener {

    private static final String TAG = "BaseGenerateDeckPage";

    @NavInject
    protected transient NavRoute mNavRoute;
    @NavInject
    protected transient Provider mProvider;
    @NavInject
    protected transient INavigator mNavigator;
    @NavInject
    protected AppBarSV mAppBarSV;

    protected transient Provider mSvProvider;
    protected transient GeminiService mGeminiService;
    protected transient ApiKeyManager mApiKeyManager;
    protected transient List<AvailableModel> mAvailableModels;
    protected transient TextView mTextSelectedModel;

    protected BaseGenerateDeckPage() {
        mAppBarSV = new AppBarSV();
    }

    protected void initProviders() {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mGeminiService = mSvProvider.get(GeminiService.class);
        mApiKeyManager = mSvProvider.get(ApiKeyManager.class);
    }

    protected void initModelSelection(View view) {
        mTextSelectedModel = view.findViewById(R.id.text_selected_model);
        Button buttonSelectModel = view.findViewById(R.id.button_select_model);
        if (mTextSelectedModel != null) {
            mTextSelectedModel.setText(mSvProvider.getContext()
                    .getString(R.string.current_model, mApiKeyManager.getSelectedModel()));
        }
        if (buttonSelectModel != null) {
            buttonSelectModel.setOnClickListener(v -> showModelSelectionDialog());
        }
    }

    protected void setupValidationObserver(TextView textValidation, Flowable<String> validationFlowable) {
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

    protected void fetchModels() {
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

    protected void showModelSelectionDialog() {
        if (mTextSelectedModel == null) return;
        Activity activity = UiUtils.getActivity(mTextSelectedModel);
        if (activity != null) {
            showModelSelectionDialog(activity);
        }
    }

    protected void showModelSelectionDialog(Activity activity) {
        if (activity == null) return;
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
                    if (mTextSelectedModel != null) {
                        if (mTextSelectedModel.getText() != null &&
                                mTextSelectedModel.getText().toString().contains(":")) {
                            mTextSelectedModel.setText(mSvProvider.getContext()
                                    .getString(R.string.current_model, selected.id));
                        } else {
                            mTextSelectedModel.setText(selected.id);
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    protected int parseEditTextInt(EditText editText, int defaultValue) {
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

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNavRoute = null;
        mProvider = null;
        mNavigator = null;
        mGeminiService = null;
        mApiKeyManager = null;
        mAvailableModels = null;
        mTextSelectedModel = null;
    }

    protected abstract void generateDeck();
}

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
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.provider.notifier.ApiKeyChangeNotifier;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class ApiKeyEntrySVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener {

    private static final String TAG = ApiKeyEntrySVDialog.class.getName();

    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient ApiKeyManager mApiKeyManager;
    private transient GeminiService mGeminiService;
    private transient ApiKeyChangeNotifier mApiKeyChangeNotifier;
    private transient EditText mEditTextApiKey;
    private transient Button mButtonSave;
    private transient Button mButtonCancel;
    private transient Button mButtonRemove;
    private transient ProgressBar mProgressValidating;

    public ApiKeyEntrySVDialog() {
        super(null);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mApiKeyManager = mSvProvider.get(ApiKeyManager.class);
        mGeminiService = mSvProvider.get(GeminiService.class);
        mApiKeyChangeNotifier = mSvProvider.get(ApiKeyChangeNotifier.class);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_api_key_entry, container, false);

        mEditTextApiKey = view.findViewById(R.id.edit_text_api_key);
        mButtonSave = view.findViewById(R.id.button_save);
        mButtonCancel = view.findViewById(R.id.button_cancel);
        mButtonRemove = view.findViewById(R.id.button_remove);
        TextView textStatus = view.findViewById(R.id.text_status);
        mProgressValidating = view.findViewById(R.id.progress_validating);

        mButtonSave.setOnClickListener(this);
        mButtonCancel.setOnClickListener(this);
        mButtonRemove.setOnClickListener(this);

        if (mApiKeyManager.hasApiKey()) {
            mButtonRemove.setVisibility(View.VISIBLE);
            mEditTextApiKey.setHint(R.string.hint_api_key);
            textStatus.setVisibility(View.VISIBLE);
            textStatus.setText(activity.getString(R.string.api_key_set));
        }

        return view;
    }

    @Override
    protected Dialog createDialog(Activity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setCancelable(true);
        builder.setView(buildView(activity, null));
        return builder.create();
    }

    @Override
    protected void onCancelDialog(DialogInterface dialog) {
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mApiKeyManager = null;
        mGeminiService = null;
        mApiKeyChangeNotifier = null;
        mEditTextApiKey = null;
        mButtonSave = null;
        mButtonCancel = null;
        mButtonRemove = null;
        mProgressValidating = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save) {
            saveApiKey();
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        } else if (id == R.id.button_remove) {
            mApiKeyManager.removeApiKey();
            mGeminiService.resetClient();
            mApiKeyChangeNotifier.onApiKeyChanged(false);
            getNavigator().pop();
        }
    }

    private void saveApiKey() {
        String apiKey = mEditTextApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            mEditTextApiKey.setError(mSvProvider.getContext().getString(R.string.hint_api_key));
            return;
        }
        mProgressValidating.setVisibility(View.VISIBLE);
        mButtonSave.setEnabled(false);
        mButtonCancel.setEnabled(false);
        mButtonRemove.setEnabled(false);
        mEditTextApiKey.setEnabled(false);
        mSvProvider.get(RxDisposer.class).add("validateApiKey",
                mGeminiService.validateApiKey(apiKey)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((result, throwable) -> {
                            mProgressValidating.setVisibility(View.GONE);
                            mButtonSave.setEnabled(true);
                            mButtonCancel.setEnabled(true);
                            mButtonRemove.setEnabled(true);
                            mEditTextApiKey.setEnabled(true);
                            if (throwable != null) {
                                mEditTextApiKey.setError(mSvProvider.getContext()
                                        .getString(R.string.error_invalid_api_key));
                                mSvProvider.get(ILogger.class).e(TAG,
                                        mSvProvider.getContext()
                                                .getString(R.string.error_invalid_api_key),
                                        throwable);
                            } else {
                                try {
                                    mApiKeyManager.saveApiKey(apiKey);
                                    mGeminiService.resetClient();
                                    mApiKeyChangeNotifier.onApiKeyChanged(true);
                                    getNavigator().pop();
                                } catch (Exception e) {
                                    mSvProvider.get(ILogger.class).e(TAG,
                                            mSvProvider.getContext()
                                                    .getString(R.string.error_saving_api_key), e);
                                }
                            }
                        }));
    }
}
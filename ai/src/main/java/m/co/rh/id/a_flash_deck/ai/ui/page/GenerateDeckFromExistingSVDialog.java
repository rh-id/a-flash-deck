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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromExistingCmd;
import m.co.rh.id.a_flash_deck.ai.model.AvailableModel;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class GenerateDeckFromExistingSVDialog extends StatefulViewDialog<Activity>
        implements View.OnClickListener {

    private static final String TAG = GenerateDeckFromExistingSVDialog.class.getName();

    public static class Args implements Serializable {
        public ArrayList<Long> mSelectedDeckIds;

        public static Args with(ArrayList<Long> selectedDeckIds) {
            Args args = new Args();
            args.mSelectedDeckIds = selectedDeckIds;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute == null || navRoute.getRouteArgs() == null) {
                return null;
            }
            return Args.of((Serializable) navRoute.getRouteArgs());
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }
    }

    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient GenerateDeckFromExistingCmd mGenerateCmd;
    private transient GeminiService mGeminiService;
    private transient ApiKeyManager mApiKeyManager;
    private transient DeckDao mDeckDao;
    private transient List<AvailableModel> mAvailableModels;
    private transient ArrayList<Long> mSelectedDeckIds;
    private transient MaterialAutoCompleteTextView mEditTextPrompt;
    private transient EditText mEditTextMaxCards;
    private transient TextView mTextSelectedModel;
    private transient TextView mTextSummary;
    private transient TextView mTextDeckNames;

    public GenerateDeckFromExistingSVDialog() {
        super(null);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mSelectedDeckIds = args.mSelectedDeckIds;
        }

        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        mGenerateCmd = mSvProvider.get(GenerateDeckFromExistingCmd.class);
        mGeminiService = mSvProvider.get(GeminiService.class);
        mApiKeyManager = mSvProvider.get(ApiKeyManager.class);
        mDeckDao = mSvProvider.get(DeckDao.class);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_generate_deck_from_existing, container, false);

        mTextSummary = view.findViewById(R.id.text_summary);
        mTextDeckNames = view.findViewById(R.id.text_deck_names);
        mTextSelectedModel = view.findViewById(R.id.text_selected_model);
        mEditTextPrompt = view.findViewById(R.id.edit_text_prompt);
        mEditTextMaxCards = view.findViewById(R.id.edit_text_max_cards);
        Button buttonSelectModel = view.findViewById(R.id.button_select_model);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        Button buttonGenerate = view.findViewById(R.id.button_generate);
        TextView textValidation = view.findViewById(R.id.text_validation);

        mEditTextPrompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String prompt = s.toString().trim();
                int maxCards = getMaxCards(mEditTextMaxCards);
                mGenerateCmd.valid(prompt, maxCards);
            }
        });

        mEditTextMaxCards.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String prompt = mEditTextPrompt.getText().toString().trim();
                int maxCards = getMaxCards(mEditTextMaxCards);
                mGenerateCmd.valid(prompt, maxCards);
            }
        });

        mTextSelectedModel.setText(mSvProvider.getContext()
                .getString(R.string.current_model, mApiKeyManager.getSelectedModel()));

        String[] suggestions = mSvProvider.getContext().getResources().getStringArray(R.array.ai_prompt_suggestions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mSvProvider.getContext(),
                android.R.layout.simple_dropdown_item_1line, suggestions);
        mEditTextPrompt.setAdapter(adapter);

        buttonSelectModel.setOnClickListener(v -> showModelSelectionDialog());
        buttonCancel.setOnClickListener(this);
        buttonGenerate.setOnClickListener(this);

        mSvProvider.get(RxDisposer.class).add("validation",
                mGenerateCmd.getPromptValidation()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (s.isEmpty()) {
                                textValidation.setVisibility(View.GONE);
                            } else {
                                textValidation.setVisibility(View.VISIBLE);
                                textValidation.setText(s);
                            }
                        }));

        loadDeckData();

        fetchModels();

        return view;
    }

    private void loadDeckData() {
        if (mSelectedDeckIds == null || mSelectedDeckIds.isEmpty()) {
            return;
        }

        mSvProvider.get(RxDisposer.class).add("loadDeckData",
                Single.fromCallable(() -> {
                    List<Deck> decks = mDeckDao.findDeckByIds(mSelectedDeckIds);
                    int totalCards = 0;
                    StringBuilder deckNames = new StringBuilder();
                    for (int i = 0; i < decks.size(); i++) {
                        Deck deck = decks.get(i);
                        int cardCount = mDeckDao.countCardByDeckId(deck.id);
                        totalCards += cardCount;
                        if (i > 0) {
                            deckNames.append(", ");
                        }
                        deckNames.append(deck.name).append(" (").append(cardCount).append(")");
                    }
                    return new DeckData(decks.size(), totalCards, deckNames.toString());
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deckData -> {
                            mTextSummary.setText(mSvProvider.getContext()
                                    .getString(R.string.summary_selected_decks, deckData.deckCount, deckData.totalCards));
                            mTextDeckNames.setText(deckData.deckNames);
                            mEditTextMaxCards.setText(String.valueOf(deckData.totalCards));
                        }, throwable -> {
                            mSvProvider.get(ILogger.class).e(TAG, "Failed to load deck data", throwable);
                        }));
    }

    private static class DeckData {
        int deckCount;
        int totalCards;
        String deckNames;

        DeckData(int deckCount, int totalCards, String deckNames) {
            this.deckCount = deckCount;
            this.totalCards = totalCards;
            this.deckNames = deckNames;
        }
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

    private void showModelSelectionDialog() {
        if (mAvailableModels == null || mAvailableModels.isEmpty()) {
            mSvProvider.get(RxDisposer.class).add("fetchModelsForDialog",
                    mGeminiService.fetchAvailableModels()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((models, throwable) -> {
                                if (throwable == null && models != null && !models.isEmpty()) {
                                    mAvailableModels = models;
                                    showModelSelectionDialog();
                                }
                            }));
            return;
        }

        Activity activity = (Activity) mTextSelectedModel.getContext();
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
                    mTextSelectedModel.setText(mSvProvider.getContext()
                            .getString(R.string.current_model, selected.id));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int getMaxCards(EditText editTextMaxCards) {
        try {
            return Integer.parseInt(editTextMaxCards.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 10;
        }
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
        mGenerateCmd = null;
        mGeminiService = null;
        mApiKeyManager = null;
        mDeckDao = null;
        mAvailableModels = null;
        mSelectedDeckIds = null;
        mEditTextPrompt = null;
        mEditTextMaxCards = null;
        mTextSelectedModel = null;
        mTextSummary = null;
        mTextDeckNames = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_generate) {
            generateDeck();
        } else if (id == R.id.button_cancel) {
            getNavigator().pop();
        }
    }

    private void generateDeck() {
        String prompt = mEditTextPrompt.getText().toString().trim();
        int maxCards = getMaxCards(mEditTextMaxCards);
        if (!mGenerateCmd.valid(prompt, maxCards)) return;
        String modelId = mApiKeyManager.getSelectedModel();
        mGenerateCmd.execute(mSelectedDeckIds, prompt, maxCards, modelId);
        mSvProvider.get(ILogger.class).i(TAG,
                mSvProvider.getContext().getString(R.string.ai_generation_from_existing_started));
        getNavigator().pop();
    }
}
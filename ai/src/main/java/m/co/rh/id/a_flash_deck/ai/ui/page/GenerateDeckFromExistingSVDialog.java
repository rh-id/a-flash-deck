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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromExistingCmd;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;

public class GenerateDeckFromExistingSVDialog extends BaseGenerateDeckSVDialog {

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

    private transient GenerateDeckFromExistingCmd mGenerateCmd;
    private transient DeckDao mDeckDao;
    private transient ArrayList<Long> mSelectedDeckIds;
    private transient MaterialAutoCompleteTextView mEditTextPrompt;
    private transient EditText mEditTextMaxCards;
    private transient TextView mTextSummary;
    private transient TextView mTextDeckNames;

    public GenerateDeckFromExistingSVDialog() {
        super();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mSelectedDeckIds = args.mSelectedDeckIds;
        }

        initProviders();
        mGenerateCmd = mSvProvider.get(GenerateDeckFromExistingCmd.class);
        mDeckDao = mSvProvider.get(DeckDao.class);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_generate_deck_from_existing, container, false);

        mTextSummary = view.findViewById(R.id.text_summary);
        mTextDeckNames = view.findViewById(R.id.text_deck_names);
        mEditTextPrompt = view.findViewById(R.id.edit_text_prompt);
        mEditTextMaxCards = view.findViewById(R.id.edit_text_max_cards);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        Button buttonGenerate = view.findViewById(R.id.button_generate);

        mEditTextPrompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String prompt = s.toString().trim();
                int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
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
                int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
                mGenerateCmd.valid(prompt, maxCards);
            }
        });

        initModelSelection(view);

        String[] suggestions = mSvProvider.getContext().getResources().getStringArray(R.array.ai_prompt_suggestions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mSvProvider.getContext(),
                android.R.layout.simple_dropdown_item_1line, suggestions);
        mEditTextPrompt.setAdapter(adapter);

        buttonCancel.setOnClickListener(this);
        buttonGenerate.setOnClickListener(this);

        setupValidationObserver(view.findViewById(R.id.text_validation), mGenerateCmd.getPromptValidation());

        loadDeckData();

        fetchModels();

        return view;
    }

    private void loadDeckData() {
        if (mSelectedDeckIds == null || mSelectedDeckIds.isEmpty()) {
            return;
        }

        mSvProvider.get(m.co.rh.id.a_flash_deck.base.rx.RxDisposer.class).add("loadDeckData",
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
                        .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
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

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        disposeBase();
        mGenerateCmd = null;
        mDeckDao = null;
        mSelectedDeckIds = null;
        mEditTextPrompt = null;
        mEditTextMaxCards = null;
        mTextSummary = null;
        mTextDeckNames = null;
    }

    @Override
    protected void generateDeck() {
        String prompt = mEditTextPrompt.getText().toString().trim();
        int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
        if (!mGenerateCmd.valid(prompt, maxCards)) return;
        String modelId = mApiKeyManager.getSelectedModel();
        mGenerateCmd.execute(mSelectedDeckIds, prompt, maxCards, modelId);
        mSvProvider.get(ILogger.class).i(TAG,
                mSvProvider.getContext().getString(R.string.ai_generation_from_existing_started));
        getNavigator().pop();
    }
}
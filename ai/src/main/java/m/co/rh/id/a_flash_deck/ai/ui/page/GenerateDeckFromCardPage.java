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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromCardCmd;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;

public class GenerateDeckFromCardPage extends BaseGenerateDeckPage {

    private static final String TAG = GenerateDeckFromCardPage.class.getName();

    public static class Args implements Serializable {
        public Long mCardId;

        public static Args with(Long cardId) {
            Args args = new Args();
            args.mCardId = cardId;
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

    private transient GenerateDeckFromCardCmd mGenerateCmd;
    private transient DeckDao mDeckDao;
    private transient CardDao mCardDao;
    private transient MarkdownRenderer mMarkdownRenderer;
    private transient Long mCardId;
    private transient MaterialAutoCompleteTextView mEditTextPrompt;
    private transient EditText mEditTextMaxCards;
    private transient TextView mTextDeckName;
    private transient TextView mTextCardQuestion;
    private transient TextView mTextCardAnswer;

    public GenerateDeckFromCardPage() {
        super();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mCardId = args.mCardId;
        }

        initProviders();
        mGenerateCmd = mSvProvider.get(GenerateDeckFromCardCmd.class);
        mDeckDao = mSvProvider.get(DeckDao.class);
        mCardDao = mSvProvider.get(CardDao.class);
        mMarkdownRenderer = mSvProvider.get(MarkdownRenderer.class);

        View view = activity.getLayoutInflater().inflate(R.layout.page_generate_deck_from_card, container, false);

        mAppBarSV.setTitle(mSvProvider.getContext().getString(R.string.title_generate_deck_from_card));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));

        mTextDeckName = view.findViewById(R.id.text_deck_name);
        mTextCardQuestion = view.findViewById(R.id.text_card_question);
        mTextCardAnswer = view.findViewById(R.id.text_card_answer);
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

        String[] suggestions = mSvProvider.getContext().getResources().getStringArray(R.array.ai_card_prompt_suggestions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mSvProvider.getContext(),
                android.R.layout.simple_dropdown_item_1line, suggestions);
        mEditTextPrompt.setAdapter(adapter);

        buttonCancel.setOnClickListener(this);
        buttonGenerate.setOnClickListener(this);

        setupValidationObserver(view.findViewById(R.id.text_validation), mGenerateCmd.getPromptValidation());

        loadCardData();

        fetchModels();

        return view;
    }

    private void loadCardData() {
        mSvProvider.get(RxDisposer.class).add("loadCardData",
                Single.fromCallable(() -> {
                    Card card = mCardDao.getCardByCardId(mCardId);
                    Deck deck = null;
                    String question = "";
                    String answer = "";
                    String deckName = "";
                    if (card != null) {
                        deck = mDeckDao.getDeckById(card.deckId);
                        question = mMarkdownRenderer.toPlainText(card.question);
                        answer = mMarkdownRenderer.toPlainText(card.answer);
                        if (deck != null) {
                            deckName = deck.name;
                        }
                    }
                    return new CardData(deckName, question, answer, card);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(cardData -> {
                            if (cardData.card == null) {
                                mSvProvider.get(ILogger.class).e(TAG, "Failed to load card: card not found");
                                return;
                            }
                            mTextDeckName.setText(cardData.deckName);
                            mTextCardQuestion.setText(mSvProvider.getContext()
                                    .getString(R.string.ai_card_preview_question, cardData.question));
                            mTextCardAnswer.setText(mSvProvider.getContext()
                                    .getString(R.string.ai_card_preview_answer, cardData.answer));
                        }, throwable -> {
                            mSvProvider.get(ILogger.class).e(TAG, "Failed to load card data", throwable);
                        }));
    }

    private static class CardData {
        String deckName;
        String question;
        String answer;
        Card card;

        CardData(String deckName, String question, String answer, Card card) {
            this.deckName = deckName;
            this.question = question;
            this.answer = answer;
            this.card = card;
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mGenerateCmd = null;
        mDeckDao = null;
        mCardDao = null;
        mMarkdownRenderer = null;
        mCardId = null;
        mEditTextPrompt = null;
        mEditTextMaxCards = null;
        mTextDeckName = null;
        mTextCardQuestion = null;
        mTextCardAnswer = null;
    }

    @Override
    protected void generateDeck() {
        String prompt = mEditTextPrompt.getText().toString().trim();
        int maxCards = parseEditTextInt(mEditTextMaxCards, 10);
        if (!mGenerateCmd.valid(prompt, maxCards)) return;
        String modelId = mApiKeyManager.getSelectedModel();
        mGenerateCmd.execute(mCardId, prompt, maxCards, modelId);
        mSvProvider.get(ILogger.class).i(TAG,
                mSvProvider.getContext().getString(R.string.ai_generation_from_card_started));
        mNavigator.pop();
    }
}

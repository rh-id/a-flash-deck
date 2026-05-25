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
import android.widget.Button;
import android.widget.EditText;

import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromTopicCmd;
import m.co.rh.id.alogger.ILogger;

public class GenerateDeckFromTopicSVDialog extends BaseGenerateDeckSVDialog {

    private static final String TAG = GenerateDeckFromTopicSVDialog.class.getName();

    private transient GenerateDeckFromTopicCmd mGenerateCmd;
    private transient EditText mEditTextTopic;
    private transient EditText mEditTextCardCount;

    public GenerateDeckFromTopicSVDialog() {
        super();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        initProviders();
        mGenerateCmd = mSvProvider.get(GenerateDeckFromTopicCmd.class);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_generate_deck, container, false);

        mEditTextTopic = view.findViewById(R.id.edit_text_topic);
        mEditTextCardCount = view.findViewById(R.id.edit_text_card_count);
        Button buttonCancel = view.findViewById(R.id.button_cancel);
        Button buttonGenerate = view.findViewById(R.id.button_generate);

        mEditTextTopic.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String topic = s.toString().trim();
                int cardCount = parseEditTextInt(mEditTextCardCount, 10);
                mGenerateCmd.valid(topic, cardCount);
            }
        });

        initModelSelection(view);

        buttonCancel.setOnClickListener(this);
        buttonGenerate.setOnClickListener(this);

        setupValidationObserver(view.findViewById(R.id.text_validation), mGenerateCmd.getTopicValidation());

        fetchModels();

        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        disposeBase();
        mGenerateCmd = null;
        mEditTextTopic = null;
        mEditTextCardCount = null;
    }

    @Override
    protected void generateDeck() {
        String topic = mEditTextTopic.getText().toString().trim();
        int cardCount = parseEditTextInt(mEditTextCardCount, 10);
        if (!mGenerateCmd.valid(topic, cardCount)) return;
        String modelId = mApiKeyManager.getSelectedModel();
        mGenerateCmd.execute(topic, cardCount, modelId);
        mSvProvider.get(ILogger.class).i(TAG,
                mSvProvider.getContext().getString(R.string.ai_generation_started, topic));
        getNavigator().pop();
    }
}
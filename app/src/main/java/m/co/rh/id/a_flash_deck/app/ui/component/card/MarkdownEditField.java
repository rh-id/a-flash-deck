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

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;

/**
 * Plain Java class that manages one editable/rendered text field pair.
 * Handles the editing, rendering, and focus management for markdown fields.
 */
public class MarkdownEditField {
    private static final String TAG = MarkdownEditField.class.getName();

    private final MarkdownRenderer mMarkdownRenderer;
    private final RxDisposer mRxDisposer;
    private final ILogger mLogger;
    private final String mFieldName;

    private EditText mEditText;
    private TextView mRenderedTextView;
    private TextWatcher mTextWatcher;
    private Consumer<String> mOnTextChangedListener;
    private int mPlaceholderRes;
    private String mCurrentText;

    /**
     * Constructor for MarkdownEditField
     *
     * @param markdownRenderer The markdown renderer service
     * @param rxDisposer      The RxJava disposer for managing subscriptions
     * @param logger          The logger service
     * @param fieldName       The field name for creating unique RxDisposer tags
     */
    public MarkdownEditField(MarkdownRenderer markdownRenderer, RxDisposer rxDisposer, ILogger logger, String fieldName) {
        mMarkdownRenderer = markdownRenderer;
        mRxDisposer = rxDisposer;
        mLogger = logger;
        mFieldName = fieldName;
    }

    /**
     * Binds this field to specific UI components
     *
     * @param editText          The EditText for editing raw markdown
     * @param renderedTextView  The TextView for displaying rendered markdown
     * @param placeholderRes    String resource for placeholder when text is empty
     * @param initialText       The initial text content
     * @param onTextChangedListener Callback when text changes (Consumer<String>)
     */
    public void bind(EditText editText, TextView renderedTextView, int placeholderRes,
                     String initialText, Consumer<String> onTextChangedListener) {
        mEditText = editText;
        mRenderedTextView = renderedTextView;
        mPlaceholderRes = placeholderRes;
        mCurrentText = initialText != null ? initialText : "";
        mOnTextChangedListener = onTextChangedListener;

        initTextWatcher();
    }

    /**
     * Initialize the text watcher
     */
    private void initTextWatcher() {
        if (mTextWatcher == null) {
            mTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mCurrentText = editable.toString();
                    if (mOnTextChangedListener != null) {
                        mOnTextChangedListener.accept(mCurrentText);
                    }
                }
            };
        }
        if (mEditText != null) {
            mEditText.addTextChangedListener(mTextWatcher);
        }
    }

    /**
     * Set the text content of this field, removing and re-adding the text watcher
     * to avoid triggering the change callback
     *
     * @param text The new text content
     */
    public void setText(String text) {
        if (mEditText != null && mTextWatcher != null) {
            mEditText.removeTextChangedListener(mTextWatcher);
            mEditText.setText(text);
            mCurrentText = text != null ? text : "";
            mEditText.addTextChangedListener(mTextWatcher);
        }
    }

    /**
     * Render the current text content to the rendered TextView asynchronously
     */
    public void render() {
        if (mRenderedTextView == null) {
            return;
        }

        if (mCurrentText == null || mCurrentText.isEmpty()) {
            mRenderedTextView.setText(mPlaceholderRes);
            return;
        }

        mRxDisposer.add("render_" + mFieldName,
                mMarkdownRenderer.parseAsync(mCurrentText)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(spanned ->
                                        mMarkdownRenderer.applyParsedMarkdown(mRenderedTextView, spanned),
                                throwable -> mLogger.e(TAG, "render field failed", throwable)));
    }

    /**
     * Switch to editable mode: hide rendered view, focus EditText, show keyboard
     *
     * @param rawSource The raw markdown source to edit
     */
    public void switchToEditable(String rawSource) {
        if (mRenderedTextView == null || mEditText == null || mTextWatcher == null) {
            return;
        }

        mRenderedTextView.setVisibility(android.view.View.GONE);
        mEditText.removeTextChangedListener(mTextWatcher);
        mEditText.setText(rawSource);
        mEditText.requestFocus();
        mEditText.addTextChangedListener(mTextWatcher);

        InputMethodManager imm = (InputMethodManager) mEditText.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Switch to rendered mode: hide keyboard, clear focus, show rendered view
     */
    public void switchToRendered() {
        if (mRenderedTextView == null || mEditText == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) mEditText.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
        }

        mEditText.clearFocus();
        mRenderedTextView.setVisibility(android.view.View.VISIBLE);
        render();
    }

    /**
     * Set error message on the EditText
     *
     * @param error The error message, or null to clear error
     */
    public void setError(String error) {
        if (mEditText != null) {
            mEditText.setError(error);
        }
    }

    /**
     * Clear error from the EditText
     */
    public void clearError() {
        if (mEditText != null) {
            mEditText.setError(null);
        }
    }

    /**
     * Dispose resources
     */
    public void dispose() {
        mTextWatcher = null;
        mEditText = null;
        mRenderedTextView = null;
        mOnTextChangedListener = null;
    }
}
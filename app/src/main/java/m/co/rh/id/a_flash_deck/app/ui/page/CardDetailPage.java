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

package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.SuspendCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeckQueryCmd;
import m.co.rh.id.a_flash_deck.app.ui.component.card.CardMediaField;
import m.co.rh.id.a_flash_deck.app.ui.component.card.MarkdownEditField;
import m.co.rh.id.a_flash_deck.base.component.AudioService;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class CardDetailPage extends StatefulView<Activity> implements RequireNavRoute, RequireComponent<Provider>, NavOnActivityResult, Toolbar.OnMenuItemClickListener, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private transient NavRoute mNavRoute;
    private Card mCard;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;
    private transient CardMediaStore mCardMediaStore;
    private transient AudioService mAudioService;
    private transient CommonNavConfig mCommonNavConfig;
    private transient NewCardCmd mNewCardCmd;
    private transient DeckQueryCmd mDeckQueryCmd;
    private transient SuspendCardCmd mSuspendCardCmd;
    private transient MaterialCheckBox mSuspendCheckBox;
    private transient boolean mSuspended;
    private transient boolean mOriginalSuspended;
    private transient MarkdownRenderer mMarkdownRenderer;
    // Markdown fields are serializable pure state holders, kept non-transient
    // so unsaved markdown text survives process death
    private MarkdownEditField mQuestionField;
    private MarkdownEditField mAnswerField;
    private transient TextWatcher mQuestionTextWatcher;
    private transient TextWatcher mAnswerTextWatcher;
    private transient Disposable mQuestionRenderDisposable;
    private transient Disposable mAnswerRenderDisposable;
    // Media fields are serializable pure state holders, kept non-transient
    // so attached-but-unsaved media survives process death
    private CardMediaField mQuestionImageField;
    private CardMediaField mAnswerImageField;
    private CardMediaField mQuestionVoiceField;
    private CardMediaField mAnswerVoiceField;
    private transient MaterialCheckBox mReversibleCheckBox;
    private transient EditText mEditTextQuestion;
    private transient EditText mEditTextAnswer;
    private transient TextView mTextRenderedQuestion;
    private transient TextView mTextRenderedAnswer;
    private transient ViewGroup mContainerImageQuestion;
    private transient ViewGroup mContainerImageAnswer;
    private transient ImageView mQuestionImageView;
    private transient ImageView mAnswerImageView;
    private transient ViewGroup mVoiceQuestionContainer;
    private transient ViewGroup mVoiceAnswerContainer;
    private transient Button mButtonSaveAndAdd;

    public CardDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_card_detail);
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        mCardMediaStore = mSvProvider.get(CardMediaStore.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mAudioService = mSvProvider.get(AudioService.class);
        // query command, used by the update flow's review-state subscription
        mDeckQueryCmd = mSvProvider.get(DeckQueryCmd.class);
        if (isUpdate()) {
            mNewCardCmd = mSvProvider.get(UpdateCardCmd.class);
            mSuspendCardCmd = mSvProvider.get(SuspendCardCmd.class);
        } else {
            mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        }
        // Create or recreate the component instances
        mMarkdownRenderer = mSvProvider.get(MarkdownRenderer.class);

        // Initialize markdown fields (non-transient: a field restored from
        // serialized state keeps its unsaved markdown text, no manual
        // restore needed)
        boolean markdownFieldsRestored = mQuestionField != null;
        if (mQuestionField == null) {
            mQuestionField = MarkdownEditField.forQuestion();
        }
        if (mAnswerField == null) {
            mAnswerField = MarkdownEditField.forAnswer();
        }
        // Initialize media fields (non-transient: a field restored from
        // serialized state keeps its attached-but-unsaved file and its
        // pending camera temp file, no manual restore needed)
        boolean mediaFieldsRestored = mQuestionImageField != null;
        if (mQuestionImageField == null) {
            mQuestionImageField = CardMediaField.forQuestionImage();
        }
        if (mAnswerImageField == null) {
            mAnswerImageField = CardMediaField.forAnswerImage();
        }
        if (mQuestionVoiceField == null) {
            mQuestionVoiceField = CardMediaField.forQuestionVoice();
        }
        if (mAnswerVoiceField == null) {
            mAnswerVoiceField = CardMediaField.forAnswerVoice();
        }

        if (mCard == null) {
            mCard = new Card();
            mCard.question = "";
            mCard.answer = "";
            Args args = getArgs();
            if (args != null) {
                if (args.isUpdate()) {
                    mCard = args.mCard;
                } else {
                    mCard.deckId = args.mDeck.id;
                }
            }
        }

        // Initialize media field values from mCard. A freshly created field
        // (first navigation) starts from the saved card in edit mode and
        // stays empty for a new card; a restored field keeps its serialized
        // state so re-running provideComponent never clobbers unsaved media
        // edits after process death.
        if (!mediaFieldsRestored) {
            if (mCard.questionImage != null && !mCard.questionImage.isEmpty()) {
                mQuestionImageField.setFile(mCardMediaStore.getCardQuestionImage(mCard.questionImage));
            }
            if (mCard.answerImage != null && !mCard.answerImage.isEmpty()) {
                mAnswerImageField.setFile(mCardMediaStore.getCardAnswerImage(mCard.answerImage));
            }
            if (mCard.questionVoice != null && !mCard.questionVoice.isEmpty()) {
                mQuestionVoiceField.setFile(mCardMediaStore.getCardQuestionVoice(mCard.questionVoice));
            }
            if (mCard.answerVoice != null && !mCard.answerVoice.isEmpty()) {
                mAnswerVoiceField.setFile(mCardMediaStore.getCardAnswerVoice(mCard.answerVoice));
            }
        }

        // Seed the markdown field state from the card, mCard is resolved
        // above so this covers both the new-card and the update flow; a
        // restored field keeps its serialized unsaved text instead
        if (!markdownFieldsRestored) {
            mQuestionField.setText(mCard.question);
            mAnswerField.setText(mCard.answer);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(
                        R.layout.page_card_detail, container, false);
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_card));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_new_card));
        }
        mAppBarSV.setMenuItemClick(this);
        mContainerImageQuestion = rootLayout.findViewById(R.id.container_image_question);
        mContainerImageAnswer = rootLayout.findViewById(R.id.container_image_answer);
        mQuestionImageView = rootLayout.findViewById(R.id.image_question);
        mQuestionImageView.setOnClickListener(this);
        mAnswerImageView = rootLayout.findViewById(R.id.image_answer);
        mAnswerImageView.setOnClickListener(this);
        Button questionDeleteImageButton = rootLayout.findViewById(R.id.button_question_delete_image);
        questionDeleteImageButton.setOnClickListener(this);
        Button answerDeleteImageButton = rootLayout.findViewById(R.id.button_answer_delete_image);
        answerDeleteImageButton.setOnClickListener(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        mButtonSaveAndAdd = rootLayout.findViewById(R.id.button_save_and_add);
        mButtonSaveAndAdd.setOnClickListener(this);
        if (isUpdate()) {
            mButtonSaveAndAdd.setVisibility(View.GONE);
        }
        Button questionMoreActionButton = rootLayout.findViewById(R.id.button_question_more_action);
        questionMoreActionButton.setOnClickListener(this);
        Button answerMoreActionButton = rootLayout.findViewById(R.id.button_answer_more_action);
        answerMoreActionButton.setOnClickListener(this);
        Button questionVoiceButton = rootLayout.findViewById(R.id.button_question_voice);
        questionVoiceButton.setOnClickListener(this);
        Button questionDeleteVoiceButton = rootLayout.findViewById(R.id.button_question_delete_voice);
        questionDeleteVoiceButton.setOnClickListener(this);
        mVoiceQuestionContainer = rootLayout.findViewById(R.id.container_voice_question);
        Button answerVoiceButton = rootLayout.findViewById(R.id.button_answer_voice);
        answerVoiceButton.setOnClickListener(this);
        Button answerDeleteVoiceButton = rootLayout.findViewById(R.id.button_answer_delete_voice);
        answerDeleteVoiceButton.setOnClickListener(this);
        mVoiceAnswerContainer = rootLayout.findViewById(R.id.container_voice_answer);
        mEditTextQuestion = rootLayout.findViewById(R.id.text_input_edit_question);
        mEditTextAnswer = rootLayout.findViewById(R.id.text_input_edit_answer);
        mTextRenderedQuestion = rootLayout.findViewById(R.id.text_rendered_question);
        mTextRenderedAnswer = rootLayout.findViewById(R.id.text_rendered_answer);
        mTextRenderedQuestion.setOnClickListener(this);
        mTextRenderedAnswer.setOnClickListener(this);
        mReversibleCheckBox = rootLayout.findViewById(R.id.checkbox_reversible);
        mSuspendCheckBox = rootLayout.findViewById(R.id.checkbox_suspended);
        // suspend only makes sense for an existing card
        mSuspendCheckBox.setVisibility(isUpdate() ? View.VISIBLE : View.GONE);
        // enabled once the current suspend state is loaded - a save before that could mis-diff the flag
        mSuspendCheckBox.setEnabled(false);
        mSuspendCheckBox.setOnCheckedChangeListener(this::onSuspendCheckedChange);

        // Set initial text BEFORE binding watchers to avoid spurious initial validation
        if (mCard != null) {
            mEditTextQuestion.setText(mCard.question);
            mEditTextAnswer.setText(mCard.answer);
            mReversibleCheckBox.setChecked(mCard.isReversibleQA);
        }

        // Bind markdown fields (attaches watchers after initial setText)
        mQuestionTextWatcher = initMarkdownTextWatcher(mQuestionField, text -> {
            mCard.question = text;
            mNewCardCmd.valid(mCard);
        });
        bindMarkdownField(mQuestionField, mEditTextQuestion, mQuestionTextWatcher);
        mAnswerTextWatcher = initMarkdownTextWatcher(mAnswerField, text -> {
            mCard.answer = text;
            mNewCardCmd.valid(mCard);
        });
        bindMarkdownField(mAnswerField, mEditTextAnswer, mAnswerTextWatcher);

        // When focus leaves an EditText, switch that field back to rendered mode
        mEditTextQuestion.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && mTextRenderedQuestion.getVisibility() != View.VISIBLE) {
                switchMarkdownToRendered(mQuestionField, mEditTextQuestion, mTextRenderedQuestion);
            }
        });
        mEditTextAnswer.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && mTextRenderedAnswer.getVisibility() != View.VISIBLE) {
                switchMarkdownToRendered(mAnswerField, mEditTextAnswer, mTextRenderedAnswer);
            }
        });

        // Render the question/answer
        renderMarkdown(mQuestionField, mTextRenderedQuestion);
        renderMarkdown(mAnswerField, mTextRenderedAnswer);

        mReversibleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> mCard.isReversibleQA = isChecked);

        // Load the card's suspend state (review-state rows are created lazily,
        // so an absent row simply means a new/never-studied, not suspended card)
        if (isUpdate()) {
            mRxDisposer.add("createView_reviewState",
                    mDeckQueryCmd.getReviewStateByCardId(mCard.id)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(reviewStateOptional -> {
                                if (mSuspendCheckBox != null) {
                                    updateSuspendedState(reviewStateOptional.isPresent()
                                            && reviewStateOptional.get().suspended);
                                }
                            },
                                    throwable -> {
                                        if (mSvProvider != null) {
                                            mLogger.e(TAG,
                                                    mSvProvider.getContext()
                                                            .getString(R.string.error_loading_deck), throwable);
                                        }
                                    }));
        }

        // Setup validation subscriptions
        mRxDisposer
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (mEditTextQuestion != null) {
                                if (!s.isEmpty()) {
                                    mEditTextQuestion.setError(s);
                                } else {
                                    mEditTextQuestion.setError(null);
                                }
                            }
                        }));
        mRxDisposer
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (mEditTextAnswer != null) {
                                if (!s.isEmpty()) {
                                    mEditTextAnswer.setError(s);
                                } else {
                                    mEditTextAnswer.setError(null);
                                }
                            }
                        }));

        // Media fields are plain state holders (no subject to subscribe):
        // sync the UI from their current values and refresh it explicitly
        // at every mutation site
        updateQuestionImageUi();
        updateAnswerImageUi();
        updateQuestionVoiceUi();
        updateAnswerVoiceUi();
        return rootLayout;
    }

    /**
     * Update the question image UI from the current field value
     */
    private void updateQuestionImageUi() {
        if (mQuestionImageView == null || mContainerImageQuestion == null) {
            return;
        }
        Optional<File> fileOpt = mQuestionImageField.getValue();
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            mQuestionImageView.setImageURI(Uri.fromFile(file));
            mContainerImageQuestion.setVisibility(View.VISIBLE);
        } else {
            mQuestionImageView.setImageURI(null);
            mContainerImageQuestion.setVisibility(View.GONE);
        }
    }

    /**
     * Update the answer image UI from the current field value
     */
    private void updateAnswerImageUi() {
        if (mAnswerImageView == null || mContainerImageAnswer == null) {
            return;
        }
        Optional<File> fileOpt = mAnswerImageField.getValue();
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            mAnswerImageView.setImageURI(Uri.fromFile(file));
            mContainerImageAnswer.setVisibility(View.VISIBLE);
        } else {
            mAnswerImageView.setImageURI(null);
            mContainerImageAnswer.setVisibility(View.GONE);
        }
    }

    /**
     * Update the question voice UI from the current field value
     */
    private void updateQuestionVoiceUi() {
        if (mVoiceQuestionContainer == null) {
            return;
        }
        if (mQuestionVoiceField.getValue().isPresent()) {
            mVoiceQuestionContainer.setVisibility(View.VISIBLE);
        } else {
            mVoiceQuestionContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Update the answer voice UI from the current field value
     */
    private void updateAnswerVoiceUi() {
        if (mVoiceAnswerContainer == null) {
            return;
        }
        if (mAnswerVoiceField.getValue().isPresent()) {
            mVoiceAnswerContainer.setVisibility(View.VISIBLE);
        } else {
            mVoiceAnswerContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Initialize a text watcher that mirrors edits into the given field state,
     * then runs the given change callback
     *
     * @param field         The markdown field to keep in sync
     * @param onTextChanged Callback when text changes (Consumer&lt;String&gt;)
     * @return The created text watcher
     */
    private TextWatcher initMarkdownTextWatcher(MarkdownEditField field, Consumer<String> onTextChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String text = editable.toString();
                field.setText(text);
                onTextChanged.accept(text);
            }
        };
    }

    /**
     * Bind a markdown field to its EditText: seed the field state from the
     * current EditText text, then attach the given watcher. The initial text
     * must be set on the EditText BEFORE this call to avoid a spurious change
     * callback
     *
     * @param field       The markdown field to bind
     * @param editText    The EditText for editing raw markdown
     * @param textWatcher The watcher to attach
     */
    private void bindMarkdownField(MarkdownEditField field, EditText editText, TextWatcher textWatcher) {
        field.setText(editText.getText().toString());
        editText.addTextChangedListener(textWatcher);
    }

    /**
     * Get the placeholder resource shown when the given field has no text
     *
     * @param field The markdown field to render
     * @return The placeholder string resource
     */
    private int getMarkdownPlaceholderRes(MarkdownEditField field) {
        return "question".equals(field.getFieldName())
                ? R.string.tap_to_edit_question : R.string.tap_to_edit_answer;
    }

    /**
     * Render the current text content of the given field to the given rendered
     * TextView asynchronously. The target TextView is captured into a local
     * before subscribing because delivery may land after teardown nulled the
     * page view refs; rendering into a detached view is harmless, a null one
     * crashes inside Markwon
     *
     * @param field            The markdown field to render
     * @param renderedTextView The TextView for displaying rendered markdown
     */
    private void renderMarkdown(MarkdownEditField field, TextView renderedTextView) {
        if (renderedTextView == null) {
            return;
        }

        if (field.getText().isEmpty()) {
            renderedTextView.setText(getMarkdownPlaceholderRes(field));
            return;
        }

        final TextView targetTextView = renderedTextView;
        Disposable disposable = mMarkdownRenderer.parseAsync(field.getText())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(spanned ->
                                mMarkdownRenderer.applyParsedMarkdown(targetTextView, spanned),
                        throwable -> mLogger.e(TAG, "render field failed", throwable));
        mRxDisposer.add("render_" + field.getFieldName(), disposable);
        // tracking is keyed by the field name so a fresh instance still routes to the right disposable
        if ("question".equals(field.getFieldName())) {
            mQuestionRenderDisposable = disposable;
        } else {
            mAnswerRenderDisposable = disposable;
        }
    }

    /**
     * Set the text content of the given field, removing and re-adding the
     * text watcher to avoid triggering the change callback
     *
     * @param field       The markdown field to update
     * @param editText    The EditText for editing raw markdown
     * @param textWatcher The watcher to detach during the programmatic setText
     * @param text        The new text content
     */
    private void setMarkdownText(MarkdownEditField field, EditText editText,
                                 TextWatcher textWatcher, String text) {
        if (editText != null && textWatcher != null) {
            editText.removeTextChangedListener(textWatcher);
            editText.setText(text);
            field.setText(text);
            editText.addTextChangedListener(textWatcher);
        }
    }

    /**
     * Switch the given field to editable mode: hide rendered view, focus
     * EditText, show keyboard
     *
     * @param field            The markdown field to edit
     * @param editText         The EditText for editing raw markdown
     * @param renderedTextView The TextView for displaying rendered markdown
     * @param textWatcher      The watcher to detach during the programmatic setText
     * @param rawSource        The raw markdown source to edit
     */
    private void switchMarkdownToEditable(MarkdownEditField field, EditText editText,
                                          TextView renderedTextView, TextWatcher textWatcher,
                                          String rawSource) {
        if (renderedTextView == null || editText == null || textWatcher == null) {
            return;
        }

        renderedTextView.setVisibility(View.GONE);
        editText.removeTextChangedListener(textWatcher);
        editText.setText(rawSource);
        field.setText(rawSource);
        editText.requestFocus();
        editText.addTextChangedListener(textWatcher);

        InputMethodManager imm = (InputMethodManager) editText.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Switch the given field to rendered mode: hide keyboard, clear focus,
     * show rendered view
     *
     * @param field            The markdown field to render
     * @param editText         The EditText for editing raw markdown
     * @param renderedTextView The TextView for displaying rendered markdown
     */
    private void switchMarkdownToRendered(MarkdownEditField field, EditText editText,
                                          TextView renderedTextView) {
        if (renderedTextView == null || editText == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) editText.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }

        editText.clearFocus();
        renderedTextView.setVisibility(View.VISIBLE);
        renderMarkdown(field, renderedTextView);
    }

    /**
     * Browse for an image file for the given image field
     *
     * @param field    The image field to fill
     * @param activity The activity
     */
    private void browseImage(CardMediaField field, Activity activity) {
        UiUtils.browseImage(activity, field.getBrowseRequestCode());
    }

    /**
     * Take a photo from camera for the given image field. The temp capture
     * file is tracked by the field itself so it survives process death.
     *
     * @param field    The image field to fill
     * @param activity The activity
     * @return The temp file created for the camera, or null if failed
     */
    private File takePhotoFromCamera(CardMediaField field, Activity activity) {
        try {
            File tempCameraFile = mFileHelper.createImageTempFile();
            field.setTempCameraFile(tempCameraFile);
            UiUtils.takeImageFromCamera(activity, field.getCameraRequestCode(), tempCameraFile);
            return tempCameraFile;
        } catch (Exception e) {
            mLogger.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Handle image URI processing for the given image field, running the given
     * UI updater once the processed file is set
     *
     * @param field     The image field to fill
     * @param imageUri  The image URI
     * @param uiUpdater The UI updater to run once the file is set
     */
    private void handleImageUri(CardMediaField field, Uri imageUri, Runnable uiUpdater) {
        String tag = "handleImageUri_" + field.getBrowseRequestCode() + "_" + field.getCameraRequestCode();
        mRxDisposer.add(tag,
                Single.fromCallable(() -> mFileHelper.createImageTempFile(imageUri))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(resultFile -> {
                            field.setFile(resultFile);
                            uiUpdater.run();
                        },
                                e -> mLogger.e(TAG, e.getMessage(), e)));
    }

    /**
     * Handle activity result from browsing or camera capture for both image
     * fields (voice fields carry no request codes and never match)
     *
     * @param requestCode The request code
     * @param resultCode  The result code
     * @param data        The intent data
     * @return true if a camera-capture result was applied (result code OK for an image field's camera request)
     */
    private boolean handleImageFieldActivityResult(int requestCode, int resultCode, Intent data) {
        if (mQuestionImageField == null || mAnswerImageField == null) {
            return false;
        }
        boolean cameraResultHandled = false;
        if (requestCode == mQuestionImageField.getBrowseRequestCode()
                && resultCode == Activity.RESULT_OK && data != null) {
            handleImageUri(mQuestionImageField, data.getData(), this::updateQuestionImageUi);
        } else if (requestCode == mQuestionImageField.getCameraRequestCode()
                && resultCode == Activity.RESULT_OK) {
            File tempCameraFile = mQuestionImageField.getTempCameraFile();
            if (tempCameraFile != null) {
                handleImageUri(mQuestionImageField, Uri.fromFile(tempCameraFile), this::updateQuestionImageUi);
                mQuestionImageField.setTempCameraFile(null);
            }
            cameraResultHandled = true;
        } else if (requestCode == mAnswerImageField.getBrowseRequestCode()
                && resultCode == Activity.RESULT_OK && data != null) {
            handleImageUri(mAnswerImageField, data.getData(), this::updateAnswerImageUi);
        } else if (requestCode == mAnswerImageField.getCameraRequestCode()
                && resultCode == Activity.RESULT_OK) {
            File tempCameraFile = mAnswerImageField.getTempCameraFile();
            if (tempCameraFile != null) {
                handleImageUri(mAnswerImageField, Uri.fromFile(tempCameraFile), this::updateAnswerImageUi);
                mAnswerImageField.setTempCameraFile(null);
            }
            cameraResultHandled = true;
        }
        return cameraResultHandled;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAudioService != null) {
            mAudioService.stopSpeech();
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }

        // Dispose component instances: cancel pending markdown renders before
        // releasing the views, a delivery after teardown would hit a nulled
        // view reference and crash inside Markwon
        if (mQuestionRenderDisposable != null) {
            mQuestionRenderDisposable.dispose();
            mQuestionRenderDisposable = null;
        }
        if (mAnswerRenderDisposable != null) {
            mAnswerRenderDisposable.dispose();
            mAnswerRenderDisposable = null;
        }
        // Detach the text watchers from the EditTexts before releasing them
        if (mEditTextQuestion != null && mQuestionTextWatcher != null) {
            mEditTextQuestion.removeTextChangedListener(mQuestionTextWatcher);
        }
        if (mEditTextAnswer != null && mAnswerTextWatcher != null) {
            mEditTextAnswer.removeTextChangedListener(mAnswerTextWatcher);
        }
        if (mQuestionField != null) {
            mQuestionField = null;
        }
        if (mAnswerField != null) {
            mAnswerField = null;
        }
        if (mQuestionImageField != null) {
            mQuestionImageField = null;
        }
        if (mAnswerImageField != null) {
            mAnswerImageField = null;
        }
        if (mQuestionVoiceField != null) {
            mQuestionVoiceField = null;
        }
        if (mAnswerVoiceField != null) {
            mAnswerVoiceField = null;
        }

        mNavRoute = null;
        mCard = null;
        mNewCardCmd = null;
        mDeckQueryCmd = null;
        mSuspendCardCmd = null;
        mSuspendCheckBox = null;
        mReversibleCheckBox = null;
        mContainerImageQuestion = null;
        mContainerImageAnswer = null;
        mQuestionImageView = null;
        mAnswerImageView = null;
        mVoiceQuestionContainer = null;
        mVoiceAnswerContainer = null;
        mButtonSaveAndAdd = null;
        mEditTextQuestion = null;
        mEditTextAnswer = null;
        mTextRenderedQuestion = null;
        mTextRenderedAnswer = null;
        mQuestionTextWatcher = null;
        mAnswerTextWatcher = null;
        mNavigator = null;
        mAudioService = null;
    }

    // Voice recording callbacks invoked from navigation result
    public void onQuestionVoiceRecorded(File file) {
        if (mQuestionVoiceField != null) {
            mQuestionVoiceField.setFile(file);
            updateQuestionVoiceUi();
        }
    }

    public void onAnswerVoiceRecorded(File file) {
        if (mAnswerVoiceField != null) {
            mAnswerVoiceField.setFile(file);
            updateAnswerVoiceUi();
        }
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    private boolean isUpdate() {
        Args args = getArgs();
        return args != null && args.isUpdate();
    }

    private void resetForm() {
        Long deckId = mCard.deckId;
        mCard = new Card();
        mCard.deckId = deckId;
        mCard.question = "";
        mCard.answer = "";

        // Clear all media fields
        if (mQuestionImageField != null) {
            mQuestionImageField.setFile(null);
        }
        if (mAnswerImageField != null) {
            mAnswerImageField.setFile(null);
        }
        if (mQuestionVoiceField != null) {
            mQuestionVoiceField.setFile(null);
        }
        if (mAnswerVoiceField != null) {
            mAnswerVoiceField.setFile(null);
        }
        updateQuestionImageUi();
        updateAnswerImageUi();
        updateQuestionVoiceUi();
        updateAnswerVoiceUi();

        // Clear and reset text fields
        if (mQuestionField != null) {
            setMarkdownText(mQuestionField, mEditTextQuestion, mQuestionTextWatcher, "");
            mEditTextQuestion.setError(null);
            switchMarkdownToRendered(mQuestionField, mEditTextQuestion, mTextRenderedQuestion);
        }
        if (mAnswerField != null) {
            setMarkdownText(mAnswerField, mEditTextAnswer, mAnswerTextWatcher, "");
            mEditTextAnswer.setError(null);
            switchMarkdownToRendered(mAnswerField, mEditTextAnswer, mTextRenderedAnswer);
        }

        mReversibleCheckBox.setChecked(false);
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            save(false);
            return true;
        } else if (id == R.id.menu_question_add_image) {
            browseImage(mQuestionImageField, mNavigator.getActivity());
        } else if (id == R.id.menu_question_add_photo) {
            takePhotoFromCamera(mQuestionImageField, mNavigator.getActivity());
        } else if (id == R.id.menu_question_add_voice) {
            mNavigator.push(Routes.COMMON_VOICERECORD, (navigator, navRoute, activity, currentView) -> {
                Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                File resultFile = provider.get(CommonNavConfig.class).result_commonVoiceRecord_file(navRoute.getRouteResult());
                if (resultFile != null) {
                    StatefulView sv = navigator.getCurrentRoute().getStatefulView();
                    if (sv instanceof CardDetailPage) {
                        ((CardDetailPage) sv).onQuestionVoiceRecorded(resultFile);
                    }
                }
            });
        } else if (id == R.id.menu_answer_add_image) {
            browseImage(mAnswerImageField, mNavigator.getActivity());
        } else if (id == R.id.menu_answer_add_photo) {
            takePhotoFromCamera(mAnswerImageField, mNavigator.getActivity());
        } else if (id == R.id.menu_answer_add_voice) {
            mNavigator.push(Routes.COMMON_VOICERECORD, (navigator, navRoute, activity, currentView) -> {
                Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                File resultFile = provider.get(CommonNavConfig.class).result_commonVoiceRecord_file(navRoute.getRouteResult());
                if (resultFile != null) {
                    StatefulView sv = navigator.getCurrentRoute().getStatefulView();
                    if (sv instanceof CardDetailPage) {
                        ((CardDetailPage) sv).onAnswerVoiceRecorded(resultFile);
                    }
                }
            });
        }
        return false;
    }

    private void save(boolean resetAfter) {
        if (!mNewCardCmd.valid(mCard)) {
            String validationError = mNewCardCmd.getValidationError();
            mLogger.i(TAG, validationError);
            return;
        }

        Context context = mSvProvider.getContext();
        String errorMessage;
        String successMessage;
        Args args = getArgs();
        if (args != null && args.isUpdate()) {
            errorMessage = context.getString(R.string.error_failed_to_update_card);
            successMessage = context.getString(R.string.success_updating_card);
        } else {
            errorMessage = context.getString(R.string.error_failed_to_add_card);
            successMessage = context.getString(R.string.success_adding_new_card);
        }

        // Get files from media fields
        File questionImageFile = mQuestionImageField.getValue().orElse(null);
        File answerImageFile = mAnswerImageField.getValue().orElse(null);
        File questionVoiceFile = mQuestionVoiceField.getValue().orElse(null);
        File answerVoiceFile = mAnswerVoiceField.getValue().orElse(null);
        Uri questionImageUri = questionImageFile != null ? Uri.fromFile(questionImageFile) : null;
        Uri answerImageUri = answerImageFile != null ? Uri.fromFile(answerImageFile) : null;
        Uri questionVoiceUri = questionVoiceFile != null ? Uri.fromFile(questionVoiceFile) : null;
        Uri answerVoiceUri = answerVoiceFile != null ? Uri.fromFile(answerVoiceFile) : null;

        String tagPrefix = resetAfter ? "onClick_newCardCmd_executeAndReset" : "onClick_newCardCmd_execute";
        mRxDisposer.add(tagPrefix,
                mNewCardCmd.execute(mCard)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((card, throwable) -> {
                            if (throwable != null) {
                                // Log without throwable for pop path (resetAfter=false), with throwable for reset path
                                if (resetAfter) {
                                    mLogger.e(TAG, errorMessage, throwable);
                                } else {
                                    mLogger.e(TAG, errorMessage);
                                }
                            } else {
                                mLogger.i(TAG, successMessage);
                                CompositeDisposable compositeDisposable = new CompositeDisposable();
                                compositeDisposable.add(
                                        mNewCardCmd.saveFiles(card, questionImageUri,
                                                answerImageUri, questionVoiceUri, answerVoiceUri)
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe((card1, throwable1) -> {
                                                    if (throwable1 != null) {
                                                        String message = throwable1.getMessage();
                                                        if (throwable1.getCause() instanceof ValidationException) {
                                                            message = throwable1.getCause().getMessage();
                                                        }
                                                        mLogger.e(TAG, message, throwable1);
                                                    } else {
                                                        mLogger.d(TAG, "Image added/updated success for " + card1.question);
                                                    }
                                                    compositeDisposable.dispose();
                                                })
                                );
                                persistSuspendIfNeeded(resetAfter, card);
                            }
                        }));
    }

    /**
     * Persists a changed suspend flag after the card itself was saved. Runs
     * before the page finishes (pop or reset) so the list refreshes with the
     * final state; on suspend failure the card save is kept and the error is
     * only logged.
     */
    private void persistSuspendIfNeeded(boolean resetAfter, Card card) {
        if (!isUpdate() || mSuspendCardCmd == null || mSuspended == mOriginalSuspended) {
            finishSave(resetAfter, card);
            return;
        }
        boolean suspend = mSuspended;
        mRxDisposer.add("onClick_save_suspendCard",
                mSuspendCardCmd.execute(card, suspend)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((card1, throwable) -> {
                            if (throwable != null) {
                                mLogger.e(TAG,
                                        mSvProvider.getContext()
                                                .getString(R.string.error_suspending_card), throwable);
                            } else {
                                mLogger.i(TAG,
                                        mSvProvider.getContext()
                                                .getString(suspend
                                                        ? R.string.success_suspending_card
                                                        : R.string.success_unsuspending_card,
                                                        card1.question));
                            }
                            finishSave(resetAfter, card);
                        }));
    }

    private void finishSave(boolean resetAfter, Card card) {
        if (resetAfter) {
            resetForm();
        } else {
            mNavigator.pop(Result.withCard(card));
        }
    }

    /**
     * Form-field edit only; the suspend flag is persisted when the user saves.
     */
    private void onSuspendCheckedChange(CompoundButton buttonView, boolean isChecked) {
        mSuspended = isChecked;
    }

    /**
     * Syncs the checkbox and the tracked suspend fields from the database state.
     * The listener is detached during the programmatic set so the sync never
     * registers as a form edit.
     */
    private void updateSuspendedState(boolean suspended) {
        mOriginalSuspended = suspended;
        mSuspended = suspended;
        mSuspendCheckBox.setEnabled(true);
        mSuspendCheckBox.setOnCheckedChangeListener(null);
        mSuspendCheckBox.setChecked(suspended);
        mSuspendCheckBox.setOnCheckedChangeListener(this::onSuspendCheckedChange);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save_and_add) {
            save(true);
        } else if (id == R.id.text_rendered_question) {
            switchMarkdownToEditable(mQuestionField, mEditTextQuestion,
                    mTextRenderedQuestion, mQuestionTextWatcher, mCard.question);
        } else if (id == R.id.text_rendered_answer) {
            switchMarkdownToEditable(mAnswerField, mEditTextAnswer,
                    mTextRenderedAnswer, mAnswerTextWatcher, mCard.answer);
        } else if (id == R.id.button_question_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.page_card_detail_question, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.setForceShowIcon(true);
            popup.show();
        } else if (id == R.id.button_answer_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.page_card_detail_answer, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.setForceShowIcon(true);
            popup.show();
        } else if (id == R.id.button_question_delete_image) {
            mQuestionImageField.setFile(null);
            updateQuestionImageUi();
        } else if (id == R.id.button_answer_delete_image) {
            mAnswerImageField.setFile(null);
            updateAnswerImageUi();
        } else if (id == R.id.image_question) {
            File file = mQuestionImageField.getValue().orElse(null);
            if (file != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mCommonNavConfig.args_commonImageView(file));
            }
        } else if (id == R.id.image_answer) {
            File file = mAnswerImageField.getValue().orElse(null);
            if (file != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mCommonNavConfig.args_commonImageView(file));
            }
        } else if (id == R.id.button_question_voice) {
            File file = mQuestionVoiceField.getValue().orElse(null);
            if (file != null) {
                mAudioService.playRecording(Uri.fromFile(file));
            }
        } else if (id == R.id.button_question_delete_voice) {
            mQuestionVoiceField.setFile(null);
            updateQuestionVoiceUi();
        } else if (id == R.id.button_answer_voice) {
            File file = mAnswerVoiceField.getValue().orElse(null);
            if (file != null) {
                mAudioService.playRecording(Uri.fromFile(file));
            }
        } else if (id == R.id.button_answer_delete_voice) {
            mAnswerVoiceField.setFile(null);
            updateAnswerVoiceUi();
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        handleImageFieldActivityResult(requestCode, resultCode, data);
    }

    public static class Result implements Serializable {
        public static Result withCard(Card card) {
            Result result = new Result();
            result.mCard = card;
            return result;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private Card mCard;

        public Card getCard() {
            return mCard;
        }
    }

    /**
     * Argument for this SV
     */
    public static class Args implements Serializable {
        public static Args withDeck(Deck deck) {
            Args args = new Args();
            args.mDeck = deck;
            return args;
        }

        public static Args forUpdate(Card card) {
            Args args = new Args();
            args.mCard = card;
            args.mOperation = 1;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private Deck mDeck;
        private Card mCard;
        private byte mOperation;

        private Args() {
        }

        public Deck getDeck() {
            return mDeck;
        }

        public boolean isUpdate() {
            return mOperation == 1;
        }
    }
}
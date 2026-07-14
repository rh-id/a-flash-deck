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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.Serializable;
import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
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

    private static final int BROWSE_FOR_QUESTION_IMAGE = 1;
    private static final int BROWSE_FOR_ANSWER_IMAGE = 2;
    private static final int CAMERA_FOR_QUESTION_IMAGE = 3;
    private static final int CAMERA_FOR_ANSWER_IMAGE = 4;

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
    private transient AudioPlayer mAudioPlayer;
    private transient CommonNavConfig mCommonNavConfig;
    private transient NewCardCmd mNewCardCmd;
    private transient TextWatcher mQuestionTextWatcher;
    private transient TextWatcher mAnswerTextWatcher;
    private transient BehaviorSubject<Optional<File>> mQuestionImageFileSubject;
    private transient BehaviorSubject<Optional<File>> mAnswerImageFileSubject;
    private File mTempCameraFile;
    private transient BehaviorSubject<Optional<File>> mQuestionVoiceSubject;
    private transient BehaviorSubject<Optional<File>> mAnswerVoiceSubject;
    private transient EditText mEditTextQuestion;
    private transient EditText mEditTextAnswer;
    private transient TextView mTextRenderedQuestion;
    private transient TextView mTextRenderedAnswer;
    private transient MarkdownRenderer mMarkdownRenderer;
    private transient MaterialCheckBox mReversibleCheckBox;
    private transient ViewGroup mContainerImageQuestion;
    private transient ViewGroup mContainerImageAnswer;
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
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mAudioPlayer = mSvProvider.get(AudioPlayer.class);
        mMarkdownRenderer = mSvProvider.get(MarkdownRenderer.class);
        if (isUpdate()) {
            mNewCardCmd = mSvProvider.get(UpdateCardCmd.class);
        } else {
            mNewCardCmd = mSvProvider.get(NewCardCmd.class);
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
        if (mCard.questionImage != null && !mCard.questionImage.isEmpty()) {
            setQuestionImageFileSubject(mFileHelper.getCardQuestionImage(mCard.questionImage));
        } else {
            if (mQuestionImageFileSubject == null) {
                setQuestionImageFileSubject(null);
            }
        }
        if (mCard.answerImage != null && !mCard.answerImage.isEmpty()) {
            setAnswerImageFileSubject(mFileHelper.getCardAnswerImage(mCard.answerImage));
        } else {
            if (mAnswerImageFileSubject == null) {
                setAnswerImageFileSubject(null);
            }
        }
        if (mCard.questionVoice != null && !mCard.questionVoice.isEmpty()) {
            setQuestionVoiceSubject(mFileHelper.getCardQuestionVoice(mCard.questionVoice));
        } else {
            if (mQuestionVoiceSubject == null) {
                setQuestionVoiceSubject(null);
            }
        }
        if (mCard.answerVoice != null && !mCard.answerVoice.isEmpty()) {
            setAnswerVoiceSubject(mFileHelper.getCardAnswerVoice(mCard.answerVoice));
        } else {
            if (mAnswerVoiceSubject == null) {
                setAnswerVoiceSubject(null);
            }
        }
        initTextWatcher();
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
        ImageView questionImageView = rootLayout.findViewById(R.id.image_question);
        questionImageView.setOnClickListener(this);
        ImageView answerImageView = rootLayout.findViewById(R.id.image_answer);
        answerImageView.setOnClickListener(this);
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
        if (mCard != null) {
            mEditTextQuestion.setText(mCard.question);
            mEditTextAnswer.setText(mCard.answer);
            mReversibleCheckBox.setChecked(mCard.isReversibleQA);
        }
        mEditTextQuestion.addTextChangedListener(mQuestionTextWatcher);
        mEditTextAnswer.addTextChangedListener(mAnswerTextWatcher);
        // When focus leaves an EditText (user taps elsewhere), switch that field
        // back to rendered mode so the markdown is re-rendered with any edits.
        mEditTextQuestion.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && mTextRenderedQuestion.getVisibility() != View.VISIBLE) {
                switchToRendered(mTextRenderedQuestion, mEditTextQuestion, this::renderQuestion);
            }
        });
        mEditTextAnswer.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && mTextRenderedAnswer.getVisibility() != View.VISIBLE) {
                switchToRendered(mTextRenderedAnswer, mEditTextAnswer, this::renderAnswer);
            }
        });
        // Render the question/answer into the read-only rendered TextViews.
        renderQuestion();
        renderAnswer();
        mReversibleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> mCard.isReversibleQA = isChecked);
        mRxDisposer
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (!s.isEmpty()) {
                                mEditTextQuestion.setError(s);
                            } else {
                                mEditTextQuestion.setError(null);
                            }
                        }));
        mRxDisposer
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (!s.isEmpty()) {
                                mEditTextAnswer.setError(s);
                            } else {
                                mEditTextAnswer.setError(null);
                            }
                        }));
        mRxDisposer
                .add("createView_questionImageChanged",
                        mQuestionImageFileSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        File file = fileOpt.get();
                                        questionImageView.setImageURI(Uri.fromFile(file));
                                        mContainerImageQuestion.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        mContainerImageQuestion.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_answerImageChanged",
                        mAnswerImageFileSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        File file = fileOpt.get();
                                        answerImageView.setImageURI(Uri.fromFile(file));
                                        mContainerImageAnswer.setVisibility(View.VISIBLE);
                                    } else {
                                        answerImageView.setImageURI(null);
                                        mContainerImageAnswer.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_questionVoiceChanged",
                        mQuestionVoiceSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        mVoiceQuestionContainer.setVisibility(View.VISIBLE);
                                    } else {
                                        mVoiceQuestionContainer.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_answerVoiceChanged",
                        mAnswerVoiceSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        mVoiceAnswerContainer.setVisibility(View.VISIBLE);
                                    } else {
                                        mVoiceAnswerContainer.setVisibility(View.GONE);
                                    }
                                }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        mNavRoute = null;
        mCard = null;
        mNewCardCmd = null;
        mQuestionTextWatcher = null;
        mAnswerTextWatcher = null;
        mEditTextQuestion = null;
        mEditTextAnswer = null;
        mReversibleCheckBox = null;
        mContainerImageQuestion = null;
        mContainerImageAnswer = null;
        mVoiceQuestionContainer = null;
        mVoiceAnswerContainer = null;
        mButtonSaveAndAdd = null;
        if (mQuestionImageFileSubject != null) {
            mQuestionImageFileSubject.onComplete();
            mQuestionImageFileSubject = null;
        }
        if (mAnswerImageFileSubject != null) {
            mAnswerImageFileSubject.onComplete();
            mAnswerImageFileSubject = null;
        }
        if (mQuestionVoiceSubject != null) {
            mQuestionVoiceSubject.onComplete();
            mQuestionVoiceSubject = null;
        }
        if (mAnswerVoiceSubject != null) {
            mAnswerVoiceSubject.onComplete();
            mAnswerVoiceSubject = null;
        }
        mNavigator = null;
    }

    private void setQuestionImageFileSubject(File file) {
        if (mQuestionImageFileSubject == null) {
            mQuestionImageFileSubject = BehaviorSubject.createDefault(Optional.ofNullable(file));
        } else {
            mQuestionImageFileSubject.onNext(Optional.ofNullable(file));
        }
    }

    private void setAnswerImageFileSubject(File file) {
        if (mAnswerImageFileSubject == null) {
            mAnswerImageFileSubject = BehaviorSubject.createDefault(Optional.ofNullable(file));
        } else {
            mAnswerImageFileSubject.onNext(Optional.ofNullable(file));
        }
    }

    private void setQuestionVoiceSubject(File file) {
        if (mQuestionVoiceSubject == null) {
            mQuestionVoiceSubject = BehaviorSubject.createDefault(Optional.ofNullable(file));
        } else {
            mQuestionVoiceSubject.onNext(Optional.ofNullable(file));
        }
    }

    private void setAnswerVoiceSubject(File file) {
        if (mAnswerVoiceSubject == null) {
            mAnswerVoiceSubject = BehaviorSubject.createDefault(Optional.ofNullable(file));
        } else {
            mAnswerVoiceSubject.onNext(Optional.ofNullable(file));
        }
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    private boolean isUpdate() {
        Args args = getArgs();
        return args != null && args.isUpdate();
    }

    private void initTextWatcher() {
        if (mQuestionTextWatcher == null) {
            mQuestionTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mCard.question = editable.toString();
                    mSvProvider.get(NewCardCmd.class)
                            .valid(mCard);
                }
            };
        }
        if (mAnswerTextWatcher == null) {
            mAnswerTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mCard.answer = editable.toString();
                    mSvProvider.get(NewCardCmd.class)
                            .valid(mCard);
                }
            };
        }
    }

    private void resetForm() {
        Long deckId = mCard.deckId;
        mCard = new Card();
        mCard.deckId = deckId;
        mCard.question = "";
        mCard.answer = "";
        setQuestionImageFileSubject(null);
        setAnswerImageFileSubject(null);
        setQuestionVoiceSubject(null);
        setAnswerVoiceSubject(null);
        mEditTextQuestion.removeTextChangedListener(mQuestionTextWatcher);
        mEditTextAnswer.removeTextChangedListener(mAnswerTextWatcher);
        mEditTextQuestion.setText("");
        mEditTextAnswer.setText("");
        mEditTextQuestion.addTextChangedListener(mQuestionTextWatcher);
        mEditTextAnswer.addTextChangedListener(mAnswerTextWatcher);
        mReversibleCheckBox.setChecked(false);
        mEditTextQuestion.post(() -> {
            if (mEditTextQuestion != null) {
                mEditTextQuestion.setError(null);
            }
            if (mEditTextAnswer != null) {
                mEditTextAnswer.setError(null);
            }
        });
        // Flip back to rendered mode for both fields (cleared above).
        switchToRendered(mTextRenderedQuestion, mEditTextQuestion, this::renderQuestion);
        switchToRendered(mTextRenderedAnswer, mEditTextAnswer, this::renderAnswer);
    }

    /**
     * Render the current {@code mCard.question} into the read-only question
     * TextView asynchronously. Shows a placeholder when the field is empty.
     * Latex/markdown spans are TextView-specific, so each render re-parses fresh.
     */
    private void renderQuestion() {
        if (mCard.question == null || mCard.question.isEmpty()) {
            mTextRenderedQuestion.setText(R.string.tap_to_edit_question);
            return;
        }
        mRxDisposer.add("createView_renderQuestion",
                mMarkdownRenderer.parseAsync(mCard.question)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(spanned ->
                                        mMarkdownRenderer.applyParsedMarkdown(mTextRenderedQuestion, spanned),
                                throwable -> mLogger.e(TAG, "render question failed", throwable)));
    }

    /** Render the current {@code mCard.answer} into the read-only answer TextView. */
    private void renderAnswer() {
        if (mCard.answer == null || mCard.answer.isEmpty()) {
            mTextRenderedAnswer.setText(R.string.tap_to_edit_answer);
            return;
        }
        mRxDisposer.add("createView_renderAnswer",
                mMarkdownRenderer.parseAsync(mCard.answer)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(spanned ->
                                        mMarkdownRenderer.applyParsedMarkdown(mTextRenderedAnswer, spanned),
                                throwable -> mLogger.e(TAG, "render answer failed", throwable)));
    }

    /**
     * Switch a field from rendered mode to editable mode: hide the overlay
     * rendered TextView (which sits on top of the always-visible EditText),
     * ensure the EditText holds the raw markdown source from {@code mCard}
     * (remove/setText/re-add dance so the watcher doesn't double-fire), focus
     * it, and show the soft keyboard.
     *
     * <p>The {@code TextInputLayout}/EditText is intentionally kept always
     * visible (never toggled gone/visible), matching the original working
     * setup — toggling a {@code TextInputLayout}'s visibility dynamically does
     * not reliably re-initialize its outlined-box rendering. The rendered
     * TextView overlays it and is the only thing toggled.
     */
    private void switchToEditable(TextView rendered, EditText editable, TextWatcher watcher,
                                  String rawSource) {
        rendered.setVisibility(View.GONE);
        editable.removeTextChangedListener(watcher);
        editable.setText(rawSource);
        editable.requestFocus();
        editable.addTextChangedListener(watcher);
        InputMethodManager imm = (InputMethodManager) editable.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editable, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Switch a field back to rendered mode: hide the soft keyboard, clear the
     * EditText focus, show the overlay rendered TextView, and re-render the
     * current {@code mCard} content (so any edits are reflected).
     */
    private void switchToRendered(TextView rendered, EditText editable, Runnable reRender) {
        InputMethodManager imm = (InputMethodManager) editable.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editable.getWindowToken(), 0);
        }
        editable.clearFocus();
        rendered.setVisibility(View.VISIBLE);
        reRender.run();
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            saveAndPop();
            return true;
        } else if (id == R.id.menu_question_add_image) {
            UiUtils.browseImage(mNavigator.getActivity(), BROWSE_FOR_QUESTION_IMAGE);
        } else if (id == R.id.menu_question_add_photo) {
            try {
                mTempCameraFile = mFileHelper.createImageTempFile();
                UiUtils.takeImageFromCamera(mNavigator.getActivity(), CAMERA_FOR_QUESTION_IMAGE,
                        mTempCameraFile);
            } catch (Exception e) {
                mLogger.e(TAG, e.getMessage(), e);
            }
        } else if (id == R.id.menu_question_add_voice) {
            mNavigator.push(Routes.COMMON_VOICERECORD, (navigator, navRoute, activity, currentView) -> {
                Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                File resultFile = provider.get(CommonNavConfig.class).result_commonVoiceRecord_file(navRoute.getRouteResult());
                if (resultFile != null) {
                    StatefulView sv = navigator.getCurrentRoute().getStatefulView();
                    if (sv instanceof CardDetailPage) {
                        ((CardDetailPage) sv).setQuestionVoiceSubject(resultFile);
                    }
                }
            });
        } else if (id == R.id.menu_answer_add_image) {
            UiUtils.browseImage(mNavigator.getActivity(), BROWSE_FOR_ANSWER_IMAGE);
        } else if (id == R.id.menu_answer_add_photo) {
            try {
                mTempCameraFile = mFileHelper.createImageTempFile();
                UiUtils.takeImageFromCamera(mNavigator.getActivity(), CAMERA_FOR_ANSWER_IMAGE,
                        mTempCameraFile);
            } catch (Exception e) {
                mLogger.e(TAG, e.getMessage(), e);
            }
        } else if (id == R.id.menu_answer_add_voice) {
            mNavigator.push(Routes.COMMON_VOICERECORD, (navigator, navRoute, activity, currentView) -> {
                Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                File resultFile = provider.get(CommonNavConfig.class).result_commonVoiceRecord_file(navRoute.getRouteResult());
                if (resultFile != null) {
                    StatefulView sv = navigator.getCurrentRoute().getStatefulView();
                    if (sv instanceof CardDetailPage) {
                        ((CardDetailPage) sv).setAnswerVoiceSubject(resultFile);
                    }
                }
            });
        }
        return false;
    }

    private void saveAndPop() {
        if (mNewCardCmd.valid(mCard)) {
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
            File questionImageFile = mQuestionImageFileSubject.getValue().orElse(null);
            File answerImageFile = mAnswerImageFileSubject.getValue().orElse(null);
            File questionVoiceFile = mQuestionVoiceSubject.getValue().orElse(null);
            File answerVoiceFile = mAnswerVoiceSubject.getValue().orElse(null);
            Uri questionImageUri = questionImageFile != null ? Uri.fromFile(questionImageFile) : null;
            Uri answerImageUri = answerImageFile != null ? Uri.fromFile(answerImageFile) : null;
            Uri questionVoiceUri = questionVoiceFile != null ? Uri.fromFile(questionVoiceFile) : null;
            Uri answerVoiceUri = answerVoiceFile != null ? Uri.fromFile(answerVoiceFile) : null;
            mRxDisposer.add("onClick_newCardCmd_execute",
                    mNewCardCmd.execute(mCard)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((card, throwable) -> {
                                if (throwable != null) {
                                    mLogger.e(TAG,
                                            errorMessage);
                                } else {
                                    mLogger.i(TAG,
                                            successMessage);
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
                                                            mLogger
                                                                    .e(TAG, message, throwable1);
                                                        } else {
                                                            mLogger.d(TAG, "Image added/updated success for " + card1.question);
                                                        }
                                                        compositeDisposable.dispose();
                                                    })
                                    );
                                    mNavigator.pop(Result.withCard(card));
                                }
                            }));
        } else {
            String validationError = mNewCardCmd.getValidationError();
            mLogger.i(TAG, validationError);
        }
    }

    private void saveAndReset() {
        if (mNewCardCmd.valid(mCard)) {
            Context context = mSvProvider.getContext();
            String errorMessage = context.getString(R.string.error_failed_to_add_card);
            String successMessage = context.getString(R.string.success_adding_new_card);
            File questionImageFile = mQuestionImageFileSubject.getValue().orElse(null);
            File answerImageFile = mAnswerImageFileSubject.getValue().orElse(null);
            File questionVoiceFile = mQuestionVoiceSubject.getValue().orElse(null);
            File answerVoiceFile = mAnswerVoiceSubject.getValue().orElse(null);
            Uri questionImageUri = questionImageFile != null ? Uri.fromFile(questionImageFile) : null;
            Uri answerImageUri = answerImageFile != null ? Uri.fromFile(answerImageFile) : null;
            Uri questionVoiceUri = questionVoiceFile != null ? Uri.fromFile(questionVoiceFile) : null;
            Uri answerVoiceUri = answerVoiceFile != null ? Uri.fromFile(answerVoiceFile) : null;
            mRxDisposer.add("onClick_newCardCmd_executeAndReset",
                    mNewCardCmd.execute(mCard)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((card, throwable) -> {
                                if (throwable != null) {
                                    mLogger.e(TAG, errorMessage, throwable);
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
                                    resetForm();
                                }
                            }));
        } else {
            String validationError = mNewCardCmd.getValidationError();
            mLogger.i(TAG, validationError);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save_and_add) {
            saveAndReset();
        } else if (id == R.id.text_rendered_question) {
            switchToEditable(mTextRenderedQuestion, mEditTextQuestion,
                    mQuestionTextWatcher, mCard.question);
        } else if (id == R.id.text_rendered_answer) {
            switchToEditable(mTextRenderedAnswer, mEditTextAnswer,
                    mAnswerTextWatcher, mCard.answer);
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
            setQuestionImageFileSubject(null);
        } else if (id == R.id.button_answer_delete_image) {
            setAnswerImageFileSubject(null);
        } else if (id == R.id.image_question) {
            File file = mQuestionImageFileSubject.getValue().orElse(null);
            if (file != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mCommonNavConfig.args_commonImageView(file));
            }
        } else if (id == R.id.image_answer) {
            File file = mAnswerImageFileSubject.getValue().orElse(null);
            if (file != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mCommonNavConfig.args_commonImageView(file));
            }
        } else if (id == R.id.button_question_voice) {
            mAudioPlayer.play(Uri.fromFile(mQuestionVoiceSubject.getValue().get()));
        } else if (id == R.id.button_question_delete_voice) {
            setQuestionVoiceSubject(null);
        } else if (id == R.id.button_answer_voice) {
            mAudioPlayer.play(Uri.fromFile(mAnswerVoiceSubject.getValue().get()));
        } else if (id == R.id.button_answer_delete_voice) {
            setAnswerVoiceSubject(null);
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        Provider provider = (Provider) INavigator.getNavConfiguration().getRequiredComponent();
        if (requestCode == BROWSE_FOR_QUESTION_IMAGE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            setQuestionImageFile(provider, fullPhotoUri);
        } else if (requestCode == BROWSE_FOR_ANSWER_IMAGE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            setAnswerImageFile(provider, fullPhotoUri);
        } else if (requestCode == CAMERA_FOR_QUESTION_IMAGE && resultCode == Activity.RESULT_OK) {
            setQuestionImageFile(provider, Uri.fromFile(mTempCameraFile));
            mTempCameraFile = null;
        } else if (requestCode == CAMERA_FOR_ANSWER_IMAGE && resultCode == Activity.RESULT_OK) {
            setAnswerImageFile(provider, Uri.fromFile(mTempCameraFile));
            mTempCameraFile = null;
        }
    }

    public void setAnswerImageFile(Provider provider, Uri fullPhotoUri) {
        Single.fromCallable(() -> mFileHelper.createImageTempFile(fullPhotoUri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(resultFile -> setAnswerImageFileSubject(resultFile),
                        e -> mLogger.e(TAG, e.getMessage(), e));
    }

    public void setQuestionImageFile(Provider provider, Uri fullPhotoUri) {
        Single.fromCallable(() -> mFileHelper.createImageTempFile(fullPhotoUri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(resultFile -> setQuestionImageFileSubject(resultFile),
                        e -> mLogger.e(TAG, e.getMessage(), e));
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
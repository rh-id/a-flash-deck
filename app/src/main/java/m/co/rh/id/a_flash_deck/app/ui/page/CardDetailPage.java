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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.app.ui.component.card.CardMediaField;
import m.co.rh.id.a_flash_deck.app.ui.component.card.MarkdownEditField;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
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
    private File mTempCameraFile; // Non-transient to survive process death
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;
    private transient CardMediaStore mCardMediaStore;
    private transient AudioPlayer mAudioPlayer;
    private transient CommonNavConfig mCommonNavConfig;
    private transient NewCardCmd mNewCardCmd;
    // New component instances - transient and recreated in provideComponent()
    private transient MarkdownEditField mQuestionField;
    private transient MarkdownEditField mAnswerField;
    private transient CardMediaField mQuestionImageField;
    private transient CardMediaField mAnswerImageField;
    private transient CardMediaField mQuestionVoiceField;
    private transient CardMediaField mAnswerVoiceField;
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
        mCardMediaStore = mSvProvider.get(CardMediaStore.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mAudioPlayer = mSvProvider.get(AudioPlayer.class);
        if (isUpdate()) {
            mNewCardCmd = mSvProvider.get(UpdateCardCmd.class);
        } else {
            mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        }
        // Create or recreate the component instances
        MarkdownRenderer markdownRenderer = mSvProvider.get(MarkdownRenderer.class);

        // Initialize markdown fields
        mQuestionField = new MarkdownEditField(markdownRenderer, mRxDisposer, mLogger, "question");
        mAnswerField = new MarkdownEditField(markdownRenderer, mRxDisposer, mLogger, "answer");
        // Initialize media fields
        mQuestionImageField = CardMediaField.forQuestionImage(mFileHelper, mLogger, mRxDisposer);
        mAnswerImageField = CardMediaField.forAnswerImage(mFileHelper, mLogger, mRxDisposer);
        mQuestionVoiceField = CardMediaField.forQuestionVoice(mFileHelper, mAudioPlayer, mLogger, mRxDisposer);
        mAnswerVoiceField = CardMediaField.forAnswerVoice(mFileHelper, mAudioPlayer, mLogger, mRxDisposer);
        // Restore temp camera file after process death (non-transient field survives)
        if (mTempCameraFile != null) {
            mQuestionImageField.setTempCameraFile(mTempCameraFile);
            mAnswerImageField.setTempCameraFile(mTempCameraFile);
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

        // Initialize media field values from mCard
        if (mCard.questionImage != null && !mCard.questionImage.isEmpty()) {
            mQuestionImageField.setFile(mCardMediaStore.getCardQuestionImage(mCard.questionImage));
        } else {
            if (mQuestionImageField.getFileSubject() == null) {
                mQuestionImageField.setFile(null);
            }
        }
        if (mCard.answerImage != null && !mCard.answerImage.isEmpty()) {
            mAnswerImageField.setFile(mCardMediaStore.getCardAnswerImage(mCard.answerImage));
        } else {
            if (mAnswerImageField.getFileSubject() == null) {
                mAnswerImageField.setFile(null);
            }
        }
        if (mCard.questionVoice != null && !mCard.questionVoice.isEmpty()) {
            mQuestionVoiceField.setFile(mCardMediaStore.getCardQuestionVoice(mCard.questionVoice));
        } else {
            if (mQuestionVoiceField.getFileSubject() == null) {
                mQuestionVoiceField.setFile(null);
            }
        }
        if (mCard.answerVoice != null && !mCard.answerVoice.isEmpty()) {
            mAnswerVoiceField.setFile(mCardMediaStore.getCardAnswerVoice(mCard.answerVoice));
        } else {
            if (mAnswerVoiceField.getFileSubject() == null) {
                mAnswerVoiceField.setFile(null);
            }
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

        EditText editTextQuestion = rootLayout.findViewById(R.id.text_input_edit_question);
        EditText editTextAnswer = rootLayout.findViewById(R.id.text_input_edit_answer);
        TextView textRenderedQuestion = rootLayout.findViewById(R.id.text_rendered_question);
        TextView textRenderedAnswer = rootLayout.findViewById(R.id.text_rendered_answer);
        textRenderedQuestion.setOnClickListener(this);
        textRenderedAnswer.setOnClickListener(this);
        mReversibleCheckBox = rootLayout.findViewById(R.id.checkbox_reversible);

        // Set initial text BEFORE binding watchers to avoid spurious initial validation
        if (mCard != null) {
            editTextQuestion.setText(mCard.question);
            editTextAnswer.setText(mCard.answer);
            mReversibleCheckBox.setChecked(mCard.isReversibleQA);
        }

        // Bind markdown fields (attaches watchers after initial setText)
        mQuestionField.bind(editTextQuestion, textRenderedQuestion, R.string.tap_to_edit_question,
                mCard.question, text -> {
                    mCard.question = text;
                    mNewCardCmd.valid(mCard);
                });
        mAnswerField.bind(editTextAnswer, textRenderedAnswer, R.string.tap_to_edit_answer,
                mCard.answer, text -> {
                    mCard.answer = text;
                    mNewCardCmd.valid(mCard);
                });

        // When focus leaves an EditText, switch that field back to rendered mode
        editTextQuestion.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && textRenderedQuestion.getVisibility() != View.VISIBLE) {
                mQuestionField.switchToRendered();
            }
        });
        editTextAnswer.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && textRenderedAnswer.getVisibility() != View.VISIBLE) {
                mAnswerField.switchToRendered();
            }
        });

        // Render the question/answer
        mQuestionField.render();
        mAnswerField.render();

        mReversibleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> mCard.isReversibleQA = isChecked);

        // Setup validation subscriptions
        mRxDisposer
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (!s.isEmpty()) {
                                mQuestionField.setError(s);
                            } else {
                                mQuestionField.clearError();
                            }
                        }));
        mRxDisposer
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(s -> {
                            if (!s.isEmpty()) {
                                mAnswerField.setError(s);
                            } else {
                                mAnswerField.clearError();
                            }
                        }));

        // Setup image field subscriptions
        mRxDisposer
                .add("createView_questionImageChanged",
                        mQuestionImageField.getFileSubject().observeOn(AndroidSchedulers.mainThread())
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
                        mAnswerImageField.getFileSubject().observeOn(AndroidSchedulers.mainThread())
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

        // Setup voice field subscriptions
        mRxDisposer
                .add("createView_questionVoiceChanged",
                        mQuestionVoiceField.getFileSubject().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        mVoiceQuestionContainer.setVisibility(View.VISIBLE);
                                    } else {
                                        mVoiceQuestionContainer.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_answerVoiceChanged",
                        mAnswerVoiceField.getFileSubject().observeOn(AndroidSchedulers.mainThread())
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

        // Dispose component instances
        if (mQuestionField != null) {
            mQuestionField.dispose();
            mQuestionField = null;
        }
        if (mAnswerField != null) {
            mAnswerField.dispose();
            mAnswerField = null;
        }
        if (mQuestionImageField != null) {
            mQuestionImageField.dispose();
            mQuestionImageField = null;
        }
        if (mAnswerImageField != null) {
            mAnswerImageField.dispose();
            mAnswerImageField = null;
        }
        if (mQuestionVoiceField != null) {
            mQuestionVoiceField.dispose();
            mQuestionVoiceField = null;
        }
        if (mAnswerVoiceField != null) {
            mAnswerVoiceField.dispose();
            mAnswerVoiceField = null;
        }

        mNavRoute = null;
        mCard = null;
        mNewCardCmd = null;
        mReversibleCheckBox = null;
        mContainerImageQuestion = null;
        mContainerImageAnswer = null;
        mVoiceQuestionContainer = null;
        mVoiceAnswerContainer = null;
        mButtonSaveAndAdd = null;
        mNavigator = null;
        mTempCameraFile = null;
    }

    // Voice recording callbacks invoked from navigation result
    public void onQuestionVoiceRecorded(File file) {
        if (mQuestionVoiceField != null) {
            mQuestionVoiceField.setFile(file);
        }
    }

    public void onAnswerVoiceRecorded(File file) {
        if (mAnswerVoiceField != null) {
            mAnswerVoiceField.setFile(file);
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

        // Clear temp camera file
        mTempCameraFile = null;

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

        // Clear and reset text fields
        if (mQuestionField != null) {
            mQuestionField.setText("");
            mQuestionField.clearError();
            mQuestionField.switchToRendered();
        }
        if (mAnswerField != null) {
            mAnswerField.setText("");
            mAnswerField.clearError();
            mAnswerField.switchToRendered();
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
            mQuestionImageField.browseImage(mNavigator.getActivity());
        } else if (id == R.id.menu_question_add_photo) {
            mTempCameraFile = mQuestionImageField.takePhotoFromCamera(mNavigator.getActivity());
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
            mAnswerImageField.browseImage(mNavigator.getActivity());
        } else if (id == R.id.menu_answer_add_photo) {
            mTempCameraFile = mAnswerImageField.takePhotoFromCamera(mNavigator.getActivity());
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
                                if (resetAfter) {
                                    resetForm();
                                } else {
                                    mNavigator.pop(Result.withCard(card));
                                }
                            }
                        }));
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_save_and_add) {
            save(true);
        } else if (id == R.id.text_rendered_question) {
            mQuestionField.switchToEditable(mCard.question);
        } else if (id == R.id.text_rendered_answer) {
            mAnswerField.switchToEditable(mCard.answer);
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
        } else if (id == R.id.button_answer_delete_image) {
            mAnswerImageField.setFile(null);
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
            mQuestionVoiceField.playVoice();
        } else if (id == R.id.button_question_delete_voice) {
            mQuestionVoiceField.setFile(null);
        } else if (id == R.id.button_answer_voice) {
            mAnswerVoiceField.playVoice();
        } else if (id == R.id.button_answer_delete_voice) {
            mAnswerVoiceField.setFile(null);
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        boolean cameraResultHandled = false;
        if (mQuestionImageField != null) {
            cameraResultHandled = mQuestionImageField.handleActivityResult(requestCode, resultCode, data);
        }
        if (!cameraResultHandled && mAnswerImageField != null) {
            cameraResultHandled = mAnswerImageField.handleActivityResult(requestCode, resultCode, data);
        }
        // Clear the process-death mirror of the temp camera file once consumed
        if (cameraResultHandled) {
            mTempCameraFile = null;
        }
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
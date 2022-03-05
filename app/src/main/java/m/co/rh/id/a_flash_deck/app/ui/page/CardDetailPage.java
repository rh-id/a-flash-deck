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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
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
import m.co.rh.id.anavigator.component.NavActivityLifecycle;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class CardDetailPage extends StatefulView<Activity> implements RequireNavRoute, RequireComponent<Provider>, NavOnActivityResult, NavActivityLifecycle, Toolbar.OnMenuItemClickListener, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardDetailPage.class.getName();

    // browse and select image for question image
    private static final int BROWSE_FOR_QUESTION_IMAGE = 1;
    // browse and select image for answer image
    private static final int BROWSE_FOR_ANSWER_IMAGE = 2;
    // camera result for question image
    private static final int CAMERA_FOR_QUESTION_IMAGE = 3;
    // camera result for answer image
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
        initTextWatcher();
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        ViewGroup containerImageQuestion = rootLayout.findViewById(R.id.container_image_question);
        ViewGroup containerImageAnswer = rootLayout.findViewById(R.id.container_image_answer);
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
        Button questionMoreActionButton = rootLayout.findViewById(R.id.button_question_more_action);
        questionMoreActionButton.setOnClickListener(this);
        Button answerMoreActionButton = rootLayout.findViewById(R.id.button_answer_more_action);
        answerMoreActionButton.setOnClickListener(this);
        Button questionVoiceButton = rootLayout.findViewById(R.id.button_question_voice);
        questionVoiceButton.setOnClickListener(this);
        Button questionDeleteVoiceButton = rootLayout.findViewById(R.id.button_question_delete_voice);
        questionDeleteVoiceButton.setOnClickListener(this);
        ViewGroup voiceQuestionContainer = rootLayout.findViewById(R.id.container_voice_question);
        EditText editTextQuestion = rootLayout.findViewById(R.id.text_input_edit_question);
        EditText editTextAnswer = rootLayout.findViewById(R.id.text_input_edit_answer);
        if (mCard != null) {
            editTextQuestion.setText(mCard.question);
            editTextAnswer.setText(mCard.answer);
        }
        editTextQuestion.addTextChangedListener(mQuestionTextWatcher);
        editTextAnswer.addTextChangedListener(mAnswerTextWatcher);
        mRxDisposer
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextQuestion.setError(s);
                            } else {
                                editTextQuestion.setError(null);
                            }
                        }));
        mRxDisposer
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextAnswer.setError(s);
                            } else {
                                editTextAnswer.setError(null);
                            }
                        }));
        mRxDisposer
                .add("createView_questionImageChanged",
                        mQuestionImageFileSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        File file = fileOpt.get();
                                        questionImageView.setImageURI(Uri.fromFile(file));
                                        containerImageQuestion.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        containerImageQuestion.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_answerImageChanged",
                        mAnswerImageFileSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        File file = fileOpt.get();
                                        answerImageView.setImageURI(Uri.fromFile(file));
                                        containerImageAnswer.setVisibility(View.VISIBLE);
                                    } else {
                                        answerImageView.setImageURI(null);
                                        containerImageAnswer.setVisibility(View.GONE);
                                    }
                                }));
        mRxDisposer
                .add("createView_questionVoiceChanged",
                        mQuestionVoiceSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fileOpt -> {
                                    if (fileOpt.isPresent()) {
                                        voiceQuestionContainer.setVisibility(View.VISIBLE);
                                    } else {
                                        voiceQuestionContainer.setVisibility(View.GONE);
                                    }
                                }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (mQuestionImageFileSubject != null) {
            mQuestionImageFileSubject.onComplete();
            mQuestionImageFileSubject = null;
        }
        if (mAnswerImageFileSubject != null) {
            mAnswerImageFileSubject.onComplete();
            mAnswerImageFileSubject = null;
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
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
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
                    // Leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Leave blank
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

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
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
                Uri questionImageUri = questionImageFile != null ? Uri.fromFile(questionImageFile) : null;
                Uri answerImageUri = answerImageFile != null ? Uri.fromFile(answerImageFile) : null;
                Uri questionVoiceUri = questionVoiceFile != null ? Uri.fromFile(questionVoiceFile) : null;
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
                                                        answerImageUri, questionVoiceUri)
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
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_question_more_action) {
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
        provider.get(ExecutorService.class)
                .execute(() -> {
                    try {
                        File resultFile = mFileHelper
                                .createImageTempFile(fullPhotoUri);
                        setAnswerImageFileSubject(resultFile);
                    } catch (IOException e) {
                        mLogger
                                .e(TAG, e.getMessage(), e);
                    }
                });
    }

    public void setQuestionImageFile(Provider provider, Uri fullPhotoUri) {
        provider.get(ExecutorService.class)
                .execute(() -> {
                    try {
                        File resultFile = mFileHelper
                                .createImageTempFile(fullPhotoUri);
                        setQuestionImageFileSubject(resultFile);
                    } catch (IOException e) {
                        mLogger
                                .e(TAG, e.getMessage(), e);
                    }
                });
    }

    @Override
    public void onResume(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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

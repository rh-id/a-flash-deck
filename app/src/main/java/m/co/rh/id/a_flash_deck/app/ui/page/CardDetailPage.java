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
import m.co.rh.id.a_flash_deck.base.BaseApplication;
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
import m.co.rh.id.aprovider.Provider;

public class CardDetailPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener, View.OnClickListener, PopupMenu.OnMenuItemClickListener, NavOnActivityResult {
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
    @NavInject
    private transient NavRoute mNavRoute;
    private Card mCard;
    private transient Provider mSvProvider;
    private transient NewCardCmd mNewCardCmd;
    private transient TextWatcher mQuestionTextWatcher;
    private transient TextWatcher mAnswerTextWatcher;
    private transient BehaviorSubject<Optional<File>> mQuestionImageFileSubject;
    private transient BehaviorSubject<Optional<File>> mAnswerImageFileSubject;
    private File mTempCameraFile;

    public CardDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_card_detail);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
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

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(IStatefulViewProvider.class);
        initTextWatcher();
        if (mCard.questionImage != null && !mCard.questionImage.isEmpty()) {
            setQuestionImageFileSubject(mSvProvider.get(FileHelper.class).getCardQuestionImage(mCard.questionImage));
        } else {
            if (mQuestionImageFileSubject == null) {
                setQuestionImageFileSubject(null);
            }
        }
        if (mCard.answerImage != null && !mCard.answerImage.isEmpty()) {
            setAnswerImageFileSubject(mSvProvider.get(FileHelper.class).getCardAnswerImage(mCard.answerImage));
        } else {
            if (mAnswerImageFileSubject == null) {
                setAnswerImageFileSubject(null);
            }
        }
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
        EditText editTextQuestion = rootLayout.findViewById(R.id.text_input_edit_question);
        EditText editTextAnswer = rootLayout.findViewById(R.id.text_input_edit_answer);
        if (mCard != null) {
            editTextQuestion.setText(mCard.question);
            editTextAnswer.setText(mCard.answer);
        }
        editTextQuestion.addTextChangedListener(mQuestionTextWatcher);
        editTextAnswer.addTextChangedListener(mAnswerTextWatcher);
        if (isUpdate()) {
            mNewCardCmd = mSvProvider.get(UpdateCardCmd.class);
        } else {
            mNewCardCmd = mSvProvider.get(NewCardCmd.class);
        }
        mSvProvider.get(RxDisposer.class)
                .add("createView_questionValid",
                        mNewCardCmd
                                .getQuestionValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextQuestion.setError(s);
                            } else {
                                editTextQuestion.setError(null);
                            }
                        }));
        mSvProvider.get(RxDisposer.class)
                .add("createView_answerValid",
                        mNewCardCmd
                                .getAnswerValid().subscribe(s -> {
                            if (!s.isEmpty()) {
                                editTextAnswer.setError(s);
                            } else {
                                editTextAnswer.setError(null);
                            }
                        }));
        mSvProvider.get(RxDisposer.class)
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
        mSvProvider.get(RxDisposer.class)
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
                Uri questionImageUri = questionImageFile != null ? Uri.fromFile(questionImageFile) : null;
                Uri answerImageUri = answerImageFile != null ? Uri.fromFile(answerImageFile) : null;
                mSvProvider.get(RxDisposer.class).add("onClick_newCardCmd_execute",
                        mNewCardCmd.execute(mCard)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((card, throwable) -> {
                                    ILogger iLogger = mSvProvider.get(ILogger.class);
                                    if (throwable != null) {
                                        iLogger.e(TAG,
                                                errorMessage);
                                    } else {
                                        iLogger.i(TAG,
                                                successMessage);
                                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                                        compositeDisposable.add(
                                                mNewCardCmd.saveImage(card, questionImageUri,
                                                        answerImageUri)
                                                        .subscribe((card1, throwable1) -> {
                                                            if (throwable1 != null) {
                                                                String message = throwable1.getMessage();
                                                                if (throwable1.getCause() instanceof ValidationException) {
                                                                    message = throwable1.getCause().getMessage();
                                                                }
                                                                mSvProvider.get(ILogger.class)
                                                                        .e(TAG, message, throwable1);
                                                            } else {
                                                                mSvProvider.get(ILogger.class).d(TAG, "Image added/updated success for " + card1.question);
                                                            }
                                                            compositeDisposable.dispose();
                                                        })
                                        );
                                        mNavigator.pop(Result.withCard(card));
                                    }
                                }));
            } else {
                String validationError = mNewCardCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, validationError);
            }
            return true;
        } else if (id == R.id.menu_question_add_image) {
            UiUtils.browseImage(mNavigator.getActivity(), BROWSE_FOR_QUESTION_IMAGE);
        } else if (id == R.id.menu_question_add_photo) {
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            try {
                mTempCameraFile = fileHelper.createImageTempFile();
                UiUtils.takeImageFromCamera(mNavigator.getActivity(), CAMERA_FOR_QUESTION_IMAGE,
                        mTempCameraFile);
            } catch (Exception e) {
                mSvProvider.get(ILogger.class).e(TAG, e.getMessage(), e);
            }
        } else if (id == R.id.menu_answer_add_image) {
            UiUtils.browseImage(mNavigator.getActivity(), BROWSE_FOR_ANSWER_IMAGE);
        } else if (id == R.id.menu_answer_add_photo) {
            FileHelper fileHelper = mSvProvider.get(FileHelper.class);
            try {
                mTempCameraFile = fileHelper.createImageTempFile();
                UiUtils.takeImageFromCamera(mNavigator.getActivity(), CAMERA_FOR_ANSWER_IMAGE,
                        mTempCameraFile);
            } catch (Exception e) {
                mSvProvider.get(ILogger.class).e(TAG, e.getMessage(), e);
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
                        mSvProvider.get(CommonNavConfig.class).args_commonImageView(file));
            }
        } else if (id == R.id.image_answer) {
            File file = mAnswerImageFileSubject.getValue().orElse(null);
            if (file != null) {
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        mSvProvider.get(CommonNavConfig.class).args_commonImageView(file));
            }
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        Provider provider = (Provider) INavigator.getNavConfiguration().getRequiredComponent();
        if (requestCode == BROWSE_FOR_QUESTION_IMAGE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            provider.get(ExecutorService.class)
                    .execute(() -> {
                        try {
                            File resultFile = provider.get(FileHelper.class)
                                    .createImageTempFile(fullPhotoUri);
                            setQuestionImageFileSubject(resultFile);
                        } catch (IOException e) {
                            provider.get(ILogger.class)
                                    .e(TAG, e.getMessage(), e);
                        }
                    });
        } else if (requestCode == BROWSE_FOR_ANSWER_IMAGE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            provider.get(ExecutorService.class)
                    .execute(() -> {
                        try {
                            File resultFile = provider.get(FileHelper.class)
                                    .createImageTempFile(fullPhotoUri);
                            setAnswerImageFileSubject(resultFile);
                        } catch (IOException e) {
                            provider.get(ILogger.class)
                                    .e(TAG, e.getMessage(), e);
                        }
                    });
        } else if (requestCode == CAMERA_FOR_QUESTION_IMAGE && resultCode == Activity.RESULT_OK) {
            File file = mTempCameraFile;
            setQuestionImageFileSubject(file);
            mTempCameraFile = null;
        } else if (requestCode == CAMERA_FOR_ANSWER_IMAGE && resultCode == Activity.RESULT_OK) {
            File file = mTempCameraFile;
            setAnswerImageFileSubject(file);
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

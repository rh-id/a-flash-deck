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
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import java.io.File;
import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.component.AudioPlayer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class TestPage extends StatefulView<Activity> implements RequireNavigator, RequireNavRoute, RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = TestPage.class.getName();

    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient TestStateModifier mTestStateModifier;
    private transient AudioPlayer mAudioPlayer;
    private transient BehaviorSubject<TestState> mTestStateSubject;
    private transient BehaviorSubject<TestState> mShowTestAnswerSubject;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mTestStateModifier = mSvProvider.get(TestStateModifier.class);
        mAudioPlayer = mSvProvider.get(AudioPlayer.class);
        mTestStateSubject = BehaviorSubject.create();
        mShowTestAnswerSubject = BehaviorSubject.create();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(
                        R.layout.page_test, container, false);
        Button buttonPrev = rootLayout.findViewById(R.id.button_previous);
        Button buttonExit = rootLayout.findViewById(R.id.button_exit);
        Button buttonNext = rootLayout.findViewById(R.id.button_next);
        buttonPrev.setOnClickListener(this);
        buttonExit.setOnClickListener(this);
        buttonNext.setOnClickListener(this);
        ImageView questionImageView = rootLayout.findViewById(R.id.image_question);
        questionImageView.setOnClickListener(this);
        ImageView answerImageView = rootLayout.findViewById(R.id.image_answer);
        answerImageView.setOnClickListener(this);
        TextView textQuestion = rootLayout.findViewById(R.id.text_question);
        TextView textAnswer = rootLayout.findViewById(R.id.text_answer);
        textAnswer.setOnClickListener(this);
        Button buttonQuestionVoice = rootLayout.findViewById(R.id.button_question_voice);
        buttonQuestionVoice.setOnClickListener(this);
        TextView textProgress = rootLayout.findViewById(R.id.text_progress);
        Context context = mSvProvider.getContext();
        FileHelper fileHelper = mSvProvider.get(FileHelper.class);
        mRxDisposer
                .add("createView_onTestState",
                        mTestStateSubject.subscribe(
                                testState -> {
                                    Card card = testState.currentCard();
                                    String progress = (testState.getCurrentCardIndex() + 1) + " / " + testState.getTotalCards();
                                    textQuestion.setText(HtmlCompat.fromHtml(card.question, HtmlCompat.FROM_HTML_MODE_LEGACY));
                                    textQuestion.setMovementMethod(LinkMovementMethod.getInstance());
                                    answerImageView.setImageURI(null);
                                    answerImageView.setVisibility(View.GONE);
                                    textAnswer.setText(context.getString(R.string.tap_to_view_answer));
                                    if (card.questionImage != null) {
                                        questionImageView.setImageURI(Uri.fromFile(
                                                fileHelper.getCardQuestionImage(card.questionImage)
                                        ));
                                        questionImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        questionImageView.setVisibility(View.GONE);
                                    }
                                    if (card.questionVoice != null) {
                                        buttonQuestionVoice.setVisibility(View.VISIBLE);
                                    } else {
                                        buttonQuestionVoice.setVisibility(View.GONE);
                                    }
                                    textProgress.setText(progress);
                                    buttonPrev.setEnabled(testState.getCurrentCardIndex() != 0);
                                    buttonNext.setEnabled(testState.getCurrentCardIndex() != testState.getTotalCards() - 1);
                                }
                        )
                );
        mRxDisposer
                .add("createView_getActiveTest",
                        mTestStateModifier.getActiveTest()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((testStateOpt, throwable) -> {
                                    Context svContext = mSvProvider.getContext();
                                    ILogger iLogger = mSvProvider.get(ILogger.class);
                                    if (throwable != null) {
                                        iLogger.e(TAG, svContext
                                                .getString(R.string.error_loading_test), throwable);
                                        mNavigator.pop();
                                    } else {
                                        if (testStateOpt.isPresent()) {
                                            TestState testState = testStateOpt.get();
                                            mTestStateSubject.onNext(testState);
                                        } else {
                                            String title = svContext.getString(R.string.title_error);
                                            String content = svContext.getString(R.string.error_test_not_found);
                                            CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
                                            mNavigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    commonNavConfig.args_commonMessageDialog(title, content));
                                            iLogger.d(TAG, content);
                                        }
                                    }
                                })
                );
        mRxDisposer
                .add("createView_onShowAnswer",
                        mShowTestAnswerSubject.subscribe(
                                testState -> {

                                }
                        ));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mTestStateSubject != null) {
            mTestStateSubject.onComplete();
            mTestStateSubject = null;
        }
        if (mShowTestAnswerSubject != null) {
            mShowTestAnswerSubject.onComplete();
            mShowTestAnswerSubject = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        TestState testState = mTestStateSubject.getValue();
        Card card = testState.currentCard();
        ILogger iLogger = mSvProvider.get(ILogger.class);
        CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
        FileHelper fileHelper = mSvProvider.get(FileHelper.class);
        Context context = mSvProvider.getContext();
        if (id == R.id.text_answer) {
            View rootView = mNavigator.findView(mNavRoute);
            ImageView answerImageView = rootView.findViewById(R.id.image_answer);
            TextView textAnswer = rootView.findViewById(R.id.text_answer);
            if (card.answerImage != null) {
                answerImageView.setImageURI(Uri.fromFile(
                        fileHelper.getCardAnswerImage(card.answerImage)
                ));
                answerImageView.setVisibility(View.VISIBLE);
            } else {
                answerImageView.setImageURI(null);
                answerImageView.setVisibility(View.GONE);
            }
            textAnswer.setText(HtmlCompat.fromHtml(card.answer, HtmlCompat.FROM_HTML_MODE_LEGACY));
            textAnswer.setMovementMethod(LinkMovementMethod.getInstance());
        } else if (id == R.id.button_previous) {
            mRxDisposer
                    .add("onCLick_buttonPrevious",
                            mTestStateModifier
                                    .previousCard(testState).subscribe((testState1, throwable) -> {
                                if (throwable != null) {
                                    iLogger.e(TAG, context.getString(R.string.error_failed_to_get_previous_card));
                                } else {
                                    mTestStateSubject.onNext(testState1);
                                }
                            })
                    );
        } else if (id == R.id.button_exit) {
            String title = context.getString(R.string.title_confirm);
            String content = context.getString(R.string.confirm_exit_test);
            mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                    commonNavConfig.args_commonBooleanDialog(title, content),
                    (navigator, navRoute, activity, currentView) -> {
                        Serializable serializable = navRoute.getRouteResult();
                        if (serializable instanceof Boolean) {
                            if ((Boolean) serializable) {
                                CompositeDisposable compositeDisposable = new CompositeDisposable();
                                compositeDisposable.add(mTestStateModifier
                                        .stopTest(testState)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe((testState1, throwable) -> {
                                            if (throwable != null) {
                                                iLogger.e(TAG, context.getString(R.string.error_failed_to_exit_test));
                                            }
                                            navigator.pop();
                                            compositeDisposable.dispose();
                                        })
                                );
                            }
                        }
                    });
        } else if (id == R.id.button_next) {
            mRxDisposer
                    .add("onClick_buttonNext",
                            mTestStateModifier
                                    .nextCard(testState)
                                    .subscribe((testState1, throwable) -> {
                                        if (throwable != null) {
                                            iLogger.e(TAG, context.getString(R.string.error_failed_to_get_next_card));
                                        } else {
                                            mTestStateSubject.onNext(testState1);
                                        }
                                    })
                    );
        } else if (id == R.id.image_question) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            fileHelper.getCardQuestionImage(card.questionImage)));
        } else if (id == R.id.image_answer) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            fileHelper.getCardAnswerImage(card.answerImage)));
        } else if (id == R.id.button_question_voice) {
            File file = fileHelper.getCardQuestionVoice(card.questionVoice);
            mAudioPlayer.play(Uri.fromFile(file));
        }
    }
}

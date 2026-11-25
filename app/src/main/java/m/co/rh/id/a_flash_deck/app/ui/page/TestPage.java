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
import android.net.Uri;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.modifier.TestStateModifier;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.AudioService;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.McqOptions;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.a_flash_deck.base.model.TestMode;
import m.co.rh.id.a_flash_deck.base.model.TestState;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.bot.provider.component.BotAnalytics;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class TestPage extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = TestPage.class.getName();
    private static final String UTTERANCE_ID_QUESTION = "tts_question";
    private static final String UTTERANCE_ID_ANSWER = "tts_answer";

    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient TestStateModifier mTestStateModifier;
    private transient AudioService mAudioService;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient DeckDao mDeckDao;
    private transient ExecutorService mExecutorService;
    // decks of the test's cards, prefetched once the test is loaded so the
    // deck TTS language can be resolved synchronously on click/autoplay
    private transient Map<Long, Deck> mDeckByIdMap;
    // dedupe key of the last autoplay-spoken card (index + id), the first
    // emission counts so the first card is spoken on test open
    private transient int mLastAutoplayCardIndex;
    private transient Long mLastAutoplayCardId;
    private transient MarkdownRenderer mMarkdownRenderer;
    private transient BotAnalytics mBotAnalytics;
    private transient BehaviorSubject<TestState> mTestStateSubject;
    private transient boolean mIsGradeInFlight;
    // TTS buttons kept as fields so the ttsEnabled setting toggle can
    // refresh their visibility outside the test-state subscription
    private transient Button mButtonQuestionTts;
    private transient Button mButtonAnswerTts;
    // mirror of the current card's answer-reveal state, reset on every
    // card change; TestState exposes no getter for it
    private transient boolean mIsAnswerRevealed;
    // MCQ views kept as fields so the state reset, the option rendering
    // subscription and the click handler all agree on the same rows.
    // mMcqOptionViews/mMcqOptionRowViews/mMcqStatusIcons are (re)created
    // defensively in createView: the navigator's snapshot-restore path
    // deserializes this page instance without running field initializers,
    // so they can arrive null here
    private transient TextView mTextMcqHint;
    private transient ViewGroup mMcqContainer;
    private transient TextView[] mMcqOptionViews;
    // clickable option row wrappers (status icon + option text), the click
    // listeners live here, not on the text views
    private transient View[] mMcqOptionRowViews;
    // per-row answer status icons (check/cross), shown on option tap
    private transient ImageView[] mMcqStatusIcons;
    // holds the mapped grade (GRADE_GOOD/GRADE_AGAIN) of the tapped MCQ
    // option until the user presses NEXT; UI-only, not persisted (a kill
    // mid-reveal resumes as an unanswered card, same as self-grade reveals)
    private transient Integer mPendingMcqGrade;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mTestStateModifier = mSvProvider.get(TestStateModifier.class);
        mAudioService = mSvProvider.get(AudioService.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        mDeckDao = mSvProvider.get(DeckDao.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mDeckByIdMap = new HashMap<>();
        mLastAutoplayCardIndex = -1;
        mLastAutoplayCardId = null;
        mMarkdownRenderer = mSvProvider.get(MarkdownRenderer.class);
        mBotAnalytics = mSvProvider.get(BotAnalytics.class);
        mTestStateSubject = BehaviorSubject.create();
        mIsGradeInFlight = false;
        mIsAnswerRevealed = false;
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
        Button buttonGradeAgain = rootLayout.findViewById(R.id.button_grade_again);
        Button buttonGradeHard = rootLayout.findViewById(R.id.button_grade_hard);
        Button buttonGradeGood = rootLayout.findViewById(R.id.button_grade_good);
        Button buttonGradeEasy = rootLayout.findViewById(R.id.button_grade_easy);
        buttonGradeAgain.setOnClickListener(this);
        buttonGradeHard.setOnClickListener(this);
        buttonGradeGood.setOnClickListener(this);
        buttonGradeEasy.setOnClickListener(this);
        ViewGroup containerGradeButtons = rootLayout.findViewById(R.id.container_grade_buttons);
        ImageView questionImageView = rootLayout.findViewById(R.id.image_question);
        questionImageView.setOnClickListener(this);
        ImageView answerImageView = rootLayout.findViewById(R.id.image_answer);
        answerImageView.setOnClickListener(this);
        TextView textQuestion = rootLayout.findViewById(R.id.text_question);
        TextView textAnswer = rootLayout.findViewById(R.id.text_answer);
        Button buttonQuestionVoice = rootLayout.findViewById(R.id.button_question_voice);
        buttonQuestionVoice.setOnClickListener(this);
        Button buttonAnswerVoice = rootLayout.findViewById(R.id.button_answer_voice);
        buttonAnswerVoice.setOnClickListener(this);
        Button buttonQuestionTts = rootLayout.findViewById(R.id.button_question_tts);
        buttonQuestionTts.setOnClickListener(this);
        mButtonQuestionTts = buttonQuestionTts;
        Button buttonAnswerTts = rootLayout.findViewById(R.id.button_answer_tts);
        buttonAnswerTts.setOnClickListener(this);
        mButtonAnswerTts = buttonAnswerTts;
        TextView textMcqHint = rootLayout.findViewById(R.id.text_mcq_hint);
        mTextMcqHint = textMcqHint;
        ViewGroup mcqContainer = rootLayout.findViewById(R.id.scroll_mcq_options);
        mMcqContainer = mcqContainer;
        // snapshot restore deserializes this page and re-runs createView
        // without field initializers, so recreate the arrays when they are
        // null. Click listeners live on the row wrappers (icon + text), the
        // text views and status icons are tracked in parallel arrays
        if (mMcqOptionViews == null) {
            mMcqOptionViews = new TextView[4];
        }
        mMcqOptionViews[0] = rootLayout.findViewById(R.id.text_option_1);
        mMcqOptionViews[1] = rootLayout.findViewById(R.id.text_option_2);
        mMcqOptionViews[2] = rootLayout.findViewById(R.id.text_option_3);
        mMcqOptionViews[3] = rootLayout.findViewById(R.id.text_option_4);
        if (mMcqOptionRowViews == null) {
            mMcqOptionRowViews = new View[4];
        }
        View optionRow1 = rootLayout.findViewById(R.id.container_option_1);
        View optionRow2 = rootLayout.findViewById(R.id.container_option_2);
        View optionRow3 = rootLayout.findViewById(R.id.container_option_3);
        View optionRow4 = rootLayout.findViewById(R.id.container_option_4);
        mMcqOptionRowViews[0] = optionRow1;
        mMcqOptionRowViews[1] = optionRow2;
        mMcqOptionRowViews[2] = optionRow3;
        mMcqOptionRowViews[3] = optionRow4;
        optionRow1.setOnClickListener(this);
        optionRow2.setOnClickListener(this);
        optionRow3.setOnClickListener(this);
        optionRow4.setOnClickListener(this);
        if (mMcqStatusIcons == null) {
            mMcqStatusIcons = new ImageView[4];
        }
        mMcqStatusIcons[0] = rootLayout.findViewById(R.id.image_option_status_1);
        mMcqStatusIcons[1] = rootLayout.findViewById(R.id.image_option_status_2);
        mMcqStatusIcons[2] = rootLayout.findViewById(R.id.image_option_status_3);
        mMcqStatusIcons[3] = rootLayout.findViewById(R.id.image_option_status_4);
        TextView textProgress = rootLayout.findViewById(R.id.text_progress);
        Context context = mSvProvider.getContext();
        CardMediaStore cardMediaStore = mSvProvider.get(CardMediaStore.class);
        mRxDisposer
                .add("createView_onTestState",
                        mTestStateSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                testState -> {
                                    Card card = testState.currentCard();
                                    String progress = (testState.getCurrentCardIndex() + 1) + " / " + testState.getTotalCards();

                                    final String questionText, questionImage, questionVoice;
                                    if (card.isReversed) {
                                        questionText = card.answer;
                                        questionImage = card.answerImage;
                                        questionVoice = card.answerVoice;
                                    } else {
                                        questionText = card.question;
                                        questionImage = card.questionImage;
                                        questionVoice = card.questionVoice;
                                    }

                                    if (questionImage != null) {
                                        questionImageView.setImageURI(Uri.fromFile(
                                                card.isReversed ? cardMediaStore.getCardAnswerImage(questionImage) : cardMediaStore.getCardQuestionImage(questionImage)
                                        ));
                                        questionImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        questionImageView.setVisibility(View.GONE);
                                    }

                                    if (questionVoice != null) {
                                        buttonQuestionVoice.setVisibility(View.VISIBLE);
                                    } else {
                                        buttonQuestionVoice.setVisibility(View.GONE);
                                    }

                                    // TTS is an always-available feature, gated
                                    // only on the user setting and a non-empty text
                                    if (mAppSharedPreferences.isTtsEnabled()
                                            && questionText != null && !questionText.isEmpty()) {
                                        buttonQuestionTts.setVisibility(View.VISIBLE);
                                    } else {
                                        buttonQuestionTts.setVisibility(View.GONE);
                                    }

                                    // Reset answer part
                                    textAnswer.setText(context.getString(R.string.tap_to_view_answer));
                                    textAnswer.setOnClickListener(this);
                                    answerImageView.setVisibility(View.GONE);
                                    answerImageView.setImageURI(null);
                                    buttonAnswerVoice.setVisibility(View.GONE);
                                    buttonAnswerTts.setVisibility(View.GONE);
                                    containerGradeButtons.setVisibility(View.GONE);
                                    mIsAnswerRevealed = false;

                                    // MCQ mode replaces the tap-to-reveal answer
                                    // with a hint and clickable option rows
                                    McqOptions mcqOptions = testState.getTestMode() == TestMode.MULTIPLE_CHOICE
                                            ? testState.getMcqOptions(card.id) : null;
                                    if (mcqOptions != null) {
                                        // MCQ: hide tap-to-reveal + answer media,
                                        // show hint + options; a state re-emission
                                        // invalidates any pending MCQ selection
                                        mPendingMcqGrade = null;
                                        textAnswer.setVisibility(View.GONE);
                                        textAnswer.setOnClickListener(null);
                                        // reset the hint: an earlier reveal on
                                        // this slot may have switched it to the
                                        // "Tap NEXT" prompt
                                        textMcqHint.setText(context.getString(R.string.mcq_choose_answer));
                                        textMcqHint.setVisibility(View.VISIBLE);
                                        mcqContainer.setVisibility(View.VISIBLE);
                                        restoreMcqOptions();
                                    } else {
                                        // classic: a state re-emission also
                                        // invalidates any pending MCQ selection
                                        mPendingMcqGrade = null;
                                        mcqContainer.setVisibility(View.GONE);
                                        textMcqHint.setVisibility(View.GONE);
                                        textAnswer.setVisibility(View.VISIBLE);
                                    }

                                    textProgress.setText(progress);
                                    buttonPrev.setEnabled(testState.getCurrentCardIndex() != 0);
                                    // position-based rule only: while an MCQ
                                    // grade is pending, the option-tap handler
                                    // force-enables NEXT itself, and both reset
                                    // paths above clear the pending grade before
                                    // this line, so it is always null here
                                    buttonNext.setEnabled(testState.getCurrentCardIndex() != testState.getTotalCards() - 1);
                                    buttonExit.setEnabled(true);

                                    // Autoplay speaks only on an actual card
                                    // change (dedupe on index + id), unrelated
                                    // state refreshes must not re-trigger
                                    if (mAppSharedPreferences.isTtsAutoplayEnabled()
                                            && (testState.getCurrentCardIndex() != mLastAutoplayCardIndex
                                            || !Objects.equals(card.id, mLastAutoplayCardId))) {
                                        mLastAutoplayCardIndex = testState.getCurrentCardIndex();
                                        mLastAutoplayCardId = card.id;
                                        speakWithTts(questionText, card, UTTERANCE_ID_QUESTION);
                                    }
                                }
                        )
                );
        // Parse the question markdown off the main thread. switchMapSingle
        // disposes the previous in-flight parse on prev/next navigation, so the
        // question always matches the current card. Guard against the card
        // having changed before the parse completes.
        // Live-refresh the TTS buttons when the user toggles the setting.
        // skip(1) drops the seeded current value, initial visibility is
        // handled by the test-state subscription above
        mRxDisposer.add("createView_ttsEnabled",
                mAppSharedPreferences.getTtsEnabledSubject()
                        .skip(1)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleTtsEnabledChanged));
        mRxDisposer
                .add("createView_onQuestionText",
                        mTestStateSubject
                                .switchMapSingle(testState -> {
                                    Card card = testState.currentCard();
                                    String questionText = card.isReversed ? card.answer : card.question;
                                    return Single.zip(
                                            Single.just(testState),
                                            mMarkdownRenderer.parseAsync(questionText),
                                            (ts, spanned) -> new Object[]{ts, spanned});
                                })
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(result -> {
                                    TestState ts = (TestState) result[0];
                                    if (ts == mTestStateSubject.getValue()) {
                                        mMarkdownRenderer.applyParsedMarkdown(textQuestion,
                                                (Spanned) result[1]);
                                    }
                                }, throwable -> {
                                    ILogger iLogger = mSvProvider.get(ILogger.class);
                                    iLogger.e(TAG, context.getString(R.string.error_loading_deck), throwable);
                                    TestState ts = mTestStateSubject.getValue();
                                    if (ts != null) {
                                        Card card = ts.currentCard();
                                        textQuestion.setText(card.isReversed ? card.answer : card.question);
                                    }
                                }));
        // Render the MCQ option texts with markdown, mirroring the question's
        // parse pattern: switchMapSingle disposes the previous in-flight
        // parse on prev/next navigation, and the state/options reference
        // guard drops results for a state that has since changed. The first
        // zipped source carries the state + options the parse was started
        // for. In self-grade mode (or when the card has no options) it idles
        // on Single.never() until the next state emission.
        mRxDisposer
                .add("createView_onMcqOptions",
                        mTestStateSubject
                                .switchMapSingle(testState -> {
                                    Card card = testState.currentCard();
                                    McqOptions mcqOptions = testState.getTestMode() == TestMode.MULTIPLE_CHOICE
                                            ? testState.getMcqOptions(card.id) : null;
                                    if (mcqOptions == null) {
                                        return Single.never();
                                    }
                                    List<Single<Object[]>> sources = new ArrayList<>();
                                    sources.add(Single.fromCallable(
                                            () -> new Object[]{testState, mcqOptions}));
                                    for (String optionText : mcqOptions.getOptionTexts()) {
                                        sources.add(mMarkdownRenderer.parseAsync(optionText)
                                                .map(spanned -> new Object[]{spanned}));
                                    }
                                    return Single.zip(sources, objects -> objects);
                                })
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(results -> {
                                    Object[] meta = (Object[]) results[0];
                                    applyMcqOptionSpans((TestState) meta[0],
                                            (McqOptions) meta[1], results);
                                }, throwable -> {
                                    ILogger iLogger = mSvProvider.get(ILogger.class);
                                    iLogger.e(TAG, context.getString(R.string.error_loading_deck), throwable);
                                    TestState ts = mTestStateSubject.getValue();
                                    if (ts == null) {
                                        return;
                                    }
                                    Card card = ts.currentCard();
                                    McqOptions mcqOptions = ts.getTestMode() == TestMode.MULTIPLE_CHOICE
                                            ? ts.getMcqOptions(card.id) : null;
                                    if (mcqOptions != null) {
                                        applyMcqOptionTexts(mcqOptions.getOptionTexts());
                                    }
                                }));
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
                                            // Prefetch the decks of the test's
                                            // cards so the deck TTS language can
                                            // be resolved synchronously. The test
                                            // state is published only after the
                                            // decks are loaded so autoplay on
                                            // open speaks with the resolved
                                            // language; on lookup failure it
                                            // falls back to the system default.
                                            List<Long> deckIds = new ArrayList<>();
                                            for (Card cardItem : testState.getCards()) {
                                                if (cardItem.deckId != null
                                                        && !deckIds.contains(cardItem.deckId)) {
                                                    deckIds.add(cardItem.deckId);
                                                }
                                            }
                                            mRxDisposer.add("createView_prefetchTtsDecks",
                                                    Single.fromCallable(() ->
                                                            mDeckDao.findDeckByIds(deckIds))
                                                            .subscribeOn(Schedulers.from(mExecutorService))
                                                            .observeOn(AndroidSchedulers.mainThread())
                                                            .subscribe(decks -> {
                                                                Map<Long, Deck> deckByIdMap = new HashMap<>();
                                                                for (Deck deck : decks) {
                                                                    deckByIdMap.put(deck.id, deck);
                                                                }
                                                                mDeckByIdMap = deckByIdMap;
                                                                mTestStateSubject.onNext(testState);
                                                            }, throwable1 -> {
                                                                iLogger.e(TAG, svContext
                                                                        .getString(R.string.error_loading_deck), throwable1);
                                                                mTestStateSubject.onNext(testState);
                                                            }));
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
        return rootLayout;
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
        if (mTestStateSubject != null) {
            mTestStateSubject.onComplete();
            mTestStateSubject = null;
        }
        mButtonQuestionTts = null;
        mButtonAnswerTts = null;
        mTextMcqHint = null;
        mMcqContainer = null;
        mPendingMcqGrade = null;
        if (mMcqOptionViews != null) {
            for (int i = 0; i < mMcqOptionViews.length; i++) {
                mMcqOptionViews[i] = null;
            }
            mMcqOptionViews = null;
        }
        if (mMcqOptionRowViews != null) {
            for (int i = 0; i < mMcqOptionRowViews.length; i++) {
                mMcqOptionRowViews[i] = null;
            }
            mMcqOptionRowViews = null;
        }
        if (mMcqStatusIcons != null) {
            for (int i = 0; i < mMcqStatusIcons.length; i++) {
                mMcqStatusIcons[i] = null;
            }
            mMcqStatusIcons = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        TestState testState = mTestStateSubject.getValue();
        if (testState == null) {
            return;
        }
        if (mIsGradeInFlight && (id == R.id.button_exit
                || id == R.id.image_question || id == R.id.image_answer
                || id == R.id.button_question_voice || id == R.id.button_answer_voice
                || id == R.id.button_question_tts || id == R.id.button_answer_tts
                || id == R.id.container_option_1 || id == R.id.container_option_2
                || id == R.id.container_option_3 || id == R.id.container_option_4)) {
            return;
        }
        Card card = testState.currentCard();
        ILogger iLogger = mSvProvider.get(ILogger.class);
        CommonNavConfig commonNavConfig = mSvProvider.get(CommonNavConfig.class);
        CardMediaStore cardMediaStore = mSvProvider.get(CardMediaStore.class);
        Context context = mSvProvider.getContext();
        if (id == R.id.text_answer) {
            revealAnswer(testState, card, view.getRootView(), false);
        } else if (id == R.id.container_option_1 || id == R.id.container_option_2
                || id == R.id.container_option_3 || id == R.id.container_option_4) {
            if (mMcqOptionViews == null || mMcqOptionRowViews == null
                    || mMcqStatusIcons == null) {
                return;
            }
            McqOptions mcqOptions = testState.getTestMode() == TestMode.MULTIPLE_CHOICE
                    ? testState.getMcqOptions(card.id) : null;
            if (mcqOptions == null) {
                return;
            }
            int tappedIndex = optionIndexOf(id);
            if (tappedIndex < 0 || tappedIndex >= mcqOptions.getOptionTexts().size()) {
                return;
            }
            boolean correct = tappedIndex == mcqOptions.getCorrectIndex();
            // disable all rows immediately — the MCQ counterpart of hiding
            // the grade buttons, prevents double-taps
            for (View mcqOptionRowView : mMcqOptionRowViews) {
                if (mcqOptionRowView != null) {
                    mcqOptionRowView.setOnClickListener(null);
                }
            }
            // tapped row: green check when correct, red cross when wrong; on a
            // wrong tap the correct row also shows a green check so the real
            // answer stays visible; all rows keep full opacity, the icons are
            // the only feedback
            for (int i = 0; i < mMcqStatusIcons.length; i++) {
                ImageView icon = mMcqStatusIcons[i];
                if (icon == null) continue;
                if (i == tappedIndex) {
                    icon.setImageResource(correct ? R.drawable.ic_mcq_correct : R.drawable.ic_mcq_wrong);
                    icon.setVisibility(View.VISIBLE);
                } else if (!correct && i == mcqOptions.getCorrectIndex()) {
                    icon.setImageResource(R.drawable.ic_mcq_correct);
                    icon.setVisibility(View.VISIBLE);
                } else {
                    icon.setVisibility(View.GONE);
                }
            }
            // defer the grade until NEXT is pressed: the status icons + reveal
            // stay on screen, the mapped grade is held in mPendingMcqGrade and
            // applied by the NEXT handler
            View rootView = view.getRootView();
            revealAnswer(testState, card, rootView, true);
            mPendingMcqGrade = correct ? ReviewScheduler.GRADE_GOOD
                    : ReviewScheduler.GRADE_AGAIN;
            // NEXT must stay reachable on the last card, where it completes
            // the session via handleGrade
            Button buttonNext = rootView.findViewById(R.id.button_next);
            buttonNext.setEnabled(true);
            mTextMcqHint.setText(context.getString(R.string.mcq_tap_next_to_continue));
        } else if (id == R.id.button_previous) {
            // going back abandons any pending MCQ grade: the state emission
            // from previousCard re-runs the observer which resets the option
            // icons/alphas/listeners, leaving the card ungraded
            mPendingMcqGrade = null;
            mAudioService.stopSpeech();
            mRxDisposer
                    .add("onCLick_buttonPrevious",
                            mTestStateModifier
                                    .previousCard(testState).subscribe((testState1, throwable) -> {
                                if (throwable != null) {
                                    iLogger.e(TAG, context.getString(R.string.error_failed_to_get_previous_card));
                                    mTestStateSubject.onNext(testState);
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
                                // stopTest discards the whole session, drop
                                // any pending MCQ grade for cleanliness
                                mPendingMcqGrade = null;
                                mAudioService.stopSpeech();
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
            if (mPendingMcqGrade != null) {
                // MCQ: the option tap only revealed the answer, the mapped
                // grade is applied here; handleGrade stops speech, disables
                // nav while in flight, and handles session-complete plus the
                // per-card error restore
                int grade = mPendingMcqGrade;
                mPendingMcqGrade = null;
                handleGrade(testState, grade, view.getRootView());
            } else {
                mAudioService.stopSpeech();
                mRxDisposer
                        .add("onClick_buttonNext",
                                mTestStateModifier
                                        .nextCard(testState)
                                        .subscribe((testState1, throwable) -> {
                                            if (throwable != null) {
                                                iLogger.e(TAG, context.getString(R.string.error_failed_to_get_next_card));
                                                mTestStateSubject.onNext(testState);
                                            } else {
                                                mTestStateSubject.onNext(testState1);
                                            }
                                        })
                        );
            }
        } else if (id == R.id.button_grade_again || id == R.id.button_grade_hard
                || id == R.id.button_grade_good || id == R.id.button_grade_easy) {
            int grade = (id == R.id.button_grade_again) ? ReviewScheduler.GRADE_AGAIN
                    : (id == R.id.button_grade_hard) ? ReviewScheduler.GRADE_HARD
                    : (id == R.id.button_grade_good) ? ReviewScheduler.GRADE_GOOD
                    : ReviewScheduler.GRADE_EASY;
            handleGrade(testState, grade, view.getRootView());
        } else if (id == R.id.image_question) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            card.isReversed ? cardMediaStore.getCardAnswerImage(card.answerImage) :
                                    cardMediaStore.getCardQuestionImage(card.questionImage)));
        } else if (id == R.id.image_answer) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    commonNavConfig.args_commonImageView(
                            card.isReversed ? cardMediaStore.getCardQuestionImage(card.questionImage) :
                                    cardMediaStore.getCardAnswerImage(card.answerImage)));
        } else if (id == R.id.button_question_voice) {
            File file = card.isReversed ? cardMediaStore.getCardAnswerVoice(card.answerVoice) :
                    cardMediaStore.getCardQuestionVoice(card.questionVoice);
            mAudioService.playRecording(Uri.fromFile(file));
        } else if (id == R.id.button_answer_voice) {
            File file = card.isReversed ? cardMediaStore.getCardQuestionVoice(card.questionVoice) :
                    cardMediaStore.getCardAnswerVoice(card.answerVoice);
            mAudioService.playRecording(Uri.fromFile(file));
        } else if (id == R.id.button_question_tts) {
            speakWithTts(card.isReversed ? card.answer : card.question, card, UTTERANCE_ID_QUESTION);
        } else if (id == R.id.button_answer_tts) {
            speakWithTts(card.isReversed ? card.question : card.answer, card, UTTERANCE_ID_ANSWER);
        }
    }

    /**
     * Reveals the current card's answer: side-aware answer image/voice
     * visibility, answer TTS button visibility, answer analytics and answer
     * timing. In self-grade mode it also parses the answer markdown into
     * text_answer and shows the grade buttons. In MCQ mode the markdown step
     * is skipped (the highlighted correct option row already shows the
     * answer text) and the grade buttons stay hidden, but the answer
     * image/voice/TTS are still surfaced.
     */
    private void revealAnswer(TestState testState, Card card, View rootView, boolean mcqMode) {
        ImageView answerImageView = rootView.findViewById(R.id.image_answer);
        TextView textAnswer = rootView.findViewById(R.id.text_answer);
        Button buttonAnswerVoice = rootView.findViewById(R.id.button_answer_voice);
        Button buttonAnswerTts = rootView.findViewById(R.id.button_answer_tts);
        ViewGroup containerGradeButtons = rootView.findViewById(R.id.container_grade_buttons);
        CardMediaStore cardMediaStore = mSvProvider.get(CardMediaStore.class);
        ILogger iLogger = mSvProvider.get(ILogger.class);
        Context context = mSvProvider.getContext();
        final String answerText;
        if (card.isReversed) {
            answerText = card.question;
            if (card.questionImage != null) {
                answerImageView.setImageURI(Uri.fromFile(
                        cardMediaStore.getCardQuestionImage(card.questionImage)
                ));
                answerImageView.setVisibility(View.VISIBLE);
            } else {
                answerImageView.setImageURI(null);
                answerImageView.setVisibility(View.GONE);
            }
            if (card.questionVoice != null) {
                buttonAnswerVoice.setVisibility(View.VISIBLE);
            }
        } else {
            answerText = card.answer;
            if (card.answerImage != null) {
                answerImageView.setImageURI(Uri.fromFile(
                        cardMediaStore.getCardAnswerImage(card.answerImage)
                ));
                answerImageView.setVisibility(View.VISIBLE);
            } else {
                answerImageView.setImageURI(null);
                answerImageView.setVisibility(View.GONE);
            }
            if (card.answerVoice != null) {
                buttonAnswerVoice.setVisibility(View.VISIBLE);
            }
        }
        textAnswer.setOnClickListener(null);
        if (!mcqMode) {
            containerGradeButtons.setVisibility(View.VISIBLE);
        }
        // follows the answer voice button lifecycle: shown on reveal
        if (mAppSharedPreferences.isTtsEnabled()
                && answerText != null && !answerText.isEmpty()) {
            buttonAnswerTts.setVisibility(View.VISIBLE);
        } else {
            buttonAnswerTts.setVisibility(View.GONE);
        }
        if (!mcqMode) {
            // Parse the answer markdown off the main thread. Keyed so a second
            // tap (or navigation) disposes any in-flight parse.
            mRxDisposer
                    .add("onClick_revealAnswer",
                            mMarkdownRenderer.parseAsync(answerText)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(spanned -> {
                                        Card current = testState.currentCard();
                                        if (current != null && current.id.equals(card.id)) {
                                            mMarkdownRenderer.applyParsedMarkdown(textAnswer, spanned);
                                        }
                                    }, throwable -> iLogger.e(TAG,
                                            context.getString(R.string.error_loading_deck), throwable)));
        }
        mBotAnalytics.trackOpenTestAnswer(card.id);
        testState.markAnswerRevealed();
        mIsAnswerRevealed = true;
    }

    /**
     * Applies the given grade to the current card: hides the grade buttons,
     * stops any ongoing speech, disables navigation while the grade is in
     * flight and re-emits the updated state on completion. Error restore is
     * per current card: a card with generated MCQ options clears the option
     * icons/alphas and re-enables the option rows, a card without options
     * re-shows the grade buttons. Session-complete pops the page and shows
     * the summary dialog.
     */
    private void handleGrade(TestState testState, int grade, View rootView) {
        ILogger iLogger = mSvProvider.get(ILogger.class);
        Context context = mSvProvider.getContext();
        ViewGroup containerGradeButtons = rootView.findViewById(R.id.container_grade_buttons);
        Button buttonPrev = rootView.findViewById(R.id.button_previous);
        Button buttonNext = rootView.findViewById(R.id.button_next);
        // hide immediately to prevent double-taps
        containerGradeButtons.setVisibility(View.GONE);
        // stop any ongoing speech, also covers the session-complete path
        mAudioService.stopSpeech();
        mIsGradeInFlight = true;
        // disable nav buttons while the grade is in flight,
        // a concurrent next/previous would double-advance the test
        buttonPrev.setEnabled(false);
        buttonNext.setEnabled(false);
        mRxDisposer.add("onClick_grade",
                mTestStateModifier.gradeCurrentCard(testState, grade)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((gradeResult, throwable) -> {
                            if (throwable != null) {
                                mIsGradeInFlight = false;
                                iLogger.e(TAG, context.getString(R.string.error_saving_card_grade), throwable);
                                mTestStateSubject.onNext(testState);
                                // restore per current card's options, not per
                                // mode: a self-grade fallback card inside an
                                // MCQ test (no generated options) must get its
                                // grade buttons back, an MCQ card must not
                                Card currentCard = testState.currentCard();
                                McqOptions mcqOptions = testState.getTestMode() == TestMode.MULTIPLE_CHOICE
                                        ? testState.getMcqOptions(currentCard.id) : null;
                                if (mcqOptions != null) {
                                    restoreMcqOptions();
                                } else {
                                    containerGradeButtons.setVisibility(View.VISIBLE);
                                }
                                // position-aware restore, Next must stay disabled on the last card
                                buttonPrev.setEnabled(testState.getCurrentCardIndex() != 0);
                                buttonNext.setEnabled(testState.getCurrentCardIndex() != testState.getTotalCards() - 1);
                            } else if (gradeResult.sessionComplete) {
                                mIsGradeInFlight = false;
                                // capture the summary before stopTest deletes the session state
                                int[] gradeCounts = gradeResult.testState.getGradeCounts();
                                int totalAnswers = gradeResult.testState.getTotalAnswers();
                                long elapsedMs = gradeResult.testState.getElapsedMs();
                                CompositeDisposable compositeDisposable = new CompositeDisposable();
                                compositeDisposable.add(mTestStateModifier.stopTest(gradeResult.testState)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe((testState2, throwable1) -> {
                                            if (throwable1 != null) {
                                                iLogger.e(TAG, context.getString(R.string.error_failed_to_exit_test), throwable1);
                                            }
                                            mNavigator.pop();
                                            String title = context.getString(R.string.test_complete);
                                            mNavigator.push(Routes.TEST_SUMMARY_DIALOG,
                                                    TestSummarySVDialog.Args.newArgs(
                                                            title, gradeCounts, totalAnswers, elapsedMs));
                                            compositeDisposable.dispose();
                                        }));
                            } else {
                                mIsGradeInFlight = false;
                                buttonPrev.setEnabled(true);
                                buttonNext.setEnabled(true);
                                mTestStateSubject.onNext(gradeResult.testState);
                            }
                        }));
    }

    /**
     * Clears the MCQ answer feedback (status icons) and re-enables the
     * option row click listeners on the wrapper containers, used by the
     * state reset and to recover from a failed grade in MCQ mode. Rows
     * without an option stay hidden.
     */
    private void restoreMcqOptions() {
        if (mMcqOptionViews == null) {
            return;
        }
        TestState testState = mTestStateSubject.getValue();
        McqOptions mcqOptions = null;
        if (testState != null && testState.getTestMode() == TestMode.MULTIPLE_CHOICE) {
            Card card = testState.currentCard();
            if (card != null && card.id != null) {
                mcqOptions = testState.getMcqOptions(card.id);
            }
        }
        List<String> optionTexts = mcqOptions == null
                ? Collections.emptyList() : mcqOptions.getOptionTexts();
        for (int i = 0; i < mMcqOptionViews.length; i++) {
            TextView mcqOptionView = mMcqOptionViews[i];
            if (mcqOptionView == null) {
                continue;
            }
            // defensive reset, the icons are the only feedback these days
            mcqOptionView.setAlpha(1f);
            View rowView = optionRowViewAt(i);
            if (rowView != null) {
                rowView.setVisibility(i < optionTexts.size() ? View.VISIBLE : View.GONE);
                rowView.setOnClickListener(this);
            }
        }
        if (mMcqStatusIcons == null) {
            return;
        }
        for (ImageView statusIcon : mMcqStatusIcons) {
            if (statusIcon == null) {
                continue;
            }
            statusIcon.setImageDrawable(null);
            statusIcon.setVisibility(View.GONE);
        }
    }

    /**
     * Applies the parsed markdown spans of the MCQ options to the option
     * rows, dropping stale results when the test state or the options
     * changed while the parse was in flight
     */
    private void applyMcqOptionSpans(TestState testState, McqOptions mcqOptions, Object[] results) {
        if (mMcqOptionViews == null || testState != mTestStateSubject.getValue()) {
            return;
        }
        Card card = testState.currentCard();
        McqOptions currentOptions = testState.getTestMode() == TestMode.MULTIPLE_CHOICE
                ? testState.getMcqOptions(card.id) : null;
        if (currentOptions != mcqOptions) {
            return;
        }
        List<String> optionTexts = mcqOptions.getOptionTexts();
        for (int i = 0; i < mMcqOptionViews.length; i++) {
            TextView mcqOptionView = mMcqOptionViews[i];
            View rowView = optionRowViewAt(i);
            if (mcqOptionView == null || rowView == null) {
                continue;
            }
            // results[0] is the state/options payload, spans start at 1
            if (i < optionTexts.size() && (i + 1) < results.length) {
                mcqOptionView.setText((Spanned) ((Object[]) results[i + 1])[0]);
                rowView.setVisibility(View.VISIBLE);
            } else {
                rowView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Fallback applying the raw option texts without markdown parsing, used
     * when the markdown parse of the option texts fails
     */
    private void applyMcqOptionTexts(List<String> optionTexts) {
        if (mMcqOptionViews == null) {
            return;
        }
        for (int i = 0; i < mMcqOptionViews.length; i++) {
            TextView mcqOptionView = mMcqOptionViews[i];
            View rowView = optionRowViewAt(i);
            if (mcqOptionView == null || rowView == null) {
                continue;
            }
            if (i < optionTexts.size()) {
                mcqOptionView.setText(optionTexts.get(i));
                rowView.setVisibility(View.VISIBLE);
            } else {
                rowView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * @return the clickable option row wrapper at the given index, null
     * when unavailable (snapshot-restore path) or out of bounds
     */
    private View optionRowViewAt(int index) {
        if (mMcqOptionRowViews == null || index < 0 || index >= mMcqOptionRowViews.length) {
            return null;
        }
        return mMcqOptionRowViews[index];
    }

    /**
     * @return the 0-based option index of the given option row view id,
     * -1 when the id is not an option row
     */
    private int optionIndexOf(int viewId) {
        if (viewId == R.id.container_option_1) return 0;
        if (viewId == R.id.container_option_2) return 1;
        if (viewId == R.id.container_option_3) return 2;
        if (viewId == R.id.container_option_4) return 3;
        return -1;
    }

    /**
     * Speaks the given text with the TTS language of the card's deck (system
     * default when the deck is unknown or has no language set), stopping any
     * ongoing audio first. Shows a toast when the TTS engine is unavailable.
     */
    private void speakWithTts(String text, Card card, String utteranceId) {
        if (!mAppSharedPreferences.isTtsEnabled() || text == null || text.isEmpty()) {
            return;
        }
        // speak the plain-text extraction, raw markdown/LaTeX markup is
        // noise when spoken; a strip to empty (e.g. math-only card) is a
        // silent no-op
        String speakable = mMarkdownRenderer.toSpeakableText(text);
        if (speakable.isEmpty()) {
            return;
        }
        Deck deck = card.deckId == null ? null : mDeckByIdMap.get(card.deckId);
        boolean spoken = mAudioService.speak(speakable, utteranceId,
                mAudioService.resolveLocale(deck == null ? null : deck.ttsLanguage));
        if (!spoken) {
            Context context = mSvProvider.getContext();
            Toast.makeText(context.getApplicationContext(),
                    context.getString(R.string.tts_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Live TTS-button refresh on the user setting toggle: turning TTS off
     * hides both buttons and stops any ongoing speech, turning it on
     * re-evaluates visibility from the current test state (the answer button
     * only reappears while the current answer is still revealed).
     */
    private void handleTtsEnabledChanged(boolean ttsEnabled) {
        Button buttonQuestionTts = mButtonQuestionTts;
        Button buttonAnswerTts = mButtonAnswerTts;
        if (buttonQuestionTts == null || buttonAnswerTts == null) {
            return;
        }
        if (!ttsEnabled) {
            mAudioService.stopSpeech();
            buttonQuestionTts.setVisibility(View.GONE);
            buttonAnswerTts.setVisibility(View.GONE);
            return;
        }
        TestState testState = mTestStateSubject.getValue();
        Card card = testState == null ? null : testState.currentCard();
        String questionText = card == null ? null
                : (card.isReversed ? card.answer : card.question);
        if (questionText != null && !questionText.isEmpty()) {
            buttonQuestionTts.setVisibility(View.VISIBLE);
        } else {
            buttonQuestionTts.setVisibility(View.GONE);
        }
        String answerText = card == null ? null
                : (card.isReversed ? card.question : card.answer);
        if (mIsAnswerRevealed && answerText != null && !answerText.isEmpty()) {
            buttonAnswerTts.setVisibility(View.VISIBLE);
        } else {
            buttonAnswerTts.setVisibility(View.GONE);
        }
    }
}

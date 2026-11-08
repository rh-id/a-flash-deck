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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

import java.io.File;
import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.DeckQueryCmd;
import m.co.rh.id.a_flash_deck.app.ui.component.card.CardItemSV;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.AudioService;
import m.co.rh.id.a_flash_deck.base.component.MarkdownRenderer;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.CardMediaStore;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class CardShowPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = CardShowPage.class.getName();
    private static final String UTTERANCE_ID_QUESTION = "tts_question";

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient CardMediaStore mCardMediaStore;
    private transient CommonNavConfig mCommonNavConfig;
    private transient AudioService mAudioService;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient DeckQueryCmd mDeckQueryCmd;
    private transient MarkdownRenderer mMarkdownRenderer;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient BehaviorSubject<Card> mCardStateSubject;
    // kept as a field so the ttsEnabled setting toggle can refresh the
    // button visibility outside the card-state subscription
    private transient Button mButtonQuestionTts;
    private Card mCard;

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mCard = args.mCard;
        }
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mCardMediaStore = mSvProvider.get(CardMediaStore.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mAudioService = mSvProvider.get(AudioService.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        // query command, used by speakQuestionTts's deck-language lookup
        mDeckQueryCmd = mSvProvider.get(DeckQueryCmd.class);
        mMarkdownRenderer = mSvProvider.get(MarkdownRenderer.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        setCardSubject(mCard);
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(
                        R.layout.page_card_show, container, false);
        ImageView questionImageView = rootLayout.findViewById(R.id.image_question);
        questionImageView.setOnClickListener(this);
        ImageView answerImageView = rootLayout.findViewById(R.id.image_answer);
        answerImageView.setOnClickListener(this);
        Button buttonExit = rootLayout.findViewById(R.id.button_exit);
        buttonExit.setOnClickListener(this);
        Button buttonMenu = rootLayout.findViewById(R.id.button_menu);
        buttonMenu.setOnClickListener(this);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        buttonEdit.setOnClickListener(this);
        Button questionVoiceButton = rootLayout.findViewById(R.id.button_question_voice);
        questionVoiceButton.setOnClickListener(this);
        Button questionTtsButton = rootLayout.findViewById(R.id.button_question_tts);
        questionTtsButton.setOnClickListener(this);
        mButtonQuestionTts = questionTtsButton;
        TextView textQuestion = rootLayout.findViewById(R.id.text_question);
        TextView textAnswer = rootLayout.findViewById(R.id.text_answer);
        mRxDisposer
                .add("createView_onCardShow",
                        mCardStateSubject.observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                card -> {
                                    if (mCardMediaStore == null || mAppSharedPreferences == null) {
                                        // post-dispose delivery, the components are
                                        // released; the captured views are detached,
                                        // updating them is harmless so only refs are guarded
                                        return;
                                    }
                                    if (card.questionImage != null) {
                                        File file = mCardMediaStore.getCardQuestionImage(card.questionImage);
                                        questionImageView.setImageURI(Uri.fromFile(file));
                                        questionImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        questionImageView.setImageURI(null);
                                        questionImageView.setVisibility(View.GONE);
                                    }
                                    if (card.answerImage != null) {
                                        File file = mCardMediaStore.getCardAnswerImage(card.answerImage);
                                        answerImageView.setImageURI(Uri.fromFile(file));
                                        answerImageView.setVisibility(View.VISIBLE);
                                    } else {
                                        answerImageView.setImageURI(null);
                                        answerImageView.setVisibility(View.GONE);
                                    }
                                    if (card.questionVoice != null) {
                                        questionVoiceButton.setVisibility(View.VISIBLE);
                                    } else {
                                        questionVoiceButton.setVisibility(View.GONE);
                                    }
                                    // TTS is an always-available feature, gated
                                    // only on the user setting and a non-empty text
                                    if (mAppSharedPreferences.isTtsEnabled()
                                            && card.question != null && !card.question.isEmpty()) {
                                        questionTtsButton.setVisibility(View.VISIBLE);
                                    } else {
                                        questionTtsButton.setVisibility(View.GONE);
                                    }
                                }
                        )
                );
        // Parse markdown off the main thread. switchMapSingle disposes the
        // previous in-flight parse whenever the card changes, so advancing to
        // the next card never shows stale text. applyParsedMarkdown must run on
        // the main thread.
        mRxDisposer
                .add("createView_onCardText",
                        mCardStateSubject
                                .switchMapSingle(card -> Single.zip(
                                        mMarkdownRenderer.parseAsync(card.question),
                                        mMarkdownRenderer.parseAsync(card.answer),
                                        (q, a) -> new Object[]{card, q, a}))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(result -> {
                                    if (mMarkdownRenderer == null || mCardStateSubject == null) {
                                        // post-dispose delivery, the renderer and the
                                        // subject are released, nothing to render into
                                        return;
                                    }
                                    Card card = (Card) result[0];
                                    if (card == mCardStateSubject.getValue()) {
                                        mMarkdownRenderer.applyParsedMarkdown(textQuestion,
                                                (Spanned) result[1]);
                                        mMarkdownRenderer.applyParsedMarkdown(textAnswer,
                                                (Spanned) result[2]);
                                    }
                                }));
        mRxDisposer
                .add("createView_onCardChanged",
                        mDeckChangeNotifier.getUpdatedCardFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(card -> {
                                    if (mCardStateSubject == null) {
                                        // guarded here not inside setCardSubject:
                                        // setCardSubject relies on the null state to
                                        // create the subject on the first createView call
                                        return;
                                    }
                                    mCard = card;
                                    setCardSubject(card);
                                }));
        // Live-refresh the TTS button when the user toggles the setting.
        // skip(1) drops the seeded current value, initial visibility is
        // handled by the card-state subscription above
        mRxDisposer.add("createView_ttsEnabled",
                mAppSharedPreferences.getTtsEnabledSubject()
                        .skip(1)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleTtsEnabledChanged));
        return rootLayout;
    }

    private void setCardSubject(Card card) {
        if (mCardStateSubject == null) {
            if (card == null) {
                mCardStateSubject = BehaviorSubject.create();
            } else {
                mCardStateSubject = BehaviorSubject.createDefault(card);
            }
        } else {
            mCardStateSubject.onNext(card);
        }
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
        if (mCardStateSubject != null) {
            mCardStateSubject.onComplete();
            mCardStateSubject = null;
        }
        mCard = null;
        mLogger = null;
        mRxDisposer = null;
        mCardMediaStore = null;
        mCommonNavConfig = null;
        mAppSharedPreferences = null;
        mDeckQueryCmd = null;
        mMarkdownRenderer = null;
        mDeckChangeNotifier = null;
        mButtonQuestionTts = null;
        mAudioService = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_exit) {
            mNavigator.finishActivity();
        } else if (id == R.id.button_menu) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.page_card_show, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();//showing popup menu
        } else if (id == R.id.button_edit) {
            mNavigator.push(Routes.CARD_DETAIL_PAGE,
                    CardDetailPage.Args.forUpdate(mCard));
        } else if (id == R.id.image_question) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    mCommonNavConfig.args_commonImageView(
                            mCardMediaStore.getCardQuestionImage(mCard.questionImage)));
        } else if (id == R.id.image_answer) {
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    mCommonNavConfig.args_commonImageView(
                            mCardMediaStore.getCardAnswerImage(mCard.answerImage)));
        } else if (id == R.id.button_question_voice) {
            File file = mCardMediaStore.getCardQuestionVoice(mCard.questionVoice);
            mAudioService.playRecording(Uri.fromFile(file));
        } else if (id == R.id.button_question_tts) {
            speakQuestionTts();
        }
    }

    /**
     * Speaks the question text displayed by this page with the TTS language
     * of the card's deck (system default when the deck is unknown or has no
     * language set), stopping any ongoing audio first. Shows a toast when the
     * TTS engine is unavailable.
     */
    private void speakQuestionTts() {
        if (!mAppSharedPreferences.isTtsEnabled()
                || mCard.question == null || mCard.question.isEmpty()) {
            return;
        }
        // speak the plain-text extraction, raw markdown/LaTeX markup is
        // noise when spoken; a strip to empty (e.g. math-only card) is a
        // silent no-op
        String speakable = mMarkdownRenderer.toSpeakableText(mCard.question);
        if (speakable.isEmpty()) {
            return;
        }
        if (mCard.deckId == null) {
            speakTts(speakable, null);
            return;
        }
        mRxDisposer.add("onClick_speakQuestionTts",
                mDeckQueryCmd.getDeckById(mCard.deckId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> speakTts(speakable, deck),
                                throwable -> {
                                    if (mSvProvider != null) {
                                        mLogger.e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_loading_deck), throwable);
                                    }
                                    // fall back to the system default language
                                    speakTts(speakable, null);
                                }));
    }

    private void speakTts(String text, Deck deck) {
        if (mAudioService == null || mSvProvider == null) {
            // post-dispose delivery, the speech components are released,
            // nothing left to speak with
            return;
        }
        boolean spoken = mAudioService.speak(text, UTTERANCE_ID_QUESTION,
                mAudioService.resolveLocale(deck == null ? null : deck.ttsLanguage));
        if (!spoken) {
            Context context = mSvProvider.getContext();
            Toast.makeText(context.getApplicationContext(),
                    context.getString(R.string.tts_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Live TTS-button refresh on the user setting toggle, visibility
     * re-evaluated from the current card.
     */
    private void handleTtsEnabledChanged(boolean ttsEnabled) {
        Button questionTtsButton = mButtonQuestionTts;
        if (questionTtsButton == null) {
            return;
        }
        Card card = mCard;
        if (ttsEnabled && card != null
                && card.question != null && !card.question.isEmpty()) {
            questionTtsButton.setVisibility(View.VISIBLE);
        } else {
            questionTtsButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_move_card) {
            CardItemSV.moveCardAction(mNavigator, mCard.clone());
            return true;
        } else if (id == R.id.menu_copy_card) {
            CardItemSV.copyCardAction(mNavigator, mCard.clone());
            return true;
        }
        return false;
    }

    public static class Args implements Serializable {
        public static Args withCard(Card card) {
            Args args = new Args();
            args.mCard = card;
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

        private Card mCard;

        private Args() {
        }

        public Card getCard() {
            return mCard;
        }
    }
}

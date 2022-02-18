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

package m.co.rh.id.a_flash_deck.app.provider.command;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class NewCardCmd {
    private static final String TAG = NewCardCmd.class.getName();
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected ILogger mLogger;
    protected DeckChangeNotifier mDeckChangeNotifier;
    protected DeckDao mDeckDao;
    protected FileHelper mFileHelper;
    protected BehaviorSubject<String> mDeckIdValidSubject;
    protected BehaviorSubject<String> mQuestionValidSubject;
    protected BehaviorSubject<String> mAnswerValidSubject;

    public NewCardCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mDeckChangeNotifier = provider.get(DeckChangeNotifier.class);
        mDeckDao = provider.get(DeckDao.class);
        mFileHelper = provider.get(FileHelper.class);
        mDeckIdValidSubject = BehaviorSubject.create();
        mQuestionValidSubject = BehaviorSubject.create();
        mAnswerValidSubject = BehaviorSubject.create();
    }

    public boolean valid(Card card) {
        boolean isValid = false;
        if (card != null) {
            boolean deckIdValid = false;
            boolean questionValid = false;
            boolean answerValid = false;
            if (card.deckId != null) {
                deckIdValid = true;
                mDeckIdValidSubject.onNext("");
            } else {
                mDeckIdValidSubject.onNext(mAppContext.getString(R.string.deck_id_is_required));
            }
            if (card.question != null && !card.question.isEmpty()) {
                questionValid = true;
                mQuestionValidSubject.onNext("");
            } else {
                mQuestionValidSubject.onNext(mAppContext.getString(R.string.question_is_required));
            }
            if (card.answer != null && !card.answer.isEmpty()) {
                answerValid = true;
                mAnswerValidSubject.onNext("");
            } else {
                mAnswerValidSubject.onNext(mAppContext.getString(R.string.answer_is_required));
            }
            isValid = deckIdValid && questionValid && answerValid;
        }
        return isValid;
    }

    public Single<Card> execute(Card card) {
        return Single.fromFuture(
                mExecutorService.submit(() -> {
                    mDeckDao.insertCard(card);
                    mDeckChangeNotifier.cardAdded(card);
                    return card;
                })
        );
    }

    public Single<Card> saveFiles(Card card, Uri questionImage, Uri answerImage, Uri questionVoice) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    if (questionImage != null) {
                        try {
                            File questionImageFile = mFileHelper.createCardQuestionImage(questionImage);
                            card.questionImage = questionImageFile.getName();
                            mFileHelper.createCardQuestionImageThumbnail(questionImage,
                                    questionImageFile.getName());
                        } catch (Exception e) {
                            mLogger.d(TAG, e.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_save_question_image));
                        }
                    } else {
                        card.questionImage = null;
                    }
                    if (answerImage != null) {
                        try {
                            File answerImageFile = mFileHelper.createCardAnswerImage(answerImage);
                            card.answerImage = answerImageFile.getName();
                            mFileHelper.createCardAnswerImageThumbnail(answerImage,
                                    answerImageFile.getName());
                        } catch (Exception e) {
                            mLogger.d(TAG, e.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_save_answer_image));
                        }
                    } else {
                        card.answerImage = null;
                    }
                    if (questionVoice != null) {
                        try {
                            File file = mFileHelper.createCardQuestionVoice(questionVoice);
                            card.questionVoice = file.getName();
                        } catch (Exception e) {
                            mLogger.d(TAG, e.getMessage(), e);
                            throw new ValidationException(mAppContext.getString(R.string.error_failed_to_save_question_voice));
                        }
                    } else {
                        card.questionVoice = null;
                    }
                    mDeckDao.updateCard(card);
                    mDeckChangeNotifier.cardUpdated(card);
                    return card;
                })
        );
    }

    public Single<Integer> countDeck() {
        return Single.fromFuture(
                mExecutorService.submit(() -> mDeckDao.countDeck())
        );
    }

    public String getValidationError() {
        String deckIdValid = mDeckIdValidSubject.getValue();
        if (deckIdValid != null && !deckIdValid.isEmpty()) {
            return deckIdValid;
        }
        String questionValid = mQuestionValidSubject.getValue();
        if (questionValid != null && !questionValid.isEmpty()) {
            return questionValid;
        }
        String answerValid = mAnswerValidSubject.getValue();
        if (answerValid != null && !answerValid.isEmpty()) {
            return answerValid;
        }
        return "";
    }

    public Flowable<String> getDeckIdValid() {
        return Flowable.fromObservable(mDeckIdValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getQuestionValid() {
        return Flowable.fromObservable(mQuestionValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getAnswerValid() {
        return Flowable.fromObservable(mAnswerValidSubject, BackpressureStrategy.BUFFER);
    }
}

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

import android.net.Uri;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.CopyCardEvent;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class CopyCardCmd {
    protected ExecutorService mExecutorService;
    protected ILogger mLogger;
    protected FileHelper mFileHelper;
    protected DeckChangeNotifier mDeckChangeNotifier;
    protected DeckDao mDeckDao;
    protected NewCardCmd mNewCardCmd;

    public CopyCardCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mDeckChangeNotifier = provider.get(DeckChangeNotifier.class);
        mDeckDao = provider.get(DeckDao.class);
        mNewCardCmd = provider.get(NewCardCmd.class);
    }

    public Single<CopyCardEvent> execute(CopyCardEvent copyCardEvent) {
        return Single.fromCallable(() -> {
                    Card card = copyCardEvent.getCopyCard();
                    Uri questionImageUri;
                    if (card.questionImage != null) {
                        questionImageUri = Uri.fromFile(mFileHelper.getCardQuestionImage(card.questionImage));
                    } else {
                        questionImageUri = null;
                    }
                    Uri answerImageUri;
                    if (card.answerImage != null) {
                        answerImageUri = Uri.fromFile(mFileHelper.getCardAnswerImage(card.answerImage));
                    } else {
                        answerImageUri = null;
                    }
                    Uri questionVoiceUri;
                    if (card.questionVoice != null) {
                        questionVoiceUri = Uri.fromFile(mFileHelper.getCardQuestionVoice(card.questionVoice));
                    } else {
                        questionVoiceUri = null;
                    }
                    mDeckDao.copyCardToDeck(card, copyCardEvent.getDestinationDeck());
                    return new CopyCardData(copyCardEvent, card, questionImageUri, answerImageUri, questionVoiceUri);
                })
                .subscribeOn(Schedulers.from(mExecutorService))
                .flatMap(data -> mNewCardCmd.saveFiles(data.card, data.questionImageUri, data.answerImageUri, data.questionVoiceUri)
                        .map(card -> {
                            mDeckChangeNotifier.cardAdded(card);
                            return data.copyCardEvent;
                        }));
    }

    private static class CopyCardData {
        final CopyCardEvent copyCardEvent;
        final Card card;
        final Uri questionImageUri;
        final Uri answerImageUri;
        final Uri questionVoiceUri;

        CopyCardData(CopyCardEvent copyCardEvent, Card card, Uri questionImageUri, Uri answerImageUri, Uri questionVoiceUri) {
            this.copyCardEvent = copyCardEvent;
            this.card = card;
            this.questionImageUri = questionImageUri;
            this.answerImageUri = answerImageUri;
            this.questionVoiceUri = questionVoiceUri;
        }
    }
}

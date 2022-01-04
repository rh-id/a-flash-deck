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
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.CopyCardEvent;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class CopyCardCmd {
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ILogger> mLogger;
    protected ProviderValue<FileHelper> mFileHelper;
    protected ProviderValue<DeckChangeNotifier> mDeckChangeNotifier;
    protected ProviderValue<DeckDao> mDeckDao;
    protected ProviderValue<NewCardCmd> mNewCardCmd;

    public CopyCardCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mLogger = provider.lazyGet(ILogger.class);
        mFileHelper = provider.lazyGet(FileHelper.class);
        mDeckChangeNotifier = provider.lazyGet(DeckChangeNotifier.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mNewCardCmd = provider.lazyGet(NewCardCmd.class);
    }

    public Single<CopyCardEvent> execute(CopyCardEvent copyCardEvent) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    Card card = copyCardEvent.getCopyCard();
                    Uri questionImageUri;
                    if (card.questionImage != null) {
                        questionImageUri = Uri.fromFile(mFileHelper.get().getCardQuestionImage(card.questionImage));
                    } else {
                        questionImageUri = null;
                    }
                    Uri answerImageUri;
                    if (card.answerImage != null) {
                        answerImageUri = Uri.fromFile(mFileHelper.get().getCardAnswerImage(card.answerImage));
                    } else {
                        answerImageUri = null;
                    }
                    Uri questionVoiceUri;
                    if (card.questionVoice != null) {
                        questionVoiceUri = Uri.fromFile(mFileHelper.get().getCardQuestionVoice(card.questionVoice));
                    } else {
                        questionVoiceUri = null;
                    }
                    mDeckDao.get().copyCardToDeck(card, copyCardEvent.getDestinationDeck());
                    mNewCardCmd.get().saveFiles(card, questionImageUri, answerImageUri, questionVoiceUri)
                            .blockingGet();
                    mDeckChangeNotifier.get().cardAdded(card);
                    return copyCardEvent;
                })
        );
    }
}

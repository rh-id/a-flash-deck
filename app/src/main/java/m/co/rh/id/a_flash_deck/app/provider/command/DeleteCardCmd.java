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

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class DeleteCardCmd {
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<DeckDao> mDeckDao;
    private ProviderValue<FileHelper> mFileHelper;
    private ProviderValue<DeckChangeNotifier> mDeckChangeNotifier;

    public DeleteCardCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mFileHelper = provider.lazyGet(FileHelper.class);
        mDeckChangeNotifier = provider.lazyGet(DeckChangeNotifier.class);
    }

    public Single<Card> execute(Card card) {
        return Single.fromFuture(mExecutorService.get().submit(() -> {
            mDeckDao.get().deleteCard(card);
            mDeckChangeNotifier.get().cardDeleted(card);
            return card;
        }));
    }
}

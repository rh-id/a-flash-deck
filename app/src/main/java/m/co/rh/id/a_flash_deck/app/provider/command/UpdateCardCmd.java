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

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.aprovider.Provider;

public class UpdateCardCmd extends NewCardCmd {
    public UpdateCardCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<Card> execute(Card card) {
        return Single.fromFuture(
                mExecutorService.submit(() -> {
                    mDeckDao.updateCard(card);
                    mDeckChangeNotifier.cardUpdated(card);
                    return card;
                })
        );
    }
}

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

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.aprovider.Provider;

public class UpdateDeckCmd extends NewDeckCmd {
    private static final String TAG = UpdateDeckCmd.class.getName();

    public UpdateDeckCmd(Context context, Provider provider) {
        super(context, provider);
    }

    @Override
    public Single<Deck> execute(Deck deck) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    mDeckDao.get().updateDeck(deck);
                    mDeckChangeNotifier.get().deckUpdated(deck);
                    return deck;
                })
        );
    }
}

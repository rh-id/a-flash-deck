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

package m.co.rh.id.a_flash_deck.timer.provider.command;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NotificationTimerQueryCmd {
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<DeckDao> mDeckDao;

    public NotificationTimerQueryCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
    }

    public Single<ArrayList<Deck>> getSelectedDecks(NotificationTimer notificationTimer) {
        return Single.fromFuture(mExecutorService.get().submit(() ->
                {
                    try {
                        JSONArray jsonArray = new JSONArray(notificationTimer.selectedDeckIds);
                        int size = jsonArray.length();
                        List<Long> deckIds = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            deckIds.add(jsonArray.getLong(i));
                        }
                        List<Deck> deckList = mDeckDao.get().findDeckByIds(deckIds);
                        return new ArrayList<>(deckList);
                    } catch (JSONException jsonException) {
                        throw new RuntimeException(jsonException);
                    }
                })
        );
    }
}

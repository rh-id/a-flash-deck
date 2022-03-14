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

package m.co.rh.id.a_flash_deck.bot.provider.notifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.bot.dao.SuggestedCardDao;
import m.co.rh.id.a_flash_deck.bot.entity.SuggestedCard;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class SuggestedCardChangeNotifier {
    private ExecutorService mExecutorService;
    private ProviderValue<SuggestedCardDao> mSuggestedCardDao;
    private BehaviorSubject<List<SuggestedCard>> mSuggestedCardSubject;

    public SuggestedCardChangeNotifier(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSuggestedCardDao = provider.lazyGet(SuggestedCardDao.class);
        mSuggestedCardSubject = BehaviorSubject.createDefault(new ArrayList<>());
        init();
    }

    private void init() {
        reloadSuggestedCard();
    }

    public void reloadSuggestedCard() {
        mExecutorService.execute(() -> {
            List<SuggestedCard> suggestedCardList = mSuggestedCardDao.get().findAllSuggestedCards();
            mSuggestedCardSubject.onNext(suggestedCardList);
        });
    }

    public Flowable<List<SuggestedCard>> getSuggestedCardFlow() {
        return Flowable.fromObservable(mSuggestedCardSubject, BackpressureStrategy.BUFFER);
    }

    public List<SuggestedCard> getSuggestedCard() {
        return mSuggestedCardSubject.getValue();
    }
}

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

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewDeckCmd {
    private static final String TAG = NewDeckCmd.class.getName();

    protected Context mAppContext;
    protected ProviderValue<ExecutorService> mExecutorService;
    protected ProviderValue<ILogger> mLogger;
    protected ProviderValue<DeckDao> mDeckDao;
    protected final BehaviorSubject<String> mNameValidationSubject;

    public NewDeckCmd(Context context, Provider provider) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mLogger = provider.lazyGet(ILogger.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mNameValidationSubject = BehaviorSubject.create();
    }

    public boolean valid(Deck deck) {
        boolean valid = false;
        if (deck != null) {
            if (deck.name == null || deck.name.isEmpty()) {
                mNameValidationSubject.onNext(mAppContext.getString(R.string.name_is_required));
            } else {
                valid = true;
                mNameValidationSubject.onNext("");
            }
        }
        return valid;
    }

    public Single<Deck> execute(Deck deck) {
        return Single.fromFuture(
                mExecutorService.get().submit(() -> {
                    mDeckDao.get().insertDeck(deck);
                    return deck;
                })
        );
    }

    // validation message
    public Flowable<String> getNameValidation() {
        return Flowable.fromObservable(mNameValidationSubject, BackpressureStrategy.BUFFER);
    }
}

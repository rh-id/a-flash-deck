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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.CardShowActivity;
import m.co.rh.id.a_flash_deck.base.constants.IntentKeys;
import m.co.rh.id.a_flash_deck.base.constants.Shortcuts;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

public class AppShortcutHandler implements ProviderDisposable {
    private static final String TAG = AppShortcutHandler.class.getName();
    private Context mAppContext;
    private ProviderValue<ILogger> mLogger;
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<DeckDao> mDeckDao;
    private DeckChangeNotifier mDeckChangeNotifier;
    private BehaviorSubject<Optional<Card>> mCardSubject;
    private CompositeDisposable mCompositeDisposable;

    public AppShortcutHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mDeckChangeNotifier = provider.get(DeckChangeNotifier.class);
        mCardSubject = BehaviorSubject.create();
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        // currently only handling pinned shortcut so do this to save some power.
        // in the future if handling other shortcut, remove this check
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mCompositeDisposable.add(
                    mDeckChangeNotifier.getDeletedDeckFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this::disablePinnedShortcut)
            );
            mCompositeDisposable.add(
                    mDeckChangeNotifier.getUpdatedDeckFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this::updatePinnedShortcut)
            );
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createPinnedShortcut(Deck deck) {
        ShortcutManager shortcutManager = mAppContext.getSystemService(ShortcutManager.class);
        if (shortcutManager.isRequestPinShortcutSupported()) {
            shortcutManager.requestPinShortcut(createShortcutInfo(deck), null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    public ShortcutInfo createShortcutInfo(Deck deck) {
        Intent shortcutIntent = new Intent(mAppContext, CardShowActivity.class);
        shortcutIntent.setAction(Intent.ACTION_CREATE_SHORTCUT);
        shortcutIntent.putExtra(IntentKeys.LONG_DECK_ID, deck.id);
        return new ShortcutInfo.Builder(mAppContext, Shortcuts.ID_SHUFFLE_CARD_WITH_DECK_ID_ + deck.id)
                .setShortLabel(deck.name)
                .setIcon(Icon.createWithResource(mAppContext, R.drawable.ic_shuffle_black))
                .setIntent(shortcutIntent)
                .build();
    }

    public void processShortcut(Intent intent) {
        if (Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            long deckId = intent.getLongExtra(IntentKeys.LONG_DECK_ID, Long.MIN_VALUE);
            if (deckId != Long.MIN_VALUE) {
                dispatchRandomCard(deckId);
            }
        }
    }

    private void disablePinnedShortcut(Deck deck) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = mAppContext.getSystemService(ShortcutManager.class);
            if (shortcutManager.isRequestPinShortcutSupported()) {
                String message = mAppContext.getString(R.string.deck_was_deleted, deck.name);
                shortcutManager.disableShortcuts(Collections.singletonList(Shortcuts.ID_SHUFFLE_CARD_WITH_DECK_ID_ + deck.id),
                        message);
            }
        }
    }

    private void updatePinnedShortcut(Deck deck) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = mAppContext.getSystemService(ShortcutManager.class);
            if (shortcutManager.isRequestPinShortcutSupported()) {
                shortcutManager.updateShortcuts(Collections.singletonList(createShortcutInfo(deck)));
            }
        }
    }


    public void dispatchRandomCard(long deckId) {
        mExecutorService.get().execute(() -> {
            List<Card> cardList = mDeckDao.get().getCardByDeckId(deckId);
            if (!cardList.isEmpty()) {
                if (cardList.size() > 1) {
                    Collections.shuffle(cardList);
                }
                dispatch(cardList.get(0));
            } else {
                String message = mAppContext.getString(R.string.error_no_card_from_deck);
                mLogger.get().i(TAG, message);
            }
        });
    }

    public void dispatch(Card card) {
        mCardSubject.onNext(Optional.ofNullable(card));
    }

    public Flowable<Optional<Card>> getCardFlow() {
        return Flowable.fromObservable(mCardSubject, BackpressureStrategy.BUFFER);
    }

    // FIXME: there should be some way to create queue with RX,
    //  rather than manually clean subject everytime item is processed
    public void clearEvent() {
        mCardSubject.onNext(Optional.empty());
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
        mCompositeDisposable = null;
        mCardSubject.onComplete();
        mCardSubject = null;
    }
}

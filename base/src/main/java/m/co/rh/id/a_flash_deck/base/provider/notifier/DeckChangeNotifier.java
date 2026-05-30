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

package m.co.rh.id.a_flash_deck.base.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.MoveCardEvent;

/**
 * A hub to notify changes in deck records
 */
public class DeckChangeNotifier {
    private Subject<Deck> mAddedDeckSubject;
    private Subject<Deck> mUpdatedDeckSubject;
    private Subject<Deck> mDeletedDeckSubject;
    private Subject<Card> mAddedCardSubject;
    private Subject<Card> mUpdatedCardSubject;
    private Subject<Card> mDeletedCardSubject;
    private Subject<MoveCardEvent> mMovedCardSubject;

    public DeckChangeNotifier() {
        mAddedDeckSubject = PublishSubject.<Deck>create().toSerialized();
        mUpdatedDeckSubject = PublishSubject.<Deck>create().toSerialized();
        mDeletedDeckSubject = PublishSubject.<Deck>create().toSerialized();
        mAddedCardSubject = PublishSubject.<Card>create().toSerialized();
        mUpdatedCardSubject = PublishSubject.<Card>create().toSerialized();
        mDeletedCardSubject = PublishSubject.<Card>create().toSerialized();
        mMovedCardSubject = PublishSubject.<MoveCardEvent>create().toSerialized();
    }

    public void deckAdded(Deck deck) {
        if (deck != null) {
            mAddedDeckSubject.onNext(deck);
        }
    }

    public void deckUpdated(Deck deck) {
        if (deck != null) {
            mUpdatedDeckSubject.onNext(deck);
        }
    }

    public void deckDeleted(Deck deck) {
        if (deck != null) {
            mDeletedDeckSubject.onNext(deck);
        }
    }

    public void cardAdded(Card card) {
        if (card != null) {
            mAddedCardSubject.onNext(card);
        }
    }

    public void cardUpdated(Card card) {
        if (card != null) {
            mUpdatedCardSubject.onNext(card);
        }
    }

    public void cardDeleted(Card card) {
        if (card != null) {
            mDeletedCardSubject.onNext(card);
        }
    }

    public void cardMoved(MoveCardEvent moveCardEvent) {
        if (moveCardEvent != null) {
            mMovedCardSubject.onNext(moveCardEvent);
        }
    }

    public Flowable<Deck> getAddedDeckFlow() {
        return Flowable.fromObservable(mAddedDeckSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Deck> getUpdatedDeckFlow() {
        return Flowable.fromObservable(mUpdatedDeckSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Deck> getDeletedDeckFlow() {
        return Flowable.fromObservable(mDeletedDeckSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Card> getAddedCardFlow() {
        return Flowable.fromObservable(mAddedCardSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Card> getUpdatedCardFlow() {
        return Flowable.fromObservable(mUpdatedCardSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Card> getDeletedCardFlow() {
        return Flowable.fromObservable(mDeletedCardSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<MoveCardEvent> getMovedCardFlow() {
        return Flowable.fromObservable(mMovedCardSubject, BackpressureStrategy.BUFFER);
    }
}

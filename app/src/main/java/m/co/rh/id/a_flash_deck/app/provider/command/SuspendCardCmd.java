/*
 *     Copyright (C) 2021-present Ruby Hartono
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
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.CardSuspendStateChangedEvent;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.repository.StudyRepository;
import m.co.rh.id.aprovider.Provider;

/**
 * Suspends or unsuspends a card (Anki-style): a suspended card is excluded
 * from due-card selection and from the study due count, but remains
 * browsable, editable and exportable.
 */
public class SuspendCardCmd {
    private ExecutorService mExecutorService;
    private StudyRepository mStudyRepository;
    private DeckChangeNotifier mDeckChangeNotifier;

    public SuspendCardCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mStudyRepository = provider.get(StudyRepository.class);
        mDeckChangeNotifier = provider.get(DeckChangeNotifier.class);
    }

    /**
     * Sets the suspended flag of the card's review state.
     * Review-state rows are created lazily (on first grade or first suspend),
     * so suspending a never-studied card creates its row here with
     * dueDateTime still null until the first grade; the study queries treat a
     * null dueDateTime as new/due again after unsuspend.
     * The find-or-create runs in a transaction so a concurrent grade cannot
     * interleave between the read and the upsert.
     */
    public Single<Card> execute(Card card, boolean suspend) {
        return Single.fromCallable(() -> {
            mStudyRepository.suspendCard(card.id, suspend);
            mDeckChangeNotifier.cardSuspendStateChanged(
                    new CardSuspendStateChangedEvent(card, suspend));
            return card;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}

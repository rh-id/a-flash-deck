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
import m.co.rh.id.a_flash_deck.base.model.TestEvent;
import m.co.rh.id.a_flash_deck.base.model.TestState;

public class TestChangeNotifier {
    private PublishSubject<TestEvent> mStartTestSubject;
    private PublishSubject<TestEvent> mStopTestSubject;
    private PublishSubject<TestState> mTestStateChangeSubject;

    public TestChangeNotifier() {
        mStartTestSubject = PublishSubject.create();
        mStopTestSubject = PublishSubject.create();
        mTestStateChangeSubject = PublishSubject.create();
    }

    public void startTest(TestEvent testEvent) {
        if (testEvent != null) {
            mStartTestSubject.onNext(testEvent);
        }
    }

    public void stopTest(TestEvent testEvent) {
        if (testEvent != null) {
            mStopTestSubject.onNext(testEvent);
        }
    }

    public void testStateChange(TestState testState) {
        if (testState != null) {
            mTestStateChangeSubject.onNext(testState);
        }
    }

    public Flowable<TestState> getTestStateChangeFlow() {
        return Flowable.fromObservable(mTestStateChangeSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<TestEvent> getStartTestEventFlow() {
        return Flowable.fromObservable(mStartTestSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<TestEvent> getStopTestEventFlow() {
        return Flowable.fromObservable(mStopTestSubject, BackpressureStrategy.BUFFER);
    }
}

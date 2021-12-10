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

import java.util.AbstractMap;
import java.util.Date;
import java.util.Map;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class NotificationTimeChangeNotifier {
    private PublishSubject<Map.Entry<Date, Date>> mStartEndDateSubject;

    public NotificationTimeChangeNotifier() {
        mStartEndDateSubject = PublishSubject.create();
    }

    public void onDateChanged(Date startDate, Date endDate) {
        mStartEndDateSubject.onNext(new AbstractMap.SimpleImmutableEntry<>(startDate, endDate));
    }

    public Flowable<Map.Entry<Date, Date>> getStartEndDateFlow() {
        return Flowable.fromObservable(mStartEndDateSubject, BackpressureStrategy.BUFFER);
    }
}

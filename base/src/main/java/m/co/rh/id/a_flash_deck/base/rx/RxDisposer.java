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

package m.co.rh.id.a_flash_deck.base.rx;

import android.content.Context;

import co.rh.id.lib.rx3_utils.disposable.UniqueKeyDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer implements ProviderDisposable {
    private UniqueKeyDisposable mUniqueKeyDisposable;

    public RxDisposer() {
        mUniqueKeyDisposable = new UniqueKeyDisposable();
    }

    public void add(String uniqueKey, Disposable disposable) {
        mUniqueKeyDisposable.add(uniqueKey, disposable);
    }

    @Override
    public void dispose(Context context) {
        mUniqueKeyDisposable.dispose();
    }
}

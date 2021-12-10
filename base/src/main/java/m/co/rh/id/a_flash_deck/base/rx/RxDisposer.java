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

import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer implements ProviderDisposable {
    private Map<String, Disposable> disposableMap;

    public RxDisposer() {
        disposableMap = new HashMap<>();
    }

    public void add(String uniqueKey, Disposable disposable) {
        Disposable existing = disposableMap.remove(uniqueKey);
        if (existing != null) {
            existing.dispose();
        }
        disposableMap.put(uniqueKey, disposable);
    }

    public void dispose() {
        if (!disposableMap.isEmpty()) {
            for (Map.Entry<String, Disposable> entry : disposableMap.entrySet()) {
                entry.getValue().dispose();
            }
            disposableMap.clear();
        }
    }

    @Override
    public void dispose(Context context) {
        dispose();
    }
}

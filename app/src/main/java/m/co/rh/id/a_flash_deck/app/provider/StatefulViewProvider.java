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

package m.co.rh.id.a_flash_deck.app.provider;

import android.content.Context;

import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderIsDisposed;
import m.co.rh.id.aprovider.ProviderValue;

public class StatefulViewProvider implements IStatefulViewProvider, ProviderDisposable, ProviderIsDisposed {
    private Provider mProvider;
    private boolean mIsDisposed;

    public StatefulViewProvider(Provider parentProvider) {
        mProvider = Provider.createNestedProvider("StatefulViewProvider", parentProvider,
                parentProvider.getContext(), new StatefulViewProviderModule());
    }

    @Override
    public <I> I get(Class<I> clazz) {
        return mProvider.get(clazz);
    }

    @Override
    public <I> I tryGet(Class<I> clazz) {
        return mProvider.tryGet(clazz);
    }

    @Override
    public <I> ProviderValue<I> lazyGet(Class<I> clazz) {
        return mProvider.lazyGet(clazz);
    }

    @Override
    public <I> ProviderValue<I> tryLazyGet(Class<I> clazz) {
        return mProvider.tryLazyGet(clazz);
    }

    @Override
    public Context getContext() {
        return mProvider.getContext();
    }

    @Override
    public void dispose() {
        if (mIsDisposed) return;
        mIsDisposed = true;
        if (mProvider != null) {
            mProvider.dispose();
            mProvider = null;
        }
    }

    @Override
    public void dispose(Context context) {
        dispose();
    }

    @Override
    public boolean isDisposed() {
        return mIsDisposed;
    }
}

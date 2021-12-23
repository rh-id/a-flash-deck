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

package m.co.rh.id.a_flash_deck.timer.provider;

import android.content.Context;

import m.co.rh.id.a_flash_deck.timer.provider.command.DeleteNotificationTimerCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.NewNotificationTimerCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.NotificationTimerQueryCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.PagedNotificationTimerItemsCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.UpdateNotificationTimerCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class NotificationTimerCmdProviderModule implements ProviderModule {
    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewNotificationTimerCmd.class, () -> new NewNotificationTimerCmd(context, provider));
        providerRegistry.registerLazy(UpdateNotificationTimerCmd.class, () -> new UpdateNotificationTimerCmd(context, provider));
        providerRegistry.registerLazy(NotificationTimerQueryCmd.class, () -> new NotificationTimerQueryCmd(provider));
        providerRegistry.registerLazy(DeleteNotificationTimerCmd.class, () -> new DeleteNotificationTimerCmd(provider));
        providerRegistry.registerLazy(PagedNotificationTimerItemsCmd.class, () -> new PagedNotificationTimerItemsCmd(context, provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}

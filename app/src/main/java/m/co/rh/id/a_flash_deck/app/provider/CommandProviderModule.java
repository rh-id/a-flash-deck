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

import m.co.rh.id.a_flash_deck.app.provider.command.CopyCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeckQueryCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeleteCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.DeleteDeckCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.MoveCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.NewCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.NewDeckCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedCardItemsCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.PagedDeckItemsCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateCardCmd;
import m.co.rh.id.a_flash_deck.app.provider.command.UpdateDeckCmd;
import m.co.rh.id.a_flash_deck.bot.provider.BotCommandProviderModule;
import m.co.rh.id.a_flash_deck.timer.provider.NotificationTimerCmdProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class CommandProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewDeckCmd.class, () -> new NewDeckCmd(provider));
        providerRegistry.registerLazy(UpdateDeckCmd.class, () -> new UpdateDeckCmd(provider));
        providerRegistry.registerLazy(NewCardCmd.class, () -> new NewCardCmd(provider));
        providerRegistry.registerLazy(UpdateCardCmd.class, () -> new UpdateCardCmd(provider));
        providerRegistry.registerLazy(PagedDeckItemsCmd.class, () -> new PagedDeckItemsCmd(provider));
        providerRegistry.registerLazy(DeleteDeckCmd.class, () -> new DeleteDeckCmd(provider));
        providerRegistry.registerLazy(DeckQueryCmd.class, () -> new DeckQueryCmd(provider));
        providerRegistry.registerLazy(PagedCardItemsCmd.class, () -> new PagedCardItemsCmd(provider));
        providerRegistry.registerLazy(DeleteCardCmd.class, () -> new DeleteCardCmd(provider));
        providerRegistry.registerLazy(MoveCardCmd.class, () -> new MoveCardCmd(provider));
        providerRegistry.registerLazy(CopyCardCmd.class, () -> new CopyCardCmd(provider));
        providerRegistry.registerLazy(ExportImportCmd.class, () -> new ExportImportCmd(provider));
        providerRegistry.registerModule(new NotificationTimerCmdProviderModule());
        providerRegistry.registerModule(new BotCommandProviderModule());
    }

    @Override
    public void dispose(Provider provider) {
        // leave blank
    }
}

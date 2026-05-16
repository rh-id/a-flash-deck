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

package m.co.rh.id.a_flash_deck.ai.provider;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_flash_deck.ai.command.GenerateDeckFromTopicCmd;
import m.co.rh.id.a_flash_deck.ai.provider.notifier.ApiKeyChangeNotifier;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.ai.service.GeminiService;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AiProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(ApiKeyManager.class, () ->
                new ApiKeyManager(provider.getContext()));
        providerRegistry.registerLazy(GeminiService.class, () ->
                new GeminiService(
                        provider.get(ApiKeyManager.class),
                        provider.get(ExecutorService.class),
                        provider.getContext()));
        providerRegistry.register(GenerateDeckFromTopicCmd.class, () ->
                new GenerateDeckFromTopicCmd(provider));
        providerRegistry.registerLazy(ApiKeyChangeNotifier.class, ApiKeyChangeNotifier::new);
    }
}
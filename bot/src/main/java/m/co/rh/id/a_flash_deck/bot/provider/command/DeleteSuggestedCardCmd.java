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

package m.co.rh.id.a_flash_deck.bot.provider.command;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_flash_deck.bot.dao.SuggestedCardDao;
import m.co.rh.id.a_flash_deck.bot.provider.notifier.SuggestedCardChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteSuggestedCardCmd {
    private ExecutorService mExecutorService;
    private SuggestedCardDao mSuggestedCardDao;
    private SuggestedCardChangeNotifier mSuggestedCardChangeNotifier;

    public DeleteSuggestedCardCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSuggestedCardDao = provider.get(SuggestedCardDao.class);
        mSuggestedCardChangeNotifier = provider.get(SuggestedCardChangeNotifier.class);
    }

    public void executeDeleteAll() {
        mExecutorService.execute(() -> {
            mSuggestedCardDao.deleteAllSuggestedCard();
            mSuggestedCardChangeNotifier.reloadSuggestedCard();
        });
    }
}

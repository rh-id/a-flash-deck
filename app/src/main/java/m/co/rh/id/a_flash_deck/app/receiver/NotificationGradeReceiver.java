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

package m.co.rh.id.a_flash_deck.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.aprovider.Provider;

/**
 * Receives the Again / Good / Hard grade actions of the timer notification.
 * Android dedupes PendingIntents by requestCode + Intent.filterEquals and
 * ignores extras, so each grade action intent carries a filterEquals-distinct
 * action built from {@link #buildGradeAction(int)} at posting time.
 */
public class NotificationGradeReceiver extends BroadcastReceiver {
    private static final String ACTION_GRADE_PREFIX =
            "m.co.rh.id.a_flash_deck.app.receiver.NotificationGradeReceiver.GRADE_";

    /**
     * @return a filterEquals-distinct action string for the given grade value
     * ({@code ReviewScheduler.GRADE_AGAIN} .. {@code GRADE_EASY}), so that
     * grade action PendingIntents cannot silently collapse into the wrong
     * grade.
     */
    public static String buildGradeAction(int grade) {
        return ACTION_GRADE_PREFIX + grade;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Provider provider = BaseApplication.of(context).getProvider();
        IAppNotificationHandler appNotificationHandler = provider.get(IAppNotificationHandler.class);
        appNotificationHandler.gradeNotificationCard(intent);
    }
}

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

package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.a_flash_deck.timer.ui.component.timer.NotificationTimerListSV;
import m.co.rh.id.a_flash_deck.timer.ui.page.NotificationTimerDetailSVDialog;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class NotificationTimerListPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private NotificationTimerListSV mNotificationTimerListSV;

    public NotificationTimerListPage() {
        mAppBarSV = new AppBarSV(R.menu.page_timer_notification_list);
        mNotificationTimerListSV = new NotificationTimerListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_timer_notification_list, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_notification_timer_list));
        mAppBarSV.setMenuItemClick(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mNotificationTimerListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            addNewNotificationTimerWorkflow(mNavigator);
            return true;
        }
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mNotificationTimerListSV.dispose(activity);
        mNotificationTimerListSV = null;
    }

    public static void addNewNotificationTimerWorkflow(INavigator navigator) {
        navigator.push(Routes.DECK_SELECT_DIALOG, DeckSelectSVDialog.Args.multiSelectMode(),
                (navigator1, navRoute, activity, currentView) -> {
                    DeckSelectSVDialog.Result result =
                            DeckSelectSVDialog.Result.of(navRoute.getRouteResult());
                    if (result != null) {
                        navigator1.push(Routes.NOTIFICATION_TIMER_DETAIL_DIALOG,
                                NotificationTimerDetailSVDialog.Args.withSelectedDecks(result.getSelectedDeck()));
                    }
                });
    }
}

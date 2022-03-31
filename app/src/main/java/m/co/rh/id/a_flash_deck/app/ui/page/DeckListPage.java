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
import m.co.rh.id.a_flash_deck.app.ui.component.deck.DeckListSV;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class DeckListPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private DeckListSV mDeckListSV;

    public DeckListPage() {
        mAppBarSV = new AppBarSV(R.menu.page_deck_list);
        mDeckListSV = new DeckListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_deck_list, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.title_deck_list));
        mAppBarSV.setMenuItemClick(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mDeckListSV.buildView(activity, rootLayout));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.menu_add) {
            mNavigator.push(Routes.DECK_DETAIL_DIALOG);
            return true;
        }
        return false;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mDeckListSV.dispose(activity);
        mDeckListSV = null;
    }
}

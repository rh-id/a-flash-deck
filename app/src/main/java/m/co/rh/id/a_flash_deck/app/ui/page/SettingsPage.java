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
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.ui.component.AppBarSV;
import m.co.rh.id.a_flash_deck.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_flash_deck.app.ui.component.settings.LogMenuSV;
import m.co.rh.id.a_flash_deck.app.ui.component.settings.VersionMenuSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class SettingsPage extends StatefulView<Activity> {

    @NavInject
    private transient Provider mProvider;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ArrayList<StatefulView> mStatefulViews;

    public SettingsPage() {
        mAppBarSV = new AppBarSV();
        mStatefulViews = new ArrayList<>();
        LogMenuSV logMenuSV = new LogMenuSV();
        mStatefulViews.add(logMenuSV);
        LicensesMenuSV licensesMenuSV = new LicensesMenuSV();
        mStatefulViews.add(licensesMenuSV);
        VersionMenuSV versionMenuSV = new VersionMenuSV();
        mStatefulViews.add(versionMenuSV);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_settings;
        ViewGroup rootLayout = (ViewGroup)
                activity.getLayoutInflater().inflate(layoutId, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ViewGroup content = rootLayout.findViewById(R.id.content);
        for (StatefulView statefulView : mStatefulViews) {
            LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            content.addView(statefulView.buildView(activity, content), lparams);
        }
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mStatefulViews != null && !mStatefulViews.isEmpty()) {
            for (StatefulView statefulView : mStatefulViews) {
                statefulView.dispose(activity);
            }
            mStatefulViews.clear();
            mStatefulViews = null;
        }
    }
}

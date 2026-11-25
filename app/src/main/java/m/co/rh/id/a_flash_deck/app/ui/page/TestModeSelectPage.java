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

package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.model.TestMode;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Test mode selection page, popped as soon as the user picks a mode so the
 * test flow can continue with the chosen mode as the route result.
 */
public class TestModeSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;

    public TestModeSelectPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        // no components needed, the page only pops with the chosen mode
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_test_mode_select, container, false);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.test_mode));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));

        ViewGroup containerSelfGrade = rootLayout.findViewById(R.id.container_option_self_grade);
        containerSelfGrade.setOnClickListener(this);
        ViewGroup containerMultipleChoice = rootLayout.findViewById(R.id.container_option_multiple_choice);
        containerMultipleChoice.setOnClickListener(this);

        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.container_option_self_grade) {
            mNavigator.pop(Result.ofMode(TestMode.SELF_GRADE));
        } else if (viewId == R.id.container_option_multiple_choice) {
            mNavigator.pop(Result.ofMode(TestMode.MULTIPLE_CHOICE));
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        mNavigator = null;
    }

    public static class Result implements Serializable {
        private static final long serialVersionUID = 1L;

        public static Result ofMode(int testMode) {
            Result result = new Result();
            result.mTestMode = TestMode.normalize(testMode);
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private int mTestMode;

        private Result() {
        }

        public int getTestMode() {
            return mTestMode;
        }
    }
}

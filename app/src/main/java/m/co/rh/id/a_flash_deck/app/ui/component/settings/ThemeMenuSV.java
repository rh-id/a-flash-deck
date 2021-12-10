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

package m.co.rh.id.a_flash_deck.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class ThemeMenuSV extends StatefulView<Activity> implements RadioGroup.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_theme, container, false);
        int selectedRadioId = getSelectedRadioId();
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        radioGroup.check(selectedRadioId);
        radioGroup.setOnCheckedChangeListener(this);
        return view;
    }

    private int getSelectedRadioId() {
        int theme = mProvider.get(AppSharedPreferences.class).getSelectedTheme();
        int result = R.id.radio_system;
        if (theme == AppCompatDelegate.MODE_NIGHT_NO) {
            result = R.id.radio_light;
        } else if (theme == AppCompatDelegate.MODE_NIGHT_YES) {
            result = R.id.radio_dark;
        }
        return result;
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i) {
        int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (i == R.id.radio_light) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (i == R.id.radio_dark) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
        }
        mProvider.get(AppSharedPreferences.class)
                .setSelectedTheme(selectedTheme);
    }
}

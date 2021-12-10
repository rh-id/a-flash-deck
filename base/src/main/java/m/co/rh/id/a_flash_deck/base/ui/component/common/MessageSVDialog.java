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

package m.co.rh.id.a_flash_deck.base.ui.component.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;

import m.co.rh.id.a_flash_deck.base.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;

/**
 * Common dialog to show message only
 */
public class MessageSVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener {

    @NavInject
    private transient NavRoute mNavRoute;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.common_dialog_message, container, false);
        TextView textTitle = rootLayout.findViewById(R.id.text_title);
        TextView textContent = rootLayout.findViewById(R.id.text_view_content);
        textTitle.setText(getTitle());
        textContent.setText(getContent());
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.button_ok) {
            getNavigator().pop();
        }
    }

    public String getTitle() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mTitle;
        }
        return null;
    }

    public String getContent() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.mContent;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title, String content) {
            Args args = new Args();
            args.mTitle = title;
            args.mContent = content;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private String mTitle;
        private String mContent;
    }
}

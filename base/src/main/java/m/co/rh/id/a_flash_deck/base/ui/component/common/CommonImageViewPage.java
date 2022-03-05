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
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.Serializable;

import m.co.rh.id.a_flash_deck.base.R;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class CommonImageViewPage extends StatefulView<Activity> implements View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.common_page_imageview, container, false);
        Button backButton = rootLayout.findViewById(R.id.button_back);
        backButton.setOnClickListener(this);
        Button shareButton = rootLayout.findViewById(R.id.button_share);
        shareButton.setOnClickListener(this);
        ImageView imageView = rootLayout.findViewById(R.id.image);
        imageView.setImageURI(getFileUri());
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_back) {
            mNavigator.pop();
        } else if (id == R.id.button_share) {
            Context context = view.getContext();
            UiUtils.shareFile(context, getFile(), context.getString(R.string.share_image));
        }
    }

    private File getFile() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return new File(args.mAbsolutePath);
        }
        return null;
    }

    private Uri getFileUri() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            File file = new File(args.mAbsolutePath);
            return Uri.fromFile(file);
        }
        return Uri.EMPTY;
    }

    public static class Args implements Serializable {
        public static Args withFileAbsolutePath(String absolutePath) {
            Args args = new Args();
            args.mAbsolutePath = absolutePath;
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

        private String mAbsolutePath;
    }
}

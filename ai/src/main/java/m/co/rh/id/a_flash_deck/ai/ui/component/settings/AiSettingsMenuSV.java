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

package m.co.rh.id.a_flash_deck.ai.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

import m.co.rh.id.a_flash_deck.ai.R;
import m.co.rh.id.a_flash_deck.ai.provider.notifier.ApiKeyChangeNotifier;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class AiSettingsMenuSV extends StatefulView<Activity> {

    @NavInject
    private transient INavigator mNavigator;

    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;
    private transient TextView mStatusText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        View view = activity.getLayoutInflater().inflate(R.layout.menu_ai_settings, container, false);
        ApiKeyManager apiKeyManager = mSvProvider.get(ApiKeyManager.class);
        mStatusText = view.findViewById(R.id.text_status);
        if (apiKeyManager.hasApiKey()) {
            mStatusText.setText(activity.getString(R.string.ai_configured));
        } else {
            mStatusText.setText(activity.getString(R.string.ai_not_configured));
        }
        view.findViewById(R.id.container_menu).setOnClickListener(v ->
                mNavigator.push(Routes.AI_API_KEY_DIALOG));
        mSvProvider.get(RxDisposer.class)
                .add("createView_onApiKeyChanged",
                        mSvProvider.get(ApiKeyChangeNotifier.class)
                                .getApiKeyChangeFlow().observeOn(AndroidSchedulers.mainThread())
                                .subscribe(hasApiKey -> {
                                    if (hasApiKey) {
                                        mStatusText.setText(mSvProvider.getContext()
                                                .getString(R.string.ai_configured));
                                    } else {
                                        mStatusText.setText(mSvProvider.getContext()
                                                .getString(R.string.ai_not_configured));
                                    }
                                }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mStatusText = null;
    }
}
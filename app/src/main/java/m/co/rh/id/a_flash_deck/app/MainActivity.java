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

package m.co.rh.id.a_flash_deck.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import com.google.android.material.color.DynamicColors;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.component.IAppNotificationHandler;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.RxProviderModule;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private BehaviorSubject<Boolean> mRebuildUi;
    private Provider mActProvider;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Provider provider = BaseApplication.of(this).getProvider();
        mActProvider = Provider.createNestedProvider("ActivityProvider", provider, this, new RxProviderModule());
        mRebuildUi = BehaviorSubject.create();
        // apply Material You dynamic colors before super.onCreate installs
        // the theme; gated on both the user setting and device support
        AppSharedPreferences appSharedPreferences = BaseApplication.of(this).getProvider()
                .get(AppSharedPreferences.class);
        if (appSharedPreferences.isDynamicColorsEnabled()
                && DynamicColors.isDynamicColorAvailable()) {
            getTheme().applyStyle(R.style.Theme_Aflashdeck_Overlay_DynamicColors, true);
        }
        // rebuild UI is expensive and error prone, avoid spam rebuild (especially due to day and night mode)
        mActProvider.get(RxDisposer.class)
                .add("rebuildUI", mRebuildUi.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                // mirrors the onCreate applyStyle gate: the overrides are
                                // skipped only when dynamic colors are actually active
                                boolean dynamicColorsActive = appSharedPreferences.isDynamicColorsEnabled()
                                        && DynamicColors.isDynamicColorAvailable();
                                if (dynamicColorsActive) {
                                    // the activity handles uiMode config changes without recreation,
                                    // so re-apply the overlay to re-resolve its DayNight variant
                                    // (light/dark) for the new configuration before views rebuild
                                    getTheme().applyStyle(R.style.Theme_Aflashdeck_Overlay_DynamicColors, true);
                                    // the window background drawable was instantiated under the previous
                                    // configuration and is never re-created by applyStyle, so re-resolve
                                    // it explicitly (the legacy branch below does the same via daynight_*)
                                    TypedValue windowBackground = new TypedValue();
                                    getTheme().resolveAttribute(android.R.attr.windowBackground, windowBackground, true);
                                    if (windowBackground.type >= TypedValue.TYPE_FIRST_COLOR_INT
                                            && windowBackground.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                                        getWindow().setBackgroundDrawable(new ColorDrawable(windowBackground.data));
                                    } else if (windowBackground.resourceId != 0) {
                                        getWindow().setBackgroundDrawableResource(windowBackground.resourceId);
                                    }
                                } else {
                                    // Switching to night mode didn't update window background for some reason?
                                    // seemed to occur on android 8 and below
                                    getWindow().setBackgroundDrawableResource(R.color.daynight_white_black);
                                }
                                BaseApplication.of(this).getNavigator(this).reBuildAllRoute();
                                // reBuildAllRoute re-installs the activity content view while the
                                // activity is running; the new hierarchy misses the initial inset
                                // dispatch, so request one explicitly or content lays out under the
                                // transparent status bar on Android 15+ (enforced edge-to-edge)
                                getWindow().getDecorView().requestApplyInsets();
                            }
                        })
                );
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this).onBackPressed();
                    }
                });
        super.onCreate(savedInstanceState);
        handleImportFile(getIntent());
        processNotification(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleImportFile(intent);
        processNotification(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActProvider.dispose();
        mActProvider = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // this is required to let navigator handle onActivityResult
        BaseApplication.of(this).getNavigator(this).onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        BaseApplication.of(this).getNavigator(this).onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // using AppCompatDelegate.setDefaultNightMode trigger this method
        // but not triggering Application.onConfigurationChanged
        mRebuildUi.onNext(true);
    }

    private void processNotification(Intent intent) {
        mActProvider.get(IAppNotificationHandler.class).processNotification(intent);
    }

    private void handleImportFile(Intent intent) {
        // handle open JSON file
        String intentAction = intent.getAction();
        if (Intent.ACTION_VIEW.equals(intentAction)) {
            Context context = getApplicationContext();
            Uri fileData = intent.getData();
            INavigator navigator = BaseApplication.of(this).getNavigator(this);
            mActProvider.get(ILogger.class).d(TAG, "begin import file");
            mActProvider.get(RxDisposer.class)
                    .add("handleImportFile_importFile",
                            mActProvider.get(ExportImportCmd.class)
                                    .importFile(fileData)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((result, throwable) -> {
                                        if (throwable != null) {
                                            handleImportError(navigator, context, throwable);
                                        } else {
                                            mActProvider.get(ILogger.class).i(TAG,
                                                    context.getString(R.string.success_import_file, result.decks.size()));
                                            if (result.showSummaryDialog) {
                                                navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                                                        mActProvider.get(CommonNavConfig.class)
                                                                .args_commonMessageDialog(
                                                                        context.getString(R.string.title_import_summary),
                                                                        result.buildSummaryMessage(context)));
                                            }
                                        }
                                    }));
        }
    }

    /**
     * Shows validation errors as a dialog (same pattern as the export flow),
     * unexpected errors are only logged.
     */
    private void handleImportError(INavigator navigator, Context context, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof ValidationException) {
            navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                    mActProvider.get(CommonNavConfig.class).args_commonMessageDialog(
                            context.getString(R.string.error), cause.getMessage()));
        } else {
            mActProvider.get(ILogger.class)
                    .e(TAG, context.getString(R.string.error_failed_to_open_file)
                            , throwable);
        }
    }
}
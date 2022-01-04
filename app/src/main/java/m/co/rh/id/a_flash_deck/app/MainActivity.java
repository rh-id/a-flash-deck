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

package m.co.rh.id.a_flash_deck.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.RxProviderModule;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
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
        // rebuild UI is expensive and error prone, avoid spam rebuild (especially due to day and night mode)
        mActProvider.get(RxDisposer.class)
                .add("rebuildUI", mRebuildUi.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                BaseApplication.of(this).getNavigator(this).reBuildAllRoute();
                                // Switching to night mode didn't update window background for some reason?
                                // seemed to occur on android 8 and below
                                getWindow().setBackgroundDrawableResource(R.color.daynight_white_black);
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
        handleJsonFile(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleJsonFile(intent);
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

    private void handleJsonFile(Intent intent) {
        // handle open JSON file
        String intentAction = intent.getAction();
        if (Intent.ACTION_VIEW.equals(intentAction)) {
            Context context = getApplicationContext();
            Uri fileData = intent.getData();
            String errorMessage = getString(R.string.error_failed_to_open_file);
            mActProvider.get(ILogger.class).d(TAG, "begin import file");
            mActProvider.get(ExecutorService.class)
                    .execute(() -> {
                        try {
                            File file = mActProvider.get(FileHelper.class)
                                    .createTempFile("Deck-Import", fileData);
                            List<DeckModel> deckModelList = mActProvider.get(ExportImportCmd.class)
                                    .importFile(file).blockingGet();
                            mActProvider.get(ILogger.class).i(TAG,
                                    context.getString(R.string.success_import_file, deckModelList.size()));
                        } catch (Throwable throwable) {
                            mActProvider.get(ILogger.class)
                                    .e(TAG, errorMessage
                                            , throwable);
                        }
                    });
        }
    }
}
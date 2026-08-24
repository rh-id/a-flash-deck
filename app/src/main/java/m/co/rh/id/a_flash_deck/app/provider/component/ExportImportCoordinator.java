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

package m.co.rh.id.a_flash_deck.app.provider.component;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.ExportImportCmd;
import m.co.rh.id.a_flash_deck.app.ui.page.DeckSelectPage;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.exception.ValidationException;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

/**
 * Coordinator for export/import workflows.
 * Plain Java class (NOT a StatefulView).
 */
public class ExportImportCoordinator {
    private static final String TAG = ExportImportCoordinator.class.getName();

    public static final int REQUEST_CODE_IMPORT_DECK = 1;

    private final Provider mProvider;
    private final RxDisposer mRxDisposer;

    public ExportImportCoordinator(Provider provider, RxDisposer rxDisposer) {
        mProvider = provider;
        mRxDisposer = rxDisposer;
    }

    /**
     * Starts the export flow with deck selection.
     */
    public void exportFlow(INavigator navigator, boolean exportAnki) {
        String logPrefix = exportAnki ? "Anki file exported: " : "File exported: ";
        navigator.push(Routes.DECK_SELECT_PAGE, DeckSelectPage.Args.multiSelectMode(),
                (navigator1, navRoute, activity, currentView) -> {
                    DeckSelectPage.Result result = DeckSelectPage.Result.of(navRoute);
                    if (result != null) {
                        Provider provider = (Provider) navigator1.getNavConfiguration().getRequiredComponent();
                        Context context = provider.getContext();
                        CompositeDisposable compositeDisposable = new CompositeDisposable();
                        Single<File> exportSingle = exportAnki ?
                                provider.get(ExportImportCmd.class).exportFileAnki(result.getSelectedDeck()) :
                                provider.get(ExportImportCmd.class).exportFile(result.getSelectedDeck());
                        compositeDisposable.add(exportSingle
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((file, throwable) -> {
                                    if (throwable != null) {
                                        if (throwable.getCause() instanceof ValidationException) {
                                            String title = context.getString(R.string.error);
                                            navigator1.push(Routes.COMMON_MESSAGE_DIALOG,
                                                    provider.get(CommonNavConfig.class).args_commonMessageDialog(title,
                                                            throwable.getCause().getMessage()));
                                    } else {
                                        provider.get(ILogger.class)
                                                .e(TAG,
                                                        throwable.getMessage(), throwable);
                                        }
                                    } else {
                                        provider.get(ILogger.class)
                                                .d(TAG,
                                                        logPrefix + file.getAbsolutePath());
                                        UiUtils.shareFile(context, file, file.getName());
                                    }
                                    compositeDisposable.dispose();
                                })
                        );
                    }
                });
    }

    /**
     * Starts the import flow by opening file chooser.
     */
    public void importFlow(Activity activity) {
        String chooserMessage = activity.getString(R.string.title_import_deck);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/zip", "application/octet-stream", "application/vnd.anki.apkg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent = Intent.createChooser(intent, chooserMessage);
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_DECK);
    }

    /**
     * Handles the import result from file chooser.
     */
    public void onImportResult(Uri data) {
        Context context = mProvider.getContext();
        mRxDisposer
                .add("onImportResult",
                        mProvider.get(ExportImportCmd.class).importFile(data)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((deckModels, throwable) -> {
                                    if (throwable != null) {
                                        mProvider.get(ILogger.class)
                                                .e(TAG,
                                                        context.getString(R.string.error_failed_to_open_file),
                                                        throwable);
                                    } else {
                                        mProvider.get(ILogger.class)
                                                .i(TAG,
                                                        context.getString(R.string.success_import_file, deckModels.size()));
                                    }
                                }));
    }
}

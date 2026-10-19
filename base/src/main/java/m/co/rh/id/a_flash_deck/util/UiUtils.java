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

package m.co.rh.id.a_flash_deck.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;

import m.co.rh.id.a_flash_deck.base.BaseApplication;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.constants.Constants;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.anavigator.component.INavigator;

public class UiUtils {
    // Request-code registry for runtime permission requests (the activity-result
    // channel is separate, numbers may repeat across channels, e.g. permission
    // code 1 vs ExportImportCoordinator's activity-result code 1, because
    // Android dispatches them via different callbacks:
    // onRequestPermissionsResult vs onActivityResult):
    // 0 = VoiceRecordSVDialog (RECORD_AUDIO),
    // 1 = HomePage (POST_NOTIFICATIONS primer),
    // 2 = NotificationTimerDetailSVDialog (POST_NOTIFICATIONS save recovery)
    public static final int REQUEST_CODE_RECORD_AUDIO = 0;
    public static final int REQUEST_CODE_POST_NOTIFICATIONS_HOME_PAGE = 1;
    public static final int REQUEST_CODE_POST_NOTIFICATIONS_TIMER_DETAIL = 2;

    public static void takeImageFromCamera(Activity activity, int requestCode, File outputFile) {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri photoURI = FileProvider.getUriForFile(activity,
                Constants.FILE_PROVIDER_AUTHORITY,
                outputFile);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        activity.startActivityForResult(cameraIntent, requestCode);
    }

    public static void browseImage(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(intent, requestCode);
        }
    }

    public static void shareFile(Context context, File file, String chooserMessage) {
        shareFile(context, file, chooserMessage, "*/*");
    }

    public static void shareFile(Context context, File file, String chooserMessage, String mime) {
        Uri fileUri =
                FileProvider.getUriForFile(
                        context,
                        Constants.FILE_PROVIDER_AUTHORITY,
                        file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType(mime);
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
    }

    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getColorFromAttribute(Context context, int attribute) {
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attribute, typedValue, true);
        return typedValue.data;
    }

    /**
     * Android 13 (API 33) and above require POST_NOTIFICATIONS to be requested
     * at runtime, notifications silently no-op otherwise. Returns true when
     * there is nothing to request: the runtime permission does not exist below
     * API 33 or it is already granted.
     */
    public static boolean isPostNotificationsGranted(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return isPermissionGranted(activity, Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Android 13 (API 33) and above require POST_NOTIFICATIONS to be requested
     * at runtime, notifications silently no-op otherwise. Returns true when the
     * system still allows showing the permission dialog: the user never saw any
     * prompt or the denial still shows the rationale. False means permanently
     * denied, firing the request would silently no-op.
     */
    public static boolean canAskPostNotificationsAgain(Activity activity) {
        AppSharedPreferences appSharedPreferences = BaseApplication.of(activity)
                .getProvider().get(AppSharedPreferences.class);
        return !appSharedPreferences.isNotificationPermissionRequested()
                || ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Android 13 (API 33) and above require POST_NOTIFICATIONS to be requested
     * at runtime, notifications silently no-op otherwise. Fires the system
     * permission dialog with the given request code and persists that the
     * request was made.
     */
    public static void requestPostNotificationsPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                requestCode);
        // persist right after firing the request so the dialog is not re-shown
        AppSharedPreferences appSharedPreferences = BaseApplication.of(activity)
                .getProvider().get(AppSharedPreferences.class);
        appSharedPreferences.setNotificationPermissionRequested(true);
    }

    /**
     * Pushes the common message dialog route with the given title and content
     * string resources.
     */
    public static void pushMessageDialog(INavigator navigator, Context context, int titleRes, int contentRes) {
        pushMessageDialog(navigator, context, titleRes, context.getString(contentRes));
    }

    /**
     * Pushes the common message dialog route with the given title string
     * resource and content string (e.g. a validation or error message).
     */
    public static void pushMessageDialog(INavigator navigator, Context context, int titleRes, String content) {
        CommonNavConfig commonNavConfig = BaseApplication.of(context)
                .getProvider().get(CommonNavConfig.class);
        navigator.push(Routes.COMMON_MESSAGE_DIALOG,
                commonNavConfig.args_commonMessageDialog(context.getString(titleRes), content));
    }

    /**
     * Returns true when the given runtime permission is granted.
     */
    public static boolean isPermissionGranted(Context context, String permission) {
        return ActivityCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns true when the grant results of a permission request report the
     * permission as denied.
     */
    public static boolean isPermissionDenied(int[] grantResults) {
        return grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_DENIED;
    }

    /**
     * Returns true when the grant results of a permission request report the
     * permission as granted.
     */
    public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Shared dispatch for navigator permission results: matches the request
     * code, then routes to the granted/denied action. An empty grantResults
     * array (interrupted request) runs neither action. Pass {@code () -> {}}
     * for an uninteresting outcome.
     */
    public static void onPermissionResult(int requestCode, int expectedRequestCode, int[] grantResults,
                                          Runnable onGranted, Runnable onDenied) {
        if (requestCode != expectedRequestCode) {
            return;
        }
        if (isPermissionGranted(grantResults)) {
            onGranted.run();
        } else if (isPermissionDenied(grantResults)) {
            onDenied.run();
        }
    }

    /**
     * Fires the system permission dialog for the given permission when it is
     * not granted yet.
     */
    public static void requestPermissionIfNeeded(Activity activity, String permission, int requestCode) {
        if (!isPermissionGranted(activity, permission)) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{permission},
                    requestCode);
        }
    }

    private UiUtils() {
    }
}

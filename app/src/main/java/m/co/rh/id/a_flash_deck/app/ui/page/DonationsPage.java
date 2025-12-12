package m.co.rh.id.a_flash_deck.app.ui.page;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class DonationsPage extends StatefulView<Activity> implements View.OnClickListener {
    private static final String TAG = DonationsPage.class.getName();
    private static final String DEV_URL = "<a href='https://rh-apps.github.io/'>https://rh-apps.github.io/</a>";

    @NavInject
    private transient Provider mProvider;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_donations, container, false);
        Button donate = rootLayout.findViewById(R.id.button_donate);
        donate.setOnClickListener(this);
        TextView otherApps = rootLayout.findViewById(R.id.text_other_apps);
        String otherAppMsg = activity.getString(R.string.donation_other_apps, DEV_URL);
        otherApps.setText(HtmlCompat.fromHtml(otherAppMsg, HtmlCompat.FROM_HTML_MODE_LEGACY));
        otherApps.setMovementMethod(LinkMovementMethod.getInstance());
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_donate) {
            Uri webpage = Uri.parse("https://teer.id/rh-id");
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webpage);
            Context context = view.getContext();
            try {
                context.startActivity(webIntent);
            } catch (ActivityNotFoundException activityNotFoundException) {
                webpage = Uri.parse("https://teer.id/rh-id");
                webIntent = new Intent(Intent.ACTION_VIEW, webpage);
                context.startActivity(webIntent);
            }
            mProvider.get(ILogger.class)
                    .i(TAG, context.getString(R.string.donation_thank_you));
        }
    }
}

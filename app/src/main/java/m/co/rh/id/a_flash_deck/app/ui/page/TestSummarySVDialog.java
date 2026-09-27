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
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Locale;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;

/**
 * Dialog that shows the summary of a completed test session
 */
public class TestSummarySVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener {

    @NavInject
    private transient NavRoute mNavRoute;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.dialog_test_summary, container, false);
        TextView textTitle = rootLayout.findViewById(R.id.text_title);
        TextView textGradeAgainCount = rootLayout.findViewById(R.id.text_grade_again_count);
        TextView textGradeHardCount = rootLayout.findViewById(R.id.text_grade_hard_count);
        TextView textGradeGoodCount = rootLayout.findViewById(R.id.text_grade_good_count);
        TextView textGradeEasyCount = rootLayout.findViewById(R.id.text_grade_easy_count);
        TextView textSummaryTotal = rootLayout.findViewById(R.id.text_summary_total);
        TextView textSummaryTime = rootLayout.findViewById(R.id.text_summary_time);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            textTitle.setText(args.mTitle);
            textGradeAgainCount.setText(String.valueOf(args.mGradeCounts[ReviewScheduler.GRADE_AGAIN]));
            textGradeHardCount.setText(String.valueOf(args.mGradeCounts[ReviewScheduler.GRADE_HARD]));
            textGradeGoodCount.setText(String.valueOf(args.mGradeCounts[ReviewScheduler.GRADE_GOOD]));
            textGradeEasyCount.setText(String.valueOf(args.mGradeCounts[ReviewScheduler.GRADE_EASY]));
            textSummaryTotal.setText(activity.getString(R.string.test_summary_total, args.mTotalAnswers));
            textSummaryTime.setText(activity.getString(R.string.test_summary_time,
                    formatElapsedMs(args.mElapsedMs)));
        }
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

    private static String formatElapsedMs(long elapsedMs) {
        long totalSeconds = elapsedMs / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title, int[] gradeCounts, int totalAnswers, long elapsedMs) {
            Args args = new Args();
            args.mTitle = title;
            args.mGradeCounts = gradeCounts;
            args.mTotalAnswers = totalAnswers;
            args.mElapsedMs = elapsedMs;
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
        private int[] mGradeCounts;
        private int mTotalAnswers;
        private long mElapsedMs;
    }
}

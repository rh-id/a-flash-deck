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

package m.co.rh.id.a_flash_deck.app.ui.component.stats;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.anavigator.StatefulView;

/**
 * Minimal bar chart for the statistics page: evenly spaced bars scaled to
 * the largest value, with the max and latest values labeled.
 * Deliberately dependency-free (custom View instead of a chart library).
 */
public class BarChartSV extends StatefulView<Activity> {

    private final int mBarColorRes;
    private int[] mValues;
    /**
     * transient: the built android View must never be part of the navigator
     * snapshot (View is not Serializable); it is rebuilt in createView
     */
    private transient BarChartView mBarChartView;

    public BarChartSV(int barColorRes) {
        mBarColorRes = barColorRes;
    }

    /**
     * Updates the chart values; safe to call before or after the view is built
     */
    public void setValues(int[] values) {
        mValues = values;
        if (mBarChartView != null) {
            mBarChartView.setValues(values);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mBarChartView = new BarChartView(activity, mBarColorRes);
        if (mValues != null) {
            mBarChartView.setValues(mValues);
        }
        return mBarChartView;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mBarChartView = null;
    }

    private static class BarChartView extends View {
        private static final float MIN_BAR_HEIGHT_DP = 2f;
        private static final float BAR_GAP_DP = 2f;
        private static final float LABEL_PADDING_DP = 4f;

        private final Paint mBarPaint;
        private final Paint mLabelPaint;
        private final Paint mBaselinePaint;
        private final float mDensity;
        private int[] mValues = new int[0];

        private BarChartView(Context context, int barColorRes) {
            super(context);
            mDensity = getResources().getDisplayMetrics().density;
            mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBarPaint.setColor(ContextCompat.getColor(context, barColorRes));
            mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mLabelPaint.setColor(ContextCompat.getColor(context,
                    R.color.daynight_gray_700_white));
            mLabelPaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 14f,
                    getResources().getDisplayMetrics()));
            mBaselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBaselinePaint.setColor(ContextCompat.getColor(context,
                    R.color.daynight_gray_300_gray_600));
        }

        private float dp(float value) {
            return value * mDensity;
        }

        private void setValues(int[] values) {
            mValues = values == null ? new int[0] : values;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mValues.length == 0) {
                return;
            }
            int width = getWidth();
            int height = getHeight();
            float labelHeight = mLabelPaint.getTextSize();
            float labelPad = dp(LABEL_PADDING_DP);
            // bottom strip (labelHeight + labelPad) keeps the last label's
            // full ascent below the baseline line, clear of the bars
            float baselineY = height - labelHeight - labelPad;
            canvas.drawLine(0, baselineY, width, baselineY, mBaselinePaint);
            int max = 0;
            for (int value : mValues) {
                if (value > max) {
                    max = value;
                }
            }
            // division guard only: the labels below must show the real max
            int scale = Math.max(max, 1);
            int count = mValues.length;
            float gap = dp(BAR_GAP_DP);
            float barWidth = (width - gap * (count - 1)) / count;
            // top strip (labelHeight + labelPad) keeps the max label clear
            // of the tallest bar
            float chartHeight = baselineY - labelHeight - labelPad;
            float minBarHeight = dp(MIN_BAR_HEIGHT_DP);
            for (int i = 0; i < count; i++) {
                int value = mValues[i];
                if (value <= 0 || barWidth <= 0) {
                    continue;
                }
                float barHeight = Math.max(minBarHeight,
                        chartHeight * value / scale);
                float left = i * (barWidth + gap);
                canvas.drawRect(left, baselineY - barHeight,
                        left + barWidth, baselineY, mBarPaint);
            }

            // drawText's y is the text baseline; labelHeight keeps the max
            // label inside the top strip, height - labelPad keeps the last
            // label inside the bottom strip
            canvas.drawText(String.valueOf(max), 0, labelHeight, mLabelPaint);
            String lastLabel = String.valueOf(mValues[count - 1]);
            float lastLabelWidth = mLabelPaint.measureText(lastLabel);
            canvas.drawText(lastLabel, width - lastLabelWidth,
                    height - labelPad, mLabelPaint);
        }
    }
}

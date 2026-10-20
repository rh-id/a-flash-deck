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
import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.app.provider.command.StatsCmd;
import m.co.rh.id.a_flash_deck.app.ui.component.stats.BarChartSV;
import m.co.rh.id.a_flash_deck.base.model.DeckDueCount;
import m.co.rh.id.a_flash_deck.base.model.ReviewStats;
import m.co.rh.id.a_flash_deck.base.model.ReviewStatsCalculator;
import m.co.rh.id.a_flash_deck.base.model.TrueRetention;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.notifier.DeckChangeNotifier;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.ui.component.common.AppBarSV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Statistics page: daily review counts, streak, retention, answer speed,
 * true retention, due forecast and per-deck breakdown, computed from the
 * review history log
 */
public class StatisticsPage extends StatefulView<Activity>
        implements RequireComponent<Provider> {
    private static final String TAG = StatisticsPage.class.getName();

    /**
     * Bucket order of the true-retention rows, shortest gap first
     */
    private static final int[] TRUE_RETENTION_BUCKET_LABEL_RES = {
            R.string.stats_bucket_lt_1_day,
            R.string.stats_bucket_1_7_days,
            R.string.stats_bucket_8_30_days,
            R.string.stats_bucket_gt_30_days
    };

    @NavInject
    private AppBarSV mAppBarSV;
    // non-transient so the charts survive navigator snapshot restore
    // (deserialization skips the constructor, which would leave transient
    // fields null and crash createView)
    @NavInject
    private BarChartSV mReviewsBarChartSV;
    @NavInject
    private BarChartSV mForecastBarChartSV;
    @NavInject
    private BarChartSV mSpeedBarChartSV;
    private transient IStatefulViewProvider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient StatsCmd mStatsCmd;
    private transient DeckChangeNotifier mDeckChangeNotifier;
    private transient TextView mTextTileReviewsToday;
    private transient TextView mTextTileStreak;
    private transient TextView mTextTileDueNow;
    private transient TextView mTextEmptyStats;
    private transient ViewGroup mContainerReviewHistory;
    private transient TextView mTextRetentionValue;
    private transient TextView mTextGradeAgainCount;
    private transient TextView mTextGradeHardCount;
    private transient TextView mTextGradeGoodCount;
    private transient TextView mTextGradeEasyCount;
    private transient ViewGroup mContainerSpeedSection;
    private transient TextView mTextSpeedSummary;
    private transient ViewGroup mContainerTrueRetentionSection;
    private transient ViewGroup mContainerTrueRetentionRows;
    private transient TextView mTextForecastSummary;
    private transient TextView mTextByDeckHeader;
    private transient ViewGroup mContainerDeckStats;

    public StatisticsPage() {
        mAppBarSV = new AppBarSV();
        mReviewsBarChartSV = new BarChartSV(R.color.daynight_teal_custom_teal_200);
        mForecastBarChartSV = new BarChartSV(R.color.grade_easy);
        mSpeedBarChartSV = new BarChartSV(R.color.grade_hard,
                BarChartSV.LABEL_MODE_ELAPSED_SECONDS);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mStatsCmd = mSvProvider.get(StatsCmd.class);
        mDeckChangeNotifier = mSvProvider.get(DeckChangeNotifier.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_statistics, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_statistics));
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));

        mTextTileReviewsToday = rootLayout.findViewById(R.id.text_tile_reviews_today);
        mTextTileStreak = rootLayout.findViewById(R.id.text_tile_streak);
        mTextTileDueNow = rootLayout.findViewById(R.id.text_tile_due_now);
        mTextEmptyStats = rootLayout.findViewById(R.id.text_empty_stats);
        mContainerReviewHistory = rootLayout.findViewById(R.id.container_review_history);
        mTextRetentionValue = rootLayout.findViewById(R.id.text_retention_value);
        mTextGradeAgainCount = rootLayout.findViewById(R.id.text_grade_again_count);
        mTextGradeHardCount = rootLayout.findViewById(R.id.text_grade_hard_count);
        mTextGradeGoodCount = rootLayout.findViewById(R.id.text_grade_good_count);
        mTextGradeEasyCount = rootLayout.findViewById(R.id.text_grade_easy_count);
        mContainerSpeedSection = rootLayout.findViewById(R.id.container_speed_section);
        mTextSpeedSummary = rootLayout.findViewById(R.id.text_speed_summary);
        mContainerTrueRetentionSection =
                rootLayout.findViewById(R.id.container_true_retention_section);
        mContainerTrueRetentionRows =
                rootLayout.findViewById(R.id.container_true_retention_rows);
        mTextForecastSummary = rootLayout.findViewById(R.id.text_forecast_summary);
        mTextByDeckHeader = rootLayout.findViewById(R.id.text_by_deck_header);
        mContainerDeckStats = rootLayout.findViewById(R.id.container_deck_stats);

        ViewGroup containerChartReviews =
                rootLayout.findViewById(R.id.container_chart_reviews);
        containerChartReviews.addView(
                mReviewsBarChartSV.buildView(activity, containerChartReviews),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        // the charts are pure-canvas views; give TalkBack users the section name
        containerChartReviews.setContentDescription(
                activity.getString(R.string.stats_reviews_last_30_days));
        ViewGroup containerChartForecast =
                rootLayout.findViewById(R.id.container_chart_forecast);
        containerChartForecast.addView(
                mForecastBarChartSV.buildView(activity, containerChartForecast),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        containerChartForecast.setContentDescription(
                activity.getString(R.string.stats_due_forecast));
        ViewGroup containerChartSpeed =
                rootLayout.findViewById(R.id.container_chart_speed);
        containerChartSpeed.addView(
                mSpeedBarChartSV.buildView(activity, containerChartSpeed),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        containerChartSpeed.setContentDescription(
                activity.getString(R.string.stats_speed));

        loadReviewStats();
        // decks added/deleted change the per-deck breakdown
        mRxDisposer.add("createView_stats_deckAdded",
                mDeckChangeNotifier.getAddedDeckFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> loadReviewStats()));
        mRxDisposer.add("createView_stats_deckDeleted",
                mDeckChangeNotifier.getDeletedDeckFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(deck -> loadReviewStats()));
        return rootLayout;
    }

    /**
     * Re-fetches the statistics; reusing the same RxDisposer tag cancels any
     * previous in-flight request
     */
    private void loadReviewStats() {
        mRxDisposer.add("createView_reviewStats",
                mStatsCmd.getReviewStats()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::renderReviewStats,
                                throwable -> mLogger.e(TAG,
                                        "Failed to load review stats", throwable)));
    }

    private void renderReviewStats(ReviewStats reviewStats) {
        Context context = mSvProvider.getContext();
        mTextTileReviewsToday.setText(String.valueOf(reviewStats.todayReviewCount));
        mTextTileStreak.setText(String.valueOf(reviewStats.streakDays));
        mTextTileDueNow.setText(String.valueOf(sumDueCounts(reviewStats)));

        // review history did not exist before this feature; the history
        // sections stay hidden until the first grade, while the due forecast
        // and per-deck breakdown work from the start
        boolean hasHistory = reviewStats.totalReviews > 0;
        mTextEmptyStats.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
        mContainerReviewHistory.setVisibility(
                hasHistory ? View.VISIBLE : View.GONE);
        if (hasHistory) {
            mReviewsBarChartSV.setValues(reviewStats.dailyReviewCounts);
            mTextRetentionValue.setText(context.getString(R.string.stats_retention_value,
                    Math.round(reviewStats.retentionPercent),
                    reviewStats.retentionTotal));
            mTextGradeAgainCount.setText(String.valueOf(
                    reviewStats.todayGradeCounts[0]));
            mTextGradeHardCount.setText(String.valueOf(
                    reviewStats.todayGradeCounts[1]));
            mTextGradeGoodCount.setText(String.valueOf(
                    reviewStats.todayGradeCounts[2]));
            mTextGradeEasyCount.setText(String.valueOf(
                    reviewStats.todayGradeCounts[3]));

            // leave the timing-dependent sections hidden for upgrades from
            // app versions that did not record timings yet (all timings 0)
            boolean hasSpeed = reviewStats.speedSampleCount > 0;
            mContainerSpeedSection.setVisibility(hasSpeed ? View.VISIBLE : View.GONE);
            if (hasSpeed) {
                mSpeedBarChartSV.setValues(reviewStats.dailyAvgSpeedMs);
                mTextSpeedSummary.setText(context.getString(
                        R.string.stats_speed_summary,
                        DateUtils.formatElapsedTime(reviewStats.medianSpeedMs / 1000L),
                        DateUtils.formatElapsedTime(reviewStats.avgSpeedMs / 1000L)));
            }
            TrueRetention trueRetention = reviewStats.trueRetention;
            boolean hasTrueRetention = trueRetention != null
                    && (trueRetention.newCardCount > 0
                    || sum(trueRetention.bucketTotalCounts) > 0);
            mContainerTrueRetentionSection.setVisibility(
                    hasTrueRetention ? View.VISIBLE : View.GONE);
            if (hasTrueRetention) {
                renderTrueRetention(trueRetention);
            }
        }

        mForecastBarChartSV.setValues(reviewStats.dailyDueCounts);
        mTextForecastSummary.setText(context.getString(
                R.string.stats_due_forecast_summary,
                reviewStats.newCardCount, reviewStats.dueLaterCount));

        mContainerDeckStats.removeAllViews();
        List<DeckDueCount> deckDueCounts = reviewStats.deckDueCounts;
        boolean hasDecks = deckDueCounts != null && !deckDueCounts.isEmpty();
        mTextByDeckHeader.setVisibility(hasDecks ? View.VISIBLE : View.GONE);
        if (hasDecks) {
            // inflate with the container's (activity-derived) context so row
            // text colors follow the activity's night mode; the application
            // context always resolves the day theme
            LayoutInflater layoutInflater = LayoutInflater.from(
                    mContainerDeckStats.getContext());
            for (DeckDueCount deckDueCount : deckDueCounts) {
                View deckStatView = layoutInflater.inflate(
                        R.layout.item_deck_stat, mContainerDeckStats, false);
                TextView deckName = deckStatView.findViewById(R.id.text_deck_name);
                TextView deckSummary = deckStatView.findViewById(R.id.text_deck_summary);
                deckName.setText(deckDueCount.deckName);
                deckSummary.setText(context.getString(R.string.stats_deck_summary,
                        deckDueCount.totalCards, deckDueCount.count));
                mContainerDeckStats.addView(deckStatView);
            }
        }
    }

    /**
     * Inflates the true-retention rows, shortest gap first; every bucket
     * row shows the bucket's correct percentage together with its sample
     * count so small denominations stay honest. A final row shows the
     * first-ever reviews as a plain count: they have no previous review
     * to measure a gap against, so a percentage would be meaningless
     */
    private void renderTrueRetention(TrueRetention trueRetention) {
        mContainerTrueRetentionRows.removeAllViews();
        Context context = mSvProvider.getContext();
        LayoutInflater layoutInflater = LayoutInflater.from(
                mContainerTrueRetentionRows.getContext());
        for (int bucket = 0;
             bucket < ReviewStatsCalculator.TRUE_RETENTION_BUCKET_COUNT;
             bucket++) {
            int bucketTotal = trueRetention.bucketTotalCounts[bucket];
            int bucketCorrect = trueRetention.bucketCorrectCounts[bucket];
            View rowView = layoutInflater.inflate(R.layout.item_retention_row,
                    mContainerTrueRetentionRows, false);
            TextView textLabel = rowView.findViewById(R.id.text_retention_label);
            TextView textValue = rowView.findViewById(R.id.text_retention_value);
            textLabel.setText(TRUE_RETENTION_BUCKET_LABEL_RES[bucket]);
            textValue.setText(context.getString(
                    R.string.stats_true_retention_value,
                    bucketTotal == 0 ? 0
                            : Math.round(bucketCorrect * 100.0 / bucketTotal),
                    bucketTotal));
            mContainerTrueRetentionRows.addView(rowView);
        }
        View newRowView = layoutInflater.inflate(R.layout.item_retention_row,
                mContainerTrueRetentionRows, false);
        TextView newTextLabel = newRowView.findViewById(R.id.text_retention_label);
        TextView newTextValue = newRowView.findViewById(R.id.text_retention_value);
        newTextLabel.setText(R.string.stats_true_retention_new_cards);
        newTextValue.setText(String.valueOf(trueRetention.newCardCount));
        mContainerTrueRetentionRows.addView(newRowView);
    }

    /**
     * @return total due/new card count across decks
     * (same semantics as the home page Study Due button)
     */
    private int sumDueCounts(ReviewStats reviewStats) {
        int sum = 0;
        List<DeckDueCount> deckDueCounts = reviewStats.deckDueCounts;
        if (deckDueCounts != null) {
            for (DeckDueCount deckDueCount : deckDueCounts) {
                sum += deckDueCount.count;
            }
        }
        return sum;
    }

    /**
     * @return the sum of the given array values
     */
    private static int sum(int[] values) {
        int sum = 0;
        if (values != null) {
            for (int value : values) {
                sum += value;
            }
        }
        return sum;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mAppBarSV != null) {
            mAppBarSV.dispose(activity);
            mAppBarSV = null;
        }
        if (mReviewsBarChartSV != null) {
            mReviewsBarChartSV.dispose(activity);
            mReviewsBarChartSV = null;
        }
        if (mForecastBarChartSV != null) {
            mForecastBarChartSV.dispose(activity);
            mForecastBarChartSV = null;
        }
        if (mSpeedBarChartSV != null) {
            mSpeedBarChartSV.dispose(activity);
            mSpeedBarChartSV = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mLogger = null;
        mRxDisposer = null;
        mStatsCmd = null;
        mDeckChangeNotifier = null;
        mTextTileReviewsToday = null;
        mTextTileStreak = null;
        mTextTileDueNow = null;
        mTextEmptyStats = null;
        mContainerReviewHistory = null;
        mTextRetentionValue = null;
        mTextGradeAgainCount = null;
        mTextGradeHardCount = null;
        mTextGradeGoodCount = null;
        mTextGradeEasyCount = null;
        mContainerSpeedSection = null;
        mTextSpeedSummary = null;
        mContainerTrueRetentionSection = null;
        mContainerTrueRetentionRows = null;
        mTextForecastSummary = null;
        mTextByDeckHeader = null;
        mContainerDeckStats = null;
    }
}

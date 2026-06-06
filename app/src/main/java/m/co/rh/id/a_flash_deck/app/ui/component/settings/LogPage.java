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

package m.co.rh.id.a_flash_deck.app.ui.component.settings;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_flash_deck.R;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class LogPage extends StatefulView<Activity> implements View.OnClickListener {
    private static final String TAG = LogPage.class.getName();

    @NavInject
    private transient Provider mProvider;

    private transient Provider mSvProvider;
    private transient BehaviorSubject<File> mLogFileSubject;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(IStatefulViewProvider.class);
        if (mLogFileSubject == null) {
            mLogFileSubject = BehaviorSubject.createDefault(mSvProvider.get(FileHelper.class).getLogFile());
        }
        View view = activity.getLayoutInflater().inflate(R.layout.page_log,
                container, false);
        ProgressBar progressBar = view.findViewById(R.id.progress_circular);
        View noRecord = view.findViewById(R.id.no_record);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        FloatingActionButton fabClear = view.findViewById(R.id.fab_clear);
        FloatingActionButton fabShare = view.findViewById(R.id.fab_share);
        fabShare.setOnClickListener(this);
        fabClear.setOnClickListener(this);
        LogLineRecyclerViewAdapter adapter = new LogLineRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        mSvProvider.get(RxDisposer.class).add("readLogFile",
                mLogFileSubject.
                        observeOn(Schedulers.from(mSvProvider
                                .get(ExecutorService.class)))
                        .map(file -> {
                            if (!file.exists()) {
                                return Collections.<String>emptyList();
                            } else {
                                List<String> lines = new ArrayList<>();
                                try (BufferedReader bufferedReader =
                                             new BufferedReader(new FileReader(file))) {
                                    String line;
                                    while ((line = bufferedReader.readLine()) != null) {
                                        lines.add(line);
                                    }
                                }
                                return lines;
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(lines -> {
                            progressBar.setVisibility(View.GONE);
                            if (lines.isEmpty()) {
                                adapter.submitList(lines);
                                noRecord.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                                fabShare.setVisibility(View.GONE);
                                fabClear.setVisibility(View.GONE);
                            } else {
                                noRecord.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);
                                fabShare.setVisibility(View.VISIBLE);
                                fabClear.setVisibility(View.VISIBLE);
                                adapter.submitList(lines, () ->
                                        recyclerView.scrollToPosition(lines.size() - 1));
                            }
                        }));

        return view;
    }

    @Override
    public void onClick(View view) {
        Activity activity = UiUtils.getActivity(view);
        FileHelper fileHelper = mSvProvider.get(FileHelper.class);
        File logFile = fileHelper.getLogFile();

        int id = view.getId();
        if (id == R.id.fab_share) {
            try {
                UiUtils.shareFile(activity, logFile, activity.getString(R.string.share_log_file));
            } catch (Throwable e) {
                mSvProvider.get(ILogger.class)
                        .e(TAG, activity.getString(R.string.error_sharing_log_file), e);
            }
        } else if (id == R.id.fab_clear) {
            fileHelper.clearLogFile();
            mSvProvider.get(ILogger.class).i(TAG, activity.getString(R.string.log_file_deleted));
            mSvProvider.get(Handler.class)
                    .post(() -> mLogFileSubject.onNext(logFile));
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }
}

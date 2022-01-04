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

package m.co.rh.id.a_flash_deck.app.provider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.TestDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Test;
import m.co.rh.id.a_flash_deck.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Helper to cleanup unused file
 */
public class FileCleanUpTask {
    private static final String TAG = FileCleanUpTask.class.getName();

    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<TestDao> mTestDao;
    private final ProviderValue<DeckDao> mDeckDao;
    private final ProviderValue<FileHelper> mFileHelper;
    private final ProviderValue<ILogger> mLogger;

    public FileCleanUpTask(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mTestDao = provider.lazyGet(TestDao.class);
        mDeckDao = provider.lazyGet(DeckDao.class);
        mFileHelper = provider.lazyGet(FileHelper.class);
        mLogger = provider.lazyGet(ILogger.class);
        cleanUp();
    }

    private void cleanUp() {
        Future<Test> testFuture = mExecutorService.get().submit(() -> mTestDao.get().getCurrentTest());
        Future<List<String>> questionImageFileList = mExecutorService.get().submit(
                () -> {
                    File questionImageParent = mFileHelper.get().getCardQuestionImageParent();
                    File[] files = questionImageParent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
        Future<List<String>> questionVoiceFileList = mExecutorService.get().submit(
                () -> {
                    File questionVoiceParent = mFileHelper.get().getCardQuestionVoiceParent();
                    File[] files = questionVoiceParent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
        Future<List<String>> answerImageFileList = mExecutorService.get().submit(
                () -> {
                    File answerImageParent = mFileHelper.get().getCardAnswerImageParent();
                    File[] files = answerImageParent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
        mExecutorService.get().execute(() -> {
            try {
                Test test = testFuture.get();
                if (test == null) {
                    List<Future<Boolean>> taskList = new ArrayList<>();
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> questionImageNames = questionImageFileList.get();
                                if (!questionImageNames.isEmpty()) {
                                    for (String questionImage : questionImageNames) {
                                        Card card = mDeckDao.get().findCardByQuestionImage(questionImage);
                                        if (card == null) {
                                            mFileHelper.get().deleteCardQuestionImage(questionImage);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> questionVoiceNames = questionVoiceFileList.get();
                                if (!questionVoiceNames.isEmpty()) {
                                    for (String questionVoiceName : questionVoiceNames) {
                                        Card card = mDeckDao.get().findCardByQuestionVoice(questionVoiceName);
                                        if (card == null) {
                                            mFileHelper.get().deleteCardQuestionVoice(questionVoiceName);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    taskList.add(
                            mExecutorService.get().submit(() -> {
                                List<String> answerImageNames = answerImageFileList.get();
                                if (!answerImageNames.isEmpty()) {
                                    for (String answerImage : answerImageNames) {
                                        Card card = mDeckDao.get().findCardByAnswerImage(answerImage);
                                        if (card == null) {
                                            mFileHelper.get().deleteCardAnswerImage(answerImage);
                                        }
                                    }
                                }
                                return true;
                            })
                    );
                    for (Future<Boolean> task : taskList) {
                        task.get();
                    }
                }
            } catch (Exception e) {
                mLogger.get().d(TAG, "Error occurred when cleaning file", e);
            }
        });
    }
}

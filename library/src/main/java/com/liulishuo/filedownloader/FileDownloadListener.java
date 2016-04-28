/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liulishuo.filedownloader;


import com.liulishuo.filedownloader.model.FileDownloadStatus;
import com.liulishuo.filedownloader.notification.FileDownloadNotificationListener;
import com.liulishuo.filedownloader.util.FileDownloadLog;

/**
 * Created by Jacksgong on 9/7/15.
 * <p/>
 * Normal flow: {@link #pending} -> {@link #started} -> {@link #connected} -> {@link #progress}  ->
 * {@link #blockComplete} -> {@link #completed}
 * <p/>
 * Maybe over with: {@link #paused}/{@link #completed}/{@link #error}/{@link #warn}
 * <p/>
 * If the task has already downloaded and exist: {@link #blockComplete} ->{@link #completed}
 *
 * @see FileDownloadLargeFileListener
 * @see FileDownloadNotificationListener
 * @see BaseDownloadTask#setSyncCallback(boolean)
 */
@SuppressWarnings("WeakerAccess")
public abstract class FileDownloadListener {

    public FileDownloadListener() {
    }

    /**
     * @param priority not handle priority any more
     * @see #FileDownloadListener()
     * @deprecated not handle priority any more
     */
    public FileDownloadListener(int priority) {
        FileDownloadLog.w(this, "not handle priority any more");
    }

    public boolean callback(FileDownloadMessage message) {
        final FileDownloadMessage.MessageSnapShot snapShot = message.getSnapshot();

        switch (snapShot.getStatus()) {
            case FileDownloadStatus.pending:
                pending(message.getTask(),
                        snapShot.getSmallSoFarBytes(),
                        snapShot.getSmallTotalBytes());
                break;
            case FileDownloadStatus.started:
                started(message.getTask());
                break;
            case FileDownloadStatus.connected:
                connected(message.getTask(),
                        snapShot.getEtag(),
                        snapShot.isResuming(),
                        snapShot.getSmallSoFarBytes(),
                        snapShot.getSmallTotalBytes());
                break;
            case FileDownloadStatus.progress:
                progress(message.getTask(),
                        snapShot.getSmallSoFarBytes(),
                        snapShot.getSmallTotalBytes());
                break;
            case FileDownloadStatus.retry:
                retry(message.getTask(),
                        snapShot.getException(),
                        snapShot.getRetryingTimes(),
                        snapShot.getSmallSoFarBytes());
                break;
            case FileDownloadStatus.blockComplete:
                blockComplete(message.getTask());
                break;
            case FileDownloadStatus.completed:
                completed(message.getTask());
                break;
            case FileDownloadStatus.error:
                error(message.getTask(),
                        snapShot.getException());
                break;
            case FileDownloadStatus.paused:
                paused(message.getTask(),
                        snapShot.getSmallSoFarBytes(),
                        snapShot.getSmallTotalBytes());
                break;
            case FileDownloadStatus.warn:
                // already same url & path in pending/running list
                warn(message.getTask());
                break;
        }

        return false;
    }

    /**
     * Enqueue, and pending, waiting for {@link #started(BaseDownloadTask)}.
     *
     * @param task       The task
     * @param soFarBytes Already downloaded bytes stored in the db
     * @param totalBytes Total bytes stored in the db
     * @see IFileDownloadMessenger#notifyPending()
     */
    protected abstract void pending(final BaseDownloadTask task, final int soFarBytes,
                                    final int totalBytes);

    /**
     * Finish pending, and start the download runnable.
     *
     * @param task Current task.
     * @see IFileDownloadMessenger#notifyStarted()
     */
    protected void started(final BaseDownloadTask task) {
    }

    /**
     * Already connected to the server, and received the Http-response.
     *
     * @param task       The task
     * @param etag       ETag
     * @param isContinue Is resume from breakpoint
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyConnected()
     */
    protected void connected(final BaseDownloadTask task, final String etag,
                             final boolean isContinue, final int soFarBytes, final int totalBytes) {

    }

    /**
     * Fetching datum from network and Writing to the local disk.
     *
     * @param task       The task
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyProgress()
     */
    protected abstract void progress(final BaseDownloadTask task, final int soFarBytes,
                                     final int totalBytes);

    /**
     * Unlike other methods in {@link #FileDownloadListener}, BlockComplete is executed in other
     * thread than main as default, when you receive this execution, it means has already completed
     * downloading, but just block the execution of {@link #completed(BaseDownloadTask)}. therefore,
     * you can unzip or do some ending operation before {@link #completed(BaseDownloadTask)} in other
     * thread.
     *
     * @param task the current task
     * @see IFileDownloadMessenger#notifyBlockComplete()
     */
    protected void blockComplete(final BaseDownloadTask task) {
    }

    /**
     * Occur a exception and has chance{@link BaseDownloadTask#setAutoRetryTimes(int)} to retry and
     * start Retry.
     *
     * @param task          The task
     * @param ex            Why retry
     * @param retryingTimes How many times will retry
     * @param soFarBytes    Number of bytes download so far
     * @see IFileDownloadMessenger#notifyRetry()
     */
    protected void retry(final BaseDownloadTask task, final Throwable ex, final int retryingTimes,
                         final int soFarBytes) {
    }

    // ======================= The task is over, if execute below methods =======================

    /**
     * Achieve complete ceremony.
     * <p/>
     * Complete downloading.
     *
     * @param task The task
     * @see IFileDownloadMessenger#notifyCompleted()
     * @see #blockComplete(BaseDownloadTask)
     */
    protected abstract void completed(final BaseDownloadTask task);

    /**
     * Task is paused, the vast majority of cases is invoking the {@link BaseDownloadTask#pause()}
     * manually.
     *
     * @param task       The task
     * @param soFarBytes Number of bytes download so far
     * @param totalBytes Total size of the download in bytes
     * @see IFileDownloadMessenger#notifyPaused()
     */
    protected abstract void paused(final BaseDownloadTask task, final int soFarBytes,
                                   final int totalBytes);

    /**
     * Occur a exception, but don't has any chance to retry.
     *
     * @param task The task
     * @param e    Any throwable on download pipeline
     * @see IFileDownloadMessenger#notifyError()
     * @see com.liulishuo.filedownloader.exception.FileDownloadHttpException
     * @see com.liulishuo.filedownloader.exception.FileDownloadGiveUpRetryException
     * @see com.liulishuo.filedownloader.exception.FileDownloadOutOfSpaceException
     */
    protected abstract void error(final BaseDownloadTask task, final Throwable e);

    /**
     * There has already had some same Tasks(Same-URL & Same-SavePath) in Pending-Queue or is
     * running.
     *
     * @param task The task
     * @see IFileDownloadMessenger#notifyWarn()
     */
    protected abstract void warn(final BaseDownloadTask task);

}

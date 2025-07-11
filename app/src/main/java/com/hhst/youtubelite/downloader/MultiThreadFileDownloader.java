package com.hhst.youtubelite.downloader;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloader;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class MultiThreadFileDownloader implements AdvancedFileDownloader {

  private final Map<String, List<Integer>> tasks = new ConcurrentHashMap<>();

  @Override
  public void download(String url, File output, ProgressCallback callback, String tag) {
    Integer id =
        FileDownloader.getImpl()
            .create(url)
            .setPath(output.getPath())
            .setAutoRetryTimes(3)
            .setCallbackProgressMinInterval(1000)
            .setListener(
                new FileDownloadListener() {
                  @Override
                  protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {}

                  @Override
                  protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                    callback.onProgress((int) Math.floor((100f * soFarBytes) / totalBytes), null);
                  }

                  @Override
                  protected void completed(BaseDownloadTask task) {
                    callback.onComplete(new File(task.getTargetFilePath()));
                  }

                  @Override
                  protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
                    callback.onCancel();
                  }

                  @Override
                  protected void error(BaseDownloadTask task, Throwable e) {
                    callback.onError((Exception) e);
                  }

                  @Override
                  protected void warn(BaseDownloadTask task) {}
                })
            .start();
    if (tag != null) {
      tasks.computeIfAbsent(tag, k -> new Vector<>()).add(id);
    }
  }

  @Override
  public void cancel(String tag) {
    if (tag == null) {
      return;
    }
    List<Integer> _tasks = tasks.get(tag);
    if (_tasks != null) {
      _tasks.forEach(id -> FileDownloader.getImpl().pause(id));
    }
  }
}

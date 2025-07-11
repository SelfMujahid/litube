package com.hhst.youtubelite.downloader;

import java.io.File;

public interface AdvancedFileDownloader {

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url The URL of the file to download.
   * @param output The file where the downloaded content will be saved.
   * @param callback A callback to report progress and completion status.
   * @param tag A tag to identify the download task, allowing for cancellation or tracking.
   */
  void download(String url, File output, ProgressCallback callback, String tag);

  default void download(String url, File output, ProgressCallback callback) {
    download(url, output, callback, null);
  }

  default void download(String url, File output) {
    download(
        url,
        output,
        new ProgressCallback() {
          @Override
          public void onProgress(int progress, String message) {}

          @Override
          public void onComplete(File file) {}

          @Override
          public void onError(Exception error) {}

          @Override
          public void onCancel() {}

          @Override
          public void onMerge() {}
        });
  }

  void cancel(String tag);
}

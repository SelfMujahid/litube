package com.hhst.youtubelite.downloader;

import android.app.Service;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.hhst.youtubelite.R;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class DownloadService extends Service {

  public final int max_download_tasks = 5;
  private ConcurrentHashMap<Integer, DownloadTask> download_tasks;
  private ExecutorService download_executor;

  @Override
  public void onCreate() {
    super.onCreate();
    download_tasks = new ConcurrentHashMap<>();
    download_executor = Executors.newFixedThreadPool(max_download_tasks);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent.getAction();
    int taskId = intent.getIntExtra("taskId", -1);
    if ("CANCEL_DOWNLOAD".equals(action)) {
      cancelDownload(taskId);
    } else if ("RETRY_DOWNLOAD".equals(action)) {
      retryDownload(taskId);
    } else if ("DELETE_DOWNLOAD".equals(action)) {
      deleteDownload(taskId);
    } else if ("DOWNLOAD_THUMBNAIL".equals(action)) {
      String url = intent.getStringExtra("thumbnail");
      String filename = intent.getStringExtra("filename");
      File outputDir = new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          getString(R.string.app_name));
      File outputFile = new File(outputDir, filename + ".jpg");
      downloadThumbnail(url, outputFile);
    }
    return super.onStartCommand(intent, flags, startId);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return new DownloadBinder();
  }

  private void showToast(String content) {
    new Handler(Looper.getMainLooper())
        .post(() -> Toast.makeText(this, content, Toast.LENGTH_SHORT).show());
  }

  private void downloadThumbnail(String thumbnail, File outputFile) {
    if (thumbnail != null) {
      download_executor.submit(
          () -> {
            try {
              FileUtils.copyURLToFile(new URL(thumbnail), outputFile);
              // notify to scan
              MediaScannerConnection.scanFile(
                  this, new String[] { outputFile.getAbsolutePath() }, null, null);
              showToast(getString(R.string.thumbnail_has_been_saved_to) + outputFile);
            } catch (Exception e) {
              Log.e(getString(R.string.failed_to_download_thumbnail), Log.getStackTraceString(e));
              showToast(getString(R.string.failed_to_download_thumbnail));
            }
          });
    }
  }

  private void executeDownload(DownloadTask task) {
    String fileName = task.getIsAudio()
        ? String.format("(audio only) %s", task.getFileName())
        : task.getFileName();
    int taskId = download_tasks.size();
    DownloadNotification notification = new DownloadNotification(this, taskId);

    // Show initial notification with better content
    String initialContent = task.getIsAudio()
        ? getString(R.string.downloading_audio) + ": " + fileName
        : getString(R.string.downloading_video) + ": " + fileName;

    startForeground(taskId, notification.showNotification(initialContent, 0));

    task.setNotification(notification);
    download_tasks.put(taskId, task);

    CompletableFuture.runAsync(
        () -> {
          try {
            // Create output file
            File output = new File(
                task.getOutputDir(),
                task.getFileName() + (task.getIsAudio() ? ".m4a" : ".mp4"));
            task.setOutput(output);
            AtomicLong lastProgress = new AtomicLong(0);
            AtomicLong lastTimestamp = new AtomicLong(System.currentTimeMillis());
            // Download using NewPipeExtractor-based YoutubeDownloader
            YoutubeDownloader.download(
                "download_task" + taskId,
                task.getVideoStream(),
                task.getAudioStream(),
                output,
                (download, total, percentage, information) -> {
                  int progress = (int) Math.round(percentage);
                  if (progress == -1) {
                    task.setState(DownloaderState.MUXING);
                    notification.startMuxing(initialContent);
                  } else {
                    task.setState(DownloaderState.DOWNLOADING);
                    if (Math.abs(progress - lastProgress.get()) > 10
                        || ((System.currentTimeMillis() - lastTimestamp.get()) > 2000)
                            && Math.abs(progress - lastProgress.get()) > 1) {
                      notification.updateProgress(progress, information);
                      lastProgress.set(progress);
                    }
                  }
                },
                this);

            showToast(
                String.format(
                    getString(R.string.download_finished), fileName, output.getPath()));
            // notify to scan
            MediaScannerConnection.scanFile(
                this, new String[] { output.getAbsolutePath() }, null, null);

            notification.completeDownload(
                String.format(
                    getString(R.string.download_finished), fileName, output.getPath()),
                output,
                task.getIsAudio() ? "audio/*" : "video/*");

            // Delete temporary files if any
            deleteTempFiles(output);
            task.setState(DownloaderState.FINISHED);
          } catch (IOException e) {
            Log.e(getString(R.string.failed_to_download), Log.getStackTraceString(e));
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("cancelled")) {
              showToast(getString(R.string.download_canceled));
              task.getNotification().cancelDownload("");
            } else {
              showToast(getString(R.string.failed_to_download));
              task.getNotification().cancelDownload("");
            }
            task.setState(DownloaderState.STOPPED);
          } catch (InterruptedException e) {
            Log.e(getString(R.string.failed_to_download), Log.getStackTraceString(e));
            showToast(getString(R.string.download_canceled));
            task.getNotification().cancelDownload("");
            task.setState(DownloaderState.STOPPED);
          }
        },
        download_executor)
        .thenRun(() -> stopForeground(true));
  }

  public void initiateDownload(DownloadTask task) {
    download_executor.submit(
        () -> {
          // check and create output directory
          File outputDir = new File(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
              getString(R.string.app_name));
          if (!outputDir.exists()) {
            boolean ignored = outputDir.mkdirs();
          }
          task.setOutputDir(outputDir);
          task.setState(DownloaderState.RUNNING);
          executeDownload(task);
        });
  }

  private void cancelDownload(int taskId) {
    DownloadTask task = download_tasks.get(taskId);
    if (task != null) {
      task.setState(DownloaderState.STOPPED);

      // Cancel both download and muxing
      YoutubeDownloader.cancel("download_task" + taskId);

      if (task.getNotification() != null) {
        task.getNotification().cancelDownload("");
      }

      // Delete temporary files if any
      deleteTempFiles(task.getOutput());
    }
  }

  private void retryDownload(int taskId) {
    DownloadTask task = download_tasks.get(taskId);
    if (task != null) {
      task.setState(DownloaderState.RUNNING);

      // Cancel both download and muxing
      YoutubeDownloader.cancel("download_task" + taskId);

      if (task.getNotification() != null) {
        task.getNotification().clearDownload();
      }
      executeDownload(task);
    }
  }

  private void deleteDownload(int taskId) {
    DownloadTask task = download_tasks.get(taskId);
    if (task != null) {
      if (task.getOutput() != null && task.getOutput().exists()) {
        try {
          FileUtils.forceDelete(task.getOutput());
          showToast(getString(R.string.file_deleted));
        } catch (IOException e) {
          Log.e(getString(R.string.failed_to_delete), Log.getStackTraceString(e));
          showToast(getString(R.string.failed_to_delete));
        }
      }
      if (task.getNotification() != null) {
        task.getNotification().clearDownload();
      }
      // Delete temporary files if any
      deleteTempFiles(task.getOutput());
    }
  }

  private void deleteTempFiles(File output) {
    try {
      FileUtils.deleteDirectory(
          new File(output.getParent(), FilenameUtils.getBaseName(output.getPath())));
    } catch (Exception e) {
      Log.d("DownloadService", "Failed to delete temporary files: " + e.getMessage());
    }
  }

  @Override
  public boolean onUnbind(Intent intent) {
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // Shutdown the executor service
    if (download_executor != null) {
      download_executor.shutdown();
    }
    // Delete temporary files
    YoutubeDownloader.deleteTempFiles();
    // Clear all download tasks and their notifications
    if (download_tasks != null) {
      for (DownloadTask task : download_tasks.values()) {
        if (task.getNotification() != null) {
          task.getNotification().clearDownload();
        }
      }
      download_tasks.clear();
    }
  }

  public class DownloadBinder extends Binder {
    public DownloadService getService() {
      return DownloadService.this;
    }
  }
}

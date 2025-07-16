package com.hhst.youtubelite.webview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hhst.youtubelite.MainActivity;
import com.hhst.youtubelite.common.YoutubeExtractor;
import com.hhst.youtubelite.downloader.DownloadDialog;
import com.hhst.youtubelite.extension.ExtensionDialog;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public class JavascriptInterface {
  private final Context context;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  
  public JavascriptInterface(Context context) {
    this.context = context;
  }

  @android.webkit.JavascriptInterface
  public void test(String url) {
    Log.d("JavascriptInterface-test", url);
  }

  @android.webkit.JavascriptInterface
  public void finishRefresh() {
    mainHandler.post(() -> ((MainActivity) context).swipeRefreshLayout.setRefreshing(false));
  }

  @android.webkit.JavascriptInterface
  public void setRefreshLayoutEnabled(boolean enabled) {
    mainHandler.post(() -> ((MainActivity) context).swipeRefreshLayout.setEnabled(enabled));
  }

  @android.webkit.JavascriptInterface
  public void download(String url) {
    mainHandler.post(() -> new DownloadDialog(url, context).show());
  }

  @android.webkit.JavascriptInterface
  public void extension() {
    mainHandler.post(() -> new ExtensionDialog(context).build());
  }

  @android.webkit.JavascriptInterface
  public void showPlayback(String title, String author, String thumbnail, long duration) {
    mainHandler.post(() -> {
      MainActivity activity = (MainActivity) context;
      if (activity != null && activity.playbackService != null) {
        activity.playbackService.showNotification(title, author, thumbnail, duration);
      }
    });
  }

  @android.webkit.JavascriptInterface
  public void hidePlayback() {
    mainHandler.post(() -> {
      MainActivity activity = (MainActivity) context;
      if (activity != null && activity.playbackService != null) {
        activity.playbackService.hideNotification();
      }
    });
  }

  @android.webkit.JavascriptInterface
  public void updatePlayback(long pos, float playbackSpeed, boolean isPlaying) {
    mainHandler.postAtFrontOfQueue(() -> {
      MainActivity activity = (MainActivity) context;
      if (activity != null && activity.playbackService != null) {
        activity.playbackService.updateProgress(pos, playbackSpeed, isPlaying);
      }
    });
  }

  @android.webkit.JavascriptInterface
  public void infoVideoDetails(String url) {
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              try {
                YoutubeExtractor.info(url);
              } catch (ExtractionException | IOException ignored) {
              }
            });
  }

  @android.webkit.JavascriptInterface
  public void shareLink(String url) {
    mainHandler.post(() -> ((MainActivity) context).shareLink(url));
  }
}

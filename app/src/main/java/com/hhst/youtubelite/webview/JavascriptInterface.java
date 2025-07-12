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
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public class JavascriptInterface {
  private final Context context;

  public JavascriptInterface(Context context) {
    this.context = context;
  }

  @android.webkit.JavascriptInterface
  public void test(String url) {
    Log.d("JavascriptInterface-test", url);
  }

  @android.webkit.JavascriptInterface
  public void finishRefresh() {
    new Handler(Looper.getMainLooper())
        .post(() -> ((MainActivity) context).swipeRefreshLayout.setRefreshing(false));
  }

  @android.webkit.JavascriptInterface
  public void setRefreshLayoutEnabled(boolean enabled) {
    new Handler(Looper.getMainLooper())
        .post(() -> ((MainActivity) context).swipeRefreshLayout.setEnabled(enabled));
  }

  @android.webkit.JavascriptInterface
  public void download(String url) {
    new Handler(Looper.getMainLooper()).post(() -> new DownloadDialog(url, context).show());
  }

  @android.webkit.JavascriptInterface
  public void extension() {
    new Handler(Looper.getMainLooper()).post(() -> new ExtensionDialog(context).build());
  }

  @android.webkit.JavascriptInterface
  public void showPlayback(String title, String author, String thumbnail, long duration) {
    new Handler(Looper.getMainLooper())
        .post(
            () ->
                ((MainActivity) context)
                    .playbackService.showNotification(title, author, thumbnail, duration));
  }

  @android.webkit.JavascriptInterface
  public void hidePlayback() {
    new Handler(Looper.getMainLooper())
        .post(() -> ((MainActivity) context).playbackService.hideNotification());
  }

  @android.webkit.JavascriptInterface
  public void updatePlayback(long pos, float playbackSpeed, boolean isPlaying) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              ((MainActivity) context)
                  .playbackService.updateProgress(pos, playbackSpeed, isPlaying);
              ((MainActivity) context).seekToPosition(pos);
              ((MainActivity) context).setVideoPlayState(isPlaying);
            });
  }

  public void infoVideoDetails(String url) {
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              try {
                YoutubeExtractor.info(url);
              } catch (ExtractionException | IOException ignored) {
              }
            });
  }

  @android.webkit.JavascriptInterface
  public void shareLink(String url) {
    new Handler(Looper.getMainLooper()).post(() -> ((MainActivity) context).shareLink(url));
  }
}

package com.hhst.youtubelite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.hhst.youtubelite.common.YoutubeExtractor;
import com.hhst.youtubelite.downloader.DownloadService;
import com.hhst.youtubelite.player.VideoPlayerController;
import com.hhst.youtubelite.player.VideoPlayerControllerImpl;
import com.hhst.youtubelite.webview.YoutubeWebview;
import com.tencent.mmkv.MMKV;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_NOTIFICATION_CODE = 100;
  private static final int REQUEST_STORAGE_CODE = 101;
  public YoutubeWebview webview;
  public SwipeRefreshLayout swipeRefreshLayout;
  public ProgressBar progressBar;
  @Getter public DownloadService downloadService;
  public PlaybackService playbackService;

  // Pip mode related fields
  private SurfaceView playerView;
  private VideoPlayerController videoController;
  private ServiceConnection playbackServiceConnection;
  private ServiceConnection downloadServiceConnection;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_main);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
          return insets;
        });

    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
    progressBar = findViewById(R.id.progressBar);
    webview = findViewById(R.id.webview);

    swipeRefreshLayout.setColorSchemeResources(R.color.light_blue, R.color.blue, R.color.dark_blue);
    swipeRefreshLayout.setOnRefreshListener(
        () ->
            webview.evaluateJavascript(
                "window.dispatchEvent(new Event('onRefresh'));", value -> {}));
    swipeRefreshLayout.setProgressViewOffset(true, 80, 180);

    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              loadScript();
              runOnUiThread(
                  () -> {
                    webview.build();
                    // Process the intent data if available
                    Intent intent = getIntent();
                    String action = intent.getAction();
                    String type = intent.getType();

                    String baseUrl = "https://m.youtube.com";
                    if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                      if (sharedText != null
                          && (sharedText.startsWith("http://")
                              || sharedText.startsWith("https://"))) {
                        Log.d("MainActivity", "Loading shared URL: " + sharedText);
                        webview.loadUrl(sharedText);
                      } else {
                        webview.loadUrl(baseUrl);
                      }
                    } else {
                      Uri intentUri = intent.getData();
                      if (intentUri != null) {
                        Log.d("MainActivity", "Loading URL from intent: " + intentUri);
                        webview.loadUrl(intentUri.toString());
                      } else {
                        webview.loadUrl(baseUrl);
                      }
                    }
                  });
            });

    requestPermissions();
    startDownloadService();
    startPlaybackService();
    initializePlayer();
    MMKV.initialize(this);
  }

  public void requestPermissions() {

    // check and require post-notification permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this, new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_CODE);
      }
    }

    // check storage permission
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_CODE);
      }
    }
  }

  private void loadScript() {
    AssetManager assetManager = getAssets();

    List<String> resourceDirs = Arrays.asList("css", "js");
    try {
      for (String dir : resourceDirs) {
        List<String> resources =
            new ArrayList<>(Arrays.asList(Objects.requireNonNull(assetManager.list(dir))));
        // inject init.js or init.min.js
        String initScript =
            resources.contains("init.js")
                ? "init.js"
                : resources.contains("init.min.js") ? "init.min.js" : null;
        if (initScript != null) {
          webview.injectJavaScript(assetManager.open(dir + "/" + initScript));
          resources.remove(initScript);
        }
        for (String script : resources) {
          InputStream stream = assetManager.open(Paths.get(dir, script).toString());
          if (FilenameUtils.getExtension(script).equals("js")) {
            webview.injectJavaScript(stream);
          } else if (FilenameUtils.getExtension(script).equals("css")) {
            webview.injectCss(stream);
          }
        }
      }
    } catch (Exception e) {
      Log.e("load scripts error", "Failed to load assets: " + Log.getStackTraceString(e));
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
      webview.evaluateJavascript("window.dispatchEvent(new Event('onGoBack'));", value -> {});
      if (webview.fullscreen != null && webview.fullscreen.getVisibility() == View.VISIBLE) {
        webview.evaluateJavascript("document.exitFullscreen()", value -> {});
        return true;
      }
      if (webview.canGoBack()) {
        webview.goBack();
      } else {
        finish();
      }
      return true;
    }
    return false;
  }

  private void startDownloadService() {
    // bind the download service
    downloadServiceConnection =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(ComponentName componentName, IBinder binder) {
            downloadService = ((DownloadService.DownloadBinder) binder).getService();
          }

          @Override
          public void onServiceDisconnected(ComponentName componentName) {}
        };

    Intent intent = new Intent(this, DownloadService.class);
    bindService(intent, downloadServiceConnection, Context.BIND_AUTO_CREATE);
  }

  private void startPlaybackService() {
    // bind
    playbackServiceConnection =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(ComponentName componentName, IBinder binder) {
            playbackService = ((PlaybackService.PlaybackBinder) binder).getService();
            playbackService.initialize(webview);
          }

          @Override
          public void onServiceDisconnected(ComponentName componentName) {}
        };
    Intent intent = new Intent(this, PlaybackService.class);
    bindService(intent, playbackServiceConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    Uri uri = intent.getData();
    if (uri != null && webview != null) {
      webview.loadUrl(uri.toString());
    }
  }

  public void shareLink(String url) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse(url));
    startActivity(intent);
  }

  /**
   * Initialize the video player component with surface view and set up listeners for playback state
   * changes and picture-in-picture mode
   */
  @SuppressLint("InflateParams")
  private void initializePlayer() {
    playerView = findViewById(R.id.video_surface);
    videoController = new VideoPlayerControllerImpl();
    videoController.initialize(this, playerView);
    // Set playback state listener
    videoController.setPlaybackStateListener(
        new VideoPlayerController.PlaybackStateListener() {
          @Override
          public void onPlaybackStateChanged(boolean isPlaying) {
            if (isPlaying) {
              webview.evaluateJavascript("window.dispatchEvent(new Event('play'));", null);
            } else {
              webview.evaluateJavascript("window.dispatchEvent(new Event('pause'));", null);
            }
          }

          @Override
          public void onProgressChanged(int position, int duration) {
            long seekSeconds = Math.round(position / 1000f);
            webview.evaluateJavascript(
                String.format(
                    Locale.getDefault(),
                    "window.dispatchEvent(new CustomEvent('seek', { detail: { time: %d } }));",
                    seekSeconds),
                null);
          }

          @Override
          public void onPlaybackCompleted() {
            Log.d("MainActivity", "Playback completed");
          }

          @Override
          public void onPlaybackError(String error) {
            Toast.makeText(MainActivity.this, "Playback error: " + error, Toast.LENGTH_LONG).show();
          }
        });
  }

  /** Enter PiP mode with only the video player visible */
  private void enterPipMode() {
    if (!webview.isWatchPage()) return;
    Rational aspectRatio = new Rational(16, 9);
    var builder = new PictureInPictureParams.Builder().setAspectRatio(aspectRatio);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setSeamlessResizeEnabled(true);
    }
    enterPictureInPictureMode(builder.build());
  }

  /** Show only the video player (for PiP) */
  private void showPlayerOnly() {
    if (playerView != null) playerView.setVisibility(View.VISIBLE);
    if (webview != null) webview.setVisibility(View.GONE);
    if (swipeRefreshLayout != null) swipeRefreshLayout.setVisibility(View.GONE);
    if (progressBar != null) progressBar.setVisibility(View.GONE);
  }

  /** Hide the video player when not in PiP */
  private void hidePlayer() {
    if (playerView != null) playerView.setVisibility(View.GONE);
    if (webview != null) webview.setVisibility(View.VISIBLE);
    if (swipeRefreshLayout != null) swipeRefreshLayout.setVisibility(View.VISIBLE);
    if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
  }

  /**
   * Set play or pause state
   *
   * @param isPlaying Whether the video is currently playing
   */
  public void setVideoPlayState(boolean isPlaying) {
    if (videoController != null) {
      if (isPlaying) videoController.getPlaybackControl().play();
      else videoController.getPlaybackControl().pause();
    }
  }

  /**
   * Seek to specific time position
   *
   * @param position Time position in milliseconds
   */
  public void seekToPosition(long position) {
    if (videoController != null) {
      videoController.getPlaybackControl().seekTo(position);
    }
  }

  /**
   * Called when the user is navigating away from the app Automatically enters picture-in-picture
   * mode
   */
  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    enterPipMode();
  }

  /**
   * Called when picture-in-picture mode changes Adjusts UI visibility based on PiP state
   *
   * @param isInPictureInPictureMode True if the activity is in picture-in-picture mode
   */
  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode);
    if (isInPictureInPictureMode) {
      String url = webview.getUrl();
      Executors.newSingleThreadExecutor()
          .execute(
              () -> {
                try {
                  var details = YoutubeExtractor.info(url);
                  if (details == null) {
                    throw new ExtractionException("Video details is null");
                  }
                  runOnUiThread(
                      () ->
                          videoController
                              .getVideoSourceControl()
                              .setVideoUrl(details.getVideoStreams().get(0).getContent()));
                } catch (ExtractionException | IOException e) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(
                                  this, R.string.failed_to_load_video_details, Toast.LENGTH_SHORT)
                              .show());
                }
              });
      showPlayerOnly();
    } else {
      hidePlayer();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unbindService(downloadServiceConnection);
    unbindService(playbackServiceConnection);
  }
}

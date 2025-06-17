package com.hhst.youtubelite;

import android.Manifest;
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
import android.view.KeyEvent;
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
import com.hhst.youtubelite.downloader.DownloadService;
import com.hhst.youtubelite.webview.YoutubeWebview;
import com.tencent.mmkv.MMKV;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import org.apache.commons.io.FilenameUtils;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_NOTIFICATION_CODE = 100;
  private static final int REQUEST_STORAGE_CODE = 101;
  public YoutubeWebview webview;
  public SwipeRefreshLayout swipeRefreshLayout;
  public ProgressBar progressBar;
  public DownloadService downloadService;
  public PlaybackService playbackService;

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

    MMKV.initialize(this);

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

                    if (Intent.ACTION_SEND.equals(action) && type != null && "text/plain".equals(type)) {
                        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (sharedText != null && (sharedText.startsWith("http://") || sharedText.startsWith("https://"))) {
                            Log.d("MainActivity", "Loading shared URL: " + sharedText);
                            webview.loadUrl(sharedText);
                        } else {
                            Log.d("MainActivity", "No valid URL in shared text, loading base URL");
                            webview.loadUrl(getString(R.string.base_url));
                        }
                    } else {
                        Uri intentUri = intent.getData();
                        if (intentUri != null) {
                            Log.d("MainActivity", "Loading URL from intent: " + intentUri);
                            webview.loadUrl(intentUri.toString());
                        } else {
                            Log.d("MainActivity", "Loading base URL: " + getString(R.string.base_url));
                            webview.loadUrl(getString(R.string.base_url));
                        }
                    }
                  });
            });

    requestPermissions();
    startDownloadService();
    startPlaybackService();
    initializeDownloader();
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
    ServiceConnection connection =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(ComponentName componentName, IBinder binder) {
            downloadService = ((DownloadService.DownloadBinder) binder).getService();
          }

          @Override
          public void onServiceDisconnected(ComponentName componentName) {}
        };

    Intent intent = new Intent(this, DownloadService.class);
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
  }

  private void startPlaybackService() {
    // bind
    ServiceConnection connection =
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
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
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

  private void initializeDownloader() {
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              // Initialize the downloader
              try {
                YoutubeDL.getInstance().init(this);
                FFmpeg.getInstance().init(this);
              } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.downloader_initialize_error, Toast.LENGTH_SHORT).show());
              }
              // try to update yt-dlp
              try {
                YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._STABLE);
              } catch (YoutubeDLException e) {
                Log.e("unable to update yt-dlp", Log.getStackTraceString(e));
              }
            });
  }

  public void shareLink(String url) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse(url));
    startActivity(intent);
  }
}

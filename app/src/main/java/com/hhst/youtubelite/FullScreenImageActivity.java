package com.hhst.youtubelite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.youtubelite.downloader.DownloadService;
import com.squareup.picasso.Picasso;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;

/** Show the thumbnail image in full screen mode. */
public class FullScreenImageActivity extends AppCompatActivity {

  // thumbnail resource url
  private String url;

  // thumbnail filename, used for saving or caching
  private String filename;

  // thumbnail file to cache
  private volatile File file;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_fullscreen_image);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(android.R.id.content),
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
          return insets;
        });
    PhotoView photoView = findViewById(R.id.photoView);
    // destroy this activity when click image or button
    photoView.setOnClickListener(view -> finish());
    photoView.setOnLongClickListener(
        view -> {
          AlertDialog menu =
              new MaterialAlertDialogBuilder(this)
                  .setCancelable(true)
                  .setItems(
                      new CharSequence[] {getString(R.string.save), getString(R.string.share)},
                      ((dialog, which) -> onContextMenuClicked(which)))
                  .create();
          Window window = menu.getWindow();
          if (window != null) window.setLayout(600, WindowManager.LayoutParams.WRAP_CONTENT);
          menu.show();
          return true;
        });

    findViewById(R.id.btnClose).setOnClickListener(view -> finish());
    url = getIntent().getStringExtra("thumbnail");
    filename = getIntent().getStringExtra("filename");
    try {
      // load image
      Picasso.get().load(url).into(photoView);
    } catch (Exception e) {
      Log.e(getString(R.string.failed_to_load_image), Log.getStackTraceString(e));
      Toast.makeText(this, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show();
    }
  }

  public void onContextMenuClicked(int index) {
    switch (index) {
      case 0:
        Intent saveIntent = new Intent(this, DownloadService.class);
        saveIntent.setAction("DOWNLOAD_THUMBNAIL");
        saveIntent.putExtra("thumbnail", url);
        saveIntent.putExtra("filename", filename);
        startService(saveIntent);
        return;
      case 1:
        File file = new File(getCacheDir(), filename + ".jpg");
        // download thumbnail to local cache directory and send it
        Executors.newSingleThreadExecutor()
            .execute(
                () -> {
                  try {
                    // download thumbnail
                    if (!file.exists()) FileUtils.copyURLToFile(new URL(url), file);
                    this.file = file;
                    // build uri
                    Uri uri =
                        FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(
                        Intent.createChooser(shareIntent, getString(R.string.share_thumbnail)));
                  } catch (IOException e) {
                    Log.e(
                        getString(R.string.failed_to_download_thumbnail),
                        Log.getStackTraceString(e));
                    runOnUiThread(
                        () ->
                            Toast.makeText(
                                    this, R.string.failed_to_download_thumbnail, Toast.LENGTH_SHORT)
                                .show());
                  }
                });
    }
  }

  @Override
  public void finish() {
    super.finish();
    // clean cached image
    if (file != null)
      Executors.newSingleThreadExecutor().execute(() -> FileUtils.deleteQuietly(file));
  }
}

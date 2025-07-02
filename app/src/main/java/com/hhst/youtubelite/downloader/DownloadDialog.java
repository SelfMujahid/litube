package com.hhst.youtubelite.downloader;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hhst.youtubelite.FullScreenImageActivity;
import com.hhst.youtubelite.MainActivity;
import com.hhst.youtubelite.R;
import com.squareup.picasso.Picasso;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

public class DownloadDialog {
  private final Context context;

  private final String url;
  private final ExecutorService executor;
  private final CountDownLatch detailsLatch;
  private DownloadDetails details;
  private View dialogView;

  public DownloadDialog(String url, Context context) {
    this.url = url;
    this.context = context;
    executor = Executors.newCachedThreadPool();
    detailsLatch = new CountDownLatch(1);
    Consumer<Exception> errHandler =
        e -> {
          // avoid some unnecessary toast
          if (e instanceof InterruptedIOException) return;
          Log.e(
              context.getString(R.string.failed_to_load_video_details), Log.getStackTraceString(e));
          new Handler(Looper.getMainLooper())
              .post(
                  () ->
                      Toast.makeText(
                              context, R.string.failed_to_load_video_details, Toast.LENGTH_SHORT)
                          .show());
        };
    executor.submit(
        () -> {
          try {
            // try to get details from cache
            details = YoutubeDownloader.info(url);
            detailsLatch.countDown();
          } catch (Exception e) {
            errHandler.accept(e);
          }
        });
  }

  public static String formatSize(long length) {
    if (length < 0) {
      return "Invalid size";
    }

    if (length == 0) {
      return "0";
    }

    int unitIndex = 0;

    String[] UNITS = {"B", "KB", "MB", "GB", "TB"};
    double size = length;

    while (size >= 1024 && unitIndex < UNITS.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return String.format(Locale.US, "%.1f %s", size, UNITS[unitIndex]);
  }

  public void show() {

    dialogView = View.inflate(context, R.layout.download_dialog, null);
    ProgressBar progressBar = dialogView.findViewById(R.id.loadingBar);
    if (progressBar != null && details == null) progressBar.setVisibility(View.VISIBLE);

    AlertDialog dialog =
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.download))
            .setView(dialogView)
            .setCancelable(true)
            .create();

    dialog.setOnDismissListener(dialogInterface -> executor.shutdownNow());

    ImageView imageView = dialogView.findViewById(R.id.download_image);
    EditText editText = dialogView.findViewById(R.id.download_edit_text);
    Button videoButton = dialogView.findViewById(R.id.button_video);
    Button thumbnailButton = dialogView.findViewById(R.id.button_thumbnail);
    final Button audioButton = dialogView.findViewById(R.id.button_audio);
    final Button cancelButton = dialogView.findViewById(R.id.button_cancel);
    final Button downloadButton = dialogView.findViewById(R.id.button_download);

    executor.submit(
        () -> {
          try {
            detailsLatch.await();
            if (progressBar != null && progressBar.getVisibility() == View.VISIBLE)
              dialogView.post(() -> progressBar.setVisibility(View.GONE));
            // load image
            loadImage(imageView);
            // load default video name
            loadVideoName(editText);
          } catch (InterruptedException ignored) {
          }
        });

    // state
    final AtomicBoolean isVideoSelected = new AtomicBoolean(false);
    final AtomicBoolean isThumbnailSelected = new AtomicBoolean(false);
    final AtomicBoolean isAudioSelected = new AtomicBoolean(false);
    final AtomicReference<VideoStream> selectedVideoStream = new AtomicReference<>(null);

    // set button default background color
    videoButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));
    thumbnailButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));
    audioButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));

    // get theme color
    TypedValue value = new TypedValue();
    context
        .getTheme()
        .resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true);
    final int themeColor = value.data;

    // on video button clicked
    videoButton.setOnClickListener(
        v -> showVideoQualityDialog(selectedVideoStream, isVideoSelected, videoButton, themeColor));

    // on thumbnail button clicked
    thumbnailButton.setOnClickListener(
        v -> {
          if (details == null) {
            return;
          }
          isThumbnailSelected.set(!isThumbnailSelected.get());
          thumbnailButton.setSelected(isThumbnailSelected.get());
          if (isThumbnailSelected.get()) {
            thumbnailButton.setBackgroundColor(themeColor);
          } else {
            thumbnailButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));
          }
        });

    // on audio-only button clicked
    audioButton.setOnClickListener(
        v -> {
          if (details == null) {
            return;
          }
          isAudioSelected.set(!isAudioSelected.get());
          audioButton.setSelected(isAudioSelected.get());
          if (isAudioSelected.get()) {
            audioButton.setBackgroundColor(themeColor);
          } else {
            audioButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));
          }
        });

    // on download button clicked
    downloadButton.setOnClickListener(
        v -> {
          // fixed in live page
          if (details == null) {
            dialog.dismiss();
            return;
          }

          if (!isVideoSelected.get() && !isThumbnailSelected.get() && !isAudioSelected.get()) {
            dialogView.post(
                () ->
                    Toast.makeText(context, R.string.select_something_first, Toast.LENGTH_SHORT)
                        .show());
            return;
          }

          String fileName = editText.getText().toString();
          if (fileName.isEmpty()) {
            fileName = details.getTitle();
          }
          fileName = sanitizeFileName(fileName);

          // download thumbnail
          if (isThumbnailSelected.get()) {
            Intent thumbnailIntent = new Intent(context, DownloadService.class);
            thumbnailIntent.setAction("DOWNLOAD_THUMBNAIL");
            thumbnailIntent.putExtra("thumbnail", details.getThumbnail());
            thumbnailIntent.putExtra("filename", fileName);
            context.startService(thumbnailIntent);
          }

          // download video/audio
          if (isVideoSelected.get() || isAudioSelected.get()) {
            Intent downloadIntent = new Intent(context, DownloadService.class);
            DownloadTask downloadTask = new DownloadTask();
            downloadTask.setUrl(url);
            downloadTask.setFileName(fileName);
            downloadTask.setThumbnail(details.getThumbnail());
            downloadTask.setVideoStream(isVideoSelected.get() ? selectedVideoStream.get() : null);
            downloadTask.setAudioStream(details.getAudioStream());
            downloadTask.setIsAudio(isAudioSelected.get());
            downloadTask.setState(DownloaderState.RUNNING);
            downloadTask.setOutputDir(null);
            downloadTask.setOutput(null);
            downloadTask.setNotification(null);

            // Start download service
            context.startService(downloadIntent);

            // Get service and initiate download
            if (context instanceof MainActivity activity) {
              DownloadService service = activity.getDownloadService();
              if (service != null) {
                service.initiateDownload(downloadTask);
              }
            }
          }

          dialog.dismiss();
        });

    // on cancel button clicked
    cancelButton.setOnClickListener(v -> dialog.dismiss());

    dialog.show();
  }

  private void loadImage(ImageView imageView) {
    if (details != null && details.getThumbnail() != null) {
      dialogView.post(
          () -> {
            Picasso.get()
                .load(details.getThumbnail())
                .error(R.drawable.ic_broken_image)
                .into(imageView);
            imageView.setOnClickListener(
                view ->
                    executor.submit(
                        () -> {
                          Intent intent = new Intent(context, FullScreenImageActivity.class);
                          intent.putExtra("thumbnail", details.getThumbnail());
                          intent.putExtra(
                              "filename",
                              String.format("%s-%s", details.getTitle(), details.getAuthor())
                                  .trim());
                          context.startActivity(intent);
                        }));
          });
    }
  }

  private void loadVideoName(EditText editText) {
    if (details != null) {
      dialogView.post(
          () -> editText.setText(String.format("%s-%s", details.getTitle(), details.getAuthor())));
    }
  }

  private void showVideoQualityDialog(
      AtomicReference<VideoStream> selectedVideoStream,
      AtomicBoolean isVideoSelected,
      Button videoButton,
      int themeColor) {
    View dialogView = View.inflate(context, R.layout.quality_selector, null);
    ProgressBar progressBar = dialogView.findViewById(R.id.loadingBar2);
    if (progressBar != null && details == null) progressBar.setVisibility(View.VISIBLE);

    AlertDialog qualityDialog =
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.video_quality))
            .setView(dialogView)
            .create();
    LinearLayout qualitySelector = dialogView.findViewById(R.id.quality_container);
    Button cancelButton = dialogView.findViewById(R.id.button_cancel);
    Button confirmButton = dialogView.findViewById(R.id.button_confirm);

    // create radio button dynamically
    AtomicReference<CheckBox> checkedBox = new AtomicReference<>();
    AtomicReference<VideoStream> selectedStream = new AtomicReference<>();
    executor.submit(
        () -> {
          try {
            detailsLatch.await();
          } catch (InterruptedException ignored) {
          }
          if (details == null
              || details.getVideoStreams() == null
              || details.getVideoStreams().isEmpty()) {
            Toast.makeText(context, R.string.failed_to_load_video_formats, Toast.LENGTH_SHORT)
                .show();
            return;
          }
          if (progressBar != null && progressBar.getVisibility() == View.VISIBLE) {
            dialogView.post(() -> progressBar.setVisibility(View.GONE));
            qualityDialog.dismiss();
            dialogView.post(
                () ->
                    showVideoQualityDialog(
                        selectedVideoStream, isVideoSelected, videoButton, themeColor));
          }
          AudioStream audioStream = details.getAudioStream();
          long audioSize =
              audioStream.getItagItem() != null ? audioStream.getItagItem().getContentLength() : 0;
          for (var stream : details.getVideoStreams()) {
            CheckBox choice = new CheckBox(context);
            choice.setText(
                String.format(
                    "%s (%s)",
                    stream.getResolution(),
                    formatSize(
                        audioSize
                            + (stream.getItagItem() != null
                                ? stream.getItagItem().getContentLength()
                                : 0))));
            choice.setLayoutParams(
                new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
            choice.setOnCheckedChangeListener(
                (v, isChecked) -> {
                  if (isChecked) {
                    if (checkedBox.get() != null) {
                      checkedBox.get().setChecked(false);
                    }
                    selectedStream.set(stream);
                    checkedBox.set((CheckBox) v);
                  } else {
                    selectedStream.set(null);
                    checkedBox.set(null);
                  }
                });
            qualitySelector.addView(choice);
            if (selectedVideoStream.get() != null && selectedVideoStream.get().equals(stream)) {
              choice.setChecked(true);
            }
          }
        });

    cancelButton.setOnClickListener(v -> qualityDialog.dismiss());
    confirmButton.setOnClickListener(
        v -> {
          if (checkedBox.get() == null) {
            selectedVideoStream.set(null);
            isVideoSelected.set(false);
            videoButton.setBackgroundColor(context.getColor(android.R.color.darker_gray));
          } else {
            selectedVideoStream.set(selectedStream.get());
            isVideoSelected.set(true);
            videoButton.setBackgroundColor(themeColor);
          }
          qualityDialog.dismiss();
        });

    qualityDialog.show();
  }

  private String sanitizeFileName(String fileName) {
    // Remove invalid characters for file names
    return fileName.replaceAll("[<>:\"/|?*]", "_");
  }
}

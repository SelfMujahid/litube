package com.hhst.youtubelite.downloader;

import android.content.Context;
import com.hhst.youtubelite.R;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

/* the main class for download video and audio */
public class YoutubeDownloader {

  private static final AdvancedFileDownloader downloader = new MultiThreadFileDownloader();

  // Flag whether the task is cancelled
  private static final Map<String, Boolean> cancelledTasks = new ConcurrentHashMap<>();

  public static void download(
      String tag,
      VideoStream videoStream,
      AudioStream audioStream,
      File output,
      ProgressCallback callback,
      Context context) {

    // Download the video and audio streams
    String baseName = FilenameUtils.getBaseName(output.getPath());
    File tempDir = new File(context.getCacheDir(), baseName);
    try {
      FileUtils.forceMkdir(tempDir);
    } catch (IOException e) {
      callback.onError(e);
    }
    File videoFile = new File(tempDir, baseName + ".mp4");
    File audioFile = new File(tempDir, baseName + ".m4a");

    if (videoStream != null) {
      downloader.download(
          videoStream.getContent(),
          videoFile,
          new ProgressCallback() {
            @Override
            public void onProgress(int progress, String message) {
              callback.onProgress(progress, context.getString(R.string.downloading_video));
            }

            @Override
            public void onComplete(File file) {
              if (Boolean.TRUE.equals(cancelledTasks.getOrDefault(tag, false))) return;
              // Download the audio stream
              downloader.download(
                  audioStream.getContent(),
                  audioFile,
                  new ProgressCallback() {
                    @Override
                    public void onProgress(int progress, String message) {
                      callback.onProgress(progress, context.getString(R.string.downloading_audio));
                    }

                    @Override
                    public void onComplete(File file) {
                      // Merge the video and audio files
                      try {
                        // Create a temporary output file for better speed
                        File tempOutput = new File(tempDir, baseName + "_merged.mp4");
                        callback.onMerge();
                        if (Boolean.TRUE.equals(cancelledTasks.getOrDefault(tag, false))) return;
                        new MediaMuxerImpl().merge(videoFile, audioFile, tempOutput);
                        if (Boolean.TRUE.equals(cancelledTasks.getOrDefault(tag, false))) return;
                        // Move merged file to output
                        if (output.exists()) {
                          File availableFile = getAvailableFile(output);
                          FileUtils.moveFile(tempOutput, availableFile);
                          callback.onComplete(availableFile);
                        } else {
                          FileUtils.moveFile(tempOutput, output);
                          callback.onComplete(output);
                        }

                      } catch (IOException e) {
                        callback.onError(e);
                      }
                    }

                    @Override
                    public void onError(Exception error) {
                      callback.onError(error);
                    }

                    @Override
                    public void onCancel() {
                      callback.onCancel();
                    }

                    @Override
                    public void onMerge() {}
                  });
            }

            @Override
            public void onError(Exception error) {
              callback.onError(error);
            }

            @Override
            public void onCancel() {
              callback.onCancel();
            }

            @Override
            public void onMerge() {}
          },
          tag);
    } else {
      downloader.download(
          audioStream.getContent(),
          audioFile,
          new ProgressCallback() {
            @Override
            public void onProgress(int progress, String message) {
              callback.onProgress(progress, context.getString(R.string.downloading_audio));
            }

            @Override
            public void onComplete(File file) {
              // Move audio file to output
              try {
                if (Boolean.TRUE.equals(cancelledTasks.getOrDefault(tag, false))) return;
                if (output.exists()) {
                  File availableFile = getAvailableFile(output);
                  FileUtils.moveFile(audioFile, availableFile);
                  callback.onComplete(availableFile);
                } else {
                  FileUtils.moveFile(audioFile, output);
                  callback.onComplete(output);
                }

              } catch (IOException e) {
                callback.onError(e);
              }
            }

            @Override
            public void onError(Exception error) {
              callback.onError(error);
            }

            @Override
            public void onCancel() {
              callback.onCancel();
            }

            @Override
            public void onMerge() {}
          },
          tag);
    }
  }

  public static void cancel(String tag) {
    // Cancel download
    cancelledTasks.put(tag, true);
    downloader.cancel(tag);
  }

  /**
   * Check if the output file is exists, if it does, find an available file name(eg. video(1).mp4,
   * video(2).mp4...)
   */
  private static File getAvailableFile(File output) {
    String baseName = FilenameUtils.getBaseName(output.getPath());
    String extension = FilenameUtils.getExtension(output.getPath());
    int i = 1;
    File file;
    do {
      file = new File(output.getParent(), baseName + "(" + i + ")." + extension);
      ++i;
    } while (file.exists());
    return file;
  }
}

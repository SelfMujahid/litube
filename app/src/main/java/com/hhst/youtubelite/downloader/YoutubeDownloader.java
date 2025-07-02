package com.hhst.youtubelite.downloader;

import android.content.Context;
import com.hhst.youtubelite.R;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

/* the main class for download video and audio */
public class YoutubeDownloader {

  private static final Map<String, UniversalDownloader> instances = new HashMap<>();
  private static final List<File> tempFiles = new ArrayList<>();

  /**
   * @param video_url not the video id but the whole url
   * @return DownloadDetails contains everything we need
   */
  public static DownloadDetails info(String video_url) throws ExtractionException, IOException {
    var extractor = new YoutubeExtractor();
    var info = extractor.extract(video_url);
    return new DownloadDetails(
        info.getId(),
        info.getName(),
        info.getUploaderName(),
        info.getDescription().getContent(),
        info.getDuration(),
        extractor.getBestThumbnail(info),
        extractor.getVideoOnlyStreams(info),
        extractor.getBestAudioStream(info));
  }

  /**
   * @param processId a unique identifier for the download process, used to cancel
   *                  the download
   * @param output    the file to save the video and audio, not the directory
   * @param context   the context to get the string resources for progress
   *                  messages
   */
  public static void download(
      String processId,
      VideoStream videoStream,
      AudioStream audioStream,
      File output,
      UniversalDownloader.ProgressCallback callback,
      Context context)
      throws IOException, InterruptedException {
    UniversalDownloader downloader = new UniversalDownloader();
    instances.put(processId, downloader);

    // Download the video and audio streams
    String baseName = FilenameUtils.getBaseName(output.getPath());
    File tempDir = new File(output.getParent(), baseName);
    FileUtils.forceMkdir(tempDir);
    File videoFile = new File(tempDir, baseName + ".mp4");
    File audioFile = new File(tempDir, baseName + ".m4a");
    try {
      if (videoStream != null) {
        tempFiles.add(
            downloader.download(
                videoStream.getContent(),
                videoFile,
                callback,
                context.getString(R.string.downloading_video)));
      }
      tempFiles.add(
          downloader.download(
              audioStream.getContent(),
              audioFile,
              callback,
              context.getString(R.string.downloading_audio)));
    } finally {
      // downloader.shutdown();
    }

    if (videoStream != null) {
      // Merge the video and audio files
      callback.onProgress(-1, -1, -1, context.getString(R.string.merging));
      MediaMuxer.merge(videoFile, audioFile, output);
    } else {
      // Move audio file to output
      FileUtils.moveFile(audioFile, output);
    }
  }

  public static void cancel(String processId) {
    // Cancel download
    UniversalDownloader downloader = instances.get(processId);
    if (downloader != null) {
      downloader.cancel();
    }

    // Cancel muxing
    MediaMuxer.cancel();
  }

  public static void deleteTempFiles() {
    tempFiles.forEach(FileUtils::deleteQuietly);
    tempFiles.clear();
  }
}

package com.hhst.youtubelite.downloader;

import android.content.Context;
import com.google.gson.Gson;
import com.hhst.youtubelite.R;
import com.tencent.mmkv.MMKV;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

/* the main class for download video and audio */
public class YoutubeDownloader {

  private static final MMKV cache = MMKV.defaultMMKV();
  private static final Gson gson = new Gson();
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

  private static String getVideoID(String url) {
    if (url == null || url.isEmpty()) {
      throw new RuntimeException("Invalid url");
    }
    // get video id from url
    Pattern pattern =
        Pattern.compile(
            "^https?://.*(?:youtu\\.be/|v/|u/\\w/|embed/|watch\\?v=)([^#&?]*).*$",
            Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(url);
    if (matcher.matches()) {
      String id = matcher.group(1);
      if (id == null) throw new RuntimeException("Invalid url");
      return id;
    }
    throw new RuntimeException("Invalid url");
  }

  public static DownloadDetails infoWithCache(String url) throws Exception {
    String id = getVideoID(url);
    DownloadDetails details = gson.fromJson(cache.decodeString(id), DownloadDetails.class);
    if (details == null) {
      details = info("https://www.youtube.com/watch?v=" + id);
      cache.encode(id, gson.toJson(details), 60 * 60 * 24);
    }
    return details;
  }

  /**
   * @param processId a unique identifier for the download process, used to cancel the download
   * @param output the file to save the video and audio, not the directory
   * @param context the context to get the string resources for progress messages
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
      downloader.shutdown();
    }

    if (videoStream != null) {
      // Merge the video and audio files
      callback.onProgress(0, 0, 100, context.getString(R.string.merging));
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

package com.hhst.youtubelite.downloader;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/* the main class for download video and audio */
public class Downloader {

  private static final MMKV cache = MMKV.defaultMMKV();
  private static final Gson gson = new Gson();

  /**
   * @param video_url not the video id but the whole url
   * @return DownloadDetails contains everything we need
   */
  public static DownloadDetails infoFromYDL(String video_url)
      throws YoutubeDL.CanceledException, YoutubeDLException, InterruptedException {
    YoutubeDLRequest request = new YoutubeDLRequest(video_url);
    request.addOption("--retries", 3);
    VideoInfo info = YoutubeDL.getInstance().getInfo(request);
    DownloadDetails details = new DownloadDetails();
    details.setId(info.getId());
    details.setTitle(info.getTitle());
    details.setAuthor(info.getUploader());
    details.setDescription(info.getDescription());
    details.setDuration((long) info.getDuration());
    details.setThumbnail(info.getThumbnail());
    return details;
  }

  @Nullable
  private static DownloadDetails infoFromData(String detailsData) {
    // Try to parse detail from video data
    var object = JsonParser.parseString(detailsData).getAsJsonObject();

    if (object.has("videoDetails")) {
      var detailsObj = object.getAsJsonObject("videoDetails");
      String videoId = detailsObj.get("videoId").getAsString();
      return new DownloadDetails(
          videoId,
          detailsObj.get("title").getAsString(),
          detailsObj.get("author").getAsString(),
          detailsObj.get("shortDescription").getAsString(),
          detailsObj.get("lengthSeconds").getAsLong(),
          String.format("https://img.youtube.com/vi/%s/hqdefault.jpg", videoId));
    }
    return null;
  }

  @NonNull
  private static String getVideoID(String url) {
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

  public static DownloadDetails infoWithCache(String url, String detailsData)
      throws Exception {
    String id = getVideoID(url);
    DownloadDetails details = gson.fromJson(cache.decodeString(id), DownloadDetails.class);
    // validate cached details
    if (details == null
        || details.getTitle() == null
        || details.getAuthor() == null
        || details.getThumbnail() == null) {
      details = infoFromData(detailsData); // faster
      if (details == null) details = infoFromYDL(url);
      cache.encode(id, gson.toJson(details), 60 * 60 * 24 * 7);
    }
    return details;
  }

  public static List<VideoFormat> fetchFormats(String url)
      throws YoutubeDL.CanceledException, YoutubeDLException, InterruptedException {
    String key = "formats:" + getVideoID(url);
    if (cache.decodeString(key) != null) {
      return gson.fromJson(cache.decodeString(key), new TypeToken<List<VideoFormat>>() {}.getType());
    } else {
      YoutubeDLRequest request = new YoutubeDLRequest(url);
      request.addOption("--retries", 3);
      VideoInfo info = YoutubeDL.getInstance().getInfo(request);
      cache.encode(key, gson.toJson(info.getFormats()), 60 * 60 * 24 * 7);
      return info.getFormats();
    }
  }

  public static void download(
      String processId,
      String video_url,
      VideoFormat video_format,
      File output,
      Function3<Float, Long, String, Unit> callback)
      throws YoutubeDL.CanceledException, InterruptedException, YoutubeDLException {
    YoutubeDLRequest request = new YoutubeDLRequest(video_url);
    request.addOption("--no-mtime");
    request.addOption("--embed-thumbnail");
    request.addOption("--concurrent-fragments", 8);
    request.addOption("-f", "bestaudio[ext=m4a]");
    request.addOption("-o", output.getAbsolutePath() + ".m4a");
    if (video_format != null && video_format.getFormatId() != null) {
      request.addOption(
          "-f",
          String.format(
              "bestvideo[height<=%s][ext=mp4]+bestaudio[ext=m4a]", video_format.getFormatId()));
      request.addOption("-o", output.getAbsolutePath());
    }
    YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, processId, callback);
    Log.d("yt-dlp download command", response.getCommand().toString());
  }

  public static boolean cancel(String processId) {
    return YoutubeDL.getInstance().destroyProcessById(processId);
  }
}

package com.hhst.youtubelite.downloader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

public class MediaMuxerUtil {

  private static final String TAG = "MediaMuxerUtil";
  private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

  public interface MuxingCallback {
    void onProgress(int progress, String message);
    boolean isCancelled();
  }

  public static void merge(File videoFile, File audioFile, File outputFile) throws IOException {
    merge(videoFile, audioFile, outputFile, null);
  }

  public static void merge(File videoFile, File audioFile, File outputFile, MuxingCallback callback) throws IOException {
    if (!videoFile.exists() || !audioFile.exists()) {
      throw new IOException("Input files do not exist");
    }
    if (videoFile.length() == 0 || audioFile.length() == 0) {
      throw new IOException("Input files are empty");
    }
    MediaExtractor videoExtractor = null;
    MediaExtractor audioExtractor = null;
    android.media.MediaMuxer muxer = null;
    try {
      videoExtractor = new MediaExtractor();
      videoExtractor.setDataSource(videoFile.getAbsolutePath());
      audioExtractor = new MediaExtractor();
      audioExtractor.setDataSource(audioFile.getAbsolutePath());
      muxer = new android.media.MediaMuxer(outputFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      int videoTrackIndex = -1;
      int audioTrackIndex = -1;
      for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
        MediaFormat format = videoExtractor.getTrackFormat(i);
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("video/")) {
          videoTrackIndex = muxer.addTrack(format);
          videoExtractor.selectTrack(i);
          break;
        }
      }
      for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
        MediaFormat format = audioExtractor.getTrackFormat(i);
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          audioTrackIndex = muxer.addTrack(format);
          audioExtractor.selectTrack(i);
          break;
        }
      }
      if (videoTrackIndex == -1 || audioTrackIndex == -1) {
        throw new IOException("Could not find video or audio tracks");
      }
      muxer.start();
      long videoDuration = getTrackDuration(videoExtractor);
      long audioDuration = getTrackDuration(audioExtractor);
      long totalDuration = Math.max(videoDuration, audioDuration);
      if (totalDuration <= 0) {
        Log.w(TAG, "Could not determine duration, using indeterminate progress");
        totalDuration = 1;
      }
      if (callback != null) {
        callback.onProgress(0, "Starting video muxing...");
      }
      muxTrack(videoExtractor, muxer, videoTrackIndex, totalDuration, callback, "video");
      if (callback != null) {
        callback.onProgress(50, "Starting audio muxing...");
      }
      muxTrack(audioExtractor, muxer, audioTrackIndex, totalDuration, callback, "audio");
      if (callback != null) {
        callback.onProgress(100, "Muxing completed");
      }
    } catch (Exception e) {
      Log.e(TAG, "Error during muxing", e);
      throw new IOException("Muxing failed: " + e.getMessage(), e);
    } finally {
      if (videoExtractor != null) {
        try {
          videoExtractor.release();
        } catch (Exception e) {
          Log.e(TAG, "Error releasing video extractor", e);
        }
      }
      if (audioExtractor != null) {
        try {
          audioExtractor.release();
        } catch (Exception e) {
          Log.e(TAG, "Error releasing audio extractor", e);
        }
      }
      if (muxer != null) {
        try {
          muxer.stop();
          muxer.release();
        } catch (Exception e) {
          Log.e(TAG, "Error releasing muxer", e);
        }
      }
    }
  }

  private static void muxTrack(MediaExtractor extractor, android.media.MediaMuxer muxer, int trackIndex, long totalDuration, MuxingCallback callback, String trackType) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    long lastProgressUpdate = 0;
    while (true) {
      if (callback != null && callback.isCancelled()) {
        throw new IOException("Muxing cancelled");
      }
      int sampleSize = extractor.readSampleData(buffer, 0);
      if (sampleSize < 0) {
        break;
      }
      bufferInfo.offset = 0;
      bufferInfo.size = sampleSize;
      bufferInfo.presentationTimeUs = extractor.getSampleTime();
      bufferInfo.flags = extractor.getSampleFlags(); // Should be one of MediaCodec.BUFFER_FLAG_*
      muxer.writeSampleData(trackIndex, buffer, bufferInfo);
      extractor.advance();
      if (callback != null && totalDuration > 0) {
        long currentTime = bufferInfo.presentationTimeUs;
        if (currentTime - lastProgressUpdate > totalDuration / 100) {
          int progress = (int) ((currentTime * 100) / totalDuration);
          progress = Math.min(progress, 100);
          String message = String.format(Locale.US, "Muxing %s: %d%%", trackType, progress);
          callback.onProgress(progress, message);
          lastProgressUpdate = currentTime;
        }
      }
    }
  }

  private static long getTrackDuration(MediaExtractor extractor) {
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      if (format.containsKey(MediaFormat.KEY_DURATION)) {
        return format.getLong(MediaFormat.KEY_DURATION);
      }
    }
    return 0;
  }
}

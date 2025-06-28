package com.hhst.youtubelite.downloader;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class MediaMuxer {

  private static final String TAG = "MediaMuxerUtil";
  private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer
  private static final AtomicBoolean isCancelled = new AtomicBoolean(false);

  public static void merge(File videoFile, File audioFile, File outputFile) throws IOException {
    isCancelled.set(false); // Reset cancellation flag
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
      muxer = new android.media.MediaMuxer(
          outputFile.getAbsolutePath(),
          android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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

      // Mux video track
      muxTrack(videoExtractor, muxer, videoTrackIndex);

      // Check for cancellation after video muxing
      if (isCancelled.get()) {
        throw new IOException("Muxing cancelled");
      }

      // Mux audio track
      muxTrack(audioExtractor, muxer, audioTrackIndex);

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

  private static void muxTrack(
      MediaExtractor extractor, android.media.MediaMuxer muxer, int trackIndex) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    while (true) {
      // Check for cancellation
      if (isCancelled.get()) {
        throw new IOException("Muxing cancelled");
      }

      int sampleSize = extractor.readSampleData(buffer, 0);
      if (sampleSize < 0) {
        break;
      }
      bufferInfo.offset = 0;
      bufferInfo.size = sampleSize;
      bufferInfo.presentationTimeUs = extractor.getSampleTime();
      int extractorFlags = extractor.getSampleFlags();
      int codecFlags = 0;
      if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
        codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
      }
      if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
        codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
      }
      bufferInfo.flags = codecFlags;
      muxer.writeSampleData(trackIndex, buffer, bufferInfo);
      extractor.advance();
    }
  }

  public static void cancel() {
    isCancelled.set(true);
    Log.d(TAG, "Muxing cancellation requested");
  }

  public static boolean isCancelled() {
    return isCancelled.get();
  }
}

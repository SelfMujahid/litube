package com.hhst.youtubelite.downloader;

import androidx.annotation.NonNull;
import java.io.File;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadTask implements Cloneable {
  private String url;
  private String fileName;
  private String thumbnail;
  private VideoStream videoStream;
  private AudioStream audioStream;
  private Boolean isAudio;
  private DownloaderState state;
  private File output;
  private DownloadNotification notification;

  @NonNull
  @Override
  public DownloadTask clone() {
    try {
      return (DownloadTask) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}

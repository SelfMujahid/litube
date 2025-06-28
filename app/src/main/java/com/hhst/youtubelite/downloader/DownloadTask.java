package com.hhst.youtubelite.downloader;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadTask {
  private String url;
  private String fileName;
  private String thumbnail;
  private VideoStream videoStream;
  private AudioStream audioStream;
  private Boolean isAudio;
  private DownloaderState state;
  private File outputDir;
  private File output;
  private DownloadNotification notification;
}

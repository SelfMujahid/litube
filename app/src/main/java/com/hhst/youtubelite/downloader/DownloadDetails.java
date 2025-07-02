package com.hhst.youtubelite.downloader;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadDetails {
  private String id;
  private String title;
  private String author;
  private String description;
  private Long duration;
  private String thumbnail;
  private List<VideoStream> videoStreams;
  private AudioStream audioStream;
}

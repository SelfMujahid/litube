package com.hhst.youtubelite.common;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

public class YoutubeExtractor {

  public YoutubeExtractor() {
    NewPipe.init(DownloaderImpl.getInstance());
  }

  public StreamInfo extract(String url) throws ExtractionException, IOException {
    return StreamInfo.getInfo(ServiceList.YouTube, url);
  }

  @Nullable
  public String getBestThumbnail(StreamInfo info) {
    var thumbnails = info.getThumbnails();
    Image bestThumbnail = null;
    Map<Image.ResolutionLevel, Integer> resolutionPriority =
        Map.of(
            Image.ResolutionLevel.HIGH, 3,
            Image.ResolutionLevel.MEDIUM, 2,
            Image.ResolutionLevel.LOW, 1,
            Image.ResolutionLevel.UNKNOWN, 0);
    for (var thumbnail : thumbnails) {

      if (bestThumbnail == null
          || Objects.requireNonNullElse(
                  resolutionPriority.get(thumbnail.getEstimatedResolutionLevel()), 0)
              > Objects.requireNonNullElse(
                  resolutionPriority.get(bestThumbnail.getEstimatedResolutionLevel()), 0)) {
        bestThumbnail = thumbnail;
      }
    }
    return bestThumbnail != null ? bestThumbnail.getUrl() : null;
  }

  @Nullable
  public AudioStream getBestAudioStream(StreamInfo info) {
    AudioStream bestAudioStream = null;
    for (var audioStream : info.getAudioStreams()) {
      if (audioStream.getFormat() == MediaFormat.M4A
          && (bestAudioStream == null
              || audioStream.getAverageBitrate() > bestAudioStream.getAverageBitrate())) {
        bestAudioStream = audioStream;
      }
    }
    return bestAudioStream;
  }

  public List<VideoStream> getVideoOnlyStreams(StreamInfo info) {
    return info.getVideoOnlyStreams().stream()
        .filter(s -> s.getFormat() == MediaFormat.MPEG_4)
        .collect(Collectors.toList());
  }
}

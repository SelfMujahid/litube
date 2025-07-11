package com.hhst.youtubelite.downloader;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class MediaMuxerImpl implements MediaMuxer {

  @Override
  public void merge(File videoFile, File audioFile, File outputFile) throws IOException {

    Movie video = MovieCreator.build(videoFile.getAbsolutePath());
    Movie audio = MovieCreator.build(audioFile.getAbsolutePath());

    List<Track> videoTracks = new ArrayList<>();
    List<Track> audioTracks = new ArrayList<>();
    for (Track track : video.getTracks()) {
      if (track.getHandler().equals("vide")) {
        videoTracks.add(track);
      }
    }
    for (Track track : audio.getTracks()) {
      if (track.getHandler().equals("soun")) {
        audioTracks.add(track);
      }
    }

    if (videoTracks.isEmpty() || audioTracks.isEmpty()) {
      throw new EmptyTrackException();
    }

    Movie result = new Movie();
    result.addTrack(videoTracks.get(0));
    result.addTrack(audioTracks.get(0));

    Container out = new DefaultMp4Builder().build(result);
    FileOutputStream fos = new FileOutputStream(outputFile);
    FileChannel fc = fos.getChannel();
    out.writeContainer(fc);
    fc.close();
    fos.close();
  }

  @Override
  public void cancel() {}

  public static class EmptyTrackException extends RuntimeException {
    public EmptyTrackException() {
      super("No video or audio tracks found in the provided files.");
    }
  }
}

package com.hhst.youtubelite.player;

import android.content.Context;
import android.view.SurfaceView;

/**
 * Video player controller interface, providing video playback, picture-in-picture mode, and video
 * source control functionality
 */
public interface VideoPlayerController {

  /** Get playback control interface */
  PlaybackControl getPlaybackControl();

  /** Get video source control interface */
  VideoSourceControl getVideoSourceControl();

  /** Set playback state listener */
  void setPlaybackStateListener(PlaybackStateListener listener);

  /** Initialize controller */
  void initialize(Context context, SurfaceView surfaceView);

  /** Release resources */
  void release();

  /** Playback control interface */
  interface PlaybackControl {
    /** Start playback */
    void play();

    /** Pause playback */
    void pause();

    /** Toggle play/pause state */
    void togglePlayPause();

    /** Seek to specified position */
    void seekTo(long position);

    /** Seek backward */
    void seekBackward();

    /** Seek forward */
    void seekForward();

    /** Get current playback position */
    int getCurrentPosition();

    /** Get total video duration */
    int getDuration();

    /** Check if video is playing */
    boolean isPlaying();
  }

  /** Video source control interface */
  interface VideoSourceControl {
    /** Set video URL */
    void setVideoUrl(String url);

    /** Get current video source */
    String getCurrentVideoSource();
  }

  /** Playback state listener */
  interface PlaybackStateListener {
    /** Playback state change */
    void onPlaybackStateChanged(boolean isPlaying);

    /** Playback progress change */
    void onProgressChanged(int position, int duration);

    /** Playback completed */
    void onPlaybackCompleted();

    /** Playback error */
    void onPlaybackError(String error);
  }
}

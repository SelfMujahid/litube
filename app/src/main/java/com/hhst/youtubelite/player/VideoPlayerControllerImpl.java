package com.hhst.youtubelite.player;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.util.Rational;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.hhst.youtubelite.R;
import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

@Getter
public class VideoPlayerControllerImpl implements VideoPlayerController {

  private static final String TAG = "VideoPlayerController";
  private static final int SEEK_DURATION_MS = 10000; // 10 seconds

  // PiP control constants
  private static final String ACTION_MEDIA_CONTROL = "media_control";
  private static final String EXTRA_CONTROL_TYPE = "control_type";
  private static final int CONTROL_TYPE_PLAY = 1;
  private static final int CONTROL_TYPE_PAUSE = 2;
  private static final int CONTROL_TYPE_REWIND = 3;
  private static final int CONTROL_TYPE_FORWARD = 4;
  private Context context;
  private SurfaceView surfaceView;
  private MediaPlayer mediaPlayer;
  private boolean isPlaying = false;
  private boolean isInPipMode = false;
  private boolean isPrepared = false;
  @Setter
  private boolean shouldStartWhenPrepared = false;
  private boolean isSurfaceReady = false;
  private String currentVideoSource = "";

  @Setter
  private PlaybackStateListener playbackStateListener;

  @Getter
  private final PlaybackControl playbackControl =
      new PlaybackControl() {
        @Override
        public void play() {
          if (mediaPlayer == null) {
            Log.e(TAG, "Cannot play: MediaPlayer is null");
            return;
          }

          if (isPrepared) {
            try {
              mediaPlayer.start();
              isPlaying = true;

              if (playbackStateListener != null) {
                playbackStateListener.onPlaybackStateChanged(true);
              }

              updatePipActions();
            } catch (Exception e) {
              Log.e(TAG, "Error starting playback", e);
              if (playbackStateListener != null) {
                playbackStateListener.onPlaybackError(
                    "Failed to start playback: " + e.getMessage());
              }
            }
          } else {
            shouldStartWhenPrepared = true;
          }
        }

        @Override
        public void pause() {
          if (mediaPlayer != null && isPlaying) {
            try {
              mediaPlayer.pause();
              isPlaying = false;

              if (playbackStateListener != null) {
                playbackStateListener.onPlaybackStateChanged(false);
              }

              updatePipActions();
            } catch (Exception e) {
              Log.e(TAG, "Error pausing playback", e);
            }
          }
        }

        @Override
        public void seekTo(long position) {
          if (mediaPlayer != null && isPrepared) {
            try {
              mediaPlayer.seekTo((int) position);
            } catch (Exception e) {
              Log.e(TAG, "Error seeking to position", e);
            }
          }
        }

        @Override
        public int getCurrentPosition() {
          if (mediaPlayer != null && isPrepared) {
            try {
              return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
              Log.e(TAG, "Error getting current position", e);
            }
          }
          return 0;
        }

        @Override
        public int getDuration() {
          if (mediaPlayer != null && isPrepared) {
            try {
              return mediaPlayer.getDuration();
            } catch (Exception e) {
              Log.e(TAG, "Error getting duration", e);
            }
          }
          return 0;
        }

        @Override
        public boolean isPlaying() {
          return isPlaying && mediaPlayer != null && isPrepared;
        }

        @Override
        public void togglePlayPause() {
          if (isPlaying()) {
            pause();
          } else {
            play();
          }
        }

        @Override
        public void seekBackward() {
          int currentPos = getCurrentPosition();
          int newPos = Math.max(0, currentPos - SEEK_DURATION_MS);
          seekTo(newPos);
        }

        @Override
        public void seekForward() {
          int currentPos = getCurrentPosition();
          int duration = getDuration();
          int newPos =
              duration > 0
                  ? Math.min(duration, currentPos + SEEK_DURATION_MS)
                  : currentPos + SEEK_DURATION_MS;
          Log.i(TAG, "Seeking forward: " + currentPos + " -> " + newPos);
          seekTo(newPos);
        }
      };
  @Getter
  private final VideoSourceControl videoSourceControl =
      new VideoSourceControl() {
        @Override
        public void setVideoUrl(String url) {
          if (url != null && !url.isEmpty()) {
            currentVideoSource = url;
            prepareVideoSource();
          } else {
            Log.w(TAG, "Invalid video URL provided: " + url);
          }
        }

        @Override
        public String getCurrentVideoSource() {
          return currentVideoSource;
        }
      };
  private BroadcastReceiver pipControlReceiver;
  // Lifecycle management methods
  @Getter @Setter
  private int currentPosition = 0;

  @Override
  public void initialize(Context context, SurfaceView surfaceView) {
    this.context = context;
    this.surfaceView = surfaceView;

    setupSurfaceView();
    setupPipControlReceiver();
    createMediaPlayer();
  }

  private void setupSurfaceView() {
    Log.i(TAG, "Setting up SurfaceView");
    surfaceView
        .getHolder()
        .addCallback(
            new SurfaceHolder.Callback() {
              @Override
              public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.i(TAG, "Surface created");
                isSurfaceReady = true;

                if (mediaPlayer != null) {
                  mediaPlayer.setDisplay(holder);

                  if (!currentVideoSource.isEmpty()) {
                    prepareVideoSource();
                  }
                }
              }

              @Override
              public void surfaceChanged(
                  @NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.i(TAG, "Surface changed: " + width + "x" + height);
              }

              @Override
              public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.i(TAG, "Surface destroyed");
                isSurfaceReady = false;

                if (mediaPlayer != null) {
                  mediaPlayer.setDisplay(null);
                }
              }
            });
  }

  private void createMediaPlayer() {
    try {
      if (mediaPlayer != null) {
        mediaPlayer.release();
      }

      mediaPlayer = new MediaPlayer();
      setupMediaPlayerListeners();

    } catch (Exception e) {
      Log.e(TAG, "Error creating MediaPlayer", e);
      if (playbackStateListener != null) {
        playbackStateListener.onPlaybackError("Failed to create media player: " + e.getMessage());
      }
    }
  }

  private void setupMediaPlayerListeners() {
    if (mediaPlayer == null) {
      Log.e(TAG, "Cannot setup listeners: MediaPlayer is null");
      return;
    }

    mediaPlayer.setOnPreparedListener(
        mp -> {
          isPrepared = true;

          if (shouldStartWhenPrepared) {
            mp.start();
            isPlaying = true;
            shouldStartWhenPrepared = false;

            if (playbackStateListener != null) {
              playbackStateListener.onPlaybackStateChanged(true);
            }
          }
        });

    mediaPlayer.setOnErrorListener(
        (mp, what, extra) -> {
          String errorMsg = getErrorMessage(what, extra);

          isPrepared = false;
          shouldStartWhenPrepared = false;
          isPlaying = false;

          if (playbackStateListener != null) {
            playbackStateListener.onPlaybackError("Playback error: " + errorMsg);
          }
          return true;
        });

    mediaPlayer.setOnCompletionListener(
        mp -> {
          isPlaying = false;

          if (playbackStateListener != null) {
            playbackStateListener.onPlaybackCompleted();
          }
        });

    mediaPlayer.setOnInfoListener(
        (mp, what, extra) -> {
          // Simplified info listener
          return false;
        });

    mediaPlayer.setOnVideoSizeChangedListener(
        (mp, width, height) -> {
          // Video size changed
        });

    mediaPlayer.setOnSeekCompleteListener(
        mp -> {
          // Seek completed
        });
  }

  private String getErrorMessage(int what, int extra) {
    String whatMsg =
        switch (what) {
          case MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown media error";
          case MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died";
          default -> "Error code: " + what;
        };

    String extraMsg =
        switch (extra) {
          case MediaPlayer.MEDIA_ERROR_IO -> "IO error";
          case MediaPlayer.MEDIA_ERROR_MALFORMED -> "Malformed media";
          case MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported media";
          case MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Timed out";
          default -> "Extra code: " + extra;
        };

    return whatMsg + " - " + extraMsg;
  }

  private void setupPipControlReceiver() {
    // Create and register the broadcast receiver
    pipControlReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
              return;
            }

            int controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0);
            handlePipControl(controlType);
          }
        };

    // Register the receiver
    IntentFilter filter = new IntentFilter(ACTION_MEDIA_CONTROL);
    ContextCompat.registerReceiver(
        context, pipControlReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  private void handlePipControl(int controlType) {
    switch (controlType) {
      case CONTROL_TYPE_PLAY:
        getPlaybackControl().play();
        break;
      case CONTROL_TYPE_PAUSE:
        getPlaybackControl().pause();
        break;
      case CONTROL_TYPE_REWIND:
        int currentPos = getPlaybackControl().getCurrentPosition();
        int newRewindPos = Math.max(0, currentPos - SEEK_DURATION_MS);
        getPlaybackControl().seekTo(newRewindPos);
        break;
      case CONTROL_TYPE_FORWARD:
        int currentPosition = getPlaybackControl().getCurrentPosition();
        int duration = getPlaybackControl().getDuration();
        int newForwardPos =
            duration > 0
                ? Math.min(duration, currentPosition + SEEK_DURATION_MS)
                : currentPosition + SEEK_DURATION_MS;
        getPlaybackControl().seekTo(newForwardPos);
        break;
      default:
        Log.w(TAG, "Unknown PiP control type: " + controlType);
        break;
    }
  }

  private void prepareVideoSource() {
    if (mediaPlayer == null || currentVideoSource.isEmpty()) {
      Log.w(TAG, "Cannot prepare video source: MediaPlayer is null or source is empty");
      return;
    }

    try {
      // Reset MediaPlayer state
      mediaPlayer.reset();
      setupMediaPlayerListeners();

      // Set data source
      if (currentVideoSource.startsWith("http://")) {
        // Convert HTTP to HTTPS for security
        String secureUrl = currentVideoSource.replace("http://", "https://");
        mediaPlayer.setDataSource(secureUrl);
      } else if (currentVideoSource.startsWith("file://")) {
        // Handle local file
        Uri videoUri = Uri.parse(currentVideoSource);
        mediaPlayer.setDataSource(context, videoUri);
      } else {
        // Assume it's a URL
        mediaPlayer.setDataSource(currentVideoSource);
      }

      // Set display if surface is ready
      if (isSurfaceReady && surfaceView.getHolder().getSurface().isValid()) {
        mediaPlayer.setDisplay(surfaceView.getHolder());
      }

      // Prepare asynchronously
      mediaPlayer.prepareAsync();

    } catch (Exception e) {
      Log.e(TAG, "Error preparing video source", e);
      isPrepared = false;
      shouldStartWhenPrepared = false;

      if (playbackStateListener != null) {
        playbackStateListener.onPlaybackError("Failed to prepare video: " + e.getMessage());
      }
    }
  }

  private ArrayList<RemoteAction> createPipActions() {
    ArrayList<RemoteAction> actions = new ArrayList<>();

    if (isPlaying) {
      Intent pauseIntent = new Intent(ACTION_MEDIA_CONTROL);
      pauseIntent.putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PAUSE);
      PendingIntent pausePendingIntent =
          PendingIntent.getBroadcast(
              context,
              2,
              pauseIntent,
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Icon pauseIcon = Icon.createWithResource(context, R.drawable.ic_pause);
      actions.add(new RemoteAction(pauseIcon, "Pause", "Pause video", pausePendingIntent));
    } else {
      Intent playIntent = new Intent(ACTION_MEDIA_CONTROL);
      playIntent.putExtra(EXTRA_CONTROL_TYPE, CONTROL_TYPE_PLAY);
      PendingIntent playPendingIntent =
          PendingIntent.getBroadcast(
              context,
              1,
              playIntent,
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Icon playIcon = Icon.createWithResource(context, R.drawable.ic_play_arrow);
      actions.add(new RemoteAction(playIcon, "Play", "Start video", playPendingIntent));
    }

    return actions;
  }

  private void updatePipActions() {
    if (isInPipMode && context instanceof AppCompatActivity activity) {
      Rational aspectRatio = new Rational(16, 9);
      PictureInPictureParams params =
          new PictureInPictureParams.Builder()
              .setAspectRatio(aspectRatio)
              .setActions(createPipActions())
              .build();
      activity.setPictureInPictureParams(params);
    }
  }

  public void onStart() {
    if (!isInPipMode && !isPlaying && isPrepared) {
      playbackControl.play();
    }
  }

  public void onPause() {
    if (!isInPipMode && isPlaying) {
      currentPosition = getPlaybackControl().getCurrentPosition();
      playbackControl.pause();
    }
  }

  public void onResume() {
    if (!isInPipMode && currentPosition > 0 && isPrepared) {
      playbackControl.seekTo(currentPosition);
      if (shouldStartWhenPrepared) {
        playbackControl.play();
      }
    }
  }

  public void onStop() {
    // Keep MediaPlayer alive for PiP mode
  }

  public void onDestroy() {
    release();
  }

  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
    isInPipMode = isInPictureInPictureMode;

    if (isInPictureInPictureMode) {
      updatePipActions();
    }
  }

  // This method is automatically generated by Lombok @Getter annotation
  // @Override
  // public PlaybackControl getPlaybackControl() {
  //   return playbackControl;
  // }

  // This method is automatically generated by Lombok @Setter annotation
  // @Override
  // public void setPlaybackStateListener(PlaybackStateListener listener) {
  //   playbackStateListener = listener;
  // }

  @Override
  public void release() {
    // Unregister PiP control receiver
    if (pipControlReceiver != null) {
      try {
        context.unregisterReceiver(pipControlReceiver);
        pipControlReceiver = null;
      } catch (Exception e) {
        Log.e(TAG, "Error unregistering PiP control receiver", e);
      }
    }

    // Release MediaPlayer resources
    if (mediaPlayer != null) {
      try {
        mediaPlayer.stop();
        mediaPlayer.reset();
        mediaPlayer.release();
        mediaPlayer = null;
      } catch (Exception e) {
        Log.e(TAG, "Error releasing MediaPlayer resources", e);
      }
    }

    // Reset state
    isPrepared = false;
    isPlaying = false;
    shouldStartWhenPrepared = false;
    isSurfaceReady = false;
    currentVideoSource = "";
    currentPosition = 0;
  }
}

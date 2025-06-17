package com.hhst.youtubelite.extension;

import com.hhst.youtubelite.R;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Extension {
  private String key; // If key is null, this is a group node.
  private int description;
  private List<Extension> children; // If children is null, this is a leaf node.

  public static List<Extension> defaultExtensionTree() {
    return List.of(
        new Extension(
            null,
            R.string.general,
            List.of(
                new Extension(Constant.enableDisplayDislikes, R.string.display_dislikes, null),
                new Extension(Constant.enableHideShorts, R.string.hide_shorts, null),
                new Extension(Constant.enableLiveChat, R.string.enable_live_chat, null))),
        new Extension(
            null,
            R.string.video,
            List.of(new Extension(Constant.enableH264ify, R.string.h264ify, null))),
        new Extension(
            null,
            R.string.player,
            List.of(
                new Extension(Constant.rememberLastPosition, R.string.remember_last_position, null),
                new Extension(Constant.rememberQuality, R.string.remember_quality, null),
                new Extension(
                    Constant.rememberPlaybackSpeed, R.string.remember_playback_speed, null),
                new Extension(Constant.enableBackgroundPlay, R.string.background_play, null))),
        new Extension(
            null,
            R.string.skip_sponsors,
            List.of(
                new Extension(Constant.skipSponsor, R.string.skip_sponsors_sponsor, null),
                new Extension(Constant.skipSelfPromo, R.string.skip_sponsors_selfpromo, null),
                new Extension(Constant.skipHighlight, R.string.skip_sponsors_highlight, null))),
        new Extension(
            null,
            R.string.other,
            List.of(
                new Extension(Constant.enableCpuTamer, R.string.cpu_tamer_experimental, null))));
  }
}

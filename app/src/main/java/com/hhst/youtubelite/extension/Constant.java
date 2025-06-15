package com.hhst.youtubelite.extension;

import java.util.Map;

public class Constant {

  // Extension key
  public static final String enableDisplayDislikes = "enable_display_dislikes";
  public static final String enableHideShorts = "enable_hide_shorts";
  public static final String enableH264ify = "enable_h264ify";
  public static final String enableLiveChat = "enable_live_chat";
  public static final String skipSponsor = "skip_sponsors";
  public static final String skipSelfPromo = "skip_self_promo";
  public static final String skipHighlight = "skip_poi_highlight";
  public static final String enableCpuTamer = "enable_cpu_tamer";
  public static final String rememberLastPosition = "remember_last_position";
  public static final String rememberQuality = "remember_quality";

  public static final Map<String, Boolean> defaultPreferences =
      Map.of(
          enableDisplayDislikes,
          true,
          enableHideShorts,
          false,
          enableH264ify,
          false,
          enableLiveChat,
          true,
          skipSponsor,
          true,
          skipSelfPromo,
          true,
          skipHighlight,
          true,
          enableCpuTamer,
          false,
          rememberLastPosition,
          true,
          rememberQuality,
          true);
}

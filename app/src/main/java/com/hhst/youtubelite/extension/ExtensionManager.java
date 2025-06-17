package com.hhst.youtubelite.extension;


import com.google.gson.Gson;
import com.hhst.youtubelite.webview.YoutubeWebview;
import java.lang.reflect.Type;
import java.util.Map;

public class ExtensionManager {

  private final YoutubeWebview webview;
  private Map<String, Boolean> mm = Map.of();

  public ExtensionManager(YoutubeWebview webview) {
    this.webview = webview;
    enableExtension();
  }

  private void enableExtension() {
    webview.evaluateJavascript(
        String.format(
            """
          (function(){
          const key = 'preferences';
          let value = localStorage.getItem(key);
          if (!value) {
            value = JSON.stringify(%s);
            localStorage.setItem(key, value);
          }
          return value;
          })();
        """,
            new Gson().toJson(Constant.defaultPreferences)),
        value -> {
          // Remove the surrounding quotes and escape characters
          if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }
          value = value.replace("\\\\", "\\").replace("\\\"", "\"");
          Type type = new com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.getType();
          mm = new Gson().fromJson(value, type);
        });
  }

  public void setEnabled(String key, Boolean enable) {
    mm.put(key, enable);
    // Update the local storage in the webview
    webview.evaluateJavascript(
        String.format(
            "(function(){localStorage.setItem('preferences', JSON.stringify(%s));})();",
            new Gson().toJson(mm)),
        null);
  }

  public Boolean isEnabled(String key) {
    return mm.get(key);
  }
}

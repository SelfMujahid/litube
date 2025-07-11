package com.hhst.youtubelite.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

public class DownloaderImpl extends Downloader {
  private static DownloaderImpl instance;

  // Singleton instance accessor
  public static DownloaderImpl getInstance() {
    if (instance == null) {
      instance = new DownloaderImpl();
    }
    return instance;
  }

  @Override
  public Response execute(Request request) throws java.io.IOException {
    // Initialize URL connection
    URL url = new URL(request.url());
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    // Set HTTP method (GET, POST, etc.)
    String method = request.httpMethod() != null ? request.httpMethod().toUpperCase() : "GET";
    connection.setRequestMethod(method);
    connection.setInstanceFollowRedirects(false); // disable auto-redirects

    // Apply all request headers
    for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
      for (String value : header.getValue()) {
        connection.addRequestProperty(header.getKey(), value);
      }
    }

    // If the method supports a body (e.g., POST), write the request payload
    byte[] data = request.dataToSend();
    if (data != null && !method.equals("GET") && !method.equals("HEAD")) {
      connection.setDoOutput(true);
      try (OutputStream os = connection.getOutputStream()) {
        os.write(data);
        os.flush();
      }
    }

    // Read the response body
    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    StringBuilder responseBuilder = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      responseBuilder.append(line).append('\n');
    }
    reader.close();

    // Collect response headers
    Map<String, List<String>> headerFields = connection.getHeaderFields();

    // Build and return the NewPipeExtractor-compatible response object
    return new Response(
        connection.getResponseCode(),
        connection.getResponseMessage(),
        headerFields,
        responseBuilder.toString(),
        connection.getURL().toString());
  }
}

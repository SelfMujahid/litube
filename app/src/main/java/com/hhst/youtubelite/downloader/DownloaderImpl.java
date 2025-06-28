package com.hhst.youtubelite.downloader;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

public class DownloaderImpl extends Downloader {

  private static DownloaderImpl instance;
  private final OkHttpClient client = new OkHttpClient();

  public static DownloaderImpl getInstance() {
    if (instance == null) {
      instance = new DownloaderImpl();
    }
    return instance;
  }

  @Override
  public Response execute(Request request) throws IOException {
    // Build the request with the provided URL
    okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());

    // Add headers to the request
    request
        .headers()
        .forEach((key, values) -> values.forEach(value -> builder.addHeader(key, value)));

    // Determine request method and body (default to GET if null)
    String method = request.httpMethod() != null ? request.httpMethod().toUpperCase() : "GET";
    RequestBody body = null;

    // Create request body if needed (e.g., for POST, PUT)
    byte[] data = request.dataToSend();
    if (data != null && !method.equals("GET") && !method.equals("HEAD")) {
      body = RequestBody.create(data);
    }

    // Apply method and body to the request
    builder.method(method, body);

    // Execute the request and convert the OkHttp response to NewPipe's Response
    try (okhttp3.Response okHttpResponse = client.newCall(builder.build()).execute()) {
      String responseBody = okHttpResponse.body() != null ? okHttpResponse.body().string() : "";
      return new Response(
          okHttpResponse.code(),
          okHttpResponse.message(),
          okHttpResponse.headers().toMultimap(),
          responseBody,
          okHttpResponse.request().url().toString());
    }
  }
}

package com.hhst.youtubelite.downloader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UniversalDownloader {
  private final OkHttpClient httpClient = new OkHttpClient();
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url         The URL of the file to download.
   * @param output      The destination file for the downloaded content.
   * @param information Additional context or identifier for the download.
   * @return Always returns null since chunks are merged and removed.
   * @throws IOException          If an I/O error occurs.
   * @throws InterruptedException If the download is interrupted.
   */
  public File download(String url, File output, String information)
          throws IOException, InterruptedException {
    return download(url, output, new DownloadConfig(), null, information);
  }

  public File download(String url, File output, DownloadConfig config, String information)
          throws IOException, InterruptedException {
    return download(url, output, config, null, information);
  }

  public File download(String url, File output, ProgressCallback callback, String information)
          throws IOException, InterruptedException {
    return download(url, output, new DownloadConfig(), callback, information);
  }

  public File download(String url, File output, DownloadConfig config, ProgressCallback callback, String information)
          throws IOException, InterruptedException {
    isCancelled.set(false);

    if (url == null || url.isEmpty()) throw new IllegalArgumentException("URL cannot be empty");
    if (output == null) throw new IllegalArgumentException("Output file cannot be null");
    if (config == null) config = new DownloadConfig();

    createOutputDirectory(output);

    long fileSize = getFileSize(url);
    boolean supportsRange = supportsRangeRequests(url);

    if (fileSize <= 0 || !supportsRange || config.getThreadCount() == 1) {
      downloadSingleThreaded(url, output, config, callback, fileSize, information);
      return null;
    } else {
      return downloadMultiThreaded(url, output, config, callback, fileSize, information);
    }
  }

  private void createOutputDirectory(File output) {
    File outputDir = output.getParentFile();
    if (outputDir != null && !outputDir.exists()) outputDir.mkdirs();
  }

  private long getFileSize(String url) throws IOException {
    Request request = new Request.Builder().url(url).head().build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.header("Content-Length") != null) {
        return Long.parseLong(response.header("Content-Length"));
      }
    }
    return -1;
  }

  private boolean supportsRangeRequests(String url) throws IOException {
    Request request = new Request.Builder().url(url).head().build();
    try (Response response = httpClient.newCall(request).execute()) {
      return "bytes".equals(response.header("Accept-Ranges"));
    }
  }

  /**
   * Downloads the file using a single thread.
   */
  private void downloadSingleThreaded(String url, File output, DownloadConfig config, ProgressCallback callback,
                                      long fileSize, String information) throws IOException, InterruptedException {
    long existingSize = 0;
    if (config.isResumeSupport() && output.exists()) {
      existingSize = output.length();
    }

    Request.Builder requestBuilder = new Request.Builder().url(url);
    if (existingSize > 0) {
      requestBuilder.addHeader("Range", "bytes=" + existingSize + "-");
    }

    try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
      if (!response.isSuccessful() && response.code() != 206) {
        throw new IOException("Download failed, HTTP status: " + response.code());
      }
      if (response.body() == null) throw new IOException("Empty response body");

      try (InputStream inputStream = response.body().byteStream();
           FileOutputStream outputStream = new FileOutputStream(output, existingSize > 0)) {
        byte[] buffer = new byte[config.getChunkSize()];
        int bytesRead;
        long totalDownloaded = existingSize;
        double lastProgressUpdate = 0;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          if (isCancelled.get()) throw new InterruptedException("Download cancelled");
          outputStream.write(buffer, 0, bytesRead);
          totalDownloaded += bytesRead;
          if (callback != null && fileSize > 0) {
            double percentage = (double) totalDownloaded / fileSize * 100;
            if (percentage - lastProgressUpdate >= 2.0 || percentage >= 100) {
              callback.onProgress(totalDownloaded, fileSize, percentage, information);
              lastProgressUpdate = percentage;
            }
          }
        }
      }
    }
  }

  /**
   * Downloads the file in multiple chunks using multiple threads.
   */
  private File downloadMultiThreaded(String url, File output, DownloadConfig config,
                                     ProgressCallback callback, long fileSize, String information)
          throws IOException, InterruptedException {
    long existingSize = output.exists() ? output.length() : 0;
    long remainingSize = fileSize - existingSize;
    if (remainingSize <= 0) return null;

    File tempDir = new File(output.getParent(), output.getName() + ".tmp");
    tempDir.mkdirs();

    int threadCount = config.getThreadCount();
    long chunkSize = remainingSize / threadCount;

    AtomicLong totalDownloaded = new AtomicLong(existingSize);
    List<Future<Void>> futures = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      long start = existingSize + i * chunkSize;
      long end = (i == threadCount - 1) ? fileSize - 1 : (start + chunkSize - 1);
      File chunkFile = new File(tempDir, "chunk_" + i);
      futures.add(executor.submit(() -> {
        downloadChunk(url, chunkFile, start, end, totalDownloaded, fileSize, callback, information);
        return null;
      }));
    }

    IOException thrownIO = null;
    boolean wasInterrupted = false;
    try {
      for (Future<Void> f : futures) {
        try {
          f.get();
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof IOException) thrownIO = (IOException) cause;
          else if (cause instanceof InterruptedException) wasInterrupted = true;
          else thrownIO = new IOException(cause);
          break;
        }
      }
    } catch (InterruptedException e) {
      wasInterrupted = true;
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    if (wasInterrupted) {
      cleanupTempFiles(tempDir, output, threadCount);
      Thread.currentThread().interrupt();
      throw new InterruptedException("Download cancelled");
    }
    if (thrownIO != null) {
      cleanupTempFiles(tempDir, output, threadCount);
      throw thrownIO;
    }

    mergeChunks(tempDir, output, threadCount, existingSize > 0);
    cleanupTempFiles(tempDir, null, threadCount);
    return null;
  }

  private void cleanupTempFiles(File tempDir, File output, int threadCount) {
    if (output != null && output.exists()) output.delete();
    for (int i = 0; i < threadCount; i++) {
      new File(tempDir, "chunk_" + i).delete();
    }
    tempDir.delete();
  }

  private void downloadChunk(String url, File chunkFile, long start, long end,
                             AtomicLong totalDownloaded, long fileSize,
                             ProgressCallback callback, String information)
          throws IOException, InterruptedException {
    long existingChunkSize = chunkFile.exists() ? chunkFile.length() : 0;
    start += existingChunkSize;
    if (start > end) return;

    Request request = new Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=" + start + "-" + end)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() != 206) throw new IOException("Server doesn't support range requests");
      if (response.body() == null) throw new IOException("Empty response body");

      try (InputStream inputStream = response.body().byteStream();
           FileOutputStream outputStream = new FileOutputStream(chunkFile, existingChunkSize > 0)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        double lastProgressUpdate = 0;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          if (isCancelled.get()) throw new InterruptedException("Download cancelled");
          outputStream.write(buffer, 0, bytesRead);
          long downloaded = totalDownloaded.addAndGet(bytesRead);
          if (callback != null && fileSize > 0) {
            double percentage = (double) downloaded / fileSize * 100;
            if (percentage - lastProgressUpdate >= 2.0 || percentage >= 100) {
              callback.onProgress(downloaded, fileSize, percentage, information);
              lastProgressUpdate = percentage;
            }
          }
        }
      }
    }
  }

  private void mergeChunks(File tempDir, File output, int threadCount, boolean append)
          throws IOException, InterruptedException {
    try (FileOutputStream outputStream = new FileOutputStream(output, append)) {
      for (int i = 0; i < threadCount; i++) {
        File chunkFile = new File(tempDir, "chunk_" + i);
        if (chunkFile.exists()) {
          try (InputStream inputStream = new FileInputStream(chunkFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
              if (isCancelled.get()) throw new InterruptedException("Download cancelled");
              outputStream.write(buffer, 0, bytesRead);
            }
          }
        }
      }
    }
  }

  public void cancel() {
    isCancelled.set(true);
  }

  /**
   * Interface for reporting download progress.
   */
  public interface ProgressCallback {
    void onProgress(long downloaded, long total, double percentage, String information);
  }

  /**
   * Configuration for download options.
   */
  @Getter
  public static class DownloadConfig {
    private int threadCount = 8;
    private int chunkSize = 4 * 1024 * 1024; // 4MB
    private boolean resumeSupport = true;

    public DownloadConfig setThreadCount(int threadCount) {
      this.threadCount = Math.max(1, Math.min(threadCount, 16));
      return this;
    }

    public DownloadConfig setChunkSize(int chunkSize) {
      this.chunkSize = Math.max(1024, chunkSize);
      return this;
    }

    public DownloadConfig setResumeSupport(boolean resumeSupport) {
      this.resumeSupport = resumeSupport;
      return this;
    }
  }
}
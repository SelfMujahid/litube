package com.hhst.youtubelite.downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UniversalDownloader {

  private final OkHttpClient httpClient = new OkHttpClient();
  private final ForkJoinPool forkJoinPool = new ForkJoinPool();

  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url The URL of the file to download.
   * @param output The file where the downloaded content will be saved.
   * @return The temporary file directory where chunks are stored, or null if no temporary files
   *     were created.
   * @throws IOException If an I/O error occurs during the download.
   * @throws InterruptedException If the download is interrupted.
   */
  public File download(String url, File output, String information)
      throws IOException, InterruptedException {
    return download(url, output, new DownloadConfig(), null, information);
  }

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url The URL of the file to download.
   * @param output The file where the downloaded content will be saved.
   * @param config Configuration for the download, can be null for default settings.
   * @param information Additional information to pass to the callback, can be null.
   * @return The temporary file directory where chunks are stored, or null if no temporary files
   *     were created.
   * @throws IOException If an I/O error occurs during the download.
   * @throws InterruptedException If the download is interrupted.
   */
  public File download(String url, File output, DownloadConfig config, String information)
      throws IOException, InterruptedException {
    return download(url, output, config, null, information);
  }

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url The URL of the file to download.
   * @param output The file where the downloaded content will be saved.
   * @param callback Optional callback for progress updates, can be null.
   * @param information Additional information to pass to the callback, can be null.
   * @return The temporary file directory where chunks are stored, or null if no temporary files
   *     were created.
   * @throws IOException If an I/O error occurs during the download.
   * @throws InterruptedException If the download is interrupted.
   */
  public File download(String url, File output, ProgressCallback callback, String information)
      throws IOException, InterruptedException {
    return download(url, output, new DownloadConfig(), callback, information);
  }

  /**
   * Downloads a file from the specified URL to the given output file.
   *
   * @param url The URL of the file to download.
   * @param output The file where the downloaded content will be saved.
   * @param config Configuration for the download, can be null for default settings.
   * @param callback Optional callback for progress updates, can be null.
   * @param information Additional information to pass to the callback, can be null.
   * @return The temporary file directory where chunks are stored, or null if no temporary files
   *     were created.
   * @throws IOException If an I/O error occurs during the download.
   * @throws InterruptedException If the download is interrupted.
   */
  public File download(
      String url, File output, DownloadConfig config, ProgressCallback callback, String information)
      throws IOException, InterruptedException {
    if (url == null || url.isEmpty()) {
      throw new IllegalArgumentException("URL cannot be null or empty");
    }
    if (output == null) {
      throw new IllegalArgumentException("Output file path cannot be null");
    }
    if (config == null) {
      config = new DownloadConfig();
    }

    createOutputDirectory(output);

    // Get file size and check if server supports range requests
    long fileSize = getFileSize(url);
    boolean supportsRange = supportsRangeRequests(url);

    if (fileSize <= 0 || !supportsRange || config.getThreadCount() == 1) {
      // Single-threaded download
      downloadSingleThreaded(url, output, config, callback, fileSize, information);
      return null; // No temporary created
    } else {
      // Multithreaded download with resume support
      return downloadMultiThreaded(url, output, config, callback, fileSize, information);
    }
  }

  private void createOutputDirectory(File output) {
    File outputDir = output.getParentFile();
    if (outputDir != null && !outputDir.exists()) {
      outputDir.mkdirs();
    }
  }

  private long getFileSize(String url) throws IOException {
    Request request = new Request.Builder().url(url).head().build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.header("Content-Length") != null) {
        String contentLength = response.header("Content-Length");
        if (contentLength == null || contentLength.isEmpty()) {
          return -1; // Content-Length not available
        }
        return Long.parseLong(contentLength);
      }
    }
    return -1;
  }

  private boolean supportsRangeRequests(String url) throws IOException {
    Request request = new Request.Builder().url(url).head().build();

    try (Response response = httpClient.newCall(request).execute()) {
      String acceptRanges = response.header("Accept-Ranges");
      return "bytes".equals(acceptRanges);
    }
  }

  private void downloadSingleThreaded(
      String url,
      File output,
      DownloadConfig config,
      ProgressCallback callback,
      long fileSize,
      String information)
      throws IOException, InterruptedException {
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
        throw new IOException("Failed to download file. HTTP status: " + response.code());
      }

      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      try (InputStream inputStream = response.body().byteStream();
          FileOutputStream outputStream = new FileOutputStream(output, existingSize > 0)) {
        byte[] buffer = new byte[config.getChunkSize()];
        int bytesRead;
        long totalDownloaded = existingSize;
        double lastProgressUpdate = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
          if (forkJoinPool.isShutdown() || isCancelled.get()) {
            throw new InterruptedException("Download canceled");
          }
          outputStream.write(buffer, 0, bytesRead);
          totalDownloaded += bytesRead;

          if (callback != null && fileSize > 0) {
            double percentage = (double) totalDownloaded / fileSize * 100;
            // Only update progress for significant changes to avoid too frequent updates
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
   * @return The temporary file directory where chunks are stored.
   */
  private File downloadMultiThreaded(
      String url,
      File output,
      DownloadConfig config,
      ProgressCallback callback,
      long fileSize,
      String information)
      throws IOException, InterruptedException {
    long existingSize = output.exists() ? output.length() : 0;
    long remainingSize = fileSize - existingSize;

    if (remainingSize <= 0) {
      return null; // File already downloaded
    }
    File tempDir = new File(output.getParent(), output.getName() + ".tmp");
    tempDir.mkdirs();

    int threadCount = config.getThreadCount();
    long chunkSize = remainingSize / threadCount;

    AtomicLong totalDownloaded = new AtomicLong(existingSize);
    DownloadChunkTask[] tasks = new DownloadChunkTask[threadCount];

    for (int i = 0; i < threadCount; i++) {
      long start = existingSize + i * chunkSize;
      long end = (i == threadCount - 1) ? fileSize - 1 : start + chunkSize - 1;
      File chunkFile = new File(tempDir, "chunk_" + i);

      tasks[i] =
          new DownloadChunkTask(
              url, chunkFile, start, end, totalDownloaded, fileSize, callback, information);
    }

    // Submit all tasks to ForkJoinPool and wait for completion
    for (DownloadChunkTask task : tasks) {
      forkJoinPool.submit(task);
    }

    // Wait for all tasks to complete
    for (DownloadChunkTask task : tasks) {
      task.join();
    }

    // Merge chunks
    mergeChunks(tempDir, output, threadCount, existingSize > 0);
    return tempDir;
  }

  private void downloadChunk(
      String url,
      File chunkFile,
      long start,
      long end,
      AtomicLong totalDownloaded,
      long fileSize,
      ProgressCallback callback,
      String information)
      throws IOException, InterruptedException {
    long existingChunkSize = 0;
    if (chunkFile.exists()) {
      existingChunkSize = chunkFile.length();
      start += existingChunkSize;
    }

    if (start > end) {
      return; // Chunk already downloaded
    }

    Request request =
        new Request.Builder().url(url).addHeader("Range", "bytes=" + start + "-" + end).build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() != 206) {
        throw new IOException(
            "Server does not support range requests. HTTP status: " + response.code());
      }

      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      try (InputStream inputStream = response.body().byteStream();
          FileOutputStream outputStream = new FileOutputStream(chunkFile, existingChunkSize > 0)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        double lastProgressUpdate = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
          if (forkJoinPool.isShutdown() || isCancelled.get()) {
            throw new InterruptedException("Download canceled");
          }
          outputStream.write(buffer, 0, bytesRead);
          long downloaded = totalDownloaded.addAndGet(bytesRead);

          if (callback != null) {
            double percentage = (double) downloaded / fileSize * 100;
            // Only update progress for significant changes to avoid too frequent updates
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
          try (InputStream inputStream = new java.io.FileInputStream(chunkFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
              if (forkJoinPool.isShutdown() || isCancelled.get()) {
                throw new InterruptedException("Download canceled");
              }
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

  public void shutdown() {

    try {
      forkJoinPool.shutdown();
      if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
        forkJoinPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      forkJoinPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** Progress callback interface */
  public interface ProgressCallback {
    void onProgress(long downloaded, long total, double percentage, String information);
  }

  /** Download configuration */
  @Getter
  public static class DownloadConfig {
    private int threadCount = 4;
    private int chunkSize = 1024 * 1024; // 1MB
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

  /** Download chunk task using ForkJoin framework */
  private class DownloadChunkTask extends RecursiveTask<Void> {
    private final String url;
    private final File chunkFile;
    private final long start;
    private final long end;
    private final AtomicLong totalDownloaded;
    private final long fileSize;
    private final ProgressCallback callback;

    private final String information;

    public DownloadChunkTask(
        String url,
        File chunkFile,
        long start,
        long end,
        AtomicLong totalDownloaded,
        long fileSize,
        ProgressCallback callback,
        String information) {
      this.url = url;
      this.chunkFile = chunkFile;
      this.start = start;
      this.end = end;
      this.totalDownloaded = totalDownloaded;
      this.fileSize = fileSize;
      this.callback = callback;
      this.information = information;
    }

    @Override
    protected Void compute() {
      try {
        downloadChunk(url, chunkFile, start, end, totalDownloaded, fileSize, callback, information);
        return null;
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}

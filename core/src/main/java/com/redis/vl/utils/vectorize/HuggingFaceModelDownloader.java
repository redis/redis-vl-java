package com.redis.vl.utils.vectorize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads and caches HuggingFace models locally for offline use. Models are cached in
 * ~/.cache/redisvl4j/models/ by default.
 */
@Slf4j
public class HuggingFaceModelDownloader {

  private static final String DEFAULT_BASE_URL = "https://huggingface.co/";
  private static final String DEFAULT_REVISION = "main";
  private static final int DEFAULT_TIMEOUT_SECONDS = 60;

  private final Path cacheDir;
  private final String baseUrl;
  private final OkHttpClient httpClient;
  private final ProgressListener progressListener;

  /** Progress listener for download tracking. */
  public interface ProgressListener {
    void onProgress(String fileName, long bytesDownloaded, long totalBytes);
  }

  public HuggingFaceModelDownloader(String cacheDir) {
    this(cacheDir, DEFAULT_BASE_URL, DEFAULT_TIMEOUT_SECONDS);
  }

  public HuggingFaceModelDownloader(String cacheDir, String baseUrl) {
    this(cacheDir, baseUrl, DEFAULT_TIMEOUT_SECONDS);
  }

  public HuggingFaceModelDownloader(String cacheDir, String baseUrl, int timeoutSeconds) {
    this(cacheDir, baseUrl, null, timeoutSeconds);
  }

  public HuggingFaceModelDownloader(String cacheDir, String baseUrl, ProgressListener listener) {
    this(cacheDir, baseUrl, listener, DEFAULT_TIMEOUT_SECONDS);
  }

  public HuggingFaceModelDownloader(
      String cacheDir, String baseUrl, ProgressListener listener, int timeoutSeconds) {
    this.cacheDir = Paths.get(cacheDir).resolve("models");
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.progressListener = listener;
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
  }

  /** Download a model from HuggingFace with default revision. */
  public Path downloadModel(String modelName) throws IOException {
    return downloadModel(modelName, DEFAULT_REVISION);
  }

  /** Download a model from HuggingFace with specified revision. */
  public Path downloadModel(String modelName, String revision) throws IOException {
    if (modelName == null || modelName.trim().isEmpty()) {
      throw new IllegalArgumentException("Model name cannot be null or empty");
    }

    Path modelDir = cacheDir.resolve(modelName).resolve(revision);

    // Check if model is already cached
    if (isModelCached(modelDir)) {
      log.info("Model {} already cached at {}", modelName, modelDir);
      return modelDir;
    }

    log.info("Downloading model {} to {}", modelName, modelDir);

    try {
      Files.createDirectories(modelDir);

      // Download required files
      downloadFile(modelName, revision, "config.json", modelDir);

      // Try to download ONNX model, checking various locations
      boolean modelDownloaded = false;
      IOException lastError = null;

      // First try standard location
      try {
        downloadFile(modelName, revision, "model.onnx", modelDir);
        modelDownloaded = true;
        log.info("Downloaded ONNX model for {}", modelName);
      } catch (IOException e) {
        lastError = e;
        // Try onnx subdirectory (sentence-transformers models often have ONNX in subdirectory)
        log.debug("Primary ONNX download failed, trying onnx/ subdirectory for {}", modelName);
        try {
          downloadFile(modelName, revision, "onnx/model.onnx", modelDir);
          // The file is now at modelDir/onnx/model.onnx, move it to modelDir/model.onnx
          Path onnxFile = modelDir.resolve("onnx").resolve("model.onnx");
          Path targetFile = modelDir.resolve("model.onnx");
          if (Files.exists(onnxFile)) {
            Files.move(onnxFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Clean up the onnx directory if empty
            try {
              Files.deleteIfExists(modelDir.resolve("onnx"));
            } catch (Exception cleanupError) {
              // Ignore cleanup errors
            }
            modelDownloaded = true;
            log.info("Downloaded ONNX model from onnx/ subdirectory for {}", modelName);
          } else {
            log.warn(
                "Downloaded onnx/model.onnx but file not found at expected location: {}", onnxFile);
          }
        } catch (IOException onnxSubdirError) {
          log.debug("ONNX subdirectory download also failed: {}", onnxSubdirError.getMessage());
          lastError = onnxSubdirError;
        }
      }

      // If ONNX not found, check if it's a SafeTensors model
      if (!modelDownloaded) {
        try {
          // Check if SafeTensors exists
          downloadFile(modelName, revision, "model.safetensors", modelDir);
          // SafeTensors exists but we can't use it
          throw new IOException(
              "Model "
                  + modelName
                  + " uses SafeTensors format which is not supported. "
                  + "ONNX format is required. Try 'sentence-transformers/all-MiniLM-L6-v2' or another model with ONNX support.");
        } catch (IOException safeTensorsError) {
          if (safeTensorsError.getMessage().contains("SafeTensors format")) {
            throw safeTensorsError;
          }
          // Neither ONNX nor SafeTensors found
          throw new IOException(
              "Model "
                  + modelName
                  + " not found or does not have ONNX format. "
                  + "Ensure the model exists and has ONNX support.",
              lastError);
        }
      }

      downloadFile(modelName, revision, "tokenizer.json", modelDir);

      // Optional files - don't fail if not found
      try {
        downloadFile(modelName, revision, "tokenizer_config.json", modelDir);
      } catch (IOException e) {
        log.debug("Optional file tokenizer_config.json not found for model {}", modelName);
      }

      try {
        downloadFile(modelName, revision, "special_tokens_map.json", modelDir);
      } catch (IOException e) {
        log.debug("Optional file special_tokens_map.json not found for model {}", modelName);
      }

      log.info("Successfully downloaded model {} to {}", modelName, modelDir);
      return modelDir;

    } catch (Exception e) {
      // Clean up partial download on failure
      cleanupPartialDownload(modelDir);
      throw new IOException("Failed to download model " + modelName, e);
    }
  }

  private boolean isModelCached(Path modelDir) {
    return Files.exists(modelDir)
        && Files.exists(modelDir.resolve("config.json"))
        && Files.exists(modelDir.resolve("model.onnx"))
        && Files.exists(modelDir.resolve("tokenizer.json"));
  }

  private void downloadFile(String modelName, String revision, String fileName, Path targetDir)
      throws IOException {
    Path targetFile = targetDir.resolve(fileName);

    // Skip if file already exists (partial download recovery)
    if (Files.exists(targetFile)) {
      log.debug("File {} already exists, skipping download", targetFile);
      return;
    }

    // Create parent directories if needed
    Path parent = targetFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    String url = String.format("%s%s/resolve/%s/%s", baseUrl, modelName, revision, fileName);
    log.debug("Downloading {} from {}", fileName, url);

    Request request = new Request.Builder().url(url).build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        if (response.code() == 404) {
          throw new IOException("Model not found: " + modelName + "/" + fileName);
        }
        throw new IOException("Failed to download " + fileName + ": " + response.code());
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("Empty response body for " + fileName);
      }

      long contentLength = responseBody.contentLength();

      // Download to temp file first
      // Extract just the filename without path for temp file creation
      String simpleFileName =
          fileName.contains("/") ? fileName.substring(fileName.lastIndexOf("/") + 1) : fileName;
      Path tempFile = Files.createTempFile(targetDir, simpleFileName, ".tmp");

      try (InputStream in = responseBody.byteStream();
          OutputStream out = Files.newOutputStream(tempFile)) {

        byte[] buffer = new byte[8192];
        long totalBytesRead = 0;
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
          out.write(buffer, 0, bytesRead);
          totalBytesRead += bytesRead;

          // Report progress if listener is set
          if (progressListener != null && contentLength > 0) {
            progressListener.onProgress(fileName, totalBytesRead, contentLength);
          }
        }
      }

      // Move temp file to final location
      Files.move(
          tempFile,
          targetFile,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      log.debug("Downloaded {} ({} bytes)", fileName, Files.size(targetFile));

    } catch (IOException e) {
      // Clean up temp file on failure
      Files.deleteIfExists(targetDir.resolve(fileName + ".tmp"));
      throw e;
    }
  }

  private void cleanupPartialDownload(Path modelDir) {
    try {
      if (Files.exists(modelDir)) {
        Files.walk(modelDir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", path, e.getMessage());
                  }
                });
      }
    } catch (IOException e) {
      log.warn("Failed to cleanup partial download at {}: {}", modelDir, e.getMessage());
    }
  }
}

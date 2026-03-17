package com.redis.vl.demos.facematch.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Utility to ensure the ArcFace ONNX model is available locally. Downloads the model from
 * HuggingFace if it is not found on the classpath or in the local cache directory.
 *
 * <p>Lookup order:
 *
 * <ol>
 *   <li>Classpath at {@code /models/arcfaceresnet100-8.onnx}
 *   <li>File system cache at {@code ~/.redisvl/models/arcfaceresnet100-8.onnx}
 *   <li>Auto-download from HuggingFace to the file system cache
 * </ol>
 */
public final class ModelDownloader {

  private static final String MODEL_NAME = "arcfaceresnet100-8.onnx";
  private static final String MODEL_URL =
      "https://huggingface.co/onnxmodelzoo/arcfaceresnet100-8/resolve/main/model/"
          + MODEL_NAME;
  private static final Path CACHE_DIR =
      Path.of(System.getProperty("user.home"), ".redisvl", "models");

  private ModelDownloader() {}

  /**
   * Load the ArcFace ONNX model bytes. Tries classpath first, then file system cache, then
   * downloads from HuggingFace.
   *
   * @return the model bytes
   * @throws IOException if the model cannot be loaded or downloaded
   */
  public static byte[] loadModelBytes() throws IOException {
    // 1. Try classpath (model bundled in resources)
    try (InputStream stream =
        ModelDownloader.class.getResourceAsStream("/models/" + MODEL_NAME)) {
      if (stream != null) {
        System.out.println("Loading ArcFace model from classpath");
        return stream.readAllBytes();
      }
    }

    // 2. Try file system cache
    Path cachedModel = CACHE_DIR.resolve(MODEL_NAME);
    if (Files.exists(cachedModel)) {
      System.out.println("Loading ArcFace model from cache: " + cachedModel);
      return Files.readAllBytes(cachedModel);
    }

    // 3. Download from HuggingFace
    System.out.println("ArcFace model not found locally. Downloading from HuggingFace...");
    System.out.println("URL: " + MODEL_URL);
    System.out.println("Target: " + cachedModel);
    download(cachedModel);
    System.out.println("Download complete (" + Files.size(cachedModel) / (1024 * 1024) + " MB)");

    return Files.readAllBytes(cachedModel);
  }

  private static void download(Path target) throws IOException {
    Files.createDirectories(target.getParent());

    HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(MODEL_URL))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();

    try {
      HttpResponse<Path> response =
          client.send(request, HttpResponse.BodyHandlers.ofFile(target));

      if (response.statusCode() != 200) {
        Files.deleteIfExists(target);
        throw new IOException(
            "Failed to download model: HTTP " + response.statusCode());
      }
    } catch (InterruptedException e) {
      Files.deleteIfExists(target);
      Thread.currentThread().interrupt();
      throw new IOException("Download interrupted", e);
    }
  }
}

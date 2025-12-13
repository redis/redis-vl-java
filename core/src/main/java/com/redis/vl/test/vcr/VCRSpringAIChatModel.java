package com.redis.vl.test.vcr;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * VCR wrapper for Spring AI ChatModel that records and replays LLM responses.
 *
 * <p>This class implements the ChatModel interface, allowing it to be used as a drop-in replacement
 * for any Spring AI chat model. It provides VCR (Video Cassette Recorder) functionality to record
 * LLM responses during test execution and replay them in subsequent runs.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ChatModel openAiModel = new OpenAiChatModel(openAiApi);
 *
 * VCRSpringAIChatModel vcrModel = new VCRSpringAIChatModel(openAiModel);
 * vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
 * vcrModel.setTestId("MyTest.testMethod");
 *
 * // Use exactly like the original model
 * String response = vcrModel.call("Hello");
 * }</pre>
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Delegate is intentionally stored and exposed for VCR functionality")
public final class VCRSpringAIChatModel implements ChatModel {

  private final ChatModel delegate;
  private VCRCassetteStore cassetteStore;
  private VCRMode mode = VCRMode.PLAYBACK_OR_RECORD;
  private String testId = "unknown";
  private final AtomicInteger callCounter = new AtomicInteger(0);

  // In-memory cassette storage for unit tests
  private final Map<String, String> cassettes = new HashMap<>();

  // Statistics
  private int cacheHits = 0;
  private int cacheMisses = 0;
  private int recordedCount = 0;

  /**
   * Creates a new VCRSpringAIChatModel wrapping the given delegate.
   *
   * @param delegate The actual ChatModel to wrap
   */
  public VCRSpringAIChatModel(ChatModel delegate) {
    this.delegate = delegate;
  }

  /**
   * Creates a new VCRSpringAIChatModel wrapping the given delegate with Redis storage.
   *
   * @param delegate The actual ChatModel to wrap
   * @param cassetteStore The cassette store for persistence
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "VCRCassetteStore is intentionally shared")
  public VCRSpringAIChatModel(ChatModel delegate, VCRCassetteStore cassetteStore) {
    this.delegate = delegate;
    this.cassetteStore = cassetteStore;
  }

  /**
   * Sets the VCR mode.
   *
   * @param mode The VCR mode to use
   */
  public void setMode(VCRMode mode) {
    this.mode = mode;
  }

  /**
   * Gets the current VCR mode.
   *
   * @return The current VCR mode
   */
  public VCRMode getMode() {
    return mode;
  }

  /**
   * Sets the test identifier for cassette key generation.
   *
   * @param testId The test identifier (typically ClassName.methodName)
   */
  public void setTestId(String testId) {
    this.testId = testId;
  }

  /**
   * Gets the current test identifier.
   *
   * @return The current test identifier
   */
  public String getTestId() {
    return testId;
  }

  /** Resets the call counter. Useful when starting a new test method. */
  public void resetCallCounter() {
    callCounter.set(0);
  }

  /**
   * Gets the underlying delegate model.
   *
   * @return The wrapped ChatModel
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Intentional exposure of delegate for advanced use cases")
  public ChatModel getDelegate() {
    return delegate;
  }

  /**
   * Preloads a cassette for testing purposes.
   *
   * @param key The cassette key
   * @param response The response text to cache
   */
  public void preloadCassette(String key, String response) {
    cassettes.put(key, response);
  }

  /**
   * Gets the number of cache hits.
   *
   * @return Cache hit count
   */
  public int getCacheHits() {
    return cacheHits;
  }

  /**
   * Gets the number of cache misses.
   *
   * @return Cache miss count
   */
  public int getCacheMisses() {
    return cacheMisses;
  }

  /**
   * Gets the number of recorded responses.
   *
   * @return Recorded count
   */
  public int getRecordedCount() {
    return recordedCount;
  }

  /** Resets all statistics. */
  public void resetStatistics() {
    cacheHits = 0;
    cacheMisses = 0;
    recordedCount = 0;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    String responseText =
        callInternal(
            () -> {
              ChatResponse response = delegate.call(prompt);
              return response.getResult().getOutput().getText();
            });
    Generation generation = new Generation(new AssistantMessage(responseText));
    return new ChatResponse(List.of(generation));
  }

  @Override
  public String call(String message) {
    return callInternal(() -> delegate.call(message));
  }

  @Override
  public String call(Message... messages) {
    return callInternal(() -> delegate.call(messages));
  }

  private String callInternal(java.util.function.Supplier<String> delegateCall) {
    if (mode == VCRMode.OFF) {
      return delegateCall.get();
    }

    String key = formatKey();

    if (mode.isPlaybackMode()) {
      String cached = loadCassette(key);
      if (cached != null) {
        cacheHits++;
        return cached;
      }

      if (mode == VCRMode.PLAYBACK) {
        throw new VCRCassetteMissingException(key, testId);
      }

      // PLAYBACK_OR_RECORD - fall through to record
    }

    // Record mode or cache miss in PLAYBACK_OR_RECORD
    cacheMisses++;
    String response = delegateCall.get();
    saveCassette(key, response);
    recordedCount++;

    return response;
  }

  private String loadCassette(String key) {
    // Check in-memory first
    String inMemory = cassettes.get(key);
    if (inMemory != null) {
      return inMemory;
    }

    // Check Redis if available
    if (cassetteStore != null) {
      com.google.gson.JsonObject cassette = cassetteStore.retrieve(key);
      if (cassette != null && cassette.has("response")) {
        return cassette.get("response").getAsString();
      }
    }

    return null;
  }

  private void saveCassette(String key, String response) {
    // Save to in-memory
    cassettes.put(key, response);

    // Save to Redis if available
    if (cassetteStore != null) {
      com.google.gson.JsonObject cassette = new com.google.gson.JsonObject();
      cassette.addProperty("response", response);
      cassette.addProperty("testId", testId);
      cassette.addProperty("type", "chat");
      cassetteStore.store(key, cassette);
    }
  }

  private String formatKey() {
    int index = callCounter.incrementAndGet();
    return String.format("vcr:chat:%s:%04d", testId, index);
  }
}

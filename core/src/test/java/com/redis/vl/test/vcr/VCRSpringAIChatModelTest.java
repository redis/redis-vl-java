package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Unit tests for VCRSpringAIChatModel.
 *
 * <p>These tests demonstrate standalone VCR usage with Spring AI ChatModel, without requiring any
 * RedisVL components.
 */
@DisplayName("VCRSpringAIChatModel")
class VCRSpringAIChatModelTest {

  private VCRSpringAIChatModel vcrModel;
  private MockSpringAIChatModel mockDelegate;

  @BeforeEach
  void setUp() {
    mockDelegate = new MockSpringAIChatModel();
    vcrModel = new VCRSpringAIChatModel(mockDelegate);
    vcrModel.setTestId("VCRSpringAIChatModelTest.test");
  }

  @Nested
  @DisplayName("OFF Mode - Direct passthrough")
  class OffMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.OFF);
    }

    @Test
    @DisplayName("should call delegate directly when VCR is OFF")
    void shouldCallDelegateDirectly() {
      String response = vcrModel.call("Hello");

      assertNotNull(response);
      assertTrue(response.contains("Mock response"));
      assertEquals(1, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should not record when VCR is OFF")
    void shouldNotRecordWhenOff() {
      vcrModel.call("Hello");
      vcrModel.call("World");

      assertEquals(2, mockDelegate.callCount.get());
      assertEquals(0, vcrModel.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("RECORD Mode")
  class RecordMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.RECORD);
    }

    @Test
    @DisplayName("should call delegate and record result")
    void shouldCallDelegateAndRecord() {
      String response = vcrModel.call("Test message");

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should record multiple calls with incrementing indices")
    void shouldRecordMultipleCalls() {
      vcrModel.call("Message 1");
      vcrModel.call("Message 2");
      vcrModel.call("Message 3");

      assertEquals(3, mockDelegate.callCount.get());
      assertEquals(3, vcrModel.getRecordedCount());
    }
  }

  @Nested
  @DisplayName("PLAYBACK Mode")
  class PlaybackMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.PLAYBACK);
    }

    @Test
    @DisplayName("should return cached response without calling delegate")
    void shouldReturnCachedResponse() {
      // Pre-load cassette
      String cachedResponse = "This is a cached LLM response";
      vcrModel.preloadCassette("vcr:chat:VCRSpringAIChatModelTest.test:0001", cachedResponse);

      String response = vcrModel.call("Test");

      assertNotNull(response);
      assertEquals(cachedResponse, response);
      assertEquals(0, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should throw when cassette not found in strict PLAYBACK mode")
    void shouldThrowWhenCassetteNotFound() {
      assertThrows(VCRCassetteMissingException.class, () -> vcrModel.call("Unknown"));
    }

    @Test
    @DisplayName("should track cache hits")
    void shouldTrackCacheHits() {
      vcrModel.preloadCassette("vcr:chat:VCRSpringAIChatModelTest.test:0001", "Cached");

      vcrModel.call("Test");

      assertEquals(1, vcrModel.getCacheHits());
      assertEquals(0, vcrModel.getCacheMisses());
    }
  }

  @Nested
  @DisplayName("PLAYBACK_OR_RECORD Mode")
  class PlaybackOrRecordMode {

    @BeforeEach
    void setUp() {
      vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
    }

    @Test
    @DisplayName("should return cached response when available")
    void shouldReturnCachedWhenAvailable() {
      String cachedResponse = "Cached LLM answer";
      vcrModel.preloadCassette("vcr:chat:VCRSpringAIChatModelTest.test:0001", cachedResponse);

      String response = vcrModel.call("Test");

      assertEquals(cachedResponse, response);
      assertEquals(0, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheHits());
    }

    @Test
    @DisplayName("should call delegate and record on cache miss")
    void shouldCallDelegateAndRecordOnMiss() {
      String response = vcrModel.call("Uncached");

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
      assertEquals(1, vcrModel.getCacheMisses());
    }

    @Test
    @DisplayName("should allow subsequent cache hits after recording")
    void shouldAllowSubsequentCacheHits() {
      // First call - cache miss, records
      vcrModel.call("Question");
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheMisses());

      // Reset counter for second call
      vcrModel.resetCallCounter();

      // Second call - cache hit from recorded value
      vcrModel.call("Question");
      assertEquals(1, mockDelegate.callCount.get()); // Still 1, not 2
      assertEquals(1, vcrModel.getCacheHits());
    }
  }

  @Nested
  @DisplayName("Prompt API")
  class PromptApi {

    @Test
    @DisplayName("should handle Prompt in RECORD mode")
    void shouldHandlePromptInRecordMode() {
      vcrModel.setMode(VCRMode.RECORD);

      Prompt prompt = new Prompt(List.of(new UserMessage("Hello from Prompt")));

      ChatResponse response = vcrModel.call(prompt);

      assertNotNull(response);
      assertNotNull(response.getResult());
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached ChatResponse for Prompt")
    void shouldReturnCachedChatResponse() {
      vcrModel.setMode(VCRMode.PLAYBACK);
      vcrModel.preloadCassette(
          "vcr:chat:VCRSpringAIChatModelTest.test:0001", "Prompt cached response");

      Prompt prompt = new Prompt(List.of(new UserMessage("Test")));

      ChatResponse response = vcrModel.call(prompt);

      assertEquals("Prompt cached response", response.getResult().getOutput().getText());
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("Message Varargs")
  class MessageVarargs {

    @Test
    @DisplayName("should handle Message varargs in RECORD mode")
    void shouldHandleMessageVarargsInRecordMode() {
      vcrModel.setMode(VCRMode.RECORD);

      String response = vcrModel.call(new UserMessage("First"), new UserMessage("Second"));

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached response for Message varargs")
    void shouldReturnCachedForMessageVarargs() {
      vcrModel.setMode(VCRMode.PLAYBACK);
      vcrModel.preloadCassette("vcr:chat:VCRSpringAIChatModelTest.test:0001", "Varargs cached");

      String response = vcrModel.call(new UserMessage("Test"));

      assertEquals("Varargs cached", response);
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("Delegate Access")
  class DelegateAccess {

    @Test
    @DisplayName("should provide access to underlying delegate")
    void shouldProvideAccessToDelegate() {
      ChatModel delegate = vcrModel.getDelegate();

      assertSame(mockDelegate, delegate);
    }
  }

  @Nested
  @DisplayName("Statistics Reset")
  class StatisticsReset {

    @Test
    @DisplayName("should reset statistics")
    void shouldResetStatistics() {
      vcrModel.setMode(VCRMode.PLAYBACK_OR_RECORD);
      vcrModel.call("Test"); // Cache miss

      assertEquals(1, vcrModel.getCacheMisses());

      vcrModel.resetStatistics();

      assertEquals(0, vcrModel.getCacheHits());
      assertEquals(0, vcrModel.getCacheMisses());
    }
  }

  /** Mock Spring AI ChatModel for testing. */
  static class MockSpringAIChatModel implements ChatModel {
    AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public ChatResponse call(Prompt prompt) {
      callCount.incrementAndGet();
      String lastMessage = "";
      if (prompt.getInstructions() != null && !prompt.getInstructions().isEmpty()) {
        Message last = prompt.getInstructions().get(prompt.getInstructions().size() - 1);
        lastMessage = last.getText();
      }
      Generation generation =
          new Generation(new AssistantMessage("Mock response to: " + lastMessage));
      return new ChatResponse(List.of(generation));
    }

    @Override
    public String call(String message) {
      callCount.incrementAndGet();
      return "Mock response to: " + message;
    }
  }
}

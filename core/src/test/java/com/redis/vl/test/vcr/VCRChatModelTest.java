package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VCRChatModel.
 *
 * <p>These tests demonstrate standalone VCR usage with LangChain4J ChatLanguageModel, without
 * requiring any RedisVL components.
 */
@DisplayName("VCRChatModel")
class VCRChatModelTest {

  private VCRChatModel vcrModel;
  private MockChatLanguageModel mockDelegate;

  @BeforeEach
  void setUp() {
    mockDelegate = new MockChatLanguageModel();
    vcrModel = new VCRChatModel(mockDelegate);
    vcrModel.setTestId("VCRChatModelTest.test");
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
      Response<AiMessage> response = vcrModel.generate(UserMessage.from("Hello"));

      assertNotNull(response);
      assertNotNull(response.content());
      assertTrue(response.content().text().contains("Mock response"));
      assertEquals(1, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should not record when VCR is OFF")
    void shouldNotRecordWhenOff() {
      vcrModel.generate(UserMessage.from("Hello"));
      vcrModel.generate(UserMessage.from("World"));

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
      Response<AiMessage> response = vcrModel.generate(UserMessage.from("Test message"));

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should record multiple calls with incrementing indices")
    void shouldRecordMultipleCalls() {
      vcrModel.generate(UserMessage.from("Message 1"));
      vcrModel.generate(UserMessage.from("Message 2"));
      vcrModel.generate(UserMessage.from("Message 3"));

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
      vcrModel.preloadCassette("vcr:chat:VCRChatModelTest.test:0001", cachedResponse);

      Response<AiMessage> response = vcrModel.generate(UserMessage.from("Test"));

      assertNotNull(response);
      assertEquals(cachedResponse, response.content().text());
      assertEquals(0, mockDelegate.callCount.get());
    }

    @Test
    @DisplayName("should throw when cassette not found in strict PLAYBACK mode")
    void shouldThrowWhenCassetteNotFound() {
      assertThrows(
          VCRCassetteMissingException.class, () -> vcrModel.generate(UserMessage.from("Unknown")));
    }

    @Test
    @DisplayName("should track cache hits")
    void shouldTrackCacheHits() {
      vcrModel.preloadCassette("vcr:chat:VCRChatModelTest.test:0001", "Cached");

      vcrModel.generate(UserMessage.from("Test"));

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
      vcrModel.preloadCassette("vcr:chat:VCRChatModelTest.test:0001", cachedResponse);

      Response<AiMessage> response = vcrModel.generate(UserMessage.from("Test"));

      assertEquals(cachedResponse, response.content().text());
      assertEquals(0, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheHits());
    }

    @Test
    @DisplayName("should call delegate and record on cache miss")
    void shouldCallDelegateAndRecordOnMiss() {
      Response<AiMessage> response = vcrModel.generate(UserMessage.from("Uncached"));

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
      assertEquals(1, vcrModel.getCacheMisses());
    }

    @Test
    @DisplayName("should allow subsequent cache hits after recording")
    void shouldAllowSubsequentCacheHits() {
      // First call - cache miss, records
      vcrModel.generate(UserMessage.from("Question"));
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getCacheMisses());

      // Reset counter for second call
      vcrModel.resetCallCounter();

      // Second call - cache hit from recorded value
      vcrModel.generate(UserMessage.from("Question"));
      assertEquals(1, mockDelegate.callCount.get()); // Still 1, not 2
      assertEquals(1, vcrModel.getCacheHits());
    }
  }

  @Nested
  @DisplayName("List of Messages")
  class ListOfMessages {

    @Test
    @DisplayName("should handle list of messages in RECORD mode")
    void shouldHandleListOfMessagesInRecordMode() {
      vcrModel.setMode(VCRMode.RECORD);

      List<ChatMessage> messages = List.of(UserMessage.from("First"), UserMessage.from("Second"));

      Response<AiMessage> response = vcrModel.generate(messages);

      assertNotNull(response);
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached response for list of messages")
    void shouldReturnCachedForListOfMessages() {
      vcrModel.setMode(VCRMode.PLAYBACK);
      vcrModel.preloadCassette("vcr:chat:VCRChatModelTest.test:0001", "List cached response");

      List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

      Response<AiMessage> response = vcrModel.generate(messages);

      assertEquals("List cached response", response.content().text());
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("String Convenience Method")
  class StringConvenienceMethod {

    @Test
    @DisplayName("should handle string input in RECORD mode")
    void shouldHandleStringInputInRecordMode() {
      vcrModel.setMode(VCRMode.RECORD);

      String response = vcrModel.generate("Simple string input");

      assertNotNull(response);
      assertTrue(response.contains("Mock response"));
      assertEquals(1, mockDelegate.callCount.get());
      assertEquals(1, vcrModel.getRecordedCount());
    }

    @Test
    @DisplayName("should return cached response for string input")
    void shouldReturnCachedForStringInput() {
      vcrModel.setMode(VCRMode.PLAYBACK);
      vcrModel.preloadCassette("vcr:chat:VCRChatModelTest.test:0001", "String cached response");

      String response = vcrModel.generate("Test string");

      assertEquals("String cached response", response);
      assertEquals(0, mockDelegate.callCount.get());
    }
  }

  @Nested
  @DisplayName("Delegate Access")
  class DelegateAccess {

    @Test
    @DisplayName("should provide access to underlying delegate")
    void shouldProvideAccessToDelegate() {
      ChatLanguageModel delegate = vcrModel.getDelegate();

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
      vcrModel.generate(UserMessage.from("Test")); // Cache miss

      assertEquals(1, vcrModel.getCacheMisses());

      vcrModel.resetStatistics();

      assertEquals(0, vcrModel.getCacheHits());
      assertEquals(0, vcrModel.getCacheMisses());
    }
  }

  /** Mock ChatLanguageModel for testing. */
  static class MockChatLanguageModel implements ChatLanguageModel {
    AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
      callCount.incrementAndGet();
      String lastMessage = "";
      if (!messages.isEmpty()) {
        ChatMessage last = messages.get(messages.size() - 1);
        if (last instanceof UserMessage um) {
          lastMessage = um.singleText();
        }
      }
      return Response.from(AiMessage.from("Mock response to: " + lastMessage));
    }
  }
}

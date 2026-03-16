package com.redis.vl.extensions.messagehistory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

/**
 * Unit tests for count() method in MessageHistory. Ported from Python:
 * tests/integration/test_message_history.py (test_standard_count, test_semantic_count)
 */
class MessageHistoryCountTest {

  private UnifiedJedis mockJedis;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    mockJedis = mock(UnifiedJedis.class);
    // Mock ftCreate to not fail
    when(mockJedis.ftCreate(anyString(), any(), any(Iterable.class))).thenReturn("OK");
  }

  @Test
  void testCountReturnsZeroWhenEmpty() {
    // Mock ftSearch for count query to return 0 results
    SearchResult emptyResult = mock(SearchResult.class);
    when(emptyResult.getTotalResults()).thenReturn(0L);
    when(mockJedis.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
        .thenReturn(emptyResult);

    MessageHistory history = new MessageHistory("test_count", mockJedis);
    assertEquals(0, history.count());
  }

  @Test
  void testCountWithSessionTag() {
    SearchResult result = mock(SearchResult.class);
    when(result.getTotalResults()).thenReturn(4L);
    when(mockJedis.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
        .thenReturn(result);

    MessageHistory history = new MessageHistory("test_count", "session1", null, mockJedis);
    assertEquals(4, history.count("session1"));
  }

  @Test
  void testCountDefaultsToInstanceSessionTag() {
    SearchResult result = mock(SearchResult.class);
    when(result.getTotalResults()).thenReturn(2L);
    when(mockJedis.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
        .thenReturn(result);

    MessageHistory history = new MessageHistory("test_count", "my-session", null, mockJedis);

    // count() with no args should use instance session tag
    assertEquals(2, history.count());
  }

  @Test
  void testCountUsesCountQuery() {
    SearchResult result = mock(SearchResult.class);
    when(result.getTotalResults()).thenReturn(3L);
    when(mockJedis.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
        .thenReturn(result);

    MessageHistory history = new MessageHistory("test_count", "session1", null, mockJedis);
    long count = history.count();

    // Verify ftSearch was called with a filter containing the session tag
    verify(mockJedis, atLeastOnce())
        .ftSearch(anyString(), contains("session1"), any(FTSearchParams.class));
  }

  private static String contains(String substring) {
    return argThat(arg -> arg != null && arg.contains(substring));
  }
}

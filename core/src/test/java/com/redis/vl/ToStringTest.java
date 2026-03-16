package com.redis.vl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.redis.vl.extensions.cache.LangCacheSemanticCache;
import com.redis.vl.extensions.messagehistory.MessageHistory;
import com.redis.vl.extensions.messagehistory.SemanticMessageHistory;
import com.redis.vl.extensions.router.SemanticRouter;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.TagField;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;

/**
 * Unit tests for toString() methods on core public classes.
 *
 * <p>Ported from Python: commit e285fed - Add __repr__ methods
 *
 * <p>Python reference: tests/unit/test_repr.py
 */
@DisplayName("toString() Tests")
class ToStringTest {

  @Test
  @DisplayName("SearchIndex: toString should include name, prefix, and storage_type")
  void testSearchIndexToString() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("test_index")
            .prefix("test_prefix")
            .storageType(IndexSchema.StorageType.HASH)
            .field(new TagField("tag1"))
            .build();

    SearchIndex index = new SearchIndex(schema);

    String repr = index.toString();

    assertThat(repr).contains("SearchIndex");
    assertThat(repr).contains("test_index");
    assertThat(repr).contains("test_prefix");
    assertThat(repr).contains("HASH");
  }

  @Test
  @DisplayName("SearchIndex: toString with JSON storage")
  void testSearchIndexToStringJson() {
    IndexSchema schema =
        IndexSchema.builder()
            .name("json_index")
            .prefix("json_prefix")
            .storageType(IndexSchema.StorageType.JSON)
            .field(new TagField("tag1"))
            .build();

    SearchIndex index = new SearchIndex(schema);

    String repr = index.toString();

    assertThat(repr).contains("json_index");
    assertThat(repr).contains("JSON");
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("MessageHistory: toString should include name and session_tag")
  void testMessageHistoryToString() {
    UnifiedJedis mockJedis = mock(UnifiedJedis.class);
    when(mockJedis.ftCreate(anyString(), any(), any(Iterable.class))).thenReturn("OK");

    MessageHistory history = new MessageHistory("chat_history", "session123", null, mockJedis);

    String repr = history.toString();

    assertThat(repr).contains("MessageHistory");
    assertThat(repr).contains("chat_history");
    assertThat(repr).contains("session123");
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName(
      "SemanticMessageHistory: toString should include name, session_tag, and distance_threshold")
  void testSemanticMessageHistoryToString() {
    UnifiedJedis mockJedis = mock(UnifiedJedis.class);
    when(mockJedis.ftCreate(anyString(), any(), any(Iterable.class))).thenReturn("OK");

    BaseVectorizer mockVectorizer = mock(BaseVectorizer.class);
    when(mockVectorizer.getDimensions()).thenReturn(384);
    when(mockVectorizer.getDataType()).thenReturn("float32");

    SemanticMessageHistory history =
        new SemanticMessageHistory("semantic_chat", "session456", null, mockVectorizer, mockJedis);

    String repr = history.toString();

    assertThat(repr).contains("SemanticMessageHistory");
    assertThat(repr).contains("semantic_chat");
    assertThat(repr).contains("session456");
    assertThat(repr).contains("0.3"); // default distance threshold
  }

  @Test
  @DisplayName("SemanticRouter: toString should include name and route count")
  void testSemanticRouterToString() {
    SemanticRouter router = new SemanticRouter("my_router");

    String repr = router.toString();

    assertThat(repr).contains("SemanticRouter");
    assertThat(repr).contains("my_router");
    assertThat(repr).contains("0"); // no routes
  }

  @Test
  @DisplayName("LangCacheSemanticCache: toString should include name and ttl")
  void testLangCacheSemanticCacheToString() {
    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("my_cache")
            .cacheId("cache-123")
            .apiKey("test-key")
            .ttl(3600)
            .build();

    String repr = cache.toString();

    assertThat(repr).contains("LangCacheSemanticCache");
    assertThat(repr).contains("my_cache");
    assertThat(repr).contains("3600");
  }
}

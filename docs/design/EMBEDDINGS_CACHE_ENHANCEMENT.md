# EmbeddingsCache Enhancement Plan

## Overview

This document outlines the plan to enhance the Java `EmbeddingsCache` to achieve feature parity with the Python `redis-vl-python` implementation.

## Current State Analysis

### Python Implementation (redis-vl-python)

| Feature | Implementation |
|---------|----------------|
| **Key Generation** | `SHA256(text:model_name)` - combined hash |
| **Storage** | Redis HASH with structured fields |
| **Fields Stored** | text, model_name, embedding, inserted_at, metadata |
| **TTL Behavior** | Refreshed on retrieval (LRU-like) |
| **Metadata** | Full JSON serialization support |
| **Batch Returns** | Ordered lists matching input order |
| **Async Support** | Full async/await variants |

### Java Implementation (Current)

| Feature | Implementation |
|---------|----------------|
| **Key Generation** | `model:SHA256(text)` - separate hash |
| **Storage** | Raw byte array via SET/GET |
| **Fields Stored** | embedding vector only |
| **TTL Behavior** | Set once at write, no refresh |
| **Metadata** | None |
| **Batch Returns** | Unordered Maps |
| **Async Support** | Synchronous only |

## Enhancement Plan

### Phase 1: Core Data Model

#### 1.1 Create CacheEntry Class

```java
package com.redis.vl.extensions.cache;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class EmbeddingCacheEntry {
    private String entryId;           // Unique identifier (hash)
    private String text;              // Original text that was embedded
    private String modelName;         // Embedding model name
    private float[] embedding;        // The embedding vector
    private Instant insertedAt;       // When the entry was cached
    private Map<String, Object> metadata;  // Optional user metadata
}
```

#### 1.2 Update Key Generation

Change from `model:hash(text)` to `hash(text:model)` for Python compatibility:

```java
private String generateKey(String text, String modelName) {
    String combined = text + ":" + modelName;
    byte[] hash = MessageDigest.getInstance("SHA-256")
        .digest(combined.getBytes(StandardCharsets.UTF_8));
    String entryId = bytesToHex(hash);
    return cacheName + ":" + entryId;
}
```

### Phase 2: Storage Enhancement

#### 2.1 Switch to Redis HASH Storage

Replace `SET`/`GET` with `HSET`/`HGETALL`:

```java
public void set(String text, String modelName, float[] embedding,
                Map<String, Object> metadata) {
    String key = generateKey(text, modelName);

    Map<String, String> fields = new HashMap<>();
    fields.put("entry_id", extractEntryId(key));
    fields.put("text", text);
    fields.put("model_name", modelName);
    fields.put("embedding", serializeEmbedding(embedding));
    fields.put("inserted_at", String.valueOf(Instant.now().toEpochMilli()));

    if (metadata != null && !metadata.isEmpty()) {
        fields.put("metadata", objectMapper.writeValueAsString(metadata));
    }

    jedis.hset(key, fields);

    if (ttl > 0) {
        jedis.expire(key, ttl);
    }
}
```

#### 2.2 Implement Structured Retrieval

```java
public Optional<EmbeddingCacheEntry> get(String text, String modelName) {
    String key = generateKey(text, modelName);
    Map<String, String> fields = jedis.hgetAll(key);

    if (fields.isEmpty()) {
        return Optional.empty();
    }

    // Refresh TTL on access (LRU behavior)
    if (ttl > 0) {
        jedis.expire(key, ttl);
    }

    return Optional.of(deserializeEntry(fields));
}

private EmbeddingCacheEntry deserializeEntry(Map<String, String> fields) {
    return EmbeddingCacheEntry.builder()
        .entryId(fields.get("entry_id"))
        .text(fields.get("text"))
        .modelName(fields.get("model_name"))
        .embedding(deserializeEmbedding(fields.get("embedding")))
        .insertedAt(Instant.ofEpochMilli(Long.parseLong(fields.get("inserted_at"))))
        .metadata(fields.containsKey("metadata")
            ? objectMapper.readValue(fields.get("metadata"), Map.class)
            : null)
        .build();
}
```

### Phase 3: TTL Enhancement

#### 3.1 TTL Refresh on Access

Add TTL refresh in all retrieval methods:

```java
public Optional<EmbeddingCacheEntry> get(String text, String modelName) {
    String key = generateKey(text, modelName);
    Map<String, String> fields = jedis.hgetAll(key);

    if (fields.isEmpty()) {
        return Optional.empty();
    }

    // LRU-like behavior: refresh TTL on access
    refreshTTL(key);

    return Optional.of(deserializeEntry(fields));
}

private void refreshTTL(String key) {
    if (ttl > 0) {
        jedis.expire(key, ttl);
    }
}
```

### Phase 4: Batch Operations Enhancement

#### 4.1 Ordered Batch Retrieval

Return `List<Optional<EmbeddingCacheEntry>>` to preserve order:

```java
public List<Optional<EmbeddingCacheEntry>> mget(List<String> texts, String modelName) {
    List<String> keys = texts.stream()
        .map(text -> generateKey(text, modelName))
        .collect(Collectors.toList());

    List<Optional<EmbeddingCacheEntry>> results = new ArrayList<>(texts.size());

    try (Pipeline pipeline = jedis.pipelined()) {
        List<Response<Map<String, String>>> responses = new ArrayList<>();

        for (String key : keys) {
            responses.add(pipeline.hgetAll(key));
        }
        pipeline.sync();

        // Refresh TTL for hits
        for (int i = 0; i < keys.size(); i++) {
            Map<String, String> fields = responses.get(i).get();
            if (!fields.isEmpty()) {
                refreshTTL(keys.get(i));
                results.add(Optional.of(deserializeEntry(fields)));
            } else {
                results.add(Optional.empty());
            }
        }
    }

    return results;
}
```

#### 4.2 Batch Existence Check

```java
public List<Boolean> mexists(List<String> texts, String modelName) {
    List<String> keys = texts.stream()
        .map(text -> generateKey(text, modelName))
        .collect(Collectors.toList());

    try (Pipeline pipeline = jedis.pipelined()) {
        List<Response<Boolean>> responses = keys.stream()
            .map(pipeline::exists)
            .collect(Collectors.toList());
        pipeline.sync();

        return responses.stream()
            .map(Response::get)
            .collect(Collectors.toList());
    }
}
```

### Phase 5: Backward Compatibility

#### 5.1 Simple API Methods

Maintain simple methods for users who don't need full features:

```java
// Simple API (returns just embedding)
public Optional<float[]> getEmbedding(String text, String modelName) {
    return get(text, modelName).map(EmbeddingCacheEntry::getEmbedding);
}

// Simple API (no metadata)
public void set(String text, String modelName, float[] embedding) {
    set(text, modelName, embedding, null);
}

// Legacy Map-based batch (for backward compat)
public Map<String, float[]> mgetAsMap(List<String> texts, String modelName) {
    List<Optional<EmbeddingCacheEntry>> results = mget(texts, modelName);
    Map<String, float[]> map = new LinkedHashMap<>();
    for (int i = 0; i < texts.size(); i++) {
        results.get(i).ifPresent(entry -> map.put(texts.get(i), entry.getEmbedding()));
    }
    return map;
}
```

### Phase 6: Embedding Serialization

#### 6.1 JSON-Compatible Float Array Serialization

```java
private String serializeEmbedding(float[] embedding) {
    // Store as JSON array for cross-language compatibility
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(embedding[i]);
    }
    sb.append("]");
    return sb.toString();
}

private float[] deserializeEmbedding(String serialized) {
    // Parse JSON array
    String content = serialized.substring(1, serialized.length() - 1);
    String[] parts = content.split(",");
    float[] result = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
        result[i] = Float.parseFloat(parts[i].trim());
    }
    return result;
}
```

## API Summary

### Enhanced EmbeddingsCache API

```java
public class EmbeddingsCache {
    // Constructor
    public EmbeddingsCache(String name, JedisPooled client, long ttlSeconds);

    // Full API (with metadata)
    public void set(String text, String modelName, float[] embedding,
                    Map<String, Object> metadata);
    public void set(String text, String modelName, float[] embedding,
                    Map<String, Object> metadata, long ttlOverride);
    public Optional<EmbeddingCacheEntry> get(String text, String modelName);

    // Simple API (just embeddings)
    public void set(String text, String modelName, float[] embedding);
    public Optional<float[]> getEmbedding(String text, String modelName);

    // Batch operations (ordered)
    public List<Optional<EmbeddingCacheEntry>> mget(List<String> texts, String modelName);
    public List<Boolean> mexists(List<String> texts, String modelName);
    public void mdrop(List<String> texts, String modelName);

    // Legacy batch (unordered map - backward compat)
    public Map<String, float[]> mgetAsMap(List<String> texts, String modelName);

    // Management
    public boolean exists(String text, String modelName);
    public boolean drop(String text, String modelName);
    public void clear();

    // Configuration
    public void setTTL(long ttlSeconds);
    public long getTTL();
}
```

## Testing Strategy

### Unit Tests

1. Test key generation matches Python format
2. Test serialization/deserialization of embeddings
3. Test metadata JSON handling
4. Test TTL refresh behavior

### Integration Tests

1. Test HASH storage structure in Redis
2. Test batch operation ordering
3. Test TTL expiration and refresh
4. Test cross-language compatibility (read Python-cached entries)

## Migration Path

1. **v1.0**: Add new methods alongside existing ones
2. **v1.1**: Deprecate old methods
3. **v2.0**: Remove deprecated methods

## Implementation Priority

| Priority | Feature | Effort |
|----------|---------|--------|
| P0 | CacheEntry class | Low |
| P0 | HASH storage | Medium |
| P1 | TTL refresh | Low |
| P1 | Ordered batch returns | Medium |
| P2 | Metadata support | Low |
| P2 | Cross-language key compat | Low |

## Success Criteria

- [ ] All Python API methods have Java equivalents
- [ ] TTL refreshed on cache access
- [ ] Batch operations preserve input order
- [ ] Metadata can be stored and retrieved
- [ ] Existing tests continue to pass
- [ ] New tests cover all enhanced functionality

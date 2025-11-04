package com.redis.vl.langchain4j;

import static com.redis.vl.utils.Utils.normCosineDistance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.VectorField;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.*;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.SearchResult;

/**
 * LangChain4J EmbeddingStore implementation using RedisVL as the backend.
 *
 * <p>This adapter allows using Redis as a vector store for LangChain4J applications, providing
 * seamless integration between LangChain4J's RAG framework and RedisVL's vector search
 * capabilities.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create Redis index
 * SearchIndex index = createEmbeddingIndex("my_embeddings", 384);
 *
 * // Create embedding store
 * EmbeddingStore<TextSegment> embeddingStore = new RedisVLEmbeddingStore(index);
 *
 * // Use with LangChain4J
 * EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
 * embeddingStore.add(embeddingModel.embed("Hello").content(),
 *                    TextSegment.from("Hello world"));
 *
 * // Search
 * List<EmbeddingMatch<TextSegment>> matches =
 *     embeddingStore.findRelevant(queryEmbedding, 10);
 * }</pre>
 *
 * <p><strong>Score Conversion:</strong> Redis uses COSINE distance (0-2, lower is better), while
 * LangChain4J uses similarity scores (0-1, higher is better). This class handles the conversion
 * automatically using {@code similarity = (2 - distance) / 2}.
 */
public class RedisVLEmbeddingStore implements EmbeddingStore<TextSegment> {

  private final SearchIndex searchIndex;
  private final ObjectMapper objectMapper;
  private final String textFieldName;
  private final String metadataFieldName;
  private final String vectorFieldName;

  /**
   * Creates a new RedisVLEmbeddingStore with default field names.
   *
   * @param searchIndex The Redis search index to use for storage
   */
  public RedisVLEmbeddingStore(SearchIndex searchIndex) {
    this(searchIndex, "text", "metadata", "vector");
  }

  /**
   * Creates a new RedisVLEmbeddingStore with custom field names.
   *
   * @param searchIndex The Redis search index to use for storage
   * @param textFieldName Name of the field storing text content
   * @param metadataFieldName Name of the field storing metadata (JSON)
   * @param vectorFieldName Name of the field storing embeddings
   */
  public RedisVLEmbeddingStore(
      SearchIndex searchIndex,
      String textFieldName,
      String metadataFieldName,
      String vectorFieldName) {
    this.searchIndex = searchIndex;
    this.textFieldName = textFieldName;
    this.metadataFieldName = metadataFieldName;
    this.objectMapper = new ObjectMapper();

    // For JSON storage, find the actual vector field name from the schema
    // Prefer alias if available (to avoid JSONPath special characters in queries)
    // otherwise fall back to field name (e.g., "vector" -> "$.vector")
    String actualVectorFieldName = vectorFieldName;
    if (searchIndex.getSchema().getStorageType()
        == com.redis.vl.schema.IndexSchema.StorageType.JSON) {
      // Find the VectorField in the schema
      for (com.redis.vl.schema.BaseField field : searchIndex.getSchema().getFields()) {
        if (field instanceof com.redis.vl.schema.VectorField) {
          // Use alias if available (preferred for queries to avoid JSONPath syntax issues)
          if (field.getAlias() != null && !field.getAlias().isEmpty()) {
            actualVectorFieldName = field.getAlias();
          } else {
            actualVectorFieldName = field.getName();
          }
          break;
        }
      }
    }
    this.vectorFieldName = actualVectorFieldName;
  }

  @Override
  public String add(Embedding embedding) {
    if (embedding == null) {
      throw new IllegalArgumentException("Embedding cannot be null");
    }
    return add(embedding, null);
  }

  @Override
  public void add(String id, Embedding embedding) {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("ID cannot be null or empty");
    }
    if (embedding == null) {
      throw new IllegalArgumentException("Embedding cannot be null");
    }
    addWithId(id, embedding, null);
  }

  /**
   * Adds an embedding with a specified ID and text segment.
   *
   * @param id The unique identifier for the embedding
   * @param embedding The embedding vector
   * @param textSegment The text segment with metadata
   */
  public void add(String id, Embedding embedding, TextSegment textSegment) {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("ID cannot be null or empty");
    }
    if (embedding == null) {
      throw new IllegalArgumentException("Embedding cannot be null");
    }
    addWithId(id, embedding, textSegment);
  }

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {
    if (embedding == null) {
      throw new IllegalArgumentException("Embedding cannot be null");
    }
    String id = UUID.randomUUID().toString();
    addWithId(id, embedding, textSegment);
    return id;
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    if (embeddings == null || embeddings.isEmpty()) {
      return Collections.emptyList();
    }
    return addAll(embeddings, Collections.nCopies(embeddings.size(), null));
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
    if (embeddings == null || embeddings.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> ids = new ArrayList<>();
    List<Map<String, Object>> documents = new ArrayList<>();

    for (int i = 0; i < embeddings.size(); i++) {
      String id = UUID.randomUUID().toString();
      ids.add(id);

      Embedding embedding = embeddings.get(i);
      TextSegment segment =
          textSegments != null && i < textSegments.size() ? textSegments.get(i) : null;

      Map<String, Object> doc = createDocument(id, embedding, segment);
      documents.add(doc);
    }

    // Batch insert using RedisVL
    searchIndex.load(documents, "id");

    return ids;
  }

  @Override
  @SuppressWarnings("removal")
  public List<EmbeddingMatch<TextSegment>> findRelevant(
      Embedding referenceEmbedding, int maxResults) {
    return findRelevant(referenceEmbedding, maxResults, 0.0);
  }

  @Override
  public dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> search(
      dev.langchain4j.store.embedding.EmbeddingSearchRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Search request cannot be null");
    }

    Embedding referenceEmbedding = request.queryEmbedding();
    if (referenceEmbedding == null) {
      throw new IllegalArgumentException("Query embedding cannot be null");
    }

    // Convert minScore (0-1 similarity) to maxDistance (0-2 cosine distance)
    float maxDistance = 2.0f - 2.0f * (float) request.minScore();

    // Build vector query with optional filter support
    VectorQuery.Builder queryBuilder =
        VectorQuery.builder()
            .field(vectorFieldName)
            .vector(referenceEmbedding.vector())
            .numResults(request.maxResults())
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .returnDistance(true);

    // Map LangChain4J filter to RedisVL filter if present
    if (request.filter() != null) {
      com.redis.vl.query.Filter redisFilter = LangChain4JFilterMapper.map(request.filter());
      queryBuilder.preFilter(redisFilter.build());
    }

    VectorQuery query = queryBuilder.build();

    // Execute search
    SearchResult searchResult = searchIndex.search(query);

    // Convert results to EmbeddingMatch objects
    List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
    for (Document doc : searchResult.getDocuments()) {
      // Get vector distance (0-2 range for COSINE)
      float distance = getDistance(doc);

      // Skip if distance exceeds threshold
      if (distance > maxDistance) {
        continue;
      }

      // Convert distance to similarity score (0-1 range)
      double score = normCosineDistance(distance);

      // Parse text segment
      TextSegment textSegment = parseTextSegment(doc);

      // Create match
      EmbeddingMatch<TextSegment> match =
          new EmbeddingMatch<>(score, doc.getId(), null, textSegment);
      matches.add(match);
    }

    return new dev.langchain4j.store.embedding.EmbeddingSearchResult<>(matches);
  }

  @Override
  @SuppressWarnings("removal")
  public List<EmbeddingMatch<TextSegment>> findRelevant(
      Embedding referenceEmbedding, int maxResults, double minScore) {
    // Delegate to new search() method
    dev.langchain4j.store.embedding.EmbeddingSearchRequest request =
        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
            .queryEmbedding(referenceEmbedding)
            .maxResults(maxResults)
            .minScore(minScore)
            .build();

    return search(request).matches();
  }

  @Override
  public void removeAll(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }

    // Get the key prefix from the search index
    String prefix = searchIndex.getSchema().getPrefix();

    // Build full Redis keys
    List<String> keys =
        ids.stream().map(id -> prefix + id).collect(java.util.stream.Collectors.toList());

    // Delete from Redis using SearchIndex API
    searchIndex.dropKeys(keys);
  }

  @Override
  public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
    if (filter == null) {
      throw new IllegalArgumentException("Filter cannot be null");
    }

    // Map LangChain4J filter to RedisVL filter
    com.redis.vl.query.Filter redisFilter = LangChain4JFilterMapper.map(filter);

    // Search for documents matching the filter
    // Use a simple query with large limit to get all matching documents
    String filterQuery = redisFilter.build();
    SearchResult searchResult = searchIndex.search(filterQuery);

    // Extract document IDs
    List<String> idsToDelete = new ArrayList<>();
    for (Document doc : searchResult.getDocuments()) {
      // Remove prefix from full key to get just the ID
      String fullKey = doc.getId();
      String prefix = searchIndex.getSchema().getPrefix();
      if (fullKey.startsWith(prefix)) {
        idsToDelete.add(fullKey.substring(prefix.length()));
      } else {
        idsToDelete.add(fullKey);
      }
    }

    // Delete the matching documents if any found
    if (!idsToDelete.isEmpty()) {
      removeAll(idsToDelete);
    }
  }

  @Override
  public void removeAll() {
    // Use SearchIndex's clear() method to delete all documents
    searchIndex.clear();
  }

  /**
   * Helper method to add a document with a specific ID.
   *
   * @param id The document ID
   * @param embedding The embedding vector
   * @param textSegment Optional text segment with content and metadata
   */
  private void addWithId(String id, Embedding embedding, TextSegment textSegment) {
    Map<String, Object> doc = createDocument(id, embedding, textSegment);
    searchIndex.load(List.of(doc), "id");
  }

  /**
   * Creates a document map for Redis storage.
   *
   * @param id The document ID
   * @param embedding The embedding vector
   * @param textSegment Optional text segment
   * @return Document map ready for Redis storage
   */
  private Map<String, Object> createDocument(
      String id, Embedding embedding, TextSegment textSegment) {
    Map<String, Object> doc = new HashMap<>();
    doc.put("id", id);
    doc.put(vectorFieldName, embedding.vector());

    if (textSegment != null) {
      // Store text content
      if (textSegment.text() != null) {
        doc.put(textFieldName, textSegment.text());
      }

      // Store metadata - both as JSON string AND as individual fields for filtering
      if (textSegment.metadata() != null && !textSegment.metadata().toMap().isEmpty()) {
        Map<String, Object> metadataMap = textSegment.metadata().toMap();

        // Store as JSON string for retrieval
        try {
          String metadataJson = objectMapper.writeValueAsString(metadataMap);
          doc.put(metadataFieldName, metadataJson);
        } catch (JsonProcessingException e) {
          throw new RuntimeException("Failed to serialize metadata to JSON", e);
        }

        // Also store individual metadata fields flat in document for filtering
        // The "$." prefix is only used in schema definition, not in field names
        doc.putAll(metadataMap);
      }
    }

    return doc;
  }

  /**
   * Parses a TextSegment from a Redis document.
   *
   * @param doc The Redis document
   * @return TextSegment with content and metadata, or null if no text
   */
  private TextSegment parseTextSegment(Document doc) {
    // For JSON storage, vector queries return the entire JSON document under a "$" key
    // We need to parse it to extract individual fields
    Object dollarField = doc.get("$");
    String text = null;
    String metadataJson = null;

    if (dollarField != null
        && searchIndex.getSchema().getStorageType()
            == com.redis.vl.schema.IndexSchema.StorageType.JSON) {
      // Parse the JSON document
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonDoc = objectMapper.readValue(dollarField.toString(), Map.class);
        text = (String) jsonDoc.get(textFieldName);
        metadataJson = (String) jsonDoc.get(metadataFieldName);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to parse JSON document from vector query result", e);
      }
    } else {
      // For HASH storage or non-vector queries, fields are at document level
      text = doc.getString(textFieldName);
      metadataJson = doc.getString(metadataFieldName);
    }

    if (text == null || text.isEmpty()) {
      return null;
    }

    // Parse metadata from JSON
    Metadata metadata = new Metadata();
    if (metadataJson != null && !metadataJson.isEmpty()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadataMap = objectMapper.readValue(metadataJson, Map.class);
        for (Map.Entry<String, Object> entry : metadataMap.entrySet()) {
          String key = entry.getKey();
          Object value = entry.getValue();
          // Put based on type
          if (value instanceof String) {
            metadata.put(key, (String) value);
          } else if (value instanceof Integer) {
            metadata.put(key, (Integer) value);
          } else if (value instanceof Long) {
            metadata.put(key, (Long) value);
          } else if (value instanceof Float) {
            metadata.put(key, (Float) value);
          } else if (value instanceof Double) {
            metadata.put(key, (Double) value);
          } else if (value != null) {
            metadata.put(key, value.toString());
          }
        }
      } catch (JsonProcessingException e) {
        // Log warning but continue - metadata is optional
        System.err.println("Warning: Failed to parse metadata JSON: " + e.getMessage());
      }
    }

    return TextSegment.from(text, metadata);
  }

  /**
   * Extracts the vector distance from a search result document.
   *
   * @param doc The Redis document
   * @return The vector distance (0-2 for COSINE metric)
   */
  private float getDistance(Document doc) {
    // Try to get vector_distance field
    Object distanceObj = doc.get("vector_distance");
    if (distanceObj != null) {
      if (distanceObj instanceof Number) {
        return ((Number) distanceObj).floatValue();
      } else if (distanceObj instanceof String) {
        return Float.parseFloat((String) distanceObj);
      }
    }

    // Fallback to score if vector_distance not available
    // Note: Document score might not be the same as distance
    if (doc.getScore() != null) {
      // Assuming score is similarity (0-1), convert to distance
      return 2.0f - 2.0f * doc.getScore().floatValue();
    }

    // Default to maximum distance if nothing available
    return 2.0f;
  }

  /**
   * Gets the underlying SearchIndex.
   *
   * @return The Redis search index
   */
  public SearchIndex getSearchIndex() {
    return searchIndex;
  }
}

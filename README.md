# RedisVL

The Java client for AI-native applications with Redis.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Language](https://img.shields.io/badge/language-Java-orange)

## Introduction

RedisVL is a Java library for building AI-native applications on Redis, providing:

- Vector similarity search
- Hybrid queries combining vectors and metadata filters
- Support for both Hash and JSON storage
- Schema-based index management

## Getting Started

### Installation

Add RedisVL to your project:

**Maven:**

```xml
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>redisvl</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle:**

```gradle
implementation 'com.redis:redisvl:0.1.0-SNAPSHOT'
```

### Setting up Redis

Run Redis Stack locally with Docker:

```bash
docker run -d --name redis-stack -p 6379:6379 -p 8001:8001 redis/redis-stack:latest
```

## Quick Start

### 1. Define an Index Schema

Create a schema using a Map:

```java
Map<String, Object> schema = Map.of(
    "index", Map.of(
        "name", "user-index",
        "prefix", "user",
        "storage_type", "json"
    ),
    "fields", List.of(
        Map.of("name", "name", "type", "tag"),
        Map.of("name", "age", "type", "numeric"),
        Map.of(
            "name", "embedding",
            "type", "vector",
            "attrs", Map.of(
                "dims", 3,
                "distance_metric", "cosine",
                "algorithm", "flat",
                "datatype", "float32"
            )
        )
    )
);
```

Or load from YAML:

```yaml
index:
  name: user-index
  prefix: user
  storage_type: json

fields:
  - name: name
    type: tag
  - name: age
    type: numeric
  - name: embedding
    type: vector
    attrs:
      dims: 3
      distance_metric: cosine
      algorithm: flat
      datatype: float32
```

```java
IndexSchema schema = IndexSchema.fromYaml("schema.yaml");
```

### 2. Create a Search Index

```java
import com.redis.vl.index.SearchIndex;
import redis.clients.jedis.UnifiedJedis;

UnifiedJedis jedis = new UnifiedJedis("redis://localhost:6379");
SearchIndex index = new SearchIndex(schema, jedis);

// Create the index in Redis
index.create(true); // true = overwrite if exists
```

### 3. Load Data

```java
List<Map<String, Object>> data = List.of(
    Map.of("name", "john", "age", 25, "embedding", new float[]{0.1f, 0.2f, 0.3f}),
    Map.of("name", "jane", "age", 30, "embedding", new float[]{0.4f, 0.5f, 0.6f})
);

// Load data with auto-generated IDs
List<String> keys = index.load(data);

// Or specify an ID field
List<String> keys = index.load(data, "name");
```

### 4. Vector Search

```java
import com.redis.vl.query.VectorQuery;

VectorQuery query = VectorQuery.builder()
    .vector(new float[]{0.15f, 0.25f, 0.35f})
    .field("embedding")
    .numResults(5)
    .returnFields("name", "age")
    .build();

List<Map<String, Object>> results = index.query(query);
```

### 5. Hybrid Queries

Combine vector search with metadata filters:

```java
import com.redis.vl.query.FilterQuery;

// Create filters
FilterQuery ageFilter = FilterQuery.numeric("age").between(20, 35);
FilterQuery nameFilter = FilterQuery.tag("name", "john");

// Combine filters
FilterQuery combined = FilterQuery.and(ageFilter, nameFilter);

// Add filter to vector query
VectorQuery hybridQuery = VectorQuery.builder()
    .vector(new float[]{0.15f, 0.25f, 0.35f})
    .field("embedding")
    .withPreFilter(combined.build())
    .numResults(5)
    .build();

List<Map<String, Object>> results = index.query(hybridQuery);
```

## Storage Types

### Hash Storage

Best for performance and memory efficiency:

```java
Map<String, Object> hashSchema = Map.of(
    "index", Map.of(
        "name", "my-hash-index",
        "storage_type", "hash"
    ),
    // ... fields
);
```

**Note:** Vectors in Hash storage must be byte arrays.

### JSON Storage

Best for flexibility and nested data:

```java
Map<String, Object> jsonSchema = Map.of(
    "index", Map.of(
        "name", "my-json-index",
        "storage_type", "json"
    ),
    // ... fields
);
```

**Note:**

- Vectors in JSON storage must be float arrays
- Field names automatically get `$.` prefix for JSON paths
- Supports nested objects with custom paths

## Query Types

### VectorQuery

K-nearest neighbor search with optional filters:

```java
VectorQuery.builder()
    .vector(queryVector)
    .field("embedding")
    .numResults(10)
    .returnFields("field1", "field2")
    .withPreFilter(filter)
    .build();
```

### FilterQuery

Build complex filter expressions:

```java
// Tag filters
FilterQuery.tag("status", "active");
FilterQuery.tagOneOf("category", "A", "B", "C");

// Numeric filters
FilterQuery.numeric("price").between(10, 100);
FilterQuery.numeric("age").gt(18);

// Text filters
FilterQuery.text("description", "redis");
FilterQuery.prefix("title", "Hello");

// Combine filters
FilterQuery.and(filter1, filter2);
FilterQuery.or(filter1, filter2);
FilterQuery.not(filter);
```

## Index Operations

```java
// Check if index exists
boolean exists = index.exists();

// Get index info
Map<String, Object> info = index.info();

// Fetch a document
Map<String, Object> doc = index.fetch("doc-id");

// Delete documents
index.dropKeys("key1", "key2");

// Delete index (with or without data)
index.delete(true); // true = also delete data
```

## Pagination

Handle large result sets efficiently:

```java
import com.redis.vl.utils.Paginator;

Paginator paginator = new Paginator(index, query);

// Iterate through pages
while (paginator.hasNext()) {
    List<Map<String, Object>> page = paginator.next();
    // Process page
}

// Or get all results
List<Map<String, Object>> allResults = paginator.all();
```

## Examples

Check out the [notebooks](notebooks/) directory for Jupyter notebook examples:

- [Getting Started](notebooks/01_getting_started.ipynb)
- [Hybrid Queries](notebooks/02_hybrid_queries.ipynb)
- [Semantic Cache](notebooks/03_llmcache.ipynb)
- [Recommendations](notebooks/04_recommendations.ipynb)
- [Hash vs JSON Storage](notebooks/05_hash_vs_json.ipynb)

### Running the Notebooks

The notebooks are available as Jupyter notebooks with Java kernel support. To run them locally:

1. **Start the notebook environment:**
   ```bash
   docker compose up -d
   ```

2. **Access Jupyter Lab:**

   Navigate to [http://localhost:8888/](http://localhost:8888/) in your web browser.

   The notebooks will be available in the file browser on the left side.

3. **Stop the environment when done:**
   ```bash
   docker compose down
   ```

**Note:** The Docker environment includes:

- Jupyter Lab with Java kernel (JJava)
- Redis Stack container
- Pre-installed RedisVL library
- All required dependencies

## Requirements

- Java 17+
- Redis Stack 7.2+ or Redis with RediSearch module

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

This project is supported by Redis, Inc. on a good faith effort basis. To report bugs or request features, please [file an issue](https://github.com/redis/redisvl/issues).

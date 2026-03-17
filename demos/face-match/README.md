# Celebrity Face Match Demo

A JavaFX 3D visualization demo showcasing RedisVL4J vector similarity search with face recognition.

## Features

- **Real Celebrity Face Recognition**: Uses ArcFace ResNet100 ONNX model for face embeddings
- **3D Visualization**: t-SNE dimensionality reduction to display celebrity faces in 3D space
- **Drag-and-Drop Face Matching**: Drop any face image to find similar celebrities
- **Redis Vector Search**: Powered by Redis 8.2 with SVS-VAMANA algorithm support
- **Persistent Embeddings**: Embeddings are cached in Redis for fast subsequent launches

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Gradle 8.x

## Quick Start

### 1. Start the Demo

The simplest way to run the demo:

```bash
./gradlew :demos:face-match:run
```

This will:
1. Automatically start Redis 8.2 on port 6380 (via Docker Compose)
2. Generate face embeddings from 11,598 celebrity images (first run only)
3. Index embeddings in Redis with persistence
4. Launch the JavaFX 3D visualization

**First launch**: Takes 5-10 minutes to generate and index all embeddings. Subsequent launches are instant thanks to Redis persistence.

### 2. Use the Demo

1. **Explore**: Use mouse to rotate/pan the 3D celebrity face cloud
2. **Drag & Drop**: Drop a face image onto the 3D scene
3. **Match**: See the top 5 similar celebrities highlighted with connecting lines
4. **Click**: Click any celebrity thumbnail for details

## Gradle Tasks

### Redis Management

```bash
# Start Redis container
./gradlew :demos:face-match:redisUp

# Stop Redis container
./gradlew :demos:face-match:redisDown

# View Redis logs
./gradlew :demos:face-match:redisLogs

# Reset Redis data (removes all cached embeddings)
./gradlew :demos:face-match:redisReset
```

### Demo Tasks

```bash
# Run the demo (auto-starts Redis)
./gradlew :demos:face-match:run

# Pre-generate embeddings only (useful for testing)
./gradlew :demos:face-match:generateEmbeddings

# Inspect ArcFace ONNX model details
./gradlew :demos:face-match:inspectModel
```

## Redis Configuration

The demo uses **Redis 8.2** with:
- **Port**: 6380 (non-default to avoid conflicts)
- **Persistence**: RDB + AOF for durability
- **Vector Search**: Built-in SVS-VAMANA algorithm support
- **Memory**: 2GB max with allkeys-lru eviction policy

### Docker Compose

The Redis instance is defined in `docker-compose.yml`:

```yaml
services:
  redis:
    image: redis:8.2
    ports:
      - "6380:6379"
    volumes:
      - redis-data:/data
    command: redis-server /usr/local/etc/redis/redis.conf
```

Configuration file: `redis.conf` (includes persistence, memory limits, etc.)

### Custom Redis Connection

Override the default connection settings:

```bash
# Use environment variables
REDIS_HOST=myhost REDIS_PORT=6380 ./gradlew :demos:face-match:run

# Or system properties
./gradlew :demos:face-match:run -Dredis.host=myhost -Dredis.port=6380
```

## Architecture

### Components

- **ArcFace ResNet100 ONNX**: Face recognition model (512-dimensional embeddings)
- **DJL Face Vectorizer**: RedisVL vectorizer implementation
- **Celebrity Index Service**: Redis vector index management
- **Dimensionality Reduction**: t-SNE for 3D visualization
- **JavaFX 3D**: Interactive 3D scene with thumbnails

### Data Flow

1. Load celebrity images from `src/main/resources/static/images/celebs/`
2. Generate 512-dim face embeddings via ArcFace model
3. Index embeddings in Redis with HNSW algorithm
4. Reduce to 3D via t-SNE for visualization
5. On image drop: generate query embedding → vector search → display matches

### Redis Index Schema

```
Index: celebrity_faces
Prefix: celeb:
Fields:
  - id (TAG)
  - name (TEXT)
  - imageUrl (TEXT)
  - embedding (VECTOR: 512 dims, HNSW, COSINE distance)
```

## Dataset

- **11,598 celebrity face images** (from IMDb celebrity dataset)
- Images stored in `src/main/resources/static/images/celebs/img_*.jpg`
- Celebrity metadata in `src/main/resources/data/celeb_faces.csv`

## Performance

### First Launch
- Embedding generation: ~5-10 minutes (11,598 images × ~50ms each)
- Indexing in Redis: ~30 seconds
- t-SNE reduction: ~10 seconds
- **Total**: ~10 minutes

### Subsequent Launches
- Load from Redis: ~3 seconds
- t-SNE reduction: ~10 seconds
- **Total**: ~15 seconds

## Troubleshooting

### Redis Connection Refused

```bash
# Check if Redis is running
docker ps | grep redisvl4j-face-match-redis

# Start Redis manually
./gradlew :demos:face-match:redisUp
```

### Out of Memory

Increase test heap size in `build.gradle.kts`:

```kotlin
tasks.test {
    maxHeapSize = "4g"  // Increase if needed
}
```

### Missing Celebrity Images

Images should be in `src/main/resources/static/images/celebs/`. If missing:

```bash
# Check image count
find src/main/resources/static/images/celebs -name "*.jpg" | wc -l
# Should show: 11598
```

### Slow Embedding Generation

First launch is slow (~10 minutes). Subsequent launches use cached embeddings from Redis.

To reset cache:
```bash
./gradlew :demos:face-match:redisReset
```

## Development

### Run Tests

```bash
./gradlew :demos:face-match:test
```

### Code Quality

```bash
# Format code
./gradlew :demos:face-match:spotlessApply

# Run SpotBugs
./gradlew :demos:face-match:spotbugsMain

# Full build with checks
./gradlew :demos:face-match:build
```

## Future Enhancements

- [ ] SVS-VAMANA algorithm integration (Redis 8.2+)
- [ ] Real-time webcam face matching
- [ ] Multiple face detection in single image
- [ ] Export match results
- [ ] Custom celebrity dataset upload

## License

Part of RedisVL4J project. See root LICENSE file.

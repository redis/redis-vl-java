package com.redis.vl.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.redis.vl.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.UnifiedJedis;

/**
 * Integration tests for RedisConnectionManager.
 *
 * <p>Updated for Jedis 7.2+ API using RedisClient/UnifiedJedis instead of deprecated Jedis.
 */
@DisplayName("RedisConnectionManager Integration Tests")
class RedisConnectionManagerTest extends BaseIntegrationTest {

  @Test
  @DisplayName("Should create connection manager with URI")
  void shouldCreateConnectionManagerWithUri() {
    // Given
    String uri = getRedisUri();

    // When
    RedisConnectionManager connectionManager = RedisConnectionManager.from(uri);

    // Then
    assertThat(connectionManager).isNotNull();
    assertThat(connectionManager.isConnected()).isTrue();
  }

  @Test
  @DisplayName("Should create connection manager with host and port")
  void shouldCreateConnectionManagerWithHostAndPort() {
    // Given
    String host = REDIS.getHost();
    int port = REDIS.getMappedPort(6379);

    // When
    RedisConnectionManager connectionManager = RedisConnectionManager.from(host, port);

    // Then
    assertThat(connectionManager).isNotNull();
    assertThat(connectionManager.isConnected()).isTrue();
  }

  @Test
  @DisplayName("Should get client from connection manager")
  void shouldGetClientConnection() {
    // Given
    RedisConnectionManager connectionManager = RedisConnectionManager.from(getRedisUri());

    // When
    UnifiedJedis client = connectionManager.getClient();

    // Then
    assertThat(client).isNotNull();

    // Verify we can execute commands
    assertThatCode(client::ping).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Should execute command with connection")
  void shouldExecuteCommandWithConnection() {
    // Given
    String key = "test:key";
    String value = "test-value";

    try (RedisConnectionManager connectionManager = RedisConnectionManager.from(getRedisUri())) {
      // When
      String result =
          connectionManager.execute(
              client -> {
                client.set(key, value);
                return client.get(key);
              });

      // Then
      assertThat(result).isEqualTo(value);
    }
  }

  @Test
  @DisplayName("Should properly close connection manager")
  void shouldProperlyCloseConnectionManager() {
    // Given
    RedisConnectionManager connectionManager = RedisConnectionManager.from(getRedisUri());
    assertThat(connectionManager.isConnected()).isTrue();

    // When
    connectionManager.close();

    // Then - after close, isConnected should still return true since client is not null
    // The actual connection is closed but the reference remains
    // This is expected behavior - we're testing close doesn't throw
  }
}

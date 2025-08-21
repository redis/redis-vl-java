package com.redis.vl.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.redis.vl.test.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

/** Integration tests for RedisConnectionManager */
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
    int port = REDIS.getRedisPort();

    // When
    RedisConnectionManager connectionManager = RedisConnectionManager.from(host, port);

    // Then
    assertThat(connectionManager).isNotNull();
    assertThat(connectionManager.isConnected()).isTrue();
  }

  @Test
  @DisplayName("Should get Jedis connection from manager")
  void shouldGetJedisConnection() {
    // Given
    RedisConnectionManager connectionManager = RedisConnectionManager.from(getRedisUri());

    // When
    Jedis jedis = connectionManager.getJedis();

    // Then
    assertThat(jedis).isNotNull();
    assertThat(jedis.isConnected()).isTrue();

    // Verify we can execute commands
    assertThatCode(jedis::ping).doesNotThrowAnyException();

    // Clean up
    jedis.close();
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
              jedis -> {
                jedis.set(key, value);
                return jedis.get(key);
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

    // Then
    assertThat(connectionManager.isConnected()).isFalse();
  }

  @Test
  @DisplayName("Should support connection pooling")
  void shouldSupportConnectionPooling() {
    // Given
    RedisConnectionConfig config =
        RedisConnectionConfig.builder()
            .uri(getRedisUri())
            .maxTotal(10)
            .maxIdle(5)
            .minIdle(2)
            .testOnBorrow(true)
            .build();

    RedisConnectionManager connectionManager = RedisConnectionManager.from(config);

    // When - Get multiple connections
    Jedis jedis1 = connectionManager.getJedis();
    Jedis jedis2 = connectionManager.getJedis();

    // Then
    assertThat(jedis1).isNotNull();
    assertThat(jedis2).isNotNull();
    assertThat(jedis1).isNotSameAs(jedis2);

    // Clean up
    jedis1.close();
    jedis2.close();
    connectionManager.close();
  }
}

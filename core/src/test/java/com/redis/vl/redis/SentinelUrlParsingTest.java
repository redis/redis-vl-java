package com.redis.vl.redis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Redis Sentinel URL parsing (#213).
 *
 * <p>Tests URL format:
 * redis+sentinel://[username:password@]host1:port1,host2:port2/service_name[/db]
 *
 * <p>Ported from Python: tests/unit/test_sentinel_url.py
 *
 * <p>Python reference: PR #385 - Sentinel URL support
 */
@DisplayName("Sentinel URL Parsing Tests")
class SentinelUrlParsingTest {

  @Test
  @DisplayName("Should parse basic Sentinel URL with single host")
  void testParseSingleSentinelHost() {
    String url = "redis+sentinel://localhost:26379/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getSentinelHosts()).hasSize(1);
    assertThat(config.getSentinelHosts().get(0).getHost()).isEqualTo("localhost");
    assertThat(config.getSentinelHosts().get(0).getPort()).isEqualTo(26379);
    assertThat(config.getServiceName()).isEqualTo("mymaster");
    assertThat(config.getDatabase()).isNull();
    assertThat(config.getUsername()).isNull();
    assertThat(config.getPassword()).isNull();
  }

  @Test
  @DisplayName("Should parse Sentinel URL with multiple hosts")
  void testParseMultipleSentinelHosts() {
    String url = "redis+sentinel://sentinel1:26379,sentinel2:26380,sentinel3:26381/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getSentinelHosts()).hasSize(3);
    assertThat(config.getSentinelHosts().get(0).getHost()).isEqualTo("sentinel1");
    assertThat(config.getSentinelHosts().get(0).getPort()).isEqualTo(26379);
    assertThat(config.getSentinelHosts().get(1).getHost()).isEqualTo("sentinel2");
    assertThat(config.getSentinelHosts().get(1).getPort()).isEqualTo(26380);
    assertThat(config.getSentinelHosts().get(2).getHost()).isEqualTo("sentinel3");
    assertThat(config.getSentinelHosts().get(2).getPort()).isEqualTo(26381);
    assertThat(config.getServiceName()).isEqualTo("mymaster");
  }

  @Test
  @DisplayName("Should parse Sentinel URL with authentication")
  void testParseSentinelUrlWithAuth() {
    String url = "redis+sentinel://user:pass@localhost:26379/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getUsername()).isEqualTo("user");
    assertThat(config.getPassword()).isEqualTo("pass");
    assertThat(config.getServiceName()).isEqualTo("mymaster");
  }

  @Test
  @DisplayName("Should parse Sentinel URL with database number")
  void testParseSentinelUrlWithDatabase() {
    String url = "redis+sentinel://localhost:26379/mymaster/2";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getServiceName()).isEqualTo("mymaster");
    assertThat(config.getDatabase()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should parse Sentinel URL with all components")
  void testParseSentinelUrlComplete() {
    String url = "redis+sentinel://user:pass@sentinel1:26379,sentinel2:26380/myservice/3";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getSentinelHosts()).hasSize(2);
    assertThat(config.getUsername()).isEqualTo("user");
    assertThat(config.getPassword()).isEqualTo("pass");
    assertThat(config.getServiceName()).isEqualTo("myservice");
    assertThat(config.getDatabase()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should use default port 26379 when port is omitted")
  void testParseSentinelUrlDefaultPort() {
    String url = "redis+sentinel://sentinel1,sentinel2:26380/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getSentinelHosts()).hasSize(2);
    assertThat(config.getSentinelHosts().get(0).getHost()).isEqualTo("sentinel1");
    assertThat(config.getSentinelHosts().get(0).getPort()).isEqualTo(26379); // default
    assertThat(config.getSentinelHosts().get(1).getHost()).isEqualTo("sentinel2");
    assertThat(config.getSentinelHosts().get(1).getPort()).isEqualTo(26380);
  }

  @Test
  @DisplayName("Should use default service name 'mymaster' when omitted")
  void testParseSentinelUrlDefaultServiceName() {
    String url = "redis+sentinel://localhost:26379";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getServiceName()).isEqualTo("mymaster");
  }

  @Test
  @DisplayName("Should throw exception for invalid Sentinel URL scheme")
  void testParseSentinelUrlInvalidScheme() {
    String url = "redis://localhost:26379/mymaster";

    assertThatThrownBy(() -> SentinelConfig.fromUrl(url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("redis+sentinel");
  }

  @Test
  @DisplayName("Should throw exception for empty Sentinel hosts")
  void testParseSentinelUrlEmptyHosts() {
    String url = "redis+sentinel:///mymaster";

    assertThatThrownBy(() -> SentinelConfig.fromUrl(url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sentinel hosts");
  }

  @Test
  @DisplayName("Should handle IPv6 addresses in Sentinel URLs")
  void testParseSentinelUrlWithIPv6() {
    String url = "redis+sentinel://[::1]:26379,[::2]:26380/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getSentinelHosts()).hasSize(2);
    assertThat(config.getSentinelHosts().get(0).getHost()).isEqualTo("::1");
    assertThat(config.getSentinelHosts().get(0).getPort()).isEqualTo(26379);
    assertThat(config.getSentinelHosts().get(1).getHost()).isEqualTo("::2");
    assertThat(config.getSentinelHosts().get(1).getPort()).isEqualTo(26380);
  }

  @Test
  @DisplayName("Should parse Sentinel URL with password only (no username)")
  void testParseSentinelUrlPasswordOnly() {
    String url = "redis+sentinel://:secretpass@localhost:26379/mymaster";

    SentinelConfig config = SentinelConfig.fromUrl(url);

    assertThat(config.getUsername()).isNull();
    assertThat(config.getPassword()).isEqualTo("secretpass");
  }
}

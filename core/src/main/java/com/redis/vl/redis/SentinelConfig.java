package com.redis.vl.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Configuration for Redis Sentinel connections.
 *
 * <p>Supports the redis+sentinel:// URL scheme for high availability Redis deployments:
 * redis+sentinel://[username:password@]host1:port1,host2:port2/service_name[/db]
 *
 * <p>Python reference: redisvl/redis/connection.py - _parse_sentinel_url
 */
@Builder
public class SentinelConfig {

  /** List of Sentinel host:port pairs */
  @Singular private final List<HostPort> sentinelHosts;

  /** Sentinel service/master name (default: "mymaster") */
  @Getter @Builder.Default private final String serviceName = "mymaster";

  /** Redis database number (optional) */
  @Getter private final Integer database;

  /** Username for authentication (optional) */
  @Getter private final String username;

  /** Password for authentication (optional) */
  @Getter private final String password;

  /** Connection timeout in milliseconds */
  @Getter @Builder.Default private final int connectionTimeout = 2000;

  /** Socket timeout in milliseconds */
  @Getter @Builder.Default private final int socketTimeout = 2000;

  /**
   * Get an unmodifiable view of the Sentinel hosts list.
   *
   * @return Unmodifiable list of Sentinel host:port pairs
   */
  public List<HostPort> getSentinelHosts() {
    return Collections.unmodifiableList(sentinelHosts);
  }

  /**
   * Parse a Sentinel URL into a SentinelConfig.
   *
   * <p>URL format: redis+sentinel://[username:password@]host1:port1,host2:port2/service_name[/db]
   *
   * @param url Sentinel URL to parse
   * @return Parsed SentinelConfig
   * @throws IllegalArgumentException if URL is invalid
   */
  public static SentinelConfig fromUrl(String url) {
    if (url == null || !url.startsWith("redis+sentinel://")) {
      throw new IllegalArgumentException(
          "URL must start with redis+sentinel:// scheme. Got: " + url);
    }

    try {
      // Remove scheme prefix
      String remaining = url.substring("redis+sentinel://".length());

      // Extract username and password from userInfo (before @)
      String username = null;
      String password = null;
      String hostsString;

      int atIndex = remaining.indexOf("@");
      if (atIndex > 0) {
        String userInfo = remaining.substring(0, atIndex);
        remaining = remaining.substring(atIndex + 1);

        String[] userInfoParts = userInfo.split(":", 2);
        if (userInfoParts.length == 2) {
          username = userInfoParts[0].isEmpty() ? null : userInfoParts[0];
          password = userInfoParts[1].isEmpty() ? null : userInfoParts[1];
        } else if (userInfoParts.length == 1 && !userInfoParts[0].isEmpty()) {
          username = userInfoParts[0];
        }
      }

      // Extract hosts (before first /)
      int slashIndex = remaining.indexOf("/");
      if (slashIndex > 0) {
        hostsString = remaining.substring(0, slashIndex);
        remaining = remaining.substring(slashIndex);
      } else if (slashIndex == 0) {
        // No hosts before slash
        throw new IllegalArgumentException(
            "Sentinel hosts cannot be empty. URL must contain at least one host:port pair.");
      } else {
        // No path - everything is hosts
        hostsString = remaining;
        remaining = "";
      }

      if (hostsString.trim().isEmpty()) {
        throw new IllegalArgumentException(
            "Sentinel hosts cannot be empty. URL must contain at least one host:port pair.");
      }

      // Parse sentinel hosts (comma-separated)
      List<HostPort> sentinelHosts = parseSentinelHosts(hostsString);

      // Parse path for service name and database
      String serviceName = "mymaster"; // default
      Integer database = null;

      if (!remaining.isEmpty() && !remaining.equals("/")) {
        // Remove leading slash
        String path = remaining.substring(1);
        String[] pathParts = path.split("/");

        if (pathParts.length > 0 && !pathParts[0].isEmpty()) {
          serviceName = pathParts[0];
        }

        if (pathParts.length > 1 && !pathParts[1].isEmpty()) {
          try {
            database = Integer.parseInt(pathParts[1]);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid database number: " + pathParts[1], e);
          }
        }
      }

      return SentinelConfig.builder()
          .sentinelHosts(sentinelHosts)
          .serviceName(serviceName)
          .database(database)
          .username(username)
          .password(password)
          .build();

    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse Sentinel URL: " + url, e);
    }
  }

  /**
   * Parse comma-separated sentinel hosts into HostPort list.
   *
   * <p>Supports formats: - host:port - host (uses default port 26379) - [ipv6]:port - [ipv6] (uses
   * default port 26379)
   *
   * @param hostsString Comma-separated host:port pairs
   * @return List of HostPort objects
   */
  private static List<HostPort> parseSentinelHosts(String hostsString) {
    List<HostPort> hosts = new ArrayList<>();
    String[] hostParts = hostsString.split(",");

    for (String hostPart : hostParts) {
      hostPart = hostPart.trim();
      if (hostPart.isEmpty()) {
        continue;
      }

      hosts.add(parseHostPort(hostPart));
    }

    if (hosts.isEmpty()) {
      throw new IllegalArgumentException(
          "Sentinel hosts cannot be empty. URL must contain at least one host:port pair.");
    }

    return hosts;
  }

  /**
   * Parse a single host:port pair.
   *
   * <p>Handles IPv6 addresses in brackets: [::1]:26379
   *
   * @param hostPort Host and optional port
   * @return HostPort object
   */
  private static HostPort parseHostPort(String hostPort) {
    String host;
    int port = 26379; // default Sentinel port

    // Handle IPv6: [::1]:26379 or [::1]
    if (hostPort.startsWith("[")) {
      int closeBracket = hostPort.indexOf("]");
      if (closeBracket == -1) {
        throw new IllegalArgumentException("Invalid IPv6 address format: " + hostPort);
      }
      host = hostPort.substring(1, closeBracket);

      // Check for port after bracket
      if (closeBracket + 1 < hostPort.length()) {
        if (hostPort.charAt(closeBracket + 1) == ':') {
          try {
            port = Integer.parseInt(hostPort.substring(closeBracket + 2));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number in: " + hostPort, e);
          }
        }
      }
    } else {
      // Handle regular host:port or just host
      int colonIndex = hostPort.lastIndexOf(":");
      if (colonIndex > 0) {
        host = hostPort.substring(0, colonIndex);
        try {
          port = Integer.parseInt(hostPort.substring(colonIndex + 1));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid port number in: " + hostPort, e);
        }
      } else {
        host = hostPort;
      }
    }

    return new HostPort(host, port);
  }

  /** Represents a host:port pair for Sentinel nodes */
  @Getter
  public static final class HostPort {
    private final String host;
    private final int port;

    public HostPort(String host, int port) {
      if (host == null || host.trim().isEmpty()) {
        throw new IllegalArgumentException("Host cannot be null or empty");
      }
      if (port <= 0 || port > 65535) {
        throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
      }
      this.host = host.trim();
      this.port = port;
    }
  }
}

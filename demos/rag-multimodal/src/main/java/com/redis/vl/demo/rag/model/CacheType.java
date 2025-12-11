package com.redis.vl.demo.rag.model;

/**
 * Types of semantic caching available in the demo.
 */
public enum CacheType {
  /** No caching - always call LLM */
  NONE("No Cache", "Always call LLM, no caching"),

  /** Local Redis-based semantic cache */
  LOCAL("Local Cache", "Redis-based semantic cache (local)"),

  /** Cloud LangCache service */
  LANGCACHE("LangCache", "Cloud-hosted semantic cache service");

  private final String displayName;
  private final String description;

  CacheType(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return displayName;
  }
}

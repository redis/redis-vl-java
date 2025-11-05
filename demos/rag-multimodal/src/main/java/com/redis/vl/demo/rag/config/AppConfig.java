package com.redis.vl.demo.rag.config;

import com.redis.vl.demo.rag.model.LLMConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration loaded from application.properties.
 *
 * <p>Provides centralized access to configuration settings including API keys, Redis connection,
 * and LangCache settings.
 */
public class AppConfig {

  private final Properties properties;

  private static AppConfig instance;

  private AppConfig() {
    properties = new Properties();
    loadProperties();
  }

  /**
   * Gets the singleton instance of AppConfig.
   *
   * @return AppConfig instance
   */
  public static synchronized AppConfig getInstance() {
    if (instance == null) {
      instance = new AppConfig();
    }
    return instance;
  }

  private void loadProperties() {
    try (InputStream input =
        getClass().getClassLoader().getResourceAsStream("application.properties")) {
      if (input == null) {
        System.err.println(
            "Warning: application.properties not found. Using default values.");
        return;
      }
      properties.load(input);
    } catch (IOException e) {
      System.err.println("Error loading application.properties: " + e.getMessage());
    }
  }

  /**
   * Gets a property value.
   *
   * @param key Property key
   * @param defaultValue Default value if key not found
   * @return Property value
   */
  public String getProperty(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  /**
   * Gets Redis host.
   *
   * @return Redis host
   */
  public String getRedisHost() {
    return getProperty("redis.host", "localhost");
  }

  /**
   * Gets Redis port.
   *
   * @return Redis port
   */
  public int getRedisPort() {
    return Integer.parseInt(getProperty("redis.port", "6399"));
  }

  /**
   * Gets configured LLM provider.
   *
   * @return LLM provider
   */
  public LLMConfig.Provider getLLMProvider() {
    String provider = getProperty("llm.provider", "OPENAI");
    try {
      return LLMConfig.Provider.valueOf(provider.toUpperCase());
    } catch (IllegalArgumentException e) {
      System.err.println("Invalid provider: " + provider + ", using OPENAI");
      return LLMConfig.Provider.OPENAI;
    }
  }

  /**
   * Gets LLM configuration for the configured provider.
   *
   * @return LLMConfig
   */
  public LLMConfig getLLMConfig() {
    LLMConfig.Provider provider = getLLMProvider();
    String apiKey = getApiKeyForProvider(provider);
    String baseUrl = getBaseUrlForProvider(provider);
    String model = getModelForProvider(provider);
    double temperature = Double.parseDouble(getProperty(getProviderPrefix(provider) + ".temperature", "0.7"));
    int maxTokens = Integer.parseInt(getProperty(getProviderPrefix(provider) + ".max.tokens", "2048"));

    return new LLMConfig(provider, model, apiKey, baseUrl, maxTokens, temperature);
  }

  private String getApiKeyForProvider(LLMConfig.Provider provider) {
    String prefix = getProviderPrefix(provider);
    return getProperty(prefix + ".api.key", "");
  }

  private String getBaseUrlForProvider(LLMConfig.Provider provider) {
    String prefix = getProviderPrefix(provider);
    return switch (provider) {
      case OPENAI -> getProperty(prefix + ".base.url", "https://api.openai.com/v1");
      case ANTHROPIC -> getProperty(prefix + ".base.url", "https://api.anthropic.com");
      case AZURE -> getProperty("azure.endpoint", "");
      case OLLAMA -> getProperty(prefix + ".base.url", "http://localhost:11434");
    };
  }

  private String getModelForProvider(LLMConfig.Provider provider) {
    String prefix = getProviderPrefix(provider);
    return getProperty(prefix + ".model", provider.getDefaultModel());
  }

  private String getProviderPrefix(LLMConfig.Provider provider) {
    return provider.name().toLowerCase();
  }

  /**
   * Checks if LangCache is enabled.
   *
   * @return true if enabled
   */
  public boolean isLangCacheEnabled() {
    return Boolean.parseBoolean(getProperty("langcache.enabled", "false"));
  }

  /**
   * Gets LangCache URL.
   *
   * @return LangCache URL
   */
  public String getLangCacheUrl() {
    return getProperty("langcache.url", "http://localhost:8000");
  }

  /**
   * Gets LangCache cache ID.
   *
   * @return LangCache cache ID
   */
  public String getLangCacheCacheId() {
    return getProperty("langcache.cache.id", "");
  }

  /**
   * Gets LangCache API key.
   *
   * @return LangCache API key
   */
  public String getLangCacheApiKey() {
    return getProperty("langcache.api.key", "");
  }

  /**
   * Gets RAG max results.
   *
   * @return Max results
   */
  public int getRagMaxResults() {
    return Integer.parseInt(getProperty("rag.max.results", "5"));
  }

  /**
   * Gets RAG minimum score.
   *
   * @return Min score
   */
  public double getRagMinScore() {
    return Double.parseDouble(getProperty("rag.min.score", "0.7"));
  }

  /**
   * Gets PDF max pages to process.
   *
   * @return Max pages
   */
  public int getPdfMaxPages() {
    return Integer.parseInt(getProperty("pdf.max.pages", "500"));
  }

  /**
   * Validates that required configuration is present.
   *
   * @return true if valid
   */
  public boolean validateConfig() {
    LLMConfig.Provider provider = getLLMProvider();
    if (provider.requiresApiKey()) {
      String apiKey = getApiKeyForProvider(provider);
      if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
        System.err.println(
            "Error: API key not configured for " + provider + ". Please update application.properties");
        return false;
      }
    }
    return true;
  }
}

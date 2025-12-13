package com.redis.vl.test.vcr;

import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for wrapping model instances with VCR interceptors.
 *
 * <p>This class provides methods to wrap various LLM model types (LangChain4J, Spring AI) with
 * their corresponding VCR wrappers.
 */
public final class VCRModelWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(VCRModelWrapper.class);

  // Class names for type checking (avoid compile-time dependencies)
  private static final String LANGCHAIN4J_EMBEDDING_MODEL =
      "dev.langchain4j.model.embedding.EmbeddingModel";
  private static final String LANGCHAIN4J_CHAT_MODEL =
      "dev.langchain4j.model.chat.ChatLanguageModel";
  private static final String SPRING_AI_EMBEDDING_MODEL =
      "org.springframework.ai.embedding.EmbeddingModel";
  private static final String SPRING_AI_CHAT_MODEL = "org.springframework.ai.chat.model.ChatModel";

  private VCRModelWrapper() {
    // Utility class
  }

  /**
   * Wraps a model field with the appropriate VCR interceptor.
   *
   * @param testInstance the test instance containing the field
   * @param field the field to wrap
   * @param testId the current test identifier
   * @param mode the VCR mode
   * @param modelName optional model name for the wrapper
   * @param cassetteStore the cassette store for persistence
   * @return true if the field was wrapped, false otherwise
   */
  @SuppressWarnings({"unchecked", "java:S3011"}) // Reflection access is intentional
  public static boolean wrapField(
      Object testInstance,
      Field field,
      String testId,
      VCRMode mode,
      String modelName,
      VCRCassetteStore cassetteStore) {

    try {
      field.setAccessible(true);
      Object model = field.get(testInstance);

      if (model == null) {
        LOG.debug("Field {} is null, skipping VCR wrapping", field.getName());
        return false;
      }

      String effectiveModelName = modelName.isEmpty() ? field.getName() : modelName;
      Object wrapped = wrapModel(model, testId, mode, effectiveModelName, cassetteStore);

      if (wrapped != null && wrapped != model) {
        field.set(testInstance, wrapped);
        LOG.info("Wrapped field {} with VCR interceptor (mode: {})", field.getName(), mode);
        return true;
      }

      return false;
    } catch (IllegalAccessException e) {
      LOG.warn("Failed to wrap field {}: {}", field.getName(), e.getMessage());
      return false;
    }
  }

  /**
   * Wraps a model with the appropriate VCR interceptor based on its type.
   *
   * @param model the model to wrap
   * @param testId the test identifier
   * @param mode the VCR mode
   * @param modelName the model name for cache keys
   * @param cassetteStore the cassette store for persistence
   * @return the wrapped model, or null if the model type is not supported
   */
  public static Object wrapModel(
      Object model, String testId, VCRMode mode, String modelName, VCRCassetteStore cassetteStore) {
    Class<?> modelClass = model.getClass();

    // Check LangChain4J EmbeddingModel
    if (implementsInterface(modelClass, LANGCHAIN4J_EMBEDDING_MODEL)) {
      return wrapLangChain4JEmbeddingModel(model, testId, mode, modelName, cassetteStore);
    }

    // Check LangChain4J ChatLanguageModel
    if (implementsInterface(modelClass, LANGCHAIN4J_CHAT_MODEL)) {
      return wrapLangChain4JChatModel(model, testId, mode, cassetteStore);
    }

    // Check Spring AI EmbeddingModel
    if (implementsInterface(modelClass, SPRING_AI_EMBEDDING_MODEL)) {
      return wrapSpringAIEmbeddingModel(model, testId, mode, modelName, cassetteStore);
    }

    // Check Spring AI ChatModel
    if (implementsInterface(modelClass, SPRING_AI_CHAT_MODEL)) {
      return wrapSpringAIChatModel(model, testId, mode, cassetteStore);
    }

    LOG.warn("Unsupported model type for VCR wrapping: {}", modelClass.getName());
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Object wrapLangChain4JEmbeddingModel(
      Object model, String testId, VCRMode mode, String modelName, VCRCassetteStore cassetteStore) {
    try {
      var wrapper =
          new VCREmbeddingModel(
              (dev.langchain4j.model.embedding.EmbeddingModel) model, cassetteStore);
      wrapper.setTestId(testId);
      wrapper.setMode(mode);
      wrapper.setModelName(modelName);
      return wrapper;
    } catch (NoClassDefFoundError e) {
      LOG.debug("LangChain4J not available: {}", e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Object wrapLangChain4JChatModel(
      Object model, String testId, VCRMode mode, VCRCassetteStore cassetteStore) {
    try {
      var wrapper =
          new VCRChatModel((dev.langchain4j.model.chat.ChatLanguageModel) model, cassetteStore);
      wrapper.setTestId(testId);
      wrapper.setMode(mode);
      return wrapper;
    } catch (NoClassDefFoundError e) {
      LOG.debug("LangChain4J not available: {}", e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Object wrapSpringAIEmbeddingModel(
      Object model, String testId, VCRMode mode, String modelName, VCRCassetteStore cassetteStore) {
    try {
      var wrapper =
          new VCRSpringAIEmbeddingModel(
              (org.springframework.ai.embedding.EmbeddingModel) model, cassetteStore);
      wrapper.setTestId(testId);
      wrapper.setMode(mode);
      wrapper.setModelName(modelName);
      return wrapper;
    } catch (NoClassDefFoundError e) {
      LOG.debug("Spring AI not available: {}", e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Object wrapSpringAIChatModel(
      Object model, String testId, VCRMode mode, VCRCassetteStore cassetteStore) {
    try {
      var wrapper =
          new VCRSpringAIChatModel(
              (org.springframework.ai.chat.model.ChatModel) model, cassetteStore);
      wrapper.setTestId(testId);
      wrapper.setMode(mode);
      return wrapper;
    } catch (NoClassDefFoundError e) {
      LOG.debug("Spring AI not available: {}", e.getMessage());
      return null;
    }
  }

  private static boolean implementsInterface(Class<?> clazz, String interfaceName) {
    try {
      Class<?> iface = Class.forName(interfaceName);
      return iface.isAssignableFrom(clazz);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}

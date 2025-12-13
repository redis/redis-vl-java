package com.redis.vl.test.vcr;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that provides VCR (Video Cassette Recorder) functionality for recording and
 * playing back LLM API calls during tests.
 *
 * <p>This extension manages:
 *
 * <ul>
 *   <li>Redis container lifecycle with AOF/RDB persistence
 *   <li>Cassette storage and retrieval
 *   <li>Test context and call counter management
 *   <li>Automatic wrapping of {@code @VCRModel} annotated fields
 * </ul>
 *
 * <p>Usage with declarative field wrapping:
 *
 * <pre>{@code
 * @VCRTest(mode = VCRMode.PLAYBACK_OR_RECORD)
 * class MyLLMTest {
 *
 *     @VCRModel
 *     private EmbeddingModel embeddingModel;
 *
 *     @VCRModel
 *     private ChatModel chatModel;
 *
 *     @BeforeEach
 *     void setup() {
 *         // Initialize models normally - VCR wraps them automatically
 *         embeddingModel = new OpenAiEmbeddingModel(...);
 *         chatModel = new OpenAiChatModel(...);
 *     }
 *
 *     @Test
 *     void testLLMCall() {
 *         // LLM calls are automatically recorded/replayed
 *         embeddingModel.embed("Hello");
 *         chatModel.generate("What is Redis?");
 *     }
 * }
 * }</pre>
 */
public class VCRExtension
    implements BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        TestWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(VCRExtension.class);

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(VCRExtension.class);

  private VCRContext context;

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    // Get VCR configuration from @VCRTest annotation
    // Walk up the class hierarchy to find the annotation (handles @Nested test classes)
    VCRTest config = findVCRTestAnnotation(extensionContext.getRequiredTestClass());

    if (config == null) {
      // No @VCRTest annotation, use defaults
      config = DefaultVCRTest.INSTANCE;
    }

    // Create and initialize context
    context = new VCRContext(config);
    context.initialize();

    // Store in extension context for access in other callbacks
    extensionContext.getStore(NAMESPACE).put("vcr-context", context);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    if (context == null) {
      return;
    }

    // Get test identifier
    String testId = getTestId(extensionContext);

    // Reset call counters for new test
    context.resetCallCounters();

    // Set current test context
    context.setCurrentTest(testId);

    // Check for method-level mode overrides
    var method = extensionContext.getRequiredTestMethod();

    VCRMode effectiveMode;
    if (method.isAnnotationPresent(VCRDisabled.class)) {
      effectiveMode = VCRMode.OFF;
    } else if (method.isAnnotationPresent(VCRRecord.class)) {
      effectiveMode = VCRMode.RECORD;
    } else {
      // Use class-level or default mode
      effectiveMode = context.getConfiguredMode();
    }
    context.setEffectiveMode(effectiveMode);

    // Wrap @VCRModel annotated fields with cassette store
    wrapAnnotatedFields(extensionContext, testId, effectiveMode, context.getCassetteStore());
  }

  /**
   * Scans the test instance for fields annotated with {@code @VCRModel} and wraps them with VCR
   * interceptors.
   */
  private void wrapAnnotatedFields(
      ExtensionContext extensionContext,
      String testId,
      VCRMode mode,
      VCRCassetteStore cassetteStore) {

    if (mode == VCRMode.OFF) {
      return; // Don't wrap if VCR is disabled
    }

    Object testInstance = extensionContext.getRequiredTestInstance();
    Class<?> testClass = testInstance.getClass();

    // Collect all fields including from parent classes
    List<Field> allFields = new ArrayList<>();
    Class<?> currentClass = testClass;
    while (currentClass != null && currentClass != Object.class) {
      for (Field field : currentClass.getDeclaredFields()) {
        allFields.add(field);
      }
      currentClass = currentClass.getSuperclass();
    }

    // Also check fields from nested test classes
    Class<?> enclosingClass = testClass.getEnclosingClass();
    if (enclosingClass != null) {
      // For nested classes, we need to access the outer instance
      for (Field field : enclosingClass.getDeclaredFields()) {
        if (field.isAnnotationPresent(VCRModel.class)) {
          wrapFieldInEnclosingInstance(
              testInstance, enclosingClass, field, testId, mode, cassetteStore);
        }
      }
    }

    // Wrap fields in the test instance
    for (Field field : allFields) {
      if (field.isAnnotationPresent(VCRModel.class)) {
        VCRModel annotation = field.getAnnotation(VCRModel.class);
        VCRModelWrapper.wrapField(
            testInstance, field, testId, mode, annotation.modelName(), cassetteStore);
      }
    }
  }

  /** Wraps a field in the enclosing instance of a nested test class. */
  @SuppressWarnings("java:S3011") // Reflection access is intentional
  private void wrapFieldInEnclosingInstance(
      Object nestedInstance,
      Class<?> enclosingClass,
      Field field,
      String testId,
      VCRMode mode,
      VCRCassetteStore cassetteStore) {
    try {
      // Find the synthetic field that holds reference to the enclosing instance
      for (Field syntheticField : nestedInstance.getClass().getDeclaredFields()) {
        if (syntheticField.getName().startsWith("this$")
            && syntheticField.getType().equals(enclosingClass)) {
          syntheticField.setAccessible(true);
          Object enclosingInstance = syntheticField.get(nestedInstance);

          if (enclosingInstance != null) {
            VCRModel annotation = field.getAnnotation(VCRModel.class);
            VCRModelWrapper.wrapField(
                enclosingInstance, field, testId, mode, annotation.modelName(), cassetteStore);
          }
          break;
        }
      }
    } catch (IllegalAccessException e) {
      LOG.warn("Failed to access enclosing instance: {}", e.getMessage());
    }
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    // Test result handling is done via TestWatcher callbacks
  }

  @Override
  public void testSuccessful(ExtensionContext extensionContext) {
    if (context == null) {
      return;
    }

    // Only update registry when recording, not in playback mode
    if (context.getEffectiveMode().isRecordMode()) {
      String testId = getTestId(extensionContext);
      context.getRegistry().registerSuccess(testId, context.getCurrentCassetteKeys());
    }
  }

  @Override
  public void testFailed(ExtensionContext extensionContext, Throwable cause) {
    if (context == null) {
      return;
    }

    // Only update registry and delete cassettes when recording
    if (context.getEffectiveMode().isRecordMode()) {
      String testId = getTestId(extensionContext);
      context.getRegistry().registerFailure(testId, cause.getMessage());

      // Delete cassettes for failed tests in RECORD mode
      context.deleteCassettes(context.getCurrentCassetteKeys());
    }
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    if (context == null) {
      return;
    }

    try {
      // Persist cassettes if in record mode
      if (context.getEffectiveMode().isRecordMode()) {
        context.persistCassettes();
      }

      // Print statistics
      context.printStatistics();
    } finally {
      // Clean up
      context.shutdown();
    }
  }

  private String getTestId(ExtensionContext ctx) {
    return ctx.getRequiredTestClass().getName() + ":" + ctx.getRequiredTestMethod().getName();
  }

  /**
   * Finds the @VCRTest annotation on the given class or any of its enclosing classes. This is
   * needed to properly handle @Nested test classes where the annotation is on the outer class.
   *
   * @param testClass the test class to search
   * @return the VCRTest annotation if found, null otherwise
   */
  private VCRTest findVCRTestAnnotation(Class<?> testClass) {
    Class<?> currentClass = testClass;

    // Walk up the class hierarchy (enclosing classes for nested classes)
    while (currentClass != null) {
      VCRTest annotation = currentClass.getAnnotation(VCRTest.class);
      if (annotation != null) {
        return annotation;
      }
      // Check enclosing class for @Nested test classes
      currentClass = currentClass.getEnclosingClass();
    }

    return null;
  }

  /** Default VCRTest annotation values for when no annotation is present. */
  @VCRTest
  private static class DefaultVCRTest {
    static final VCRTest INSTANCE = DefaultVCRTest.class.getAnnotation(VCRTest.class);
  }
}

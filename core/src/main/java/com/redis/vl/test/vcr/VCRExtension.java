package com.redis.vl.test.vcr;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

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
 *   <li>LLM call interception via ByteBuddy
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @VCRTest(mode = VCRMode.PLAYBACK)
 * class MyLLMTest {
 *     @Test
 *     void testLLMCall() {
 *         // LLM calls are automatically recorded/replayed
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

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(VCRExtension.class);

  private VCRContext context;

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    // Get VCR configuration from @VCRTest annotation
    VCRTest config = extensionContext.getRequiredTestClass().getAnnotation(VCRTest.class);

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

    if (method.isAnnotationPresent(VCRDisabled.class)) {
      context.setEffectiveMode(VCRMode.OFF);
    } else if (method.isAnnotationPresent(VCRRecord.class)) {
      context.setEffectiveMode(VCRMode.RECORD);
    } else {
      // Use class-level or default mode
      context.setEffectiveMode(context.getConfiguredMode());
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

    String testId = getTestId(extensionContext);
    context.getRegistry().registerSuccess(testId, context.getCurrentCassetteKeys());
  }

  @Override
  public void testFailed(ExtensionContext extensionContext, Throwable cause) {
    if (context == null) {
      return;
    }

    String testId = getTestId(extensionContext);
    context.getRegistry().registerFailure(testId, cause.getMessage());

    // Optionally delete cassettes for failed tests in RECORD mode
    if (context.getEffectiveMode() == VCRMode.RECORD) {
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

  /** Default VCRTest annotation values for when no annotation is present. */
  @VCRTest
  private static class DefaultVCRTest {
    static final VCRTest INSTANCE = DefaultVCRTest.class.getAnnotation(VCRTest.class);
  }
}

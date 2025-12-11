package com.redis.vl.demo.rag.ui;

import com.redis.vl.demo.rag.config.AppConfig;
import com.redis.vl.demo.rag.model.CacheType;
import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.model.LLMConfig;
import com.redis.vl.demo.rag.model.LogEntry.Category;
import com.redis.vl.demo.rag.service.EventLogger;
import com.redis.vl.demo.rag.service.RAGService;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/**
 * Main chat interface controller.
 *
 * <p>Manages the chat UI, message flow, and interactions with RAGService.
 */
public class ChatController extends BorderPane {

  private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();
  private final ListView<ChatMessage> messageListView;
  private final TextField inputField;
  private final Button sendButton;
  private final ComboBox<CacheType> cacheComboBox;
  private final Label statusLabel;
  private final Label providerLabel;
  private final Label modelLabel;
  private final Label loadedPdfLabel;
  private Button uploadPdfButton;
  private final PDFViewerPanel pdfViewer;
  private final LogPanel logPanel;
  private final EventLogger eventLogger;
  private File currentPdfFile;

  private RAGService ragService;
  private com.redis.vl.demo.rag.service.ServiceFactory serviceFactory;
  private com.redis.vl.demo.rag.service.PDFIngestionService pdfIngestionService;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AppConfig config = AppConfig.getInstance();

  @SuppressWarnings("this-escape")
  public ChatController() {
    // Initialize event logger first
    eventLogger = new EventLogger();
    eventLogger.info(Category.SYSTEM, "Application starting...");

    // Initialize fields first
    uploadPdfButton = new Button("Upload PDF");
    uploadPdfButton.setOnAction(e -> uploadPdf());

    // PDF viewer panel (left side)
    pdfViewer = new PDFViewerPanel();

    // Log panel (bottom)
    logPanel = new LogPanel();
    logPanel.setEventLogger(eventLogger);
    pdfViewer.setPrefWidth(450);
    pdfViewer.setMinWidth(300);

    // Message list (center/right side)
    messageListView = new ListView<>(messages);
    messageListView.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(ChatMessage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setGraphic(null);
                } else {
                  // Pass page navigator to enable click-to-page references
                  setGraphic(new MessageBubble(item, pdfViewer::goToPage));
                }
              }
            });
    messageListView.setPadding(new Insets(10));
    messageListView.getStyleClass().add("message-list");

    // Use SplitPane for PDF viewer and chat
    SplitPane splitPane = new SplitPane();
    splitPane.setOrientation(Orientation.HORIZONTAL);
    splitPane.getItems().addAll(pdfViewer, messageListView);
    splitPane.setDividerPositions(0.4);
    SplitPane.setResizableWithParent(pdfViewer, false);

    setCenter(splitPane);

    // Right control panel
    VBox rightPanel = new VBox(15);
    rightPanel.setPadding(new Insets(15));
    rightPanel.getStyleClass().add("control-panel");
    rightPanel.setPrefWidth(300);
    rightPanel.setMinWidth(300);

    // Configuration section
    Label configTitle = new Label("Configuration");
    configTitle.getStyleClass().add("section-title");

    providerLabel = new Label("Provider: " + config.getLLMProvider().getDisplayName());
    providerLabel.getStyleClass().add("info-label");

    modelLabel = new Label("Model: " + config.getLLMConfig().model());
    modelLabel.getStyleClass().add("info-label");

    loadedPdfLabel = new Label("No PDF loaded");
    loadedPdfLabel.getStyleClass().add("info-label");
    loadedPdfLabel.setWrapText(true);

    Separator configSeparator = new Separator();

    // Cache section
    Label cacheTitle = new Label("Semantic Cache");
    cacheTitle.getStyleClass().add("section-title");

    cacheComboBox = new ComboBox<>();
    cacheComboBox.getItems().addAll(CacheType.values());
    cacheComboBox.setValue(config.isLangCacheEnabled() ? CacheType.LANGCACHE : CacheType.NONE);
    cacheComboBox.getStyleClass().add("cache-combobox");
    cacheComboBox.setMaxWidth(Double.MAX_VALUE);

    Label cacheInfo = new Label("Select caching mode:\n• No Cache: Always call LLM\n• Local: Redis-based cache\n• LangCache: Cloud cache");
    cacheInfo.getStyleClass().add("info-text");
    cacheInfo.setWrapText(true);

    Separator cacheSeparator = new Separator();

    // Actions section
    Label actionsTitle = new Label("Actions");
    actionsTitle.getStyleClass().add("section-title");

    uploadPdfButton.getStyleClass().add("action-button");
    uploadPdfButton.setMaxWidth(Double.MAX_VALUE);

    Separator actionsSeparator = new Separator();

    // Status section
    Label statusTitle = new Label("Status");
    statusTitle.getStyleClass().add("section-title");

    statusLabel = new Label("Ready");
    statusLabel.getStyleClass().add("status-label");
    statusLabel.setWrapText(true);

    // Spacer to push status to bottom
    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    rightPanel.getChildren().addAll(
        configTitle, providerLabel, modelLabel, loadedPdfLabel, configSeparator,
        cacheTitle, cacheComboBox, cacheInfo, cacheSeparator,
        actionsTitle, uploadPdfButton, actionsSeparator,
        spacer,
        statusTitle, statusLabel
    );

    // Bottom input area
    HBox inputSection = new HBox(10);
    inputSection.setPadding(new Insets(10));
    inputSection.getStyleClass().add("input-area");

    inputField = new TextField();
    inputField.setPromptText("Ask a question about the PDF...");
    inputField.getStyleClass().add("input-field");
    HBox.setHgrow(inputField, Priority.ALWAYS);
    inputField.setOnAction(e -> sendMessage());

    sendButton = new Button("Send");
    sendButton.setDefaultButton(true);
    sendButton.getStyleClass().add("send-button");
    sendButton.setOnAction(e -> sendMessage());

    inputSection.getChildren().addAll(inputField, sendButton);
    setBottom(inputSection);

    // Right side: control panel + log panel (far right edge)
    HBox rightContainer = new HBox();
    rightContainer.getChildren().addAll(rightPanel, logPanel);
    setRight(rightContainer);

    // Add welcome message
    addSystemMessage("Welcome to RedisVL Multimodal RAG Demo!\n\nConfiguration loaded from application.properties:\n• Provider: " +
        config.getLLMProvider().getDisplayName() + "\n• Model: " + config.getLLMConfig().model() +
        "\n\nUpload a PDF to get started.");
  }

  private void sendMessage() {
    String userInput = inputField.getText().trim();
    if (userInput.isEmpty()) {
      return;
    }

    // Initialize RAG service if needed
    if (ragService == null) {
      initializeRAGService();
      if (ragService == null) {
        addSystemMessage("Failed to initialize RAG service. Check configuration in application.properties");
        return;
      }
    }

    // Clear input
    inputField.clear();

    // Add user message
    ChatMessage userMessage = ChatMessage.user(userInput);
    messages.add(userMessage);
    scrollToBottom();

    // Disable input while processing
    setInputEnabled(false);
    statusLabel.setText("Thinking...");

    CacheType cacheType = cacheComboBox.getValue();
    eventLogger.info(Category.LLM, "Query: \"" + truncate(userInput, 50) + "\" (cache: " + cacheType + ")");

    // Process in background
    long startTime = System.currentTimeMillis();
    executor.submit(
        () -> {
          try {
            ChatMessage response = ragService.query(userInput, cacheType);
            long elapsed = System.currentTimeMillis() - startTime;

            Platform.runLater(
                () -> {
                  messages.add(response);
                  scrollToBottom();

                  if (response.fromCache()) {
                    eventLogger.info(Category.CACHE, "Cache HIT - Response in " + elapsed + "ms");
                  } else {
                    eventLogger.info(Category.LLM, "LLM response in " + elapsed + "ms (" + response.tokenCount() + " tokens)");
                  }

                  if (!response.references().isEmpty()) {
                    eventLogger.debug(Category.RETRIEVAL, "Retrieved " + response.references().size() + " source references");
                  }
                });
          } catch (Exception e) {
            Platform.runLater(() -> {
              addSystemMessage("Error: " + e.getMessage());
              eventLogger.error(Category.LLM, "Query failed: " + e.getMessage());
            });
          } finally {
            Platform.runLater(
                () -> {
                  setInputEnabled(true);
                  statusLabel.setText("Ready");
                  inputField.requestFocus();
                });
          }
        });
  }

  private void initializeRAGService() {
    if (serviceFactory == null) {
      addSystemMessage("Service factory not initialized. Check Redis connection.");
      eventLogger.error(Category.SYSTEM, "Service factory not initialized");
      return;
    }

    // Validate configuration
    if (!config.validateConfig()) {
      addSystemMessage("Configuration validation failed. Please check application.properties and ensure API keys are set.");
      eventLogger.error(Category.SYSTEM, "Configuration validation failed");
      return;
    }

    try {
      LLMConfig llmConfig = config.getLLMConfig();
      eventLogger.info(Category.LLM, "Initializing " + llmConfig.provider().getDisplayName() + " (" + llmConfig.model() + ")...");
      ragService = serviceFactory.createRAGService(llmConfig);
      addSystemMessage("Initialized " + llmConfig.provider().getDisplayName() + " successfully.");
      eventLogger.info(Category.LLM, "LLM initialized successfully");
    } catch (Exception e) {
      addSystemMessage("Failed to initialize LLM: " + e.getMessage());
      eventLogger.error(Category.LLM, "LLM initialization failed: " + e.getMessage());
    }
  }

  private void uploadPdf() {
    if (serviceFactory == null) {
      addSystemMessage("Service factory not initialized. Check Redis connection.");
      eventLogger.error(Category.PDF, "Upload failed - service factory not initialized");
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select PDF Document");
    fileChooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

    File file = fileChooser.showOpenDialog(getScene().getWindow());
    if (file != null) {
      currentPdfFile = file;
      statusLabel.setText("Processing PDF: " + file.getName());
      addSystemMessage("Processing PDF: " + file.getName() + "...");
      eventLogger.info(Category.PDF, "Loading PDF: " + file.getName());

      // Load PDF into viewer immediately (on JavaFX thread)
      try {
        pdfViewer.loadPDF(file);
        eventLogger.debug(Category.PDF, "PDF viewer loaded: " + pdfViewer.getTotalPages() + " pages");
      } catch (Exception e) {
        addSystemMessage("Warning: Could not display PDF preview: " + e.getMessage());
        eventLogger.warn(Category.PDF, "PDF preview failed: " + e.getMessage());
      }

      // Index PDF in background
      long startTime = System.currentTimeMillis();
      executor.submit(
          () -> {
            try {
              // Initialize PDF service if needed
              if (pdfIngestionService == null) {
                pdfIngestionService = serviceFactory.createPDFIngestionService();
              }

              // Process PDF
              String documentId = "doc_" + System.currentTimeMillis();
              int chunks = pdfIngestionService.ingestPDF(file, documentId);
              long elapsed = System.currentTimeMillis() - startTime;

              Platform.runLater(
                  () -> {
                    addSystemMessage(
                        String.format(
                            "PDF processed successfully. Indexed %d chunks. You can now ask questions about the document.",
                            chunks));
                    loadedPdfLabel.setText("Loaded: " + file.getName() + " (" + chunks + " chunks)");
                    statusLabel.setText("Ready");
                    eventLogger.info(Category.PDF, "Indexed " + chunks + " chunks in " + elapsed + "ms");
                  });
            } catch (Exception e) {
              Platform.runLater(
                  () -> {
                    addSystemMessage("Error processing PDF: " + e.getMessage());
                    statusLabel.setText("Ready");
                    eventLogger.error(Category.PDF, "Indexing failed: " + e.getMessage());
                  });
            }
          });
    }
  }

  private void addSystemMessage(String content) {
    ChatMessage systemMessage =
        new ChatMessage(
            java.util.UUID.randomUUID().toString(),
            ChatMessage.Role.ASSISTANT,
            content,
            java.time.Instant.now(),
            0,
            0.0,
            "System",
            false,
            java.util.List.of());
    messages.add(systemMessage);
    scrollToBottom();
  }

  private void setInputEnabled(boolean enabled) {
    inputField.setDisable(!enabled);
    sendButton.setDisable(!enabled);
  }

  private void scrollToBottom() {
    Platform.runLater(
        () -> {
          if (!messages.isEmpty()) {
            messageListView.scrollTo(messages.size() - 1);
          }
        });
  }

  /**
   * Sets the service factory.
   *
   * @param serviceFactory Service factory instance
   */
  public void setServiceFactory(com.redis.vl.demo.rag.service.ServiceFactory serviceFactory) {
    this.serviceFactory = serviceFactory;
    updateStatusLabel("Connected to Redis. Select provider and enter API key to start.");
    eventLogger.info(Category.SYSTEM, "Connected to Redis");
  }

  /**
   * Updates the status label text.
   *
   * @param text Status text
   */
  private void updateStatusLabel(String text) {
    if (statusLabel != null) {
      Platform.runLater(() -> statusLabel.setText(text));
    }
  }

  /**
   * Shutdown executor and close resources.
   */
  public void shutdown() {
    executor.shutdown();
    if (pdfViewer != null) {
      pdfViewer.close();
    }
  }

  /**
   * Gets the PDF viewer panel for external navigation.
   *
   * @return PDFViewerPanel instance
   */
  public PDFViewerPanel getPdfViewer() {
    return pdfViewer;
  }

  /**
   * Truncates a string to the specified length.
   *
   * @param text String to truncate
   * @param maxLength Maximum length
   * @return Truncated string with "..." if needed
   */
  private static String truncate(String text, int maxLength) {
    if (text == null || text.length() <= maxLength) {
      return text;
    }
    return text.substring(0, maxLength) + "...";
  }
}

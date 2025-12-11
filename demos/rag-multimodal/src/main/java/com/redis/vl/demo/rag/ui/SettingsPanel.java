package com.redis.vl.demo.rag.ui;

import com.redis.vl.demo.rag.config.AppConfig;
import com.redis.vl.demo.rag.model.CacheType;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Settings panel content for the vertical tab pane.
 *
 * <p>Contains configuration, cache toggle, upload button, and status display.
 */
public class SettingsPanel extends ScrollPane {

  private final ComboBox<CacheType> cacheComboBox;
  private final Button uploadPdfButton;
  private final Label statusLabel;
  private final Label providerLabel;
  private final Label modelLabel;
  private final Label loadedPdfLabel;

  /**
   * Creates the settings panel.
   */
  public SettingsPanel() {
    AppConfig config = AppConfig.getInstance();

    VBox content = new VBox(12);
    content.setPadding(new Insets(16));
    content.getStyleClass().add("settings-panel");

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

    Separator configSep = new Separator();

    // Cache section
    Label cacheTitle = new Label("Semantic Cache");
    cacheTitle.getStyleClass().add("section-title");

    cacheComboBox = new ComboBox<>();
    cacheComboBox.getItems().addAll(CacheType.values());
    cacheComboBox.setValue(CacheType.NONE);  // Default to no cache
    cacheComboBox.getStyleClass().add("cache-combobox");
    cacheComboBox.setMaxWidth(Double.MAX_VALUE);

    Label cacheInfo = new Label("• No Cache: Always call LLM\n• Local: Redis semantic cache\n• LangCache: Cloud cache");
    cacheInfo.getStyleClass().add("info-text");
    cacheInfo.setWrapText(true);

    Separator cacheSep = new Separator();

    // Document section
    Label docTitle = new Label("Document");
    docTitle.getStyleClass().add("section-title");

    uploadPdfButton = new Button("Upload PDF");
    uploadPdfButton.getStyleClass().add("action-button");
    uploadPdfButton.setMaxWidth(Double.MAX_VALUE);

    Separator docSep = new Separator();

    // Status section
    Label statusTitle = new Label("Status");
    statusTitle.getStyleClass().add("section-title");

    statusLabel = new Label("Ready");
    statusLabel.getStyleClass().add("status-label");
    statusLabel.setWrapText(true);

    // Spacer
    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    content.getChildren().addAll(
        configTitle, providerLabel, modelLabel, loadedPdfLabel, configSep,
        cacheTitle, cacheComboBox, cacheInfo, cacheSep,
        docTitle, uploadPdfButton, docSep,
        spacer,
        statusTitle, statusLabel
    );

    setContent(content);
    setFitToWidth(true);
    setHbarPolicy(ScrollBarPolicy.NEVER);
    getStyleClass().add("settings-scroll");
  }

  /** Returns the cache type combo box. */
  public ComboBox<CacheType> getCacheComboBox() {
    return cacheComboBox;
  }

  /** Returns the upload button. */
  public Button getUploadPdfButton() {
    return uploadPdfButton;
  }

  /** Updates the status label text. */
  public void setStatus(String status) {
    statusLabel.setText(status);
  }

  /** Updates the loaded PDF label. */
  public void setLoadedPdf(String pdfName) {
    loadedPdfLabel.setText(pdfName != null ? "PDF: " + pdfName : "No PDF loaded");
  }

  /** Updates the provider label. */
  public void setProvider(String provider) {
    providerLabel.setText("Provider: " + provider);
  }

  /** Updates the model label. */
  public void setModel(String model) {
    modelLabel.setText("Model: " + model);
  }
}

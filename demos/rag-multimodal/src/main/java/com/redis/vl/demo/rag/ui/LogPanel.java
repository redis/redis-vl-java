package com.redis.vl.demo.rag.ui;

import com.redis.vl.demo.rag.model.LogEntry;
import com.redis.vl.demo.rag.service.EventLogger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * Slide-out log panel with vertical tab on right side.
 *
 * <p>Shows a vertical "LOG" tab that expands to reveal the event log when clicked.
 */
public class LogPanel extends HBox {

  private static final double COLLAPSED_WIDTH = 32;
  private static final double EXPANDED_WIDTH = 400;

  private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();
  private final ListView<LogEntry> logListView;
  private final VBox tabButton;
  private final BorderPane logContent;
  private final Label countBadge;

  private boolean expanded = false;
  private EventLogger eventLogger;

  @SuppressWarnings("this-escape")
  public LogPanel() {
    getStyleClass().add("log-panel-container");

    // Vertical tab button on left side of this component
    tabButton = new VBox();
    tabButton.getStyleClass().add("log-tab-button");
    tabButton.setAlignment(Pos.CENTER);
    tabButton.setMinWidth(COLLAPSED_WIDTH);
    tabButton.setMaxWidth(COLLAPSED_WIDTH);
    tabButton.setOnMouseClicked(e -> toggleExpanded());

    // Vertical "LOG" text
    Text logText = new Text("L\nO\nG");
    logText.getStyleClass().add("log-tab-text");

    // Count badge
    countBadge = new Label("0");
    countBadge.getStyleClass().add("log-count-badge");

    tabButton.getChildren().addAll(logText, countBadge);

    // Log content panel
    logContent = new BorderPane();
    logContent.getStyleClass().add("log-content");
    logContent.setVisible(false);
    logContent.setManaged(false);
    logContent.setPrefWidth(EXPANDED_WIDTH - COLLAPSED_WIDTH);

    // Header
    HBox header = new HBox(10);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(8, 12, 8, 12));
    header.getStyleClass().add("log-header");

    Label titleLabel = new Label("Event Log");
    titleLabel.getStyleClass().add("log-title");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button clearButton = new Button("Clear");
    clearButton.getStyleClass().add("log-clear-button");
    clearButton.setOnAction(e -> clear());

    header.getChildren().addAll(titleLabel, spacer, clearButton);
    logContent.setTop(header);

    // Log list
    logListView = new ListView<>(entries);
    logListView.getStyleClass().add("log-list");
    logListView.setCellFactory(lv -> new LogEntryCell());
    logListView.setFocusTraversable(false);
    logContent.setCenter(logListView);

    getChildren().addAll(tabButton, logContent);

    // Start collapsed
    setMinWidth(COLLAPSED_WIDTH);
    setMaxWidth(COLLAPSED_WIDTH);
    setPrefWidth(COLLAPSED_WIDTH);
  }

  /**
   * Connects the panel to an EventLogger.
   *
   * @param logger EventLogger to receive events from
   */
  public void setEventLogger(EventLogger logger) {
    this.eventLogger = logger;

    // Add existing entries
    entries.addAll(logger.getEntries());
    updateBadge();

    // Listen for new entries
    logger.addListener(this::onNewEntry);
  }

  private void onNewEntry(LogEntry entry) {
    Platform.runLater(() -> {
      entries.add(entry);
      updateBadge();

      // Auto-scroll to bottom if expanded
      if (expanded) {
        logListView.scrollTo(entries.size() - 1);
      }
    });
  }

  private void toggleExpanded() {
    expanded = !expanded;

    if (expanded) {
      logContent.setVisible(true);
      logContent.setManaged(true);
      setMinWidth(EXPANDED_WIDTH);
      setMaxWidth(EXPANDED_WIDTH);
      setPrefWidth(EXPANDED_WIDTH);
      logListView.scrollTo(entries.size() - 1);
      tabButton.getStyleClass().add("expanded");
    } else {
      logContent.setVisible(false);
      logContent.setManaged(false);
      setMinWidth(COLLAPSED_WIDTH);
      setMaxWidth(COLLAPSED_WIDTH);
      setPrefWidth(COLLAPSED_WIDTH);
      tabButton.getStyleClass().remove("expanded");
    }
  }

  private void clear() {
    entries.clear();
    if (eventLogger != null) {
      eventLogger.clear();
    }
    updateBadge();
  }

  private void updateBadge() {
    countBadge.setText(String.valueOf(entries.size()));
  }

  /**
   * Custom cell for rendering log entries with color coding.
   */
  private static class LogEntryCell extends ListCell<LogEntry> {
    @Override
    protected void updateItem(LogEntry item, boolean empty) {
      super.updateItem(item, empty);

      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        getStyleClass().removeAll("log-info", "log-debug", "log-warn", "log-error");
      } else {
        setText(item.formatted());

        // Apply style based on level
        getStyleClass().removeAll("log-info", "log-debug", "log-warn", "log-error");
        switch (item.level()) {
          case INFO -> getStyleClass().add("log-info");
          case DEBUG -> getStyleClass().add("log-debug");
          case WARN -> getStyleClass().add("log-warn");
          case ERROR -> getStyleClass().add("log-error");
        }
      }
    }
  }
}

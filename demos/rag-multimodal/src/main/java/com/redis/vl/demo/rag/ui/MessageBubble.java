package com.redis.vl.demo.rag.ui;

import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.model.Reference;
import com.redis.vl.demo.rag.service.JTokKitCostTracker;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Custom JavaFX control for rendering chat message bubbles.
 *
 * <p>Displays message content with cost and token information for assistant messages.
 */
public class MessageBubble extends HBox {

  private final ChatMessage message;
  private final Consumer<Integer> pageNavigator;

  /**
   * Creates a new message bubble.
   *
   * @param message Chat message to display
   */
  @SuppressWarnings("this-escape")
  public MessageBubble(ChatMessage message) {
    this(message, null);
  }

  /**
   * Creates a new message bubble with page navigation support.
   *
   * @param message Chat message to display
   * @param pageNavigator Callback for navigating to a page (receives 0-based page number)
   */
  @SuppressWarnings("this-escape")
  public MessageBubble(ChatMessage message, Consumer<Integer> pageNavigator) {
    this.message = message;
    this.pageNavigator = pageNavigator;
    build();
  }

  private void build() {
    setSpacing(10);
    setPadding(new Insets(5, 10, 5, 10));

    // Create message content container
    VBox contentBox = new VBox(5);
    contentBox.setMaxWidth(600);

    // Message text
    TextFlow textFlow = new TextFlow();
    Text messageText = new Text(message.content());
    messageText.setWrappingWidth(580);
    textFlow.getChildren().add(messageText);

    contentBox.getChildren().add(textFlow);

    // Add cost info for assistant messages
    if (message.role() == ChatMessage.Role.ASSISTANT && message.tokenCount() > 0) {
      Label costLabel = new Label();
      String costText =
          String.format(
              "%s • %s • %s",
              JTokKitCostTracker.formatTokens(message.tokenCount()),
              JTokKitCostTracker.formatCost(message.costUsd()),
              message.model());

      if (message.fromCache()) {
        costText += " • From Cache";
        costLabel.getStyleClass().add("cached");
      }

      costLabel.setText(costText);
      costLabel.getStyleClass().add("cost-label");
      contentBox.getChildren().add(costLabel);
    }

    // Add references section if available
    if (message.references() != null && !message.references().isEmpty()) {
      FlowPane refsPane = new FlowPane();
      refsPane.setHgap(5);
      refsPane.setVgap(3);
      refsPane.getStyleClass().add("references-pane");

      Label refsLabel = new Label("Sources:");
      refsLabel.getStyleClass().add("refs-label");
      refsPane.getChildren().add(refsLabel);

      for (Reference ref : message.references()) {
        Hyperlink pageLink = new Hyperlink("p." + ref.page());
        pageLink.getStyleClass().add("page-link");

        // Set tooltip with preview
        String tooltipText = ref.type() + ": " + ref.preview();
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        pageLink.setTooltip(tooltip);

        // Navigate to page on click (convert 1-indexed to 0-indexed)
        if (pageNavigator != null) {
          pageLink.setOnAction(e -> pageNavigator.accept(ref.page() - 1));
        } else {
          pageLink.setDisable(true);
        }

        refsPane.getChildren().add(pageLink);
      }

      contentBox.getChildren().add(refsPane);
    }

    // Style the bubble based on role
    contentBox.getStyleClass().add("message-bubble");
    if (message.role() == ChatMessage.Role.USER) {
      contentBox.getStyleClass().add("user");
      setAlignment(Pos.CENTER_RIGHT);
    } else {
      contentBox.getStyleClass().add("assistant");
      setAlignment(Pos.CENTER_LEFT);
      if (message.fromCache()) {
        contentBox.getStyleClass().add("cached");
      }
    }

    getChildren().add(contentBox);
  }

  /**
   * Gets the chat message.
   *
   * @return Chat message
   */
  public ChatMessage getMessage() {
    return message;
  }
}

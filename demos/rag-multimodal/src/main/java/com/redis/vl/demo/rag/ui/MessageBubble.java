package com.redis.vl.demo.rag.ui;

import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.service.JTokKitCostTracker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
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

  /**
   * Creates a new message bubble.
   *
   * @param message Chat message to display
   */
  @SuppressWarnings("this-escape")
  public MessageBubble(ChatMessage message) {
    this.message = message;
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
        costText += " • ⚡ From Cache";
        costLabel.getStyleClass().add("cached");
      }

      costLabel.setText(costText);
      costLabel.getStyleClass().add("cost-label");
      contentBox.getChildren().add(costLabel);
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

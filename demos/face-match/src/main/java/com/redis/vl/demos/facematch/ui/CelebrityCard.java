package com.redis.vl.demos.facematch.ui;

import com.redis.vl.demos.facematch.model.Celebrity;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * UI component for displaying a celebrity with their image and name.
 */
public class CelebrityCard extends VBox {

    private static final int IMAGE_SIZE = 80;
    private final Celebrity celebrity;
    private final ImageView imageView;
    private final Label nameLabel;

    public CelebrityCard(Celebrity celebrity) {
        this.celebrity = celebrity;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(10));
        setSpacing(5);
        setStyle("-fx-background-color: rgba(45, 45, 45, 0.8); -fx-background-radius: 8;");
        setMaxWidth(IMAGE_SIZE + 20);

        // Create circular image view with placeholder
        imageView = new ImageView();
        imageView.setFitWidth(IMAGE_SIZE);
        imageView.setFitHeight(IMAGE_SIZE);
        imageView.setPreserveRatio(true);

        // Circular clip
        Circle clip = new Circle(IMAGE_SIZE / 2.0, IMAGE_SIZE / 2.0, IMAGE_SIZE / 2.0);
        imageView.setClip(clip);

        // Use placeholder avatar (colored circle with initials)
        Image placeholder = createPlaceholderAvatar(celebrity);
        imageView.setImage(placeholder);

        // Name label
        nameLabel = new Label(celebrity.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(IMAGE_SIZE + 10);
        nameLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(imageView, nameLabel);
    }

    /**
     * Create a placeholder avatar with initials and a color based on the celebrity name.
     */
    private Image createPlaceholderAvatar(Celebrity celebrity) {
        // For now, return a simple colored circle
        // In a real app, we'd generate an image with initials
        // TODO: Generate actual avatar image with initials
        return null; // JavaFX will show a broken image icon, which we'll style
    }

    public Celebrity getCelebrity() {
        return celebrity;
    }

    public void setHighlighted(boolean highlighted) {
        if (highlighted) {
            setStyle("-fx-background-color: rgba(76, 175, 80, 0.8); -fx-background-radius: 8; -fx-border-color: #4CAF50; -fx-border-width: 2; -fx-border-radius: 8;");
        } else {
            setStyle("-fx-background-color: rgba(45, 45, 45, 0.8); -fx-background-radius: 8;");
        }
    }
}

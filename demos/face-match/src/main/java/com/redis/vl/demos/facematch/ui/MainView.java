package com.redis.vl.demos.facematch.ui;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import com.redis.vl.demos.facematch.service.CelebrityIndexService;
import com.redis.vl.demos.facematch.service.DataLoaderService;
import com.redis.vl.demos.facematch.service.DimensionalityReductionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import redis.clients.jedis.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main view with 3D cloud of square thumbnail images positioned by PCA.
 */
public class MainView {

    private final BorderPane root;
    private final SubScene scene3D;
    private final Group root3D;
    private final PerspectiveCamera camera;
    private List<Celebrity3D> celebrities = new ArrayList<>();
    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;
    private final Rotate rotateX;
    private final Rotate rotateY;
    private final Label statusLabel;
    private Label celebCount;
    private Label selectedCelebLabel;
    private List<ImageView> thumbnailViews = new ArrayList<>();

    public MainView() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        // Initialize 3D scene
        root3D = new Group();
        rotateX = new Rotate(0, Rotate.X_AXIS);
        rotateY = new Rotate(0, Rotate.Y_AXIS);
        root3D.getTransforms().addAll(rotateX, rotateY);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        camera.setTranslateZ(-2000); // Zoom out to see whole collection at startup

        scene3D = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        scene3D.setFill(Color.rgb(30, 30, 30));
        scene3D.setCamera(camera);

        // Status label
        statusLabel = new Label("Loading celebrities...");
        statusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-weight: bold;");
        statusLabel.setMouseTransparent(true);

        // Add mouse controls
        addMouseControls();

        // Create UI layout
        createLayout();

        // Load data asynchronously
        loadDataAsync();
    }

    private void loadDataAsync() {
        new Thread(() -> {
            try {
                // Connect to Redis
                HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
                UnifiedJedis jedis = new UnifiedJedis(hostAndPort);
                CelebrityIndexService indexService = new CelebrityIndexService(jedis);

                // Check if index exists and has data
                boolean indexExists = false;
                try {
                    long count = indexService.count();
                    indexExists = count > 0;
                } catch (Exception e) {
                    // Index doesn't exist yet
                }

                // If no data, generate and index sample celebrities
                if (!indexExists) {
                    Platform.runLater(() -> statusLabel.setText("Generating sample data..."));

                    DataLoaderService dataLoader = new DataLoaderService();
                    List<Celebrity> celebs = dataLoader.generateSampleCelebrities(100);

                    Platform.runLater(() -> statusLabel.setText("Creating Redis index..."));
                    indexService.createIndex();

                    Platform.runLater(() -> statusLabel.setText("Indexing celebrities..."));
                    indexService.indexCelebrities(celebs);

                    Platform.runLater(() -> statusLabel.setText("Reducing dimensions..."));
                    DimensionalityReductionService reducer = new DimensionalityReductionService();
                    this.celebrities = reducer.reduceTo3D(celebs);
                } else {
                    // Load existing data
                    Platform.runLater(() -> statusLabel.setText("Loading from Redis..."));

                    // Initialize the index (createIndex() is safe to call even if it exists)
                    indexService.createIndex();

                    List<Celebrity> celebs = indexService.getAllCelebrities();

                    Platform.runLater(() -> statusLabel.setText("Reducing dimensions..."));
                    DimensionalityReductionService reducer = new DimensionalityReductionService();
                    this.celebrities = reducer.reduceTo3D(celebs);
                }

                // Render celebrities in JavaFX thread
                Platform.runLater(() -> {
                    renderCelebrities();
                    celebCount.setText("Celebrities: " + celebrities.size());
                    statusLabel.setText(String.format("%d celebrities loaded", celebrities.size()));
                    // Fade out status label after 2 seconds
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                    pause.setOnFinished(e -> statusLabel.setVisible(false));
                    pause.play();
                });

                jedis.close();
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading data: " + e.getMessage());
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
                });
            }
        }).start();
    }

    private void createLayout() {
        // Center: 3D cloud visualization
        StackPane center = new StackPane(scene3D);
        center.setStyle("-fx-background-color: #1e1e1e;");

        // Add status label overlay
        StackPane.setAlignment(statusLabel, Pos.CENTER);
        center.getChildren().add(statusLabel);

        // Drag-drop hint overlay
        Label dropHint = new Label("Drag & drop a face image here");
        dropHint.setStyle("-fx-font-size: 18px; -fx-text-fill: rgba(255,255,255,0.3); -fx-font-weight: bold;");
        StackPane.setAlignment(dropHint, Pos.TOP_CENTER);
        StackPane.setMargin(dropHint, new Insets(20, 0, 0, 0));
        center.getChildren().add(dropHint);

        root.setCenter(center);

        // Right side: Controls and info
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(20));
        rightPanel.setStyle("-fx-background-color: #2d2d2d;");
        rightPanel.setPrefWidth(300);

        Label title = new Label("Celebrity Face Match");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitle = new Label("RedisVL4J 3D Vector Search Demo");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        Separator sep1 = new Separator(Orientation.HORIZONTAL);

        Label statsLabel = new Label("Statistics");
        statsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        celebCount = new Label("Celebrities: " + celebrities.size());
        celebCount.setStyle("-fx-text-fill: #aaa;");

        selectedCelebLabel = new Label("Selected: None");
        selectedCelebLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 11px;");

        Label embeddingDim = new Label("Embedding Dimension: 512");
        embeddingDim.setStyle("-fx-text-fill: #aaa;");

        Separator sep2 = new Separator(javafx.geometry.Orientation.HORIZONTAL);

        Label controlsLabel = new Label("Controls");
        controlsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label control1 = new Label("• Click: Select celebrity");
        control1.setStyle("-fx-text-fill: #aaa;");

        Label control2 = new Label("• Scroll: Browse grid");
        control2.setStyle("-fx-text-fill: #aaa;");

        Label control3 = new Label("• Drop image: Find matches");
        control3.setStyle("-fx-text-fill: #aaa;");

        Separator sep3 = new Separator(Orientation.HORIZONTAL);

        Label featuresLabel = new Label("Redis Features");
        featuresLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label feature1 = new Label("✓ Vector Search (HNSW)");
        feature1.setStyle("-fx-text-fill: #4CAF50;");

        Label feature2 = new Label("✓ 512D Face Embeddings");
        feature2.setStyle("-fx-text-fill: #4CAF50;");

        Label feature3 = new Label("✓ KNN Query (Top-K)");
        feature3.setStyle("-fx-text-fill: #4CAF50;");

        rightPanel.getChildren().addAll(
            title, subtitle, sep1,
            statsLabel, celebCount, selectedCelebLabel, embeddingDim, sep2,
            controlsLabel, control1, control2, control3, sep3,
            featuresLabel, feature1, feature2, feature3
        );

        root.setRight(rightPanel);
    }

    private void renderCelebrities() {
        if (celebrities == null || celebrities.isEmpty()) return;

        // Clear existing thumbnails
        root3D.getChildren().clear();
        thumbnailViews.clear();

        // Render each celebrity as a flat thumbnail in 3D space
        for (int i = 0; i < celebrities.size(); i++) {
            Celebrity3D celeb3D = celebrities.get(i);
            final int celebIndex = i;

            // Create flat square thumbnail
            Image avatarImage = createCelebrityAvatar(celeb3D.getCelebrity());
            ImageView imageView = new ImageView(avatarImage);
            imageView.setFitWidth(40);  // Square thumbnail size
            imageView.setFitHeight(40);
            imageView.setPreserveRatio(true);

            // Position in 3D space based on PCA coordinates
            imageView.setTranslateX(celeb3D.getX());
            imageView.setTranslateY(celeb3D.getY());
            imageView.setTranslateZ(celeb3D.getZ());

            // Add click handler for highlighting
            imageView.setOnMouseClicked(event -> highlightCelebrity(celebIndex));

            thumbnailViews.add(imageView);
            root3D.getChildren().add(imageView);
        }
    }

    /**
     * Create or load celebrity avatar/image.
     * Tries to load actual image from resources first, falls back to placeholder with initials.
     */
    private Image createCelebrityAvatar(Celebrity celebrity) {
        // Try to load actual celebrity image based on imageUrl
        // ImageUrl format: "http://example.com/images/celeb_X.jpg" where X is the numeric ID
        String imageUrl = celebrity.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                // Extract ID from URL (e.g., "celeb_123" -> "123")
                String[] parts = imageUrl.split("/");
                String filename = parts[parts.length - 1]; // "celeb_X.jpg"
                String idStr = filename.replace("celeb_", "").replace(".jpg", "");

                // Try to load from classpath resources
                String resourcePath = "/static/images/celebs/img_" + idStr + ".jpg";
                var inputStream = getClass().getResourceAsStream(resourcePath);

                if (inputStream != null) {
                    return new Image(inputStream, 80, 80, true, true);
                }
            } catch (Exception e) {
                // Fall through to placeholder
            }
        }

        // Fallback: Generate placeholder avatar with initials
        int size = 80;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Generate a consistent color based on the celebrity name
        int hash = celebrity.getName().hashCode();
        Color bgColor = Color.hsb((hash & 0xFF) * 360.0 / 255.0, 0.6, 0.7);

        // Draw square background
        gc.setFill(bgColor);
        gc.fillRect(0, 0, size, size);

        // Get initials (first letter of first two words)
        String[] nameParts = celebrity.getName().split(" ");
        String initials = "";
        if (nameParts.length > 0) {
            initials += nameParts[0].charAt(0);
        }
        if (nameParts.length > 1) {
            initials += nameParts[1].charAt(0);
        }
        initials = initials.toUpperCase();

        // Draw initials
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 32));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(initials, size / 2.0, size / 2.0);

        // Convert canvas to image
        WritableImage image = new WritableImage(size, size);
        canvas.snapshot(null, image);
        return image;
    }

    private void addMouseControls() {
        scene3D.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });

        scene3D.setOnMouseDragged(event -> {
            rotateX.setAngle(anchorAngleX - (anchorY - event.getSceneY()));
            rotateY.setAngle(anchorAngleY + (anchorX - event.getSceneX()));
        });

        scene3D.setOnScroll(event -> {
            double delta = event.getDeltaY();
            camera.setTranslateZ(camera.getTranslateZ() + delta * 0.5);
        });
    }

    private void highlightCelebrity(int index) {
        if (index < 0 || index >= celebrities.size()) return;

        Celebrity celebrity = celebrities.get(index).getCelebrity();

        // Update selected label
        selectedCelebLabel.setText(String.format("Selected: %s (ID: %s)",
                celebrity.getName(), celebrity.getId()));

        // Reset all thumbnails to default style (no effect/border)
        for (ImageView view : thumbnailViews) {
            view.setEffect(null);
        }

        // Highlight selected thumbnail with glow effect
        if (index < thumbnailViews.size()) {
            ImageView selectedView = thumbnailViews.get(index);
            javafx.scene.effect.Glow glow = new javafx.scene.effect.Glow(0.8);
            selectedView.setEffect(glow);
        }
    }

    public BorderPane getRoot() {
        return root;
    }
}

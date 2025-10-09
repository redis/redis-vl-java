package com.redis.vl.demos.facematch.ui;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import com.redis.vl.demos.facematch.service.CelebrityIndexService;
import com.redis.vl.demos.facematch.service.DataLoaderService;
import com.redis.vl.demos.facematch.service.DimensionalityReductionService;
import com.redis.vl.utils.vectorize.DJLFaceVectorizer;
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
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
    private ImageView droppedImageView = null;
    private List<ImageView> matchedThumbnails = new ArrayList<>();
    private List<Celebrity> matchedCelebrities = new ArrayList<>();
    private HBox matchButtonsPanel = null;
    private double lastMouseX, lastMouseY;
    private boolean isPanning = false;

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

        // Add drag-and-drop support
        setupDragAndDrop();

        // Create UI layout
        createLayout();

        // Load data asynchronously
        loadDataAsync();
    }

    private void setupDragAndDrop() {
        scene3D.setOnDragOver((DragEvent event) -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        scene3D.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                var file = db.getFiles().get(0);
                if (file.getName().toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
                    handleImageDrop(file.toURI().toString());
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleImageDrop(String imageUrl) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> statusLabel.setText("Processing image..."));
                statusLabel.setVisible(true);

                // Load the dropped image
                Image droppedImage = new Image(imageUrl);

                // Generate embedding using RedisVL DJL Face vectorizer
                java.io.InputStream imageStream = new java.net.URL(imageUrl).openStream();
                DJLFaceVectorizer vectorizer = new DJLFaceVectorizer();
                float[] queryEmbedding = vectorizer.embedImage(imageStream);
                vectorizer.close();
                imageStream.close();

                // Perform similarity search
                Platform.runLater(() -> statusLabel.setText("Searching for similar faces..."));

                // Connect to Redis
                HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
                UnifiedJedis jedis = new UnifiedJedis(hostAndPort);
                CelebrityIndexService indexService = new CelebrityIndexService(jedis);
                indexService.createIndex(); // Initialize the index

                // Search for top 5 similar celebrities
                var faceMatches = indexService.findSimilarFaces(queryEmbedding, 5);
                List<Celebrity> matches = faceMatches.stream()
                    .map(fm -> fm.getCelebrity())
                    .collect(java.util.stream.Collectors.toList());

                // Highlight matches in UI
                Platform.runLater(() -> {
                    highlightMatches(matches, droppedImage);
                    statusLabel.setText(String.format("Found %d similar celebrities!", matches.size()));

                    // Fade out status after 3 seconds
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
                    pause.setOnFinished(e -> statusLabel.setVisible(false));
                    pause.play();
                });

                jedis.close();
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("Error processing image: " + e.getMessage());
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
                });
            }
        }).start();
    }

    private void highlightMatches(List<Celebrity> matches) {
        highlightMatches(matches, null);
    }

    private void highlightMatches(List<Celebrity> matches, Image droppedImage) {
        System.out.println("DEBUG: highlightMatches called with " + matches.size() + " matches");

        // Remove old dropped image and clear match buttons
        if (droppedImageView != null) {
            root3D.getChildren().remove(droppedImageView);
            droppedImageView = null;
        }
        matchedThumbnails.clear();
        matchedCelebrities.clear();
        if (matchButtonsPanel != null) {
            matchButtonsPanel.getChildren().clear();
            matchButtonsPanel.setVisible(false);
            matchButtonsPanel.setManaged(false);
        }

        // Reset all thumbnails and restore click handlers
        for (ImageView view : thumbnailViews) {
            view.setEffect(null);
            // Don't clear style here - it will be set below for matches
            view.setScaleX(1.0);
            view.setScaleY(1.0);

            // Restore original click handler
            view.setOnMouseClicked(event -> {
                Celebrity clicked = (Celebrity) view.getUserData();
                if (clicked != null) {
                    highlightCelebrity(clicked, view);
                }
            });
        }

        // Add dropped image at center if provided (larger size)
        if (droppedImage != null) {
            // Input image always at Z=0 (center)
            double inputImageZ = 0;

            droppedImageView = new ImageView(droppedImage);
            droppedImageView.setFitWidth(100);  // Increased from 60 to 100
            droppedImageView.setFitHeight(100);
            droppedImageView.setPreserveRatio(true);
            droppedImageView.setTranslateX(0);  // Center position
            droppedImageView.setTranslateY(0);
            droppedImageView.setTranslateZ(inputImageZ);

            // PUSH ALL THUMBNAILS BEHIND THE INPUT IMAGE
            for (ImageView thumb : thumbnailViews) {
                double currentZ = thumb.getTranslateZ();
                // If thumbnail is in front or at same level, push it back
                if (currentZ >= inputImageZ) {
                    thumb.setTranslateZ(currentZ - 500);
                }
            }

            System.out.println("DEBUG: Input image at Z=" + inputImageZ + ", pushed all thumbnails behind");

            // Add prominent red border using effect instead of CSS (ImageView doesn't support -fx-border)
            javafx.scene.effect.InnerShadow redBorder = new javafx.scene.effect.InnerShadow();
            redBorder.setColor(Color.web("#EB352A"));
            redBorder.setWidth(12);  // Doubled from 6
            redBorder.setHeight(12); // Doubled from 6
            redBorder.setOffsetX(0);
            redBorder.setOffsetY(0);
            droppedImageView.setEffect(redBorder);

            // Make dropped image non-selectable
            droppedImageView.setMouseTransparent(false);
            droppedImageView.setOnMouseClicked(event -> {
                // Ignore clicks on dropped image
                event.consume();
            });

            root3D.getChildren().add(droppedImageView);

            // Position camera using simple iterative algorithm for 75% visibility
            rotateX.setAngle(0);
            rotateY.setAngle(0);
            camera.setTranslateX(0);
            camera.setTranslateY(0);

            // Start camera far away
            double cameraZ = -2000;
            double targetVisibility = 0.75;
            double step = 50; // Move camera closer by 50 units each iteration
            double minSafeDistance = 500; // Keep camera at least 500 units away from input

            // Keep moving camera closer until input image is 75% visible
            for (int i = 0; i < 100; i++) {
                double visibility = calculateVisibility(droppedImageView, thumbnailViews, cameraZ);
                System.out.println("DEBUG: Iteration " + i + ": cameraZ=" + cameraZ + ", visibility=" + (visibility * 100) + "%");

                if (visibility >= targetVisibility) {
                    System.out.println("DEBUG: Target visibility achieved!");
                    break;
                }

                // Move camera closer
                cameraZ += step;

                // Don't get too close to input image
                if (cameraZ >= (inputImageZ - minSafeDistance)) {
                    System.out.println("DEBUG: Camera reached safe distance limit");
                    cameraZ = inputImageZ - minSafeDistance;
                    break;
                }
            }

            camera.setTranslateZ(cameraZ);
            System.out.println("DEBUG: Final camera position Z=" + cameraZ);

            // Create "Center" button for returning to input image
            Button centerButton = new Button("⌖");  // Center/target icon
            centerButton.setStyle(
                "-fx-background-color: #2196F3;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-min-width: 40px;" +
                "-fx-min-height: 30px;"
            );
            centerButton.setOnAction(e -> {
                if (droppedImageView != null) {
                    centerAndBringForward(droppedImageView);
                }
            });
            matchButtonsPanel.getChildren().add(centerButton);
            matchButtonsPanel.setVisible(true);
            matchButtonsPanel.setManaged(true);
        }

        // Highlight top 3 matches with borders and connecting lines
        int connectCount = Math.min(3, matches.size());
        for (int i = 0; i < connectCount; i++) {
            Celebrity match = matches.get(i);
            System.out.println("DEBUG: Looking for match #" + (i+1) + ": " + match.getName() + " (ID: " + match.getId() + ")");

            // Find the corresponding thumbnail
            for (int j = 0; j < celebrities.size(); j++) {
                if (celebrities.get(j).getCelebrity().getId().equals(match.getId())) {
                    ImageView thumbnail = thumbnailViews.get(j);
                    System.out.println("DEBUG: Found match at index " + j + ", applying border and connection");

                    // Add solid colored border using effect (ImageView doesn't support CSS borders in 3D)
                    Color borderColor;
                    if (i == 0) {
                        borderColor = Color.web("#00ff00");  // Best match: green
                    } else if (i == 1) {
                        borderColor = Color.web("#ffa500");  // Second: orange
                    } else {
                        borderColor = Color.web("#00ffff");  // Third: cyan
                    }
                    javafx.scene.effect.InnerShadow border = new javafx.scene.effect.InnerShadow();
                    border.setColor(borderColor);
                    border.setWidth(12);  // Doubled from 6
                    border.setHeight(12); // Doubled from 6
                    border.setOffsetX(0);
                    border.setOffsetY(0);
                    thumbnail.setEffect(border);

                    // Scale up match
                    double scale = 1.3 - (i * 0.1);
                    thumbnail.setScaleX(scale);
                    thumbnail.setScaleY(scale);

                    // Store matched thumbnail with celebrity data
                    matchedThumbnails.add(thumbnail);
                    matchedCelebrities.add(match);

                    // Store celebrity in thumbnail's UserData
                    thumbnail.setUserData(match);

                    // Disable click handler on matched thumbnails to preserve highlight
                    thumbnail.setOnMouseClicked(event -> {
                        event.consume(); // Block the click
                    });

                    String bgColor = i == 0 ? "#00ff00" : i == 1 ? "#ffa500" : "#00ffff";
                    Button matchButton = new Button(String.valueOf(i + 1));
                    matchButton.setStyle(
                        "-fx-background-color: " + bgColor + ";" +
                        "-fx-text-fill: black;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-min-width: 40px;" +
                        "-fx-min-height: 30px;"
                    );

                    final ImageView targetThumb = thumbnail;
                    matchButton.setOnAction(e -> centerAndBringForward(targetThumb));

                    if (matchButtonsPanel != null) {
                        matchButtonsPanel.getChildren().add(matchButton);
                    }

                    break;
                }
            }
        }
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
        // Center: 3D cloud visualization (responsive)
        StackPane center = new StackPane(scene3D);
        center.setStyle("-fx-background-color: #1e1e1e;");

        // Make SubScene responsive to window size
        scene3D.widthProperty().bind(center.widthProperty());
        scene3D.heightProperty().bind(center.heightProperty());

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

        Label control2 = new Label("• Shift+Drag: Pan view");
        control2.setStyle("-fx-text-fill: #aaa;");

        Label control3 = new Label("• Drop image: Find matches");
        control3.setStyle("-fx-text-fill: #aaa;");

        Separator sepMatches = new Separator(Orientation.HORIZONTAL);

        Label matchesLabel = new Label("Matches");
        matchesLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Create matches navigation panel (horizontal layout)
        matchButtonsPanel = new HBox(5);
        matchButtonsPanel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        matchButtonsPanel.setVisible(false);
        matchButtonsPanel.setManaged(false);

        rightPanel.getChildren().addAll(
            title, subtitle, sep1,
            statsLabel, celebCount, selectedCelebLabel, embeddingDim, sep2,
            controlsLabel, control1, control2, control3, sepMatches,
            matchesLabel, matchButtonsPanel
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

            // Create flat square thumbnail
            Image avatarImage = createCelebrityAvatar(celeb3D.getCelebrity());
            ImageView imageView = new ImageView(avatarImage);
            imageView.setFitWidth(40);  // Square thumbnail size
            imageView.setFitHeight(40);
            imageView.setPreserveRatio(true);

            // Position in 3D space based on t-SNE coordinates
            // The Z coordinate from t-SNE already provides depth variation
            imageView.setTranslateX(celeb3D.getX());
            imageView.setTranslateY(celeb3D.getY());
            imageView.setTranslateZ(celeb3D.getZ());

            // Store celebrity reference in ImageView's user data
            imageView.setUserData(celeb3D.getCelebrity());

            // Add click handler for highlighting
            imageView.setOnMouseClicked(event -> {
                Celebrity clicked = (Celebrity) imageView.getUserData();
                highlightCelebrity(clicked, imageView);
            });

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
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
            isPanning = event.isShiftDown();
        });

        scene3D.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - lastMouseX;
            double deltaY = event.getSceneY() - lastMouseY;

            if (isPanning || event.isShiftDown()) {
                // Pan mode: translate the scene
                root3D.setTranslateX(root3D.getTranslateX() + deltaX);
                root3D.setTranslateY(root3D.getTranslateY() + deltaY);
            } else {
                // Rotate mode
                rotateX.setAngle(anchorAngleX - (anchorY - event.getSceneY()));
                rotateY.setAngle(anchorAngleY + (anchorX - event.getSceneX()));
            }

            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });

        scene3D.setOnScroll(event -> {
            double delta = event.getDeltaY();
            camera.setTranslateZ(camera.getTranslateZ() + delta * 0.5);
        });
    }

    private void highlightCelebrity(Celebrity celebrity, ImageView clickedView) {
        System.out.println("DEBUG: Clicked celebrity=" + celebrity.getName() + ", id=" + celebrity.getId());

        // Update selected label
        selectedCelebLabel.setText(String.format("Selected: %s (ID: %s)",
                celebrity.getName(), celebrity.getId()));

        // Reset all thumbnails to default style (no effect/border)
        for (ImageView view : thumbnailViews) {
            view.setEffect(null);
        }

        // Highlight selected thumbnail with glow effect
        javafx.scene.effect.Glow glow = new javafx.scene.effect.Glow(0.8);
        clickedView.setEffect(glow);
    }

    /**
     * Center camera on a specific thumbnail
     */
    private void centerOnThumbnail(ImageView thumbnail) {
        // Animate camera to center on thumbnail
        javafx.animation.TranslateTransition transition = new javafx.animation.TranslateTransition(
            javafx.util.Duration.seconds(0.5), root3D
        );
        transition.setToX(-thumbnail.getTranslateX());
        transition.setToY(-thumbnail.getTranslateY());
        transition.play();

        // Zoom in
        javafx.animation.TranslateTransition zoomTransition = new javafx.animation.TranslateTransition(
            javafx.util.Duration.seconds(0.5), camera
        );
        zoomTransition.setToZ(-500);
        zoomTransition.play();
    }

    private void centerAndBringForward(ImageView target) {
        // Step 1: Reset viewport to default orientation (show full cloud)
        // Reset rotations directly
        rotateX.setAngle(0);
        rotateY.setAngle(0);

        // Reset root3D pan to default
        javafx.animation.TranslateTransition resetPan = new javafx.animation.TranslateTransition(
            javafx.util.Duration.seconds(0.3), root3D
        );
        resetPan.setToX(0);
        resetPan.setToY(0);

        // Reset camera zoom to see full cloud
        javafx.animation.TranslateTransition resetZoom = new javafx.animation.TranslateTransition(
            javafx.util.Duration.seconds(0.3), camera
        );
        resetZoom.setToZ(-2000);

        // Step 2: After reset completes, center on target within viewport
        resetZoom.setOnFinished(e -> {
            // Scale up target for visibility
            target.setScaleX(2.5);
            target.setScaleY(2.5);

            // Calculate viewport dimensions (SubScene width/height)
            double viewportWidth = scene3D.getWidth();
            double viewportHeight = scene3D.getHeight();

            // Now animate to center the target in the viewport CENTER (not considering right panel)
            javafx.animation.TranslateTransition panTransition = new javafx.animation.TranslateTransition(
                javafx.util.Duration.seconds(0.5), root3D
            );
            panTransition.setToX(-target.getTranslateX());
            panTransition.setToY(-target.getTranslateY());

            // Move camera VERY CLOSE to target's Z position
            // Calculate zoom to be 15% of viewport size
            // Field of view formula: distance = (viewportSize / 2) / tan(FOV / 2)
            // For 15% of viewport, target should fill 15% of height
            double targetZ = target.getTranslateZ();

            // Get actual image size - handle both thumbnail (40px) and dropped (100px) images
            double imageSize = target.getFitWidth(); // Use width since they're square
            if (imageSize == 0) {
                imageSize = 100; // Default for dropped image
            }
            double targetHeight = imageSize * 2.5; // Scaled height

            // Calculate distance so target fills 15% of viewport height
            // tan(FOV/2) ≈ 0.414 for default JavaFX FOV of ~45 degrees
            double desiredCoverage = 0.15; // 15% of viewport
            double cameraDistance = (targetHeight / desiredCoverage) / (2 * 0.414);

            double cameraZ = targetZ - cameraDistance; // Position camera in front of target

            javafx.animation.TranslateTransition zoomTransition = new javafx.animation.TranslateTransition(
                javafx.util.Duration.seconds(0.5), camera
            );
            zoomTransition.setToZ(cameraZ);

            // Play pan and zoom together
            javafx.animation.ParallelTransition focusTransition = new javafx.animation.ParallelTransition(
                panTransition, zoomTransition
            );

            focusTransition.play();
        });

        // Start the reset sequence
        javafx.animation.ParallelTransition resetTransition = new javafx.animation.ParallelTransition(
            resetPan, resetZoom
        );
        resetTransition.play();

        // Update Statistics panel
        Celebrity selectedCeleb = (Celebrity) target.getUserData();
        if (selectedCeleb != null) {
            selectedCelebLabel.setText(String.format("Selected: %s (ID: %s)",
                    selectedCeleb.getName(), selectedCeleb.getId()));
        }
    }


    /**
     * Calculate how much of the input image is visible from a camera position.
     * Counts thumbnails between camera and input image that would occlude it.
     *
     * @param inputImage The input image
     * @param allThumbnails All celebrity thumbnails
     * @param cameraZ Camera Z position
     * @return Visibility percentage (0.0 to 1.0)
     */
    private double calculateVisibility(ImageView inputImage, List<ImageView> allThumbnails, double cameraZ) {
        double inputZ = inputImage.getTranslateZ();
        double inputX = inputImage.getTranslateX();
        double inputY = inputImage.getTranslateY();

        // Count thumbnails between camera and input that would occlude
        int occluderCount = 0;
        for (ImageView thumb : allThumbnails) {
            double thumbZ = thumb.getTranslateZ();

            // Thumbnail occludes if it's between camera and input image
            if (thumbZ > cameraZ && thumbZ < inputZ) {
                // Check if thumbnail is close to center (would actually occlude)
                double thumbX = thumb.getTranslateX();
                double thumbY = thumb.getTranslateY();
                double distance2D = Math.sqrt(
                    Math.pow(thumbX - inputX, 2) + Math.pow(thumbY - inputY, 2)
                );

                // Only count as occluder if within 200 units in X-Y plane
                if (distance2D < 200) {
                    occluderCount++;
                }
            }
        }

        // Simple visibility calculation: each occluder reduces visibility by ~10%
        double visibility = 1.0 - (occluderCount * 0.10);
        if (visibility < 0) visibility = 0;

        return visibility;
    }

    public BorderPane getRoot() {
        return root;
    }
}

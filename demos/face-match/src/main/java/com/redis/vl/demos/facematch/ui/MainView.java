package com.redis.vl.demos.facematch.ui;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import com.redis.vl.demos.facematch.service.CelebrityIndexService;
import com.redis.vl.demos.facematch.service.DataLoaderService;
import com.redis.vl.demos.facematch.service.DimensionalityReductionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.*;
import redis.clients.jedis.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Main view containing 3D visualization and controls.
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
        camera.setTranslateZ(-500);

        scene3D = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        scene3D.setFill(Color.rgb(30, 30, 30));
        scene3D.setCamera(camera);

        // Status label for showing loading progress
        statusLabel = new Label("Loading celebrities...");
        statusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-weight: bold;");
        statusLabel.setMouseTransparent(true);

        // Add mouse controls for 3D scene
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
                    List<Celebrity> celebrities = dataLoader.generateSampleCelebrities(100);

                    Platform.runLater(() -> statusLabel.setText("Creating Redis index..."));
                    indexService.createIndex();

                    Platform.runLater(() -> statusLabel.setText("Indexing celebrities..."));
                    indexService.indexCelebrities(celebrities);

                    Platform.runLater(() -> statusLabel.setText("Reducing dimensions..."));
                    DimensionalityReductionService reducer = new DimensionalityReductionService();
                    this.celebrities = reducer.reduceTo3D(celebrities);
                } else {
                    // Load existing data
                    Platform.runLater(() -> statusLabel.setText("Loading from Redis..."));
                    List<Celebrity> celebrities = indexService.getAllCelebrities();

                    Platform.runLater(() -> statusLabel.setText("Reducing dimensions..."));
                    DimensionalityReductionService reducer = new DimensionalityReductionService();
                    this.celebrities = reducer.reduceTo3D(celebrities);
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
        // Left side: 3D visualization
        StackPane center = new StackPane(scene3D);
        center.setStyle("-fx-background-color: #1e1e1e;");

        // Add status label
        center.getChildren().add(statusLabel);

        // Drag-drop hint overlay
        Label dropHint = new Label("Drag & drop a face image here");
        dropHint.setStyle("-fx-font-size: 18px; -fx-text-fill: rgba(255,255,255,0.3); -fx-font-weight: bold;");
        dropHint.setMouseTransparent(true);
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

        Label embeddingDim = new Label("Embedding Dimension: 512");
        embeddingDim.setStyle("-fx-text-fill: #aaa;");

        Label reducedDim = new Label("Reduced Dimension: 3D (PCA)");
        reducedDim.setStyle("-fx-text-fill: #aaa;");

        Separator sep2 = new Separator(Orientation.HORIZONTAL);

        Label controlsLabel = new Label("3D Controls");
        controlsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label control1 = new Label("• Drag: Rotate view");
        control1.setStyle("-fx-text-fill: #aaa;");

        Label control2 = new Label("• Scroll: Zoom in/out");
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
            statsLabel, celebCount, embeddingDim, reducedDim, sep2,
            controlsLabel, control1, control2, control3, sep3,
            featuresLabel, feature1, feature2, feature3
        );

        root.setRight(rightPanel);

        // Bind scene3D size to center pane
        scene3D.widthProperty().bind(center.widthProperty());
        scene3D.heightProperty().bind(center.heightProperty());
    }

    private void renderCelebrities() {
        if (celebrities == null) return;

        // Clear existing spheres
        root3D.getChildren().clear();

        for (Celebrity3D celeb : celebrities) {
            Sphere sphere = new Sphere(3);
            sphere.setTranslateX(celeb.getX());
            sphere.setTranslateY(celeb.getY());
            sphere.setTranslateZ(celeb.getZ());

            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(Color.rgb(100, 150, 255));
            material.setSpecularColor(Color.WHITE);
            sphere.setMaterial(material);

            // Add tooltip with celebrity name
            Tooltip tooltip = new Tooltip(celeb.getCelebrity().getName());
            Tooltip.install(sphere, tooltip);

            root3D.getChildren().add(sphere);
        }
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

    public BorderPane getRoot() {
        return root;
    }
}

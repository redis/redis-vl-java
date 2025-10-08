package com.redis.vl.demos.facematch.ui;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main view containing 3D visualization and controls.
 */
public class MainView {

    private final BorderPane root;
    private final SubScene scene3D;
    private final Group root3D;
    private final PerspectiveCamera camera;
    private final List<Celebrity3D> celebrities;
    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;
    private final Rotate rotateX;
    private final Rotate rotateY;

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

        // Generate sample celebrity data
        celebrities = generateSampleCelebrities();

        // Render 3D points
        renderCelebrities();

        // Add mouse controls for 3D scene
        addMouseControls();

        // Create UI layout
        createLayout();
    }

    private void createLayout() {
        // Left side: 3D visualization
        StackPane center = new StackPane(scene3D);
        center.setStyle("-fx-background-color: #1e1e1e;");

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

        Label celebCount = new Label("Celebrities: " + celebrities.size());
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

    private List<Celebrity3D> generateSampleCelebrities() {
        List<Celebrity3D> list = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for reproducibility

        // Generate 100 sample celebrities with random embeddings and 3D coordinates
        for (int i = 0; i < 100; i++) {
            float[] embedding = new float[512];
            for (int j = 0; j < 512; j++) {
                embedding[j] = (float) random.nextGaussian();
            }

            Celebrity celebrity = new Celebrity(
                "celeb_" + i,
                "Celebrity " + i,
                "http://example.com/image_" + i + ".jpg",
                embedding
            );

            // Generate 3D coordinates in a sphere-like distribution
            double x = random.nextGaussian() * 100;
            double y = random.nextGaussian() * 100;
            double z = random.nextGaussian() * 100;

            list.add(new Celebrity3D(celebrity, x, y, z));
        }

        return list;
    }

    private void renderCelebrities() {
        for (Celebrity3D celeb : celebrities) {
            Sphere sphere = new Sphere(3);
            sphere.setTranslateX(celeb.getX());
            sphere.setTranslateY(celeb.getY());
            sphere.setTranslateZ(celeb.getZ());

            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(Color.rgb(100, 150, 255));
            material.setSpecularColor(Color.WHITE);
            sphere.setMaterial(material);

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

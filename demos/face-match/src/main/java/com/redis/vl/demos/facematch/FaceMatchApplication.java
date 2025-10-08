package com.redis.vl.demos.facematch;

import com.redis.vl.demos.facematch.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX application for Celebrity Face Match demo.
 *
 * This demo showcases RedisVL4J vector similarity search with:
 * - 3D visualization of celebrity face embeddings (t-SNE/PCA reduction)
 * - Drag-and-drop face image matching
 * - Visual highlighting of top-K nearest neighbors
 */
public class FaceMatchApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();

        Scene scene = new Scene(mainView.getRoot(), 1200, 800);
        primaryStage.setTitle("Celebrity Face Match - RedisVL4J Demo");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

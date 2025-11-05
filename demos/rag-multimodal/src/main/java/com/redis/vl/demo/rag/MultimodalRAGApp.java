package com.redis.vl.demo.rag;

import com.redis.vl.demo.rag.service.ServiceFactory;
import com.redis.vl.demo.rag.ui.ChatController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import java.util.Optional;

/**
 * Multimodal RAG demo application using RedisVL and LangChain4J.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Chat interface with message bubbles
 *   <li>Cost tracking per message
 *   <li>LangCache toggle for semantic caching
 *   <li>PDF ingestion with multimodal support
 *   <li>Multiple LLM provider support
 * </ul>
 */
public class MultimodalRAGApp extends Application {

  private ChatController chatController;
  private ServiceFactory serviceFactory;

  @Override
  public void start(Stage primaryStage) throws Exception {
    // Get Redis connection details from environment or use defaults
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6399"));

    // Initialize services
    serviceFactory = new ServiceFactory();
    try {
      serviceFactory.initialize(redisHost, redisPort);
      System.out.println("Connected to Redis at " + redisHost + ":" + redisPort);
    } catch (Exception e) {
      showErrorDialog(
          "Redis Connection Failed",
          "Could not connect to Redis at "
              + redisHost
              + ":"
              + redisPort
              + "\n\n"
              + "Error: "
              + e.getMessage()
              + "\n\n"
              + "Please ensure Redis is running and try again.");
      return;
    }

    // Create UI
    chatController = new ChatController();
    chatController.setServiceFactory(serviceFactory);

    Scene scene = new Scene(chatController, 1200, 800);
    scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

    primaryStage.setTitle("RedisVL Multimodal RAG Demo");
    primaryStage.setScene(scene);
    primaryStage.setOnCloseRequest(
        e -> {
          chatController.shutdown();
          if (serviceFactory != null) {
            serviceFactory.close();
          }
        });
    primaryStage.show();
  }

  private void showErrorDialog(String title, String content) {
    Alert alert = new Alert(AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(content);
    alert.showAndWait();
  }

  public static void main(String[] args) {
    // Print usage information
    System.out.println("RedisVL Multimodal RAG Demo");
    System.out.println("===========================");
    System.out.println();
    System.out.println("Environment Variables:");
    System.out.println("  REDIS_HOST - Redis host (default: localhost)");
    System.out.println("  REDIS_PORT - Redis port (default: 6399)");
    System.out.println();
    System.out.println("Starting application...");

    launch(args);
  }
}

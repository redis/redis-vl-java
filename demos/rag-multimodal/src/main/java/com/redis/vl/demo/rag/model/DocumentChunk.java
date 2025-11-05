package com.redis.vl.demo.rag.model;

/**
 * Represents a chunk of a document with multimodal content.
 *
 * @param id Unique chunk identifier
 * @param documentId Parent document identifier
 * @param pageNumber Page number (for PDFs)
 * @param textSummary Text summary for embedding/search
 * @param imageData Raw image data (can be null)
 * @param chunkType Type of chunk (TEXT, IMAGE, TABLE)
 */
public record DocumentChunk(
    String id, String documentId, int pageNumber, String textSummary, byte[] imageData, ChunkType chunkType) {

  public enum ChunkType {
    TEXT,
    IMAGE,
    TABLE
  }

  /**
   * Creates a text chunk.
   *
   * @param documentId Document ID
   * @param pageNumber Page number
   * @param textContent Text content
   * @return Text chunk
   */
  public static DocumentChunk text(String documentId, int pageNumber, String textContent) {
    return new DocumentChunk(
        generateId(), documentId, pageNumber, textContent, null, ChunkType.TEXT);
  }

  /**
   * Creates an image chunk with summary.
   *
   * @param documentId Document ID
   * @param pageNumber Page number
   * @param summary Text summary for search
   * @param imageData Raw image bytes
   * @return Image chunk
   */
  public static DocumentChunk image(
      String documentId, int pageNumber, String summary, byte[] imageData) {
    return new DocumentChunk(
        generateId(), documentId, pageNumber, summary, imageData, ChunkType.IMAGE);
  }

  private static String generateId() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * Checks if this chunk has image data.
   *
   * @return true if image data present
   */
  public boolean hasImage() {
    return imageData != null && imageData.length > 0;
  }
}

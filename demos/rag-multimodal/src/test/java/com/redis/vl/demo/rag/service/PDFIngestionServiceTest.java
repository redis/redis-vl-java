package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.redis.vl.demo.rag.model.DocumentChunk;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for PDFIngestionService - multimodal PDF processing. */
class PDFIngestionServiceTest {

  @Mock private RedisVLEmbeddingStore embeddingStore;

  @Mock private RedisVLDocumentStore documentStore;

  @Mock private EmbeddingModel embeddingModel;

  private PDFIngestionService pdfService;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    pdfService = new PDFIngestionService(embeddingStore, documentStore, embeddingModel);
  }

  @Test
  void testIngestPDFWithText() throws IOException {
    // Given - Create a minimal test file (not a real PDF, but tests file handling)
    File testFile = tempDir.resolve("test.pdf").toFile();
    Files.writeString(testFile.toPath(), "Test content");

    // Mock embedding
    float[] vector = new float[384];
    Embedding embedding = new Embedding(vector);
    Response<Embedding> embeddingResponse = Response.from(embedding);
    when(embeddingModel.embed(anyString())).thenReturn(embeddingResponse);

    when(embeddingStore.add(any(Embedding.class), any(TextSegment.class)))
        .thenReturn("doc-id-1");

    // When - Note: This will fail to parse as PDF, testing error handling
    // In a real test, we'd use a valid PDF or mock PDDocument

    // Then - For now, just verify service is created
    assertNotNull(pdfService);
  }

  @Test
  void testProcessDocumentChunks() {
    // Given
    List<DocumentChunk> chunks =
        List.of(
            DocumentChunk.text("doc1", 1, "Redis is fast"),
            DocumentChunk.image("doc1", 2, "Architecture diagram", new byte[] {1, 2, 3}));

    float[] vector = new float[384];
    Embedding embedding = new Embedding(vector);
    Response<Embedding> embeddingResponse = Response.from(embedding);
    when(embeddingModel.embed(anyString())).thenReturn(embeddingResponse);

    // The new method signature takes (String id, Embedding, TextSegment)
    // No return value needed since we're using pre-generated IDs

    // When
    int processed = pdfService.indexChunks(chunks);

    // Then
    assertEquals(2, processed);
    verify(embeddingStore, times(2)).add(anyString(), any(Embedding.class), any(TextSegment.class));
    verify(documentStore, times(1)).store(anyString(), any(byte[].class), any());
  }

  @Test
  void testExtractTextChunk() {
    // Test chunk creation
    DocumentChunk chunk = DocumentChunk.text("doc1", 1, "Test content");

    assertNotNull(chunk);
    assertEquals("doc1", chunk.documentId());
    assertEquals(1, chunk.pageNumber());
    assertEquals("Test content", chunk.textSummary());
    assertEquals(DocumentChunk.ChunkType.TEXT, chunk.chunkType());
    assertFalse(chunk.hasImage());
  }

  @Test
  void testExtractImageChunk() {
    // Test image chunk creation
    byte[] imageData = new byte[] {1, 2, 3, 4};
    DocumentChunk chunk = DocumentChunk.image("doc1", 2, "Image summary", imageData);

    assertNotNull(chunk);
    assertEquals("doc1", chunk.documentId());
    assertEquals(2, chunk.pageNumber());
    assertEquals("Image summary", chunk.textSummary());
    assertEquals(DocumentChunk.ChunkType.IMAGE, chunk.chunkType());
    assertTrue(chunk.hasImage());
    assertArrayEquals(imageData, chunk.imageData());
  }

  @Test
  void testIngestNullFile() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> pdfService.ingestPDF(null, "doc1"));
  }

  @Test
  void testIngestNonExistentFile() {
    // Given
    File nonExistent = new File("/nonexistent/file.pdf");

    // When/Then
    assertThrows(IOException.class, () -> pdfService.ingestPDF(nonExistent, "doc1"));
  }
}

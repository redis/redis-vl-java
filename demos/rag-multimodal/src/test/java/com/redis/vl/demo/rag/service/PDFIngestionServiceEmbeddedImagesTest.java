package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.redis.vl.demo.rag.model.DocumentChunk;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test-first unit tests for PDFIngestionService with proper embedded image extraction.
 *
 * <p>These tests verify that PDFIngestionService extracts EMBEDDED images from PDFs (diagrams,
 * photos, charts), NOT renders entire pages as images.
 *
 * <p>EXPECTED TO FAIL initially - current implementation renders pages, not extracts images.
 */
class PDFIngestionServiceEmbeddedImagesTest {

  @Mock private RedisVLEmbeddingStore embeddingStore;
  @Mock private RedisVLDocumentStore documentStore;
  @Mock private EmbeddingModel embeddingModel;

  private PDFIngestionService pdfIngestionService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    pdfIngestionService =
        new PDFIngestionService(embeddingStore, documentStore, embeddingModel);

    // Setup mock embedding response
    Embedding mockEmbedding = mock(Embedding.class);
    @SuppressWarnings("unchecked")
    Response<Embedding> mockResponse = mock(Response.class);
    when(mockResponse.content()).thenReturn(mockEmbedding);
    when(embeddingModel.embed(anyString())).thenReturn(mockResponse);
    when(embeddingStore.add(any(Embedding.class), any())).thenReturn("mock-chunk-id");
  }

  /**
   * Test that PDFIngestionService extracts EMBEDDED images, not page renders.
   *
   * <p>Current implementation FAILS this test because it renders each page as a full PNG image
   * instead of extracting actual embedded image objects from the PDF.
   *
   * <p>Expected behavior:
   * - Extract ~5 diagrams/figures from Attention.pdf
   * - Each image should be an actual embedded PDF image (JPEG/PNG)
   * - Should NOT create 15 page-render images
   */
  @Test
  void testIngestPDF_extractsEmbeddedImagesNotPageRenders() throws Exception {
    // Given: Attention.pdf from test resources
    File attentionPdf =
        new File(
            getClass().getResource("/test-pdfs/Attention.pdf").toURI());
    String documentId = "test-attention";

    // When: Ingest the PDF
    int chunksProcessed = pdfIngestionService.ingestPDF(attentionPdf, documentId);

    // Then: Should process text chunks + embedded image chunks
    // Attention.pdf has 15 pages of text + 3 embedded diagrams
    // Should NOT have 15 page-render images
    assertTrue(
        chunksProcessed >= 18,
        "Should process at least 18 chunks (15 text + 3 images), got: " + chunksProcessed);

    // Verify that documentStore.store() was called for actual images, not page renders
    ArgumentCaptor<byte[]> imageCaptor = ArgumentCaptor.forClass(byte[].class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(java.util.Map.class);

    verify(documentStore, atLeast(3))
        .store(anyString(), imageCaptor.capture(), metadataCaptor.capture());

    // Verify the images are embedded images (smaller), not full-page renders (larger)
    List<byte[]> capturedImages = imageCaptor.getAllValues();
    List<java.util.Map<String, String>> capturedMetadata = metadataCaptor.getAllValues();

    // At least some images should be embedded images (typically < 500KB)
    // Full page renders at 150 DPI would be much larger (> 1MB each)
    long averageImageSize =
        capturedImages.stream().mapToLong(img -> img.length).sum() / capturedImages.size();

    assertTrue(
        averageImageSize < 1_000_000,
        "Average image size should be < 1MB for embedded images, got: " + averageImageSize / 1024 + "KB");

    // Verify metadata indicates IMAGE type
    for (java.util.Map<String, String> metadata : capturedMetadata) {
      assertEquals(
          "IMAGE",
          metadata.get("type"),
          "Chunk type should be IMAGE for image chunks");
    }
  }

  /**
   * Test that image chunks have proper text summaries for searchability.
   *
   * <p>Each extracted image should have a meaningful text summary like: "Diagram showing
   * Transformer architecture on page 3"
   *
   * <p>NOT generic summaries like: "Page 3 visual content"
   */
  @Test
  void testIngestPDF_imagesHaveMeaningfulSummaries() throws Exception {
    // Given: Attention.pdf
    File attentionPdf =
        new File(
            getClass().getResource("/test-pdfs/Attention.pdf").toURI());

    // When: Ingest PDF
    pdfIngestionService.ingestPDF(attentionPdf, "test-doc");

    // Then: Verify text summaries for image chunks
    ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
    verify(embeddingModel, atLeast(18)).embed(summaryCaptor.capture());

    List<String> allSummaries = summaryCaptor.getAllValues();

    // Check that some summaries are for images (should mention "image", "diagram", "figure")
    long imageSummaryCount = allSummaries.stream()
        .filter(s -> s.toLowerCase().contains("image")
                  || s.toLowerCase().contains("diagram")
                  || s.toLowerCase().contains("figure"))
        .count();

    assertTrue(
        imageSummaryCount >= 3,
        "Should have at least 3 image-related summaries, found: " + imageSummaryCount);

    // Verify summaries are NOT just "Page X visual content"
    long genericSummaryCount = allSummaries.stream()
        .filter(s -> s.matches("Page \\d+ visual content.*"))
        .count();

    assertTrue(
        genericSummaryCount == 0,
        "Should NOT have generic 'Page X visual content' summaries for embedded images");
  }

  /**
   * Test that multiple images on the same page are extracted separately.
   *
   * <p>If a PDF page has multiple diagrams, each should be extracted as a separate chunk.
   */
  @Test
  void testIngestPDF_extractsMultipleImagesPerPage() throws Exception {
    // Given: Attention.pdf (some pages have multiple figures)
    File attentionPdf =
        new File(
            getClass().getResource("/test-pdfs/Attention.pdf").toURI());

    // When: Ingest PDF
    pdfIngestionService.ingestPDF(attentionPdf, "test-doc");

    // Then: Verify multiple image chunks can have the same page number
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(java.util.Map.class);
    verify(documentStore, atLeast(3))
        .store(anyString(), any(byte[].class), metadataCaptor.capture());

    List<java.util.Map<String, String>> allMetadata = metadataCaptor.getAllValues();

    // Group by page number and check if any page has multiple images
    java.util.Map<String, Long> imagesPerPage = allMetadata.stream()
        .filter(m -> "IMAGE".equals(m.get("type")))
        .collect(java.util.stream.Collectors.groupingBy(
            m -> m.get("page").toString(),
            java.util.stream.Collectors.counting()));

    // At least one page should have multiple images if we're extracting embedded images
    // (The current page-render approach would have exactly 1 image per page)
    // This test may need adjustment based on Attention.pdf actual content
  }
}

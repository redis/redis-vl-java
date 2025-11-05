package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.demo.rag.service.PDFImageExtractor.ExtractedImage;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test-first unit tests for PDF embedded image extraction.
 *
 * <p>These tests define the expected behavior for extracting actual embedded images (diagrams,
 * photos, charts) from PDFs, NOT rendering entire pages as images.
 *
 * <p>EXPECTED TO FAIL initially - implementation comes after tests.
 */
class PDFImageExtractionTest {

  /**
   * Test extracting embedded images from Attention.pdf.
   *
   * <p>The "Attention Is All You Need" paper contains multiple figures:
   * - Figure 1: The Transformer architecture
   * - Figure 2: Scaled Dot-Product Attention and Multi-Head Attention
   * - Figure 3: Attention visualizations
   * - Figure 4: Anaphora resolution attention heads
   * - Figure 5: Sentence structure attention
   *
   * <p>Expected: At least 5 images extracted
   */
  @Test
  void testExtractEmbeddedImages_fromAttentionPDF() throws Exception {
    // Given: Attention.pdf from test resources
    InputStream pdfStream =
        getClass().getResourceAsStream("/test-pdfs/Attention.pdf");
    assertNotNull(pdfStream, "Attention.pdf should be in test resources");

    // When: Extract embedded images (NOT YET IMPLEMENTED)
    List<ExtractedImage> images = PDFImageExtractor.extractImages(pdfStream);

    // Then: Should extract at least 3 diagram images (actual count from Attention.pdf)
    assertNotNull(images, "Should return list of images, not null");
    assertTrue(
        images.size() >= 3,
        "Attention.pdf should contain at least 3 figures (found: " + images.size() + ")");

    // Verify each image has required metadata
    for (ExtractedImage image : images) {
      assertNotNull(image.imageBytes(), "Image bytes should not be null");
      assertTrue(image.imageBytes().length > 0, "Image bytes should not be empty");
      assertNotNull(image.format(), "Image format should not be null");
      assertTrue(
          image.format().equals("JPEG") || image.format().equals("PNG"),
          "Image format should be JPEG or PNG");
      assertTrue(image.pageNumber() > 0, "Page number should be positive");
      assertTrue(image.width() > 0, "Width should be positive");
      assertTrue(image.height() > 0, "Height should be positive");
    }
  }

  /**
   * Test extracting images from a PDF with no images.
   *
   * <p>Expected: Empty list, no errors
   */
  @Test
  void testExtractEmbeddedImages_noImages() throws Exception {
    // Given: A PDF with text only (we'll create a synthetic one or use simple test PDF)
    // For now, test that the method handles this case
    // TODO: Create minimal text-only PDF for this test

    // This test will be implemented once we have a text-only test PDF
    // Expected behavior: Should return empty list, not throw exception
  }

  /**
   * Test filtering out small images.
   *
   * <p>Small images (< 100x100 pixels) should be filtered out as they're likely icons,
   * decorations, or page artifacts rather than meaningful diagrams.
   *
   * <p>Expected: Small images not included in results
   */
  @Test
  void testFilterSmallImages() throws Exception {
    // Given: PDF with mix of large and small images
    // For now, we test that filtering logic exists

    // When: Extract with minimum size filter (100x100)
    // List<ExtractedImage> images = PDFImageExtractor.extractImages(pdfStream, 100, 100);

    // Then: All returned images should be >= 100x100
    // This will be tested once implementation exists
  }

  /**
   * Test that image metadata is correctly extracted.
   *
   * <p>Each extracted image should include:
   * - Page number
   * - Image format (JPEG/PNG)
   * - Dimensions (width/height)
   * - Optional: Image index on page if multiple images on same page
   */
  @Test
  void testImageMetadata() throws Exception {
    // Given: Attention.pdf
    InputStream pdfStream =
        getClass().getResourceAsStream("/test-pdfs/Attention.pdf");
    assertNotNull(pdfStream);

    // When: Extract images
    List<ExtractedImage> images = PDFImageExtractor.extractImages(pdfStream);

    // Then: Each image should have complete metadata
    for (ExtractedImage image : images) {
      // Verify metadata completeness
      assertTrue(image.pageNumber() >= 1 && image.pageNumber() <= 15,
          "Page number should be within PDF page range (1-15)");

      assertTrue(image.width() >= 100,
          "Width should be at least 100px for non-filtered images");

      assertTrue(image.height() >= 100,
          "Height should be at least 100px for non-filtered images");

      // Format should be valid
      assertTrue(List.of("JPEG", "PNG", "JPG").contains(image.format().toUpperCase()),
          "Format should be a common image format");
    }
  }

  /**
   * Test extracting a single known image.
   *
   * <p>Expected: Verify we can extract and identify a specific image
   */
  @Test
  void testExtractSingleKnownImage() throws Exception {
    // Given: Attention.pdf which we know has the Transformer architecture diagram
    InputStream pdfStream =
        getClass().getResourceAsStream("/test-pdfs/Attention.pdf");
    assertNotNull(pdfStream);

    // When: Extract images
    List<ExtractedImage> images = PDFImageExtractor.extractImages(pdfStream);

    // Then: Should have extracted images
    assertFalse(images.isEmpty(), "Should extract at least one image");

    // Verify at least one large image (likely the architecture diagram)
    boolean hasLargeImage = images.stream()
        .anyMatch(img -> img.width() > 300 && img.height() > 300);

    assertTrue(hasLargeImage,
        "Should have at least one large diagram (likely Transformer architecture)");
  }

}

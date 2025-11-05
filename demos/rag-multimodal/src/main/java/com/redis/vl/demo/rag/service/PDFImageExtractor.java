package com.redis.vl.demo.rag.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for extracting embedded images from PDF documents.
 *
 * <p>Extracts actual embedded images (diagrams, photos, charts) from PDF content,
 * NOT page renders.
 *
 * <p>Uses Apache PDFBox to access PDF XObject images.
 */
public class PDFImageExtractor {

  private static final Logger log = LoggerFactory.getLogger(PDFImageExtractor.class);

  // Minimum image dimensions to filter out small icons/decorations
  private static final int DEFAULT_MIN_WIDTH = 100;
  private static final int DEFAULT_MIN_HEIGHT = 100;

  /**
   * Extracts embedded images from a PDF input stream.
   *
   * @param pdfStream PDF input stream
   * @return List of extracted images with metadata
   * @throws IOException if PDF processing fails
   */
  public static List<ExtractedImage> extractImages(InputStream pdfStream) throws IOException {
    return extractImages(pdfStream, DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT);
  }

  /**
   * Extracts embedded images from a PDF input stream with custom size filter.
   *
   * @param pdfStream PDF input stream
   * @param minWidth Minimum image width in pixels
   * @param minHeight Minimum image height in pixels
   * @return List of extracted images with metadata
   * @throws IOException if PDF processing fails
   */
  public static List<ExtractedImage> extractImages(
      InputStream pdfStream, int minWidth, int minHeight) throws IOException {

    List<ExtractedImage> extractedImages = new ArrayList<>();

    try (PDDocument document = Loader.loadPDF(pdfStream.readAllBytes())) {
      int pageNumber = 1;

      for (PDPage page : document.getPages()) {
        List<ExtractedImage> pageImages = extractImagesFromPage(page, pageNumber, minWidth, minHeight);
        extractedImages.addAll(pageImages);
        pageNumber++;
      }
    }

    log.info("Extracted {} embedded images from PDF", extractedImages.size());
    return extractedImages;
  }

  /**
   * Extracts images from a single PDF page.
   *
   * @param page PDF page
   * @param pageNumber Page number (1-indexed)
   * @param minWidth Minimum width filter
   * @param minHeight Minimum height filter
   * @return List of images from this page
   */
  private static List<ExtractedImage> extractImagesFromPage(
      PDPage page, int pageNumber, int minWidth, int minHeight) {

    List<ExtractedImage> images = new ArrayList<>();
    int imageIndex = 0;

    try {
      PDResources resources = page.getResources();
      if (resources == null) {
        return images;
      }

      for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames()) {
        PDXObject xObject = resources.getXObject(name);

        if (xObject instanceof PDImageXObject) {
          PDImageXObject image = (PDImageXObject) xObject;

          // Filter by size
          int width = image.getWidth();
          int height = image.getHeight();

          if (width < minWidth || height < minHeight) {
            log.debug(
                "Skipping small image on page {}: {}x{}", pageNumber, width, height);
            continue;
          }

          // Extract image bytes
          BufferedImage bufferedImage = image.getImage();
          if (bufferedImage == null) {
            log.warn("Could not convert PDImageXObject to BufferedImage on page {}", pageNumber);
            continue;
          }

          // Determine format (JPEG or PNG based on original or convert to PNG)
          String format = determineImageFormat(image);
          byte[] imageBytes = convertImageToBytes(bufferedImage, format);

          ExtractedImage extractedImage =
              new ExtractedImage(imageBytes, format, pageNumber, width, height, imageIndex);

          images.add(extractedImage);
          imageIndex++;

          log.debug(
              "Extracted image {} from page {}: {}x{} ({})",
              imageIndex,
              pageNumber,
              width,
              height,
              format);
        }
      }
    } catch (IOException e) {
      log.error("Error extracting images from page {}: {}", pageNumber, e.getMessage(), e);
    }

    return images;
  }

  /**
   * Determines the image format from PDImageXObject.
   *
   * @param image PDF image object
   * @return Format string ("JPEG" or "PNG")
   */
  private static String determineImageFormat(PDImageXObject image) {
    String suffix = image.getSuffix();
    if (suffix != null) {
      if (suffix.equalsIgnoreCase("jpg") || suffix.equalsIgnoreCase("jpeg")) {
        return "JPEG";
      } else if (suffix.equalsIgnoreCase("png")) {
        return "PNG";
      }
    }

    // Default to PNG for lossless conversion
    return "PNG";
  }

  /**
   * Converts BufferedImage to byte array.
   *
   * @param image Image to convert
   * @param format Image format ("JPEG" or "PNG")
   * @return Image bytes
   * @throws IOException if conversion fails
   */
  private static byte[] convertImageToBytes(BufferedImage image, String format)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, format, baos);
    return baos.toByteArray();
  }

  /**
   * Represents an extracted image from a PDF.
   *
   * @param imageBytes Raw image bytes
   * @param format Image format (JPEG, PNG)
   * @param pageNumber Page number (1-indexed)
   * @param width Image width in pixels
   * @param height Image height in pixels
   * @param imageIndex Index of image on the page (0-indexed)
   */
  public record ExtractedImage(
      byte[] imageBytes, String format, int pageNumber, int width, int height, int imageIndex) {

    public ExtractedImage {
      if (imageBytes == null || imageBytes.length == 0) {
        throw new IllegalArgumentException("Image bytes cannot be null or empty");
      }
      if (format == null || format.isEmpty()) {
        throw new IllegalArgumentException("Format cannot be null or empty");
      }
      if (pageNumber < 1) {
        throw new IllegalArgumentException("Page number must be positive");
      }
      if (width < 1 || height < 1) {
        throw new IllegalArgumentException("Dimensions must be positive");
      }
    }
  }
}

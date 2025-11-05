package com.redis.vl.demo.rag.service;

import com.redis.vl.demo.rag.model.DocumentChunk;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for ingesting and processing multimodal PDF documents.
 *
 * <p>Implements Option 3 architecture:
 *
 * <ul>
 *   <li>Extracts text and generates summaries for embedding/search
 *   <li>Extracts images and stores raw bytes for vision LLM generation
 *   <li>Stores both in Redis using appropriate stores
 * </ul>
 */
public class PDFIngestionService {

  private static final Logger log = LoggerFactory.getLogger(PDFIngestionService.class);

  private final RedisVLEmbeddingStore embeddingStore;
  private final RedisVLDocumentStore documentStore;
  private final EmbeddingModel embeddingModel;

  /**
   * Creates a new PDFIngestionService.
   *
   * @param embeddingStore Store for text embeddings
   * @param documentStore Store for raw binary content
   * @param embeddingModel Model for generating embeddings
   */
  public PDFIngestionService(
      RedisVLEmbeddingStore embeddingStore,
      RedisVLDocumentStore documentStore,
      EmbeddingModel embeddingModel) {
    this.embeddingStore = embeddingStore;
    this.documentStore = documentStore;
    this.embeddingModel = embeddingModel;
  }

  /**
   * Ingests a PDF file and indexes its content.
   *
   * @param pdfFile PDF file to ingest
   * @param documentId Unique document identifier
   * @return Number of chunks processed
   * @throws IOException if PDF processing fails
   * @throws IllegalArgumentException if pdfFile is null
   */
  public int ingestPDF(File pdfFile, String documentId) throws IOException {
    if (pdfFile == null) {
      throw new IllegalArgumentException("PDF file cannot be null");
    }
    if (!pdfFile.exists()) {
      throw new IOException("PDF file does not exist: " + pdfFile.getAbsolutePath());
    }

    log.info("Ingesting PDF: {} with ID: {}", pdfFile.getName(), documentId);

    List<DocumentChunk> chunks = extractChunks(pdfFile, documentId);
    return indexChunks(chunks);
  }

  /**
   * Extracts chunks from PDF.
   *
   * @param pdfFile PDF file
   * @param documentId Document ID
   * @return List of document chunks
   * @throws IOException if extraction fails
   */
  private List<DocumentChunk> extractChunks(File pdfFile, String documentId) throws IOException {
    List<DocumentChunk> chunks = new ArrayList<>();

    // Extract text chunks
    try (PDDocument document = Loader.loadPDF(pdfFile)) {
      PDFTextStripper textStripper = new PDFTextStripper();

      int numPages = document.getNumberOfPages();
      log.info("Processing {} pages", numPages);

      for (int pageNum = 0; pageNum < numPages; pageNum++) {
        // Extract text from page
        textStripper.setStartPage(pageNum + 1);
        textStripper.setEndPage(pageNum + 1);
        String pageText = textStripper.getText(document);

        if (pageText != null && !pageText.trim().isEmpty()) {
          chunks.add(DocumentChunk.text(documentId, pageNum + 1, pageText.trim()));
        }
      }
    }

    // Extract embedded images (diagrams, charts, photos) using PDFImageExtractor
    try (java.io.FileInputStream fis = new java.io.FileInputStream(pdfFile)) {
      List<PDFImageExtractor.ExtractedImage> extractedImages =
          PDFImageExtractor.extractImages(fis);

      for (PDFImageExtractor.ExtractedImage image : extractedImages) {
        // Create meaningful summary for searchability
        String imageSummary =
            String.format(
                "Image %d from page %d of %s (%dx%d %s)",
                image.imageIndex() + 1,
                image.pageNumber(),
                pdfFile.getName(),
                image.width(),
                image.height(),
                image.format());

        chunks.add(
            DocumentChunk.image(
                documentId, image.pageNumber(), imageSummary, image.imageBytes()));
      }

      log.info("Extracted {} embedded images from PDF", extractedImages.size());
    }

    log.info("Extracted {} total chunks from PDF", chunks.size());
    return chunks;
  }

  /**
   * Indexes document chunks into Redis stores.
   *
   * @param chunks Document chunks
   * @return Number of chunks indexed
   */
  public int indexChunks(List<DocumentChunk> chunks) {
    int indexed = 0;

    for (DocumentChunk chunk : chunks) {
      try {
        // Generate embedding for text summary
        Embedding embedding = embeddingModel.embed(chunk.textSummary()).content();

        // Use pre-generated chunk ID
        String chunkId = chunk.id();

        // Create metadata with chunk_id included BEFORE storing
        Metadata metadata = new Metadata();
        metadata.put("chunk_id", chunkId);
        metadata.put("document_id", chunk.documentId());
        metadata.put("page", chunk.pageNumber());
        metadata.put("type", chunk.chunkType().name());

        // Store embedding with text summary and metadata (including chunk_id)
        TextSegment segment = TextSegment.from(chunk.textSummary(), metadata);
        embeddingStore.add(chunkId, embedding, segment);

        // If chunk has image, store raw image separately
        if (chunk.hasImage()) {
          Map<String, String> imageMetadata = new HashMap<>();
          imageMetadata.put("chunk_id", chunkId);
          imageMetadata.put("document_id", chunk.documentId());
          imageMetadata.put("page", String.valueOf(chunk.pageNumber()));
          imageMetadata.put("type", chunk.chunkType().name());

          documentStore.store(chunkId, chunk.imageData(), imageMetadata);
        }

        indexed++;
      } catch (Exception e) {
        log.error("Failed to index chunk: {}", e.getMessage(), e);
      }
    }

    log.info("Indexed {} chunks", indexed);
    return indexed;
  }

}

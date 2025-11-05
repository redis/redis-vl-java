package com.redis.vl.demo.rag;

import com.redis.vl.demo.rag.model.DocumentChunk;
import com.redis.vl.demo.rag.service.FigureCaptionExtractor;
import com.redis.vl.demo.rag.service.PDFImageExtractor;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Standalone script to demonstrate multimodal RAG with SVS-VAMANA and quantization.
 *
 * This is a hands-on demonstration showing:
 * 1. Index creation with SVS-VAMANA algorithm and vector quantization
 * 2. PDF ingestion with text and image extraction
 * 3. Various query types: pre-filtered, hybrid, text RAG, and multimodal
 * 4. Actual outputs from Redis and GPT-4o
 */
public class MultimodalRAGStandalone {

    private static final int VECTOR_DIM = 384;
    private static final String INDEX_NAME = "research-papers-vamana";
    private static final String REDIS_URL = "redis://localhost:6379";
    private static final String PDF_PATH = "/Users/brian.sam-bodden/Code/redisvl4j/demos/rag-multimodal/src/test/resources/test-pdfs/Attention.pdf";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("Multimodal RAG with SVS-VAMANA and Quantization");
        System.out.println("=".repeat(80));
        System.out.println();

        // Check for OpenAI API key
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("ERROR: OPENAI_API_KEY environment variable not set");
            System.err.println("Set it with: export OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        // Step 1: Create index with SVS-VAMANA and quantization
        System.out.println("Step 1: Creating Redis index with SVS-VAMANA and quantization");
        System.out.println("-".repeat(80));

        UnifiedJedis jedis = new UnifiedJedis(new HostAndPort("localhost", 6379));

        Map<String, Object> schemaMap = Map.of(
            "index", Map.of(
                "name", INDEX_NAME,
                "prefix", "paper:",
                "storage_type", "hash"
            ),
            "fields", List.of(
                Map.of("name", "document_id", "type", "tag"),
                Map.of("name", "page", "type", "numeric"),
                Map.of("name", "text", "type", "text"),
                Map.of("name", "type", "type", "tag"),
                Map.of("name", "chunk_id", "type", "text"),
                Map.of(
                    "name", "vector",
                    "type", "vector",
                    "attrs", Map.of(
                        "dims", VECTOR_DIM,
                        "distance_metric", "cosine",
                        "algorithm", "svs-vamana",  // SVS-VAMANA algorithm
                        "datatype", "float32",
                        // SVS-VAMANA specific parameters
                        "vamana_alpha", 1.2,
                        "vamana_search_size", 100,
                        "vamana_epsilon", 0.01,
                        // Enable quantization for memory efficiency
                        "quantization_type", "fp16"  // 16-bit float quantization
                    )
                )
            )
        );

        IndexSchema schema = IndexSchema.fromDict(schemaMap);
        SearchIndex index = new SearchIndex(schema, jedis);

        try {
            index.create(true); // Overwrite if exists
            System.out.println("✓ Index created successfully with SVS-VAMANA algorithm");
            System.out.println("  - Algorithm: svs-vamana");
            System.out.println("  - Quantization: fp16 (16-bit float)");
            System.out.println("  - Alpha: 1.2, Search Size: 100, Epsilon: 0.01");
        } catch (Exception e) {
            System.out.println("✓ Index already exists or creation failed: " + e.getMessage());
        }
        System.out.println();

        // Step 2: Initialize components
        System.out.println("Step 2: Initializing components");
        System.out.println("-".repeat(80));

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        RedisVLEmbeddingStore embeddingStore = new RedisVLEmbeddingStore(index);
        RedisVLDocumentStore documentStore = new RedisVLDocumentStore(jedis, "docs:");

        System.out.println("✓ Embedding model: sentence-transformers/all-MiniLM-L6-v2 (384 dims)");
        System.out.println("✓ Embedding store: RedisVLEmbeddingStore");
        System.out.println("✓ Document store: RedisVLDocumentStore (for raw image bytes)");
        System.out.println();

        // Step 3: Ingest PDF with rich image descriptions from GPT-4o
        System.out.println("Step 3: Ingesting Attention.pdf with GPT-4o vision");
        System.out.println("-".repeat(80));

        File pdfFile = new File(PDF_PATH);
        if (!pdfFile.exists()) {
            System.err.println("ERROR: PDF file not found at: " + PDF_PATH);
            System.exit(1);
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        String documentId = "attention-paper";

        // Extract text chunks
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            int numPages = document.getNumberOfPages();

            System.out.println("Processing " + numPages + " pages...");

            for (int pageNum = 0; pageNum < numPages; pageNum++) {
                textStripper.setStartPage(pageNum + 1);
                textStripper.setEndPage(pageNum + 1);
                String pageText = textStripper.getText(document);

                if (pageText != null && !pageText.trim().isEmpty()) {
                    chunks.add(DocumentChunk.text(documentId, pageNum + 1, pageText.trim()));
                }
            }
        }

        System.out.println("✓ Extracted " + chunks.size() + " text chunks");

        // Extract figure captions from PDF text
        System.out.println("✓ Extracting figure captions from PDF...");
        Map<Integer, List<FigureCaptionExtractor.FigureCaption>> captionsByPage =
            FigureCaptionExtractor.extractCaptions(pdfFile);

        int totalCaptions = captionsByPage.values().stream().mapToInt(List::size).sum();
        System.out.println("✓ Found " + totalCaptions + " figure caption(s)");

        // Extract embedded images and generate rich descriptions using GPT-4o
        System.out.println("✓ Generating semantic descriptions for images using GPT-4o...");
        ChatLanguageModel visionModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4o")
            .temperature(0.3)
            .maxTokens(150)
            .build();

        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            List<PDFImageExtractor.ExtractedImage> extractedImages =
                PDFImageExtractor.extractImages(fis, 200, 200);

            System.out.println("✓ Extracted " + extractedImages.size() + " embedded images");

            for (PDFImageExtractor.ExtractedImage image : extractedImages) {
                // Find matching caption for this image
                FigureCaptionExtractor.FigureCaption caption =
                    FigureCaptionExtractor.findCaptionForImage(
                        image.pageNumber(), image.imageIndex(), captionsByPage);

                // Generate rich semantic description using GPT-4o vision
                String base64Image = Base64.getEncoder().encodeToString(image.imageBytes());

                List<dev.langchain4j.data.message.Content> visionContents = new ArrayList<>();

                // Include figure number in the prompt if caption found
                String prompt;
                if (caption != null) {
                    prompt = "This is Figure " + caption.figureNumber() + " from a research paper. " +
                        "Describe this diagram or figure in 1-2 sentences. " +
                        "Focus on what it shows, the key components, and its purpose. " +
                        "Begin your description with 'Figure " + caption.figureNumber() + ": '";
                } else {
                    prompt = "Describe this diagram or figure from a research paper in 1-2 sentences. " +
                        "Focus on what it shows, the key components, and its purpose. " +
                        "Be specific and technical.";
                }

                visionContents.add(TextContent.from(prompt));
                visionContents.add(ImageContent.from(base64Image, "image/png"));

                List<ChatMessage> visionMessages = new ArrayList<>();
                visionMessages.add(UserMessage.from(visionContents));

                Response<AiMessage> descriptionResponse = visionModel.generate(visionMessages);
                String richDescription = descriptionResponse.content().text();

                // If caption exists and GPT-4o didn't include figure number, prepend it
                if (caption != null && !richDescription.toLowerCase().contains("figure " + caption.figureNumber())) {
                    richDescription = "Figure " + caption.figureNumber() + ": " + richDescription;
                }

                chunks.add(DocumentChunk.image(documentId, image.pageNumber(), richDescription, image.imageBytes()));

                String displayText = caption != null
                    ? "Figure " + caption.figureNumber() + " (page " + image.pageNumber() + "): " +
                      truncate(richDescription, 100)
                    : "Image " + (image.imageIndex() + 1) + " (page " + image.pageNumber() + "): " +
                      truncate(richDescription, 100);

                System.out.println("  - " + displayText);
            }
        }
        System.out.println();

        // Step 4: Index chunks with embeddings
        System.out.println("Step 4: Indexing chunks with embeddings");
        System.out.println("-".repeat(80));

        int indexed = 0;
        for (DocumentChunk chunk : chunks) {
            // Generate embedding for text summary
            Embedding embedding = embeddingModel.embed(chunk.textSummary()).content();
            String chunkId = chunk.id();

            // Create metadata with chunk_id
            Metadata metadata = new Metadata();
            metadata.put("chunk_id", chunkId);
            metadata.put("document_id", chunk.documentId());
            metadata.put("page", chunk.pageNumber());
            metadata.put("type", chunk.chunkType().name());

            // Store embedding with metadata
            TextSegment segment = TextSegment.from(chunk.textSummary(), metadata);
            embeddingStore.add(chunkId, embedding, segment);

            // RedisVLEmbeddingStore stores metadata as JSON, but we need individual fields for filtering
            // Manually write searchable fields as top-level hash fields
            String redisKey = "paper:" + chunkId;
            jedis.hset(redisKey, "type", chunk.chunkType().name());
            jedis.hset(redisKey, "page", String.valueOf(chunk.pageNumber()));
            jedis.hset(redisKey, "document_id", chunk.documentId());

            // If chunk has image, store raw bytes
            if (chunk.hasImage()) {
                documentStore.store(chunkId, chunk.imageData(),
                    Map.of("document_id", chunk.documentId(),
                           "page", String.valueOf(chunk.pageNumber()),
                           "type", chunk.chunkType().name()));
            }
            indexed++;
        }

        System.out.println("✓ Indexed " + indexed + " chunks total");
        System.out.println("  - Text chunks stored with embeddings in vector index");
        System.out.println("  - Image chunks: summaries in vector index + raw bytes in document store");
        System.out.println();

        // Step 5: Query examples
        System.out.println("Step 5: Query Examples");
        System.out.println("=".repeat(80));
        System.out.println();

        // Initialize retriever and chat model
        RedisVLContentRetriever retriever = RedisVLContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(10)  // Retrieve top 10 most relevant chunks (text + images)
            .minScore(0.0)   // No filtering - let semantic search find best matches
            .build();

        ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4o")
            .temperature(0.7)
            .maxTokens(500)
            .build();

        // Example 5a: Basic vector search
        basicVectorSearch(embeddingModel, index);

        // Example 5b: Pre-filtered vector search (images only)
        preFilteredSearch(embeddingModel, index);

        // Example 5c: Text RAG query
        textRAGQuery(retriever, chatModel, "What is the Transformer architecture?");

        // Example 5d: Multimodal query with images
        multimodalQuery(retriever, chatModel, documentStore, index, embeddingModel,
            "Describe the Multi-Head Attention diagram. How many attention heads are shown?");

        // Example 5e: Figure number query (testing figure caption extraction)
        multimodalQuery(retriever, chatModel, documentStore, index, embeddingModel,
            "Describe Figure 1");

        System.out.println("=".repeat(80));
        System.out.println("Demo completed successfully!");
        System.out.println("=".repeat(80));

        jedis.close();
    }

    private static void basicVectorSearch(EmbeddingModel embeddingModel, SearchIndex index) {
        System.out.println("Example 5a: Basic Vector Search");
        System.out.println("-".repeat(80));
        System.out.println("Query: 'attention mechanism in neural networks'");
        System.out.println();

        float[] queryVector = embeddingModel.embed("attention mechanism in neural networks").content().vector();

        VectorQuery query = VectorQuery.builder()
            .vector(queryVector)
            .field("vector")
            .numResults(3)
            .returnFields("text", "page", "type", "vector_distance")
            .build();

        List<Map<String, Object>> results = index.query(query);

        System.out.println("Top 3 results:");
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            String type = (String) result.get("type");
            String text = (String) result.get("text");
            String page = (String) result.get("page");
            String distance = (String) result.get("vector_distance");

            System.out.println("  " + (i + 1) + ". [" + type + "] Page " + page + " (distance: " + distance + ")");
            System.out.println("     " + truncate(text, 100));
            System.out.println();
        }
    }

    private static void preFilteredSearch(EmbeddingModel embeddingModel, SearchIndex index) {
        System.out.println("Example 5b: Pre-Filtered Vector Search (Images Only)");
        System.out.println("-".repeat(80));
        System.out.println("Query: 'neural network architecture diagram'");
        System.out.println("Filter: type == IMAGE");
        System.out.println();

        float[] queryVector = embeddingModel.embed("neural network architecture diagram").content().vector();

        // Create tag filter for IMAGE type
        Filter imageFilter = Filter.tag("type", "IMAGE");

        VectorQuery query = VectorQuery.builder()
            .vector(queryVector)
            .field("vector")
            .numResults(3)
            .returnFields("text", "page", "type", "vector_distance")
            .build();

        // Set filter after building
        query.setFilter(imageFilter);

        List<Map<String, Object>> results = index.query(query);

        System.out.println("Top 3 image results:");
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            String text = (String) result.get("text");
            String page = (String) result.get("page");
            String type = (String) result.get("type");
            String distance = (String) result.get("vector_distance");

            System.out.println("  " + (i + 1) + ". [" + type + "] Page " + page + " (distance: " + distance + ")");
            System.out.println("     " + text);
            System.out.println();
        }
    }

    private static void textRAGQuery(RedisVLContentRetriever retriever,
                                     ChatLanguageModel chatModel,
                                     String question) {
        System.out.println("Example 5c: Text RAG Query");
        System.out.println("-".repeat(80));
        System.out.println("Question: " + question);
        System.out.println();

        // Retrieve relevant context
        List<Content> retrievedContent = retriever.retrieve(Query.from(question));

        // Build context
        StringBuilder context = new StringBuilder("Context:\n");
        for (Content content : retrievedContent) {
            context.append(content.textSegment().text()).append("\n\n");
        }

        // Build messages
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("You are a helpful research paper assistant."));
        messages.add(UserMessage.from(context + "\nQuestion: " + question));

        // Generate response
        Response<AiMessage> response = chatModel.generate(messages);
        String answer = response.content().text();

        System.out.println("Retrieved " + retrievedContent.size() + " relevant chunks");
        System.out.println();
        System.out.println("GPT-4o Response:");
        System.out.println(answer);
        System.out.println();
    }

    private static void multimodalQuery(RedisVLContentRetriever retriever,
                                        ChatLanguageModel chatModel,
                                        RedisVLDocumentStore documentStore,
                                        SearchIndex index,
                                        EmbeddingModel embeddingModel,
                                        String question) {
        System.out.println("Example 5d: Multimodal Query (Text + Images)");
        System.out.println("-".repeat(80));
        System.out.println("Question: " + question);
        System.out.println();

        // Single semantic retrieval - images have rich descriptions from GPT-4o,
        // so they rank naturally alongside text chunks
        List<Content> retrievedContent = retriever.retrieve(Query.from(question));

        // Check if query is asking for a specific figure number
        java.util.regex.Pattern figurePattern = java.util.regex.Pattern.compile(
            "(?i)\\b(figure|fig\\.?)\\s+(\\d+)\\b");
        java.util.regex.Matcher figureMatcher = figurePattern.matcher(question);
        String requestedFigureNum = null;
        if (figureMatcher.find()) {
            requestedFigureNum = figureMatcher.group(2);
            System.out.println("Detected specific figure request: Figure " + requestedFigureNum);
        }

        // Separate text and image content by metadata
        List<Content> textContent = new ArrayList<>();
        List<Content> imageContent = new ArrayList<>();

        for (Content content : retrievedContent) {
            if (content.textSegment() != null && content.textSegment().metadata() != null) {
                String type = content.textSegment().metadata().getString("type");
                if ("IMAGE".equals(type)) {
                    // If specific figure requested, only include images that match
                    if (requestedFigureNum != null) {
                        String description = content.textSegment().text();
                        String figurePrefix = "Figure " + requestedFigureNum + ":";
                        if (description.startsWith(figurePrefix) ||
                            description.toLowerCase().contains("figure " + requestedFigureNum + ":")) {
                            imageContent.add(content);
                        }
                    } else {
                        imageContent.add(content);
                    }
                } else {
                    textContent.add(content);
                }
            } else {
                textContent.add(content);
            }
        }

        System.out.println("Retrieved " + retrievedContent.size() + " total results:");
        System.out.println("  - " + textContent.size() + " text chunks");
        System.out.println("  - " + imageContent.size() + " image chunks" +
            (requestedFigureNum != null ? " (filtered for Figure " + requestedFigureNum + ")" : ""));

        if (imageContent.isEmpty()) {
            System.out.println("\nNo images found in top results. Falling back to text-only RAG.");
            textRAGQuery(retriever, chatModel, question);
            return;
        }

        // Build multimodal message
        List<dev.langchain4j.data.message.Content> messageContents = new ArrayList<>();

        // Add text context
        if (!textContent.isEmpty()) {
            StringBuilder contextText = new StringBuilder("Text Context:\n");
            for (Content tc : textContent) {
                contextText.append(tc.textSegment().text()).append("\n\n");
            }
            contextText.append("Question: ").append(question);
            messageContents.add(TextContent.from(contextText.toString()));
        } else {
            messageContents.add(TextContent.from("Question: " + question));
        }

        // Add images from retrieved content
        System.out.println("\nImages being sent to GPT-4o:");
        for (Content imgContent : imageContent) {
            String chunkId = imgContent.textSegment().metadata().getString("chunk_id");
            String description = imgContent.textSegment().text();

            System.out.println("  - " + truncate(description, 80));

            Optional<RedisVLDocumentStore.Document> doc = documentStore.retrieve(chunkId);
            if (doc.isPresent()) {
                byte[] imageBytes = doc.get().content();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                messageContents.add(ImageContent.from(base64Image, "image/png"));
            }
        }

        // Build messages
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = "You are a helpful assistant that analyzes research papers. " +
            "When images are provided, carefully analyze their visual content.";

        if (requestedFigureNum != null) {
            systemPrompt += " The user is asking about Figure " + requestedFigureNum + " specifically. " +
                "Focus your response on describing Figure " + requestedFigureNum + ".";
        }

        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(messageContents));

        // Generate response
        System.out.println("\nGPT-4o Response (with vision):");
        System.out.println("-".repeat(80));
        Response<AiMessage> response = chatModel.generate(messages);
        String answer = response.content().text();
        System.out.println(answer);
        System.out.println();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}

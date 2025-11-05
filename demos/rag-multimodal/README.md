# Multimodal RAG Demo

A JavaFX application demonstrating multimodal Retrieval-Augmented Generation (RAG) using RedisVL and LangChain4J.

## Features

- **Chat Interface**: Modern chat UI with message bubbles
- **Cost Tracking**: Display token count and cost per message
- **LangCache Integration**: Toggle semantic caching to demonstrate cost savings
- **Multimodal PDF Processing**: Ingest PDFs with text, images, and tables
- **Multiple LLM Providers**: Support for OpenAI, Anthropic, Azure, and Ollama
- **Vector Search**: Semantic search using Redis as the vector database

## Architecture

### Option 3: Hybrid Approach (Implemented)
- **Text summaries** stored with embeddings for semantic search
- **Raw images** stored separately for vision LLM generation
- Best balance of search accuracy and generation quality

## Prerequisites

### 1. Redis Stack

You need Redis Stack running for vector search capabilities:

```bash
# Using Docker (recommended) - Note: Using port 6399 to avoid conflicts
docker run -d --name redis-rag-demo -p 6399:6379 redis/redis-stack:latest

# Or using Homebrew on macOS (configure to use port 6399)
brew install redis-stack

# Or download from https://redis.io/download
```

### 2. Java 21+

Ensure you have Java 21 or later installed:

```bash
java -version
```

## Quick Start

### 1. Start Redis

```bash
# Start Redis Stack on port 6399 (avoids conflicts with default port)
docker run -d --name redis-rag-demo -p 6399:6379 redis/redis-stack:latest
```

### 2. Build the Application

```bash
cd /Users/brian.sam-bodden/Code/redisvl4j
./gradlew :demos:rag-multimodal:build
```

### 3. Run the Application

```bash
./gradlew :demos:rag-multimodal:run
```

The application window will open. You should see:
- "Connected to Redis. Select provider and enter API key to start." in the status bar

### 4. Configure LLM Provider

In the application:

1. **Select Provider**: Choose from the dropdown (OpenAI, Anthropic, Azure, or Ollama)
2. **Enter API Key**:
   - For OpenAI/Anthropic/Azure: Enter your API key
   - For Ollama: No API key needed (uses local server)
3. **Upload PDF**: Click "Upload PDF" to ingest a document
4. **Ask Questions**: Type questions about the PDF in the input field

## Using Different LLM Providers

### OpenAI (Recommended for Testing)

```bash
# Set your API key as environment variable
export OPENAI_API_KEY=sk-...

# Or enter it in the application UI
```

In the app:
- Select "OpenAI" from provider dropdown
- Enter your API key
- Default model: gpt-4o

### Anthropic Claude

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

In the app:
- Select "Anthropic" from provider dropdown
- Enter your API key
- Default model: claude-3-5-sonnet-20241022

### Ollama (Local/Free)

First, install and run Ollama:

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull a model with vision support
ollama pull llama3.2-vision

# Start Ollama (runs on http://localhost:11434)
ollama serve
```

In the app:
- Select "Ollama" from provider dropdown
- No API key needed
- Default model: llama3.2-vision

## Example Usage

### 1. Upload a PDF

1. Click "Upload PDF" button
2. Select a PDF file (e.g., a technical paper, documentation, or report)
3. Wait for processing (status shows progress)
4. You'll see: "PDF processed successfully. Indexed X chunks."

### 2. Ask Questions

With LangCache disabled (checkbox unchecked):
```
You: What is this document about?
Assistant: [Answers based on PDF content] • 150 tokens • $0.0004 • gpt-4o

You: What are the main conclusions?
Assistant: [Detailed answer] • 200 tokens • $0.0005 • gpt-4o
```

Enable LangCache (check the box) and ask the same question again:
```
You: What is this document about?
Assistant: [Same answer] • 150 tokens • $0.0000 • gpt-4o • ⚡ From Cache
```

Notice the $0.0000 cost and the green "From Cache" indicator!

## Environment Variables

```bash
# Redis connection (optional, defaults shown)
export REDIS_HOST=localhost
export REDIS_PORT=6399  # Demo uses 6399 to avoid conflicts

# LLM API keys (optional, can enter in UI)
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
```

## Troubleshooting

### Redis Connection Failed

**Error**: "Could not connect to Redis at localhost:6399"

**Solution**:
```bash
# Check if Redis is running
docker ps | grep redis-rag-demo

# If not running, start it
docker run -d --name redis-rag-demo -p 6399:6379 redis/redis-stack:latest

# Or restart existing container
docker start redis-rag-demo
```

### LLM Initialization Failed

**Error**: "Failed to initialize LLM"

**Solution**:
- Verify your API key is correct
- Check internet connection
- For Ollama, ensure `ollama serve` is running

### PDF Processing Errors

**Error**: "Error processing PDF"

**Solution**:
- Ensure PDF is not encrypted
- Try a smaller PDF file first
- Check logs for specific errors

## Components

### Services

- **ServiceFactory**: Initializes and wires all services
- **RAGService**: Handles query processing with retrieval and generation
- **PDFIngestionService**: Extracts and processes multimodal content from PDFs
- **JTokKitCostTracker**: Calculates token counts and costs for different models

### Models

- **ChatMessage**: Represents chat messages with cost information
- **DocumentChunk**: Represents multimodal document chunks
- **LLMConfig**: Configuration for different LLM providers

### UI

- **MultimodalRAGApp**: Main JavaFX application
- **ChatController**: Main chat interface controller
- **MessageBubble**: Custom control for rendering chat messages

## Technologies

- **JavaFX 21**: Modern UI framework
- **RedisVL**: Vector search and document storage
- **LangChain4J**: LLM orchestration
- **Apache PDFBox**: PDF processing
- **JTokkit**: Token counting
- **Redis Stack**: Vector database backend

## Development

### Running Tests

```bash
./gradlew :demos:rag-multimodal:test
```

### Building Distribution

```bash
./gradlew :demos:rag-multimodal:distZip
```

The distribution will be in `demos/rag-multimodal/build/distributions/`

## Next Steps

- Implement LangCache integration for semantic caching
- Add support for more document formats
- Implement conversation history with RedisVLChatMemoryStore
- Add image display for retrieved visual content
- Implement advanced filtering and search options

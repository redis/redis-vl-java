<div align="center">
<div><img src="https://redis.io/images/redis-logo.svg" style="width: 130px"> </div>
<h1>RedisVL4J Java Notebooks</h1>
<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java](https://img.shields.io/badge/Java-21-orange)
![RedisVL4J](https://img.shields.io/badge/RedisVL4J-Latest-green)

</div>
<div>
    ✨ Interactive Jupyter notebooks demonstrating RedisVL4J capabilities for AI and ML applications with Redis. ✨
</div>

<div></div>
<br>

[**Setup**](#setup) | [**Running the Project**](#running-the-project) | [**Notebooks**](#notebooks) | [**Project Structure**](#project-structure) | [**Implementation Details**](#implementation-details)

</div>
<br>

## Setup

This project uses Docker Compose to set up a complete environment for running Java-based AI applications with RedisVL4J. The environment includes:

- A Jupyter Notebook server with Java kernel support
- Redis Stack (includes Redis and RedisInsight)
- RedisVL4J library built from source
- Pre-installed dependencies for AI/ML workloads

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- OpenAI API key (for notebooks that use OpenAI services - optional)

### Environment Configuration

1. Create a `.env` file in the project root (optional for OpenAI-based examples):

```bash
OPENAI_API_KEY=your_openai_api_key_here
```

## Running the Project

> **Quick Start:** See the [main README](../README.md#running-the-notebooks) for simplified instructions.

### Detailed Steps

1. Navigate to the notebooks directory:

   ```bash
   cd notebooks
   ```

2. Start the Docker containers:

   ```bash
   docker compose up -d
   ```

3. Access the Jupyter environment:
   - Open your browser and navigate to [http://localhost:8888](http://localhost:8888)
   - No authentication is required (token/password disabled for local development)

4. Access RedisInsight (optional):
   - Open your browser and navigate to [http://localhost:8001](http://localhost:8001)
   - Connect to Redis using the following details:
     - Host: redis-stack
     - Port: 6379
     - No password (unless configured)

5. When finished, stop the containers:

   ```bash
   docker compose down
   ```

## Notebooks

| Notebook | Description | Status |
| --- | --- | --- |
| [01_getting_started.ipynb](./01_getting_started.ipynb) | Introduction to RedisVL4J basic concepts and usage | ✅ |
| [02_hybrid_queries.ipynb](./02_hybrid_queries.ipynb) | Demonstrates hybrid search capabilities combining vector and text queries | ✅ |
| [05_hash_vs_json.ipynb](./05_hash_vs_json.ipynb) | Comparison of Redis Hash vs JSON storage types for vector data | ✅ |

## Project Structure

```bash
notebooks/
├── .env                         # Environment variables (optional)
├── docker-compose.yml           # Docker Compose configuration
├── jupyter/                     # Jupyter configuration files
│   ├── Dockerfile               # Dockerfile for Jupyter with Java kernel
│   ├── environment.yml          # Conda environment specification
│   ├── install.py               # Java kernel installation script
│   └── java/                    # Java dependencies and configuration
│       └── pom.xml              # Maven project file with dependencies
├── resources/                   # Data files for notebooks
│   └── sample_data.json         # Sample datasets for examples
└── *.ipynb                      # Jupyter notebooks
```

## Implementation Details

### Java Jupyter Kernel

The project uses [JJava](https://github.com/dflib/jjava), a Jupyter kernel for Java based on JShell. This allows for interactive Java development in Jupyter notebooks.

Key components:

- Java 21 for modern Java features
- Maven for dependency management
- JJava kernel for Jupyter integration
- RedisVL4J built from source

### RedisVL4J Integration

The notebooks showcase how to use RedisVL4J capabilities with Redis:

- **RedisVL4J**: Java port of the Redis Vector Library
- **Redis Vector Store**: Used for storing and querying vector embeddings
- **Search Indexes**: For building vector similarity search applications
- **Hybrid Queries**: Combining vector and traditional search

### Docker Configuration

The Docker setup includes:

1. **Jupyter Container**:
   - Based on minimal Jupyter notebook image
   - Adds Java 21, Maven, and the JJava kernel
   - Builds RedisVL4J from source and includes it in classpath
   - Includes Python environment for utilities

2. **Redis Container**:
   - Uses Redis Stack image with Vector Search capabilities
   - Persists data using Docker volumes
   - Exposes Redis on port 6379 and RedisInsight on port 8001

## Getting Started

After starting the environment, begin with the `01_getting_started.ipynb` notebook to learn the basics of RedisVL4J, then explore the other notebooks to see advanced features and use cases.

Each notebook is self-contained and includes explanations of the concepts being demonstrated along with runnable code examples.
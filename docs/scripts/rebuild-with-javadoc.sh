#!/bin/bash
set -e

echo "🔄 Rebuilding RedisVL Documentation with Javadoc Integration"
echo "======================================================================"

# Navigate to project root
cd "$(dirname "$0")/../.."
PROJECT_ROOT=$(pwd)
echo "📁 Project root: $PROJECT_ROOT"

echo ""
echo "🧹 Cleaning previous builds..."
./gradlew clean
rm -rf docs/build/site
rm -rf docs/content/modules/ROOT/assets/javadoc
rm -rf docs/content/modules/ROOT/attachments/javadoc
rm -rf docs/node_modules/.cache

echo ""
echo "🔨 Building documentation with Javadoc integration..."
./gradlew :docs:build

echo ""
echo "✅ Build completed! Checking integration..."

# Quick validation
if [ -d "docs/build/site/redisvl/current/_attachments/javadoc" ]; then
    echo "✓ Javadoc attachments found in built site"
    javadoc_count=$(find docs/build/site -path "*/_attachments/javadoc/*" -name "*.html" | wc -l)
    echo "✓ Found $javadoc_count Javadoc HTML files"
else
    echo "❌ Javadoc attachments not found in built site"
    echo "   Expected directory: docs/build/site/redisvl/current/_attachments/javadoc"
    exit 1
fi

echo ""
echo "🎉 Rebuild completed successfully!"
echo ""
echo "🌐 To test locally:"
echo "   cd docs"
echo "   docker-compose up -d"
echo "   open http://localhost:8000/redisvl/current/getting-started.html"
echo ""
echo "📚 Direct Javadoc URLs:"
echo "   Complete API: http://localhost:8000/redisvl/current/_attachments/javadoc/aggregate/"
echo "   Core Module: http://localhost:8000/redisvl/current/_attachments/javadoc/modules/core/"
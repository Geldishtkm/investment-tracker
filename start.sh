#!/bin/bash
# Start script for Portfolio Tracker

echo "🚀 Starting Portfolio Tracker..."

# List files in target directory
echo "📁 Files in target directory:"
ls -la target/

# Check if JAR file exists
if [ -f "target/tracker-0.0.1-SNAPSHOT.jar" ]; then
    echo "✅ JAR file found! Starting application..."
    java -jar target/tracker-0.0.1-SNAPSHOT.jar
else
    echo "❌ JAR file not found!"
    echo "📁 Current directory contents:"
    ls -la
    echo "📁 Target directory contents:"
    ls -la target/ || echo "Target directory not found"
    exit 1
fi

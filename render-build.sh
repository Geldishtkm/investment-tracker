#!/bin/bash
# Render build script for Portfolio Tracker

echo "🚀 Starting build on Render..."

# Install Java 17
echo "📦 Installing Java 17..."
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17.0.2-open
sdk use java 17.0.2-open

# Install Maven
echo "📦 Installing Maven..."
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install maven 3.9.4
sdk use maven 3.9.4

# Build the application
echo "🔨 Building with Maven..."
mvn clean package -DskipTests

echo "✅ Build completed successfully!"
ls -la target/

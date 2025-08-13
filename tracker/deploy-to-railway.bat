@echo off
echo ========================================
echo 🚀 PORTFOLIO TRACKER - RAILWAY DEPLOYMENT
echo ========================================
echo.
echo This script will help you deploy your project to Railway for FREE!
echo.
echo STEP 1: Build your project
echo ========================================
echo Building with Maven...
call .\mvnw.cmd clean package -DskipTests
if %errorlevel% neq 0 (
    echo ❌ Build failed! Please check the errors above.
    pause
    exit /b 1
)
echo ✅ Build successful!
echo.
echo STEP 2: Check deployment files
echo ========================================
if exist railway.json (
    echo ✅ railway.json found
) else (
    echo ❌ railway.json missing
)
if exist railway.toml (
    echo ✅ railway.toml found
) else (
    echo ❌ railway.toml missing
)
if exist nixpacks.toml (
    echo ✅ nixpacks.toml found
) else (
    echo ❌ nixpacks.toml missing
)
if exist Dockerfile (
    echo ✅ Dockerfile found
) else (
    echo ❌ Dockerfile missing
)
if exist .railwayignore (
    echo ✅ .railwayignore found
) else (
    echo ❌ .railwayignore missing
)
echo.
echo STEP 3: Deploy to Railway (FIXED FOR MONOREPO!)
echo ========================================
echo.
echo 1. Go to: https://railway.app
echo 2. Click "Start a New Project"
echo 3. Choose "Deploy from GitHub repo"
echo 4. Select your portfolio tracker repository
echo 5. IMPORTANT: Set Root Directory to / (leave empty)
echo 6. Set Build Command: mvn clean package -DskipTests
echo 7. Set Start Command: java -jar target/tracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
echo 8. Click "Deploy Now"
echo.
echo STEP 4: Add PostgreSQL Database
echo ========================================
echo 1. In Railway, click "New"
echo 2. Choose "Database" → "PostgreSQL"
echo 3. Copy the connection details
echo.
echo STEP 5: Set Environment Variables
echo ========================================
echo In Railway → Your App → Variables tab, add:
echo.
echo DATABASE_URL=jdbc:postgresql://your-host:5432/your-db
echo DATABASE_USERNAME=your-username
echo DATABASE_PASSWORD=your-password
echo JWT_SECRET=your-super-long-secret-key
echo JWT_EXPIRATION=3600000
echo ALLOWED_ORIGINS=https://your-app.railway.app
echo JAVA_HOME=/nix/var/nix/profiles/default
echo.
echo STEP 6: Test Your App
echo ========================================
echo 1. Visit your Railway app URL
echo 2. Test: /actuator/health
echo 3. Try registering a user
echo 4. Test crypto prices: /api/crypto/top
echo.
echo 🎉 Your app will be live on the internet!
echo.
echo Need help? Check: FREE_DEPLOYMENT_GUIDE.md
echo.
echo 🚨 MONOREPO ERROR FIXED! ✅
echo.
pause

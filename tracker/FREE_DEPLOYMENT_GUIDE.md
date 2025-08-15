# 🚀 FREE DEPLOYMENT GUIDE - RAILWAY

## 🎯 **DEPLOY YOUR PORTFOLIO TRACKER FOR FREE!**

### **What You Get:**
- ✅ **FREE hosting** (Railway gives $5 credit monthly)
- ✅ **Automatic deployments** from GitHub
- ✅ **Free PostgreSQL database**
- ✅ **Custom domain** (optional)
- ✅ **SSL certificates** included

---

## 📋 **STEP 1: PREPARE YOUR PROJECT**

### **1.1 Push to GitHub**
```bash
# If you haven't already, create a GitHub repository
git init
git add .
git commit -m "Ready for deployment"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
git push -u origin main
```

### **1.2 Verify Files**
Make sure you have these files in your project:
- ✅ `railway.json` ✅
- ✅ `railway.toml` ✅
- ✅ `nixpacks.toml` ✅
- ✅ `Dockerfile` ✅
- ✅ `.railwayignore` ✅
- ✅ `pom.xml` ✅
- ✅ `src/` folder ✅

---

## 🌐 **STEP 2: DEPLOY ON RAILWAY**

### **2.1 Go to Railway**
1. Visit: [railway.app](https://railway.app)
2. Click **"Start a New Project"**
3. Choose **"Deploy from GitHub repo"**
4. Select your portfolio tracker repository

### **2.2 Configure Your App (IMPORTANT!)**
1. **Project Name**: `portfolio-tracker` (or any name you want)
2. **Branch**: `main`
3. **Root Directory**: `/` (leave empty - this is crucial!)
4. **Build Command**: `mvn clean package -DskipTests`
5. **Start Command**: `java -jar target/tracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
6. Click **"Deploy Now"**

### **2.3 Add PostgreSQL Database**
1. In your Railway project, click **"New"**
2. Choose **"Database"** → **"PostgreSQL"**
3. Wait for it to be created
4. Copy the connection details

---

## ⚙️ **STEP 3: CONFIGURE ENVIRONMENT VARIABLES**

### **3.1 In Railway Dashboard:**
Go to your app → **Variables** tab and add:

```bash
# Database (use the values from your PostgreSQL service)
DATABASE_URL=jdbc:postgresql://your-railway-postgres-host:5432/your-db-name
DATABASE_USERNAME=your-railway-postgres-user
DATABASE_PASSWORD=your-railway-postgres-password

# Security (generate a strong secret)
JWT_SECRET=your-super-long-secret-key-here-make-it-very-long-and-random
JWT_EXPIRATION=3600000

# CORS (your Railway app URL)
ALLOWED_ORIGINS=https://your-app-name.railway.app

# Port (Railway sets this automatically)
PORT=8080

# Java configuration
JAVA_HOME=/nix/var/nix/profiles/default
```

### **3.2 Generate JWT Secret:**
```bash
# Use this command to generate a strong secret
openssl rand -base64 64
# Or use any random string generator online
```

---

## 🚀 **STEP 4: DEPLOY & TEST**

### **4.1 Automatic Deployment**
- Railway will automatically build and deploy your app
- Watch the build logs for any errors
- Wait for "Deploy successful" message

### **4.2 Test Your App**
1. Click on your app URL in Railway
2. Test the health endpoint: `/actuator/health`
3. Try registering a new user
4. Test the crypto prices endpoint: `/api/crypto/top`

---

## 🔧 **TROUBLESHOOTING**

### **Common Issues:**

#### **Monorepo Error:**
- **Problem**: "This usually happens when using a monorepo without the correct root directory set"
- **Solution**: Make sure Root Directory is set to `/` (empty) in Railway
- **Alternative**: Use the `railway.toml` file we created

#### **Build Fails:**
- Check that all files are committed to GitHub
- Verify `pom.xml` has correct dependencies
- Check Railway build logs for specific errors
- Ensure `railway.toml` and `nixpacks.toml` are present

#### **Database Connection Fails:**
- Verify environment variables are correct
- Check PostgreSQL service is running
- Ensure database credentials match

#### **App Won't Start:**
- Check environment variables are set
- Verify the start command in Railway dashboard
- Check app logs in Railway dashboard

---

## 🌍 **STEP 5: CUSTOM DOMAIN (OPTIONAL)**

### **5.1 Add Custom Domain:**
1. In Railway → **Settings** → **Domains**
2. Add your domain (e.g., `tracker.yourdomain.com`)
3. Update DNS records as instructed
4. Update `ALLOWED_ORIGINS` in environment variables

---

## 📊 **MONITORING & MAINTENANCE**

### **6.1 Check Logs:**
- Railway → Your App → **Deployments** → **View Logs**

### **6.2 Monitor Usage:**
- Railway → **Usage** tab to see resource consumption

### **6.3 Scale (if needed):**
- Free tier: 512MB RAM, shared CPU
- Paid: Starts at $5/month for more resources

---

## 🎉 **YOU'RE LIVE!**

### **Your App is Now:**
- ✅ **Hosted for FREE** on Railway
- ✅ **Automatically deployed** from GitHub
- ✅ **Database connected** and working
- ✅ **SSL secured** with HTTPS
- ✅ **Ready for users** worldwide!

### **Next Steps:**
1. Share your app URL with friends
2. Monitor performance in Railway dashboard
3. Set up automatic deployments for future updates
4. Consider adding monitoring and alerts

---

## 💰 **COST BREAKDOWN**

### **Free Tier (What You Get):**
- **Hosting**: $5 credit monthly (FREE)
- **Database**: Included in free tier
- **Bandwidth**: 100GB/month
- **Builds**: Unlimited
- **Deployments**: Unlimited

### **If You Need More:**
- **Pro Plan**: $20/month (more resources)
- **Team Plan**: $20/month per user

---

## 🆘 **NEED HELP?**

### **Railway Support:**
- [Railway Documentation](https://docs.railway.app)
- [Railway Discord](https://discord.gg/railway)
- [Railway Community](https://community.railway.app)

### **Your Project Status:**
- ✅ **Code**: Production Ready
- ✅ **Security**: Hardened
- ✅ **Database**: Configured
- ✅ **Deployment**: Automated
- ✅ **Railway Config**: Fixed for monorepo

---

## 🚨 **IMPORTANT: MONOREPO FIX**

### **If you still get the monorepo error:**

1. **Delete the project** from Railway
2. **Recreate it** with these exact settings:
   - Root Directory: `/` (leave completely empty)
   - Build Command: `mvn clean package -DskipTests`
   - Start Command: `java -jar target/tracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`

3. **Alternative**: Use the `railway.toml` file we created

---

**🎯 You're all set for FREE deployment! Your portfolio tracker will be live on the internet in minutes! 🚀**

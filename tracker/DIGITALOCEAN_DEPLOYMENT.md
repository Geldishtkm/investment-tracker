# 🚀 DIGITALOCEAN DEPLOYMENT GUIDE

## 🎯 **Deploy Your Portfolio Tracker to DigitalOcean**

### **What You Get:**
- ✅ **Professional hosting** with DigitalOcean
- ✅ **Managed PostgreSQL database**
- ✅ **Automatic scaling** and load balancing
- ✅ **SSL certificates** included
- ✅ **Global CDN** for fast performance

---

## 📋 **STEP 1: PREPARE YOUR PROJECT**

### **1.1 Push to GitHub**
```bash
git add .
git commit -m "Add DigitalOcean configuration"
git push origin main
```

### **1.2 Verify Files**
Make sure you have these files:
- ✅ `.do/app.yaml` ✅
- ✅ `Dockerfile` ✅
- ✅ `src/main/resources/application-digitalocean.properties` ✅
- ✅ `pom.xml` ✅

---

## 🌐 **STEP 2: DEPLOY ON DIGITALOCEAN**

### **2.1 Go to DigitalOcean**
1. Visit: [cloud.digitalocean.com](https://cloud.digitalocean.com)
2. Click **"Create"** → **"Apps"**
3. Choose **"Link Your GitHub Account"**
4. Select your portfolio tracker repository

### **2.2 Configure Your App**
1. **App Name**: `portfolio-tracker`
2. **Branch**: `main`
3. **Root Directory**: `/` (leave empty)
4. **Build Command**: `mvn clean package -DskipTests`
5. **Run Command**: `java -jar target/tracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=digitalocean`
6. **Environment**: `Java`
7. **Instance Size**: `Basic XXS` (cheapest option)

### **2.3 Add PostgreSQL Database**
1. In your app, click **"Resources"**
2. Click **"Create/Attach a Resource"**
3. Choose **"Database"** → **"PostgreSQL"**
4. Select **"db-s-1vcpu-1gb"** (cheapest)
5. Choose **"Same Region"** as your app
6. Click **"Create and Attach"**

---

## ⚙️ **STEP 3: SET ENVIRONMENT VARIABLES**

### **3.1 In DigitalOcean Dashboard:**
Go to your app → **Settings** → **Environment Variables** and add:

```bash
# Database (from your PostgreSQL service)
DATABASE_URL=jdbc:postgresql://your-db-host:5432/your-db-name
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-db-password

# Security
JWT_SECRET=your-super-long-secret-key-here
JWT_EXPIRATION=86400000

# CORS (your DigitalOcean app URL)
ALLOWED_ORIGINS=https://your-app-name.ondigitalocean.app

# Port
SERVER_PORT=8080

# Profile
SPRING_PROFILES_ACTIVE=digitalocean
```

### **3.2 Generate JWT Secret:**
```bash
# Use this command to generate a strong secret
openssl rand -base64 64
```

---

## 🚀 **STEP 4: DEPLOY & TEST**

### **4.1 Deploy**
1. Click **"Create Resources"**
2. Wait for build and deployment (5-10 minutes)
3. Your app will be live at: `https://your-app-name.ondigitalocean.app`

### **4.2 Test Your App**
```bash
# Health check
curl https://your-app-name.ondigitalocean.app/actuator/health

# Test registration
curl -X POST https://your-app-name.ondigitalocean.app/auth/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=testuser&password=testpass123"
```

---

## 🔧 **TROUBLESHOOTING**

### **Common Issues:**

#### **1. Build Fails**
- Check Maven version compatibility
- Verify all dependencies in `pom.xml`
- Check build logs in DigitalOcean

#### **2. App Crashes on Startup**
- Verify environment variables are set
- Check database connection
- Review application logs

#### **3. Database Connection Issues**
- Ensure PostgreSQL is in same region
- Verify connection string format
- Check firewall rules

#### **4. CORS Issues**
- Update `ALLOWED_ORIGINS` with your frontend domain
- Verify CORS configuration in `application-digitalocean.properties`

---

## 💰 **COST ESTIMATE**

### **Monthly Costs:**
- **App Platform**: $5/month (Basic XXS)
- **PostgreSQL Database**: $15/month (db-s-1vcpu-1gb)
- **Total**: ~$20/month

### **Free Tier:**
- **$200 credit** for new users (valid for 60 days)
- **Free hosting** for 2 months!

---

## 🎉 **SUCCESS!**

Your Portfolio Tracker is now:
- 🌐 **Live on the internet**
- 🔒 **Securely configured**
- 📊 **With real-time crypto data**
- 🎯 **All hardcoded calculations replaced**
- 🚀 **Ready for production use**

---

## 📞 **Need Help?**

- **DigitalOcean Docs**: [docs.digitalocean.com](https://docs.digitalocean.com)
- **Community**: [digitalocean.com/community](https://digitalocean.com/community)
- **Support**: Available in your DigitalOcean dashboard

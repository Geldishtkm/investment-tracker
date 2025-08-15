# Portfolio Tracker

A modern, secure portfolio management application built with Spring Boot and React.

## 🚀 Features

- **Portfolio Management** - Track investments and assets
- **Risk Analysis** - VaR calculations and portfolio optimization
- **Real-time Data** - Live cryptocurrency prices
- **Security** - JWT authentication and role-based access
- **Responsive UI** - Modern React frontend with Tailwind CSS

## 🛠️ Tech Stack

### Backend
- **Spring Boot 3.2** - Java 21 application framework
- **PostgreSQL** - Production database
- **Flyway** - Database migrations
- **Spring Security** - Authentication and authorization
- **JWT** - Stateless authentication

### Frontend
- **React 18** - Modern UI framework
- **TypeScript** - Type-safe development
- **Tailwind CSS** - Utility-first styling
- **Vite** - Fast build tool

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 12+

### Backend
```bash
# Build
mvn clean package

# Run
java -jar target/tracker-0.0.1-SNAPSHOT.jar
```

### Frontend
```bash
# Install dependencies
npm install

# Development
npm run dev

# Production build
npm run build
```

## 🔐 Security

- All API endpoints require authentication (except `/auth/**`, `/api/crypto/**`)
- JWT-based stateless authentication
- User data isolation
- Security headers configured

## 📊 API Endpoints

- **Authentication**: `/auth/**`
- **Public**: `/api/crypto/**`, `/api/price-history/**`
- **Protected**: All other `/api/**` endpoints

## 🗄️ Database

- PostgreSQL with Flyway migrations
- Automatic schema validation
- No auto-updates (production safe)

## 📝 License

This project is proprietary software. 
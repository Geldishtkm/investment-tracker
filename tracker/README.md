# Portfolio Tracker Frontend

A modern, ultra-fast React + TypeScript frontend for tracking investment portfolios. Built with Vite for lightning-fast development and production builds.

## ⚡ Modern Tech Stack

- **Vite** - Ultra-fast build tool and dev server
- **React 18** - Latest React with hooks and concurrent features
- **TypeScript** - Type-safe development
- **Lucide React** - Beautiful, consistent icons
- **Modern CSS** - Custom styling with modern design patterns

## 🚀 Performance Benefits

- **Instant startup** - Development server starts in milliseconds
- **Lightning-fast HMR** - Changes appear instantly
- **Optimized builds** - Smaller, faster production bundles
- **Type safety** - Catch errors at compile time
- **Modern tooling** - Latest web standards and optimizations

## Features

- 📊 **Portfolio Overview** - View total portfolio value and asset statistics
- ➕ **Add Assets** - Easy-to-use form to add new investments
- ✏️ **Edit Assets** - Inline editing with real-time validation
- 🗑️ **Delete Assets** - Remove assets with confirmation
- 🔍 **Search & Filter** - Find and organize assets quickly
- 🎨 **Modern UI** - Beautiful, responsive design with gradient backgrounds
- 📱 **Mobile Friendly** - Works perfectly on all device sizes
- ⚡ **Real-time Updates** - Instant updates when adding/editing assets
- 🔄 **Refresh Data** - Manual refresh capability

## Prerequisites

- Node.js (version 16 or higher)
- npm or yarn
- Spring Boot backend running on `http://localhost:8080`

## Installation

1. **Navigate to the project directory**
   ```bash
   cd C:\Users\geldi\Desktop\frontend_inv
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Start the development server**
   ```bash
   npm run dev
   ```

4. **Open your browser**
   Navigate to `http://localhost:5173` (Vite's default port)

## Available Scripts

- `npm run dev` - Start development server (ultra-fast)
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint

## Backend Requirements

Make sure your Spring Boot backend is running and has the following endpoints:

- `GET /api/assets` - Get all assets
- `POST /api/assets` - Add new asset
- `PUT /api/assets/{id}` - Update asset
- `DELETE /api/assets/{id}` - Delete asset
- `GET /api/assets/total` - Get total portfolio value

The backend should expect and return JSON in this format:

```json
{
  "id": 1,
  "name": "Bitcoin",
  "quantity": 0.5,
  "pricePerUnit": 45000.00
}
```

## Project Structure

```
src/
├── components/
│   ├── AssetCard.tsx      # Individual asset display with CRUD
│   ├── AssetForm.tsx      # Add asset modal form
│   ├── EmptyState.tsx     # Empty portfolio state
│   └── PortfolioSummary.tsx # Portfolio statistics
├── services/
│   └── api.ts            # TypeScript API service
├── types/
│   └── index.ts          # TypeScript type definitions
├── App.tsx               # Main application component
├── main.tsx              # Application entry point
└── index.css             # Global styles
```

## TypeScript Benefits

- **Type Safety** - Catch errors at compile time
- **Better IDE Support** - Enhanced autocomplete and refactoring
- **Self-Documenting Code** - Types serve as documentation
- **Refactoring Confidence** - Safe code changes with type checking

## Vite Benefits

- **Instant Server Start** - No waiting for bundling
- **Lightning Fast HMR** - Changes appear instantly
- **Optimized Builds** - Smaller, faster production bundles
- **Modern Tooling** - Uses latest web standards
- **Better DX** - Enhanced development experience

## Configuration

The application is configured to proxy requests to `http://localhost:8080` (your Spring Boot backend) in the `vite.config.ts` file:

```typescript
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
```

## Usage

1. **View Portfolio** - The main page shows your current portfolio with total value and asset count
2. **Add Asset** - Click "Add Asset" to open the form modal
3. **Edit Asset** - Hover over any asset card and click the menu to edit
4. **Delete Asset** - Use the menu to delete assets with confirmation
5. **Search & Filter** - Use the search bar and sort options to organize assets
6. **Refresh** - Use the refresh button to reload data from the backend

## Styling

The application uses a custom CSS approach with:
- Modern gradient backgrounds
- Card-based layouts
- Responsive grid system
- Smooth animations and transitions
- Professional color scheme
- Hover effects and micro-interactions

## Troubleshooting

### Common Issues

1. **Backend Connection Error**
   - Ensure your Spring Boot backend is running on port 8080
   - Check that CORS is properly configured on the backend

2. **Port Already in Use**
   - Vite will automatically suggest an alternative port
   - Or kill the process using the current port

3. **TypeScript Errors**
   - Run `npm run lint` to check for type issues
   - Fix any type errors before running the app

4. **Dependencies Issues**
   - Delete `node_modules` and `package-lock.json`
   - Run `npm install` again

### Development Tips

- Use browser developer tools to debug API calls
- Check the Network tab for request/response details
- Use React Developer Tools for component debugging
- TypeScript will catch many errors at compile time

## Performance Comparison

| Feature | Vite + TS | Create React App |
|---------|-----------|------------------|
| **Dev Server Start** | ~100ms | ~3-5 seconds |
| **Hot Reload** | Instant | ~1-2 seconds |
| **Build Time** | ~10-30s | ~1-3 minutes |
| **Bundle Size** | Smaller | Larger |
| **Type Safety** | ✅ Full | ❌ None |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is open source and available under the MIT License. 
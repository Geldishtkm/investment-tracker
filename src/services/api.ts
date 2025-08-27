import { Asset, CryptoPrice, AssetWithPrice, Coin, PriceHistoryPoint } from '../types';
import { authService } from './authService';

const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080';

export const assetService = {
  getAllAssets: async (): Promise<Asset[]> => {
    try {
      const authHeader = authService.getAuthHeader();
      console.log('Sending request to getAllAssets with auth header:', authHeader);
      
      const response = await fetch(`${API_BASE_URL}/api/assets`, {
        headers: {
          'Content-Type': 'application/json',
          ...authHeader
        }
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        console.error('getAllAssets failed:', response.status, response.statusText, errorText);
        throw new Error(`Failed to fetch assets: ${response.status} ${response.statusText} - ${errorText}`);
      }
      
      const data = await response.json();
      return data;
    } catch (error) {
      console.error('Error fetching assets:', error);
      throw error;
    }
  },

  addAsset: async (asset: Omit<Asset, 'id'>): Promise<Asset> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authService.getAuthHeader()
        },
        body: JSON.stringify(asset)
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to add asset: ${response.status} ${response.statusText} - ${errorText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error adding asset:', error);
      throw error;
    }
  },

  updateAsset: async (id: number, asset: Omit<Asset, 'id'>): Promise<Asset> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          ...authService.getAuthHeader()
        },
        body: JSON.stringify(asset)
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to update asset: ${response.status} ${response.statusText} - ${errorText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error updating asset:', error);
      throw error;
    }
  },

  deleteAsset: async (id: number): Promise<void> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets/${id}`, {
        method: 'DELETE',
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to delete asset: ${response.status} ${response.statusText} - ${errorText}`);
      }
    } catch (error) {
      console.error('Error deleting asset:', error);
      throw error;
    }
  },

  getAllAssetsWithPrices: async (): Promise<AssetWithPrice[]> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets/with-prices`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (response.ok) {
        return await response.json();
      }
      
      console.log('with-prices endpoint not available, using fallback');
      const assets = await assetService.getAllAssets();
      const assetsWithPrices: AssetWithPrice[] = assets.map(asset => ({
        ...asset,
        currentPrice: asset.pricePerUnit,
        priceChange: 0,
        priceChangePercent: 0
      }));
      return assetsWithPrices;
    } catch (error) {
      console.error('Error fetching assets with prices:', error);
      return []; // Final fallback
    }
  },

  getCryptoPrices: async (): Promise<CryptoPrice[]> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/crypto/prices`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch crypto prices: ${response.status} ${response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error fetching crypto prices:', error);
      throw error;
    }
  },

  getCryptoPrice: async (coinId: string): Promise<number> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/crypto/price/${coinId}`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch crypto price for ${coinId}: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      return typeof data === 'number' ? data : data.price;
    } catch (error) {
      console.error(`Error fetching crypto price for ${coinId}:`, error);
      throw error;
    }
  },

  getTopCoins: async (): Promise<Coin[]> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/crypto/top`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch top coins: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      // Backend returns { coins: [...], count: 100 }, we need the coins array
      return data.coins || data;
    } catch (error) {
      console.error('Error fetching top coins:', error);
      throw error;
    }
  },

  getPriceHistory: async (coinId: string): Promise<PriceHistoryPoint[]> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/price-history/${coinId}`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch price history: ${response.status} ${response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error fetching price history:', error);
      throw error;
    }
  },

  getRiskMetrics: async (): Promise<any> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets/risk-metrics`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch risk metrics: ${response.status} ${response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error fetching risk metrics:', error);
      throw error;
    }
  },

  // Get all analytics data in one call
  getAllAnalytics: async (): Promise<any> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/assets/analytics`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to fetch analytics: ${response.status} ${response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error fetching analytics:', error);
      throw error;
    }
  },

  getCurrentPrice: async (assetName: string): Promise<number> => {
    try {
      // Try to get price from crypto API first
      const response = await fetch(`${API_BASE_URL}/api/crypto/price/${assetName.toLowerCase()}`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (response.ok) {
        const data = await response.json();
        return typeof data === 'number' ? data : data.price;
      }
      
      // Fallback to stored price
      return 0;
    } catch (error) {
      console.error(`Error fetching current price for ${assetName}:`, error);
      return 0;
    }
  }
};

// Analytics service (placeholder for future use)
export const analyticsService = {
  // Future analytics endpoints can be added here
};

// Price History API - Connects to your Spring Boot backend
export const priceHistoryService = {
  // Fetch historical price data for a specific coin
  getPriceHistory: async (coinId: string): Promise<PriceHistoryPoint[]> => {
    try {
      console.log('🔍 Fetching price history for:', coinId);
      const response = await fetch(`${API_BASE_URL}/api/price-history/${coinId}`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      console.log('📡 Response status:', response.status);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch price history for ${coinId}: ${response.statusText}`);
      }
      
      const rawData = await response.json();
      console.log('📊 Raw data received:', rawData);
      console.log('📊 Data length:', rawData.length);
      
      if (!Array.isArray(rawData) || rawData.length === 0) {
        console.warn('⚠️ No data received from backend');
        return [];
      }
      
      // Transform the raw data from [timestamp, price][] to PriceHistoryPoint[]
      const transformedData = rawData.map(([timestamp, price]: [number, number]) => ({
        timestamp,
        price,
        date: new Date(timestamp).toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit'
        })
      }));
      
      console.log('✅ Transformed data:', transformedData.slice(0, 3)); // Show first 3 items
      return transformedData;
    } catch (error) {
      console.error('❌ Error fetching price history:', error);
      throw error;
    }
  },

  // Get price history with time range (uses your backend's cached data)
  getPriceHistoryWithRange: async (coinId: string, days: number = 30): Promise<PriceHistoryPoint[]> => {
    try {
      console.log('🔍 Fetching price history with range for:', coinId, 'days:', days);
      
      // Check authentication status
      const isAuth = authService.isAuthenticated();
      const token = authService.getToken();
      const authHeader = authService.getAuthHeader();
      
      console.log('🔍 Authentication check:', {
        isAuthenticated: isAuth,
        hasToken: !!token,
        tokenLength: token?.length,
        authHeader: authHeader
      });
      
      // Use your backend's days parameter
      const response = await fetch(`${API_BASE_URL}/api/price-history/${coinId}?days=${days}`, {
        headers: {
          'Content-Type': 'application/json',
          ...authHeader
        }
      });
      
      console.log('📡 Response status:', response.status);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch price history for ${coinId}: ${response.statusText}`);
      }
      
      const rawData = await response.json();
      console.log('📊 Raw data received:', rawData);
      console.log('📊 Data length:', rawData.length);
      
      if (!Array.isArray(rawData) || rawData.length === 0) {
        console.warn('⚠️ No data received from backend for', coinId);
        
        // Try alternative coin ID if the first one failed
        const alternativeIds = {
          'ripple': 'xrp',
          'xrp': 'ripple',
          'bitcoin': 'btc',
          'btc': 'bitcoin',
          'ethereum': 'eth',
          'eth': 'ethereum'
        };
        
        const alternativeId = alternativeIds[coinId.toLowerCase() as keyof typeof alternativeIds];
        if (alternativeId) {
          console.log('🔄 Trying alternative coin ID:', alternativeId);
          return priceHistoryService.getPriceHistoryWithRange(alternativeId, days);
        }
        
        return [];
      }
      
      // Transform the raw data from [timestamp, price][] to PriceHistoryPoint[]
      const transformedData = rawData.map(([timestamp, price]: [number, number]) => ({
        timestamp,
        price,
        date: new Date(timestamp).toLocaleDateString('en-US', {
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit'
        })
      }));
      

      return transformedData;
    } catch (error) {
      console.error('❌ Error fetching price history with range:', error);
      throw error;
    }
  },

  // Force refresh cache for a specific coin
  refreshPriceHistory: async (coinId: string, days: number = 90): Promise<void> => {
    try {
      console.log('🔄 Refreshing price history cache for:', coinId, 'days:', days);
      
      const response = await fetch(`${API_BASE_URL}/api/price-history/refresh/${coinId}?days=${days}`, {
        method: 'POST',
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to refresh price history for ${coinId}: ${response.statusText}`);
      }
      
      console.log('✅ Cache refreshed successfully for:', coinId);
    } catch (error) {
      console.error('❌ Error refreshing price history:', error);
      throw error;
    }
  },

  // Get cache status for a specific coin
  getCacheStatus: async (coinId: string): Promise<any> => {
    try {
      console.log('📊 Getting cache status for:', coinId);
      
      const response = await fetch(`${API_BASE_URL}/api/price-history/status/${coinId}`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to get cache status for ${coinId}: ${response.statusText}`);
      }
      
      const status = await response.json();
      console.log('📊 Cache status:', status);
      return status;
    } catch (error) {
      console.error('❌ Error getting cache status:', error);
      throw error;
    }
  },

  // Get full service cache summary
  getServiceStatus: async (): Promise<any> => {
    try {
      console.log('📊 Getting service status');
      
      const response = await fetch(`${API_BASE_URL}/api/price-history/status`, {
        headers: {
          ...authService.getAuthHeader()
        }
      });
      
      if (!response.ok) {
        throw new Error(`Failed to get service status: ${response.statusText}`);
      }
      
      const status = await response.json();
      console.log('📊 Service status:', status);
      return status;
    } catch (error) {
      console.error('❌ Error getting service status:', error);
      throw error;
    }
  }
};



 
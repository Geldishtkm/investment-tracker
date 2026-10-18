import { Asset, AssetWithPrice, Coin, PortfolioAnalytics } from '../types';
import { API_BASE_URL } from '../config';
import { authService } from './authService';


export const assetService = {
  getAllAssets: async (): Promise<Asset[]> => {
    try {
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets`, {
        headers: {
          'Content-Type': 'application/json'
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
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
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
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json'
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
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets/${id}`, {
        method: 'DELETE',
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
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets/with-prices`);
      
      if (response.ok) {
        return await response.json();
      }
      
      const assets = await assetService.getAllAssets();
      const assetsWithPrices: AssetWithPrice[] = assets.map(asset => ({
        ...asset,
        currentPrice: asset.pricePerUnit,
        priceChange: 0,
        priceChangePercent: 0
      }));
      return assetsWithPrices;
    } catch (error) {
      // Don't turn a failure into an empty list: callers would show "portfolio is empty"
      console.error('Error fetching assets with prices:', error);
      throw error;
    }
  },

  // Update all asset prices from CoinGecko in one batched backend call
  refreshPrices: async (): Promise<{ updated: number; unpriced: string[] }> => {
    const response = await authService.authFetch(`${API_BASE_URL}/api/assets/refresh-prices`, {
      method: 'POST',
    });
    if (!response.ok) {
      throw new Error(`Failed to refresh prices: ${response.status}`);
    }
    return response.json();
  },

  getTopCoins: async (): Promise<Coin[]> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/crypto/top`);
      
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

  // Get all analytics data in one call
  getAllAnalytics: async (): Promise<PortfolioAnalytics> => {
    try {
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets/analytics`);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch analytics: ${response.status} ${response.statusText}`);
      }
      
      return await response.json();
    } catch (error) {
      console.error('Error fetching analytics:', error);
      throw error;
    }
  },

};




 
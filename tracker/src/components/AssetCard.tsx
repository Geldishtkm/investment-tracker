import React, { useState } from 'react';
import { Edit, Save, X, Trash2, TrendingUp, DollarSign, Percent, Target } from 'lucide-react';
import { Asset } from '../types';
import { assetService } from '../services/api';

interface AssetCardProps {
  asset: Asset;
  onUpdate: (asset: Asset) => void;
  onDelete: (id: number, assetName: string) => void;
  onShowToast: (type: 'success' | 'error' | 'info', message: string) => void;
}

const AssetCard: React.FC<AssetCardProps> = ({ asset, onUpdate, onDelete, onShowToast }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    name: asset.name,
    quantity: asset.quantity.toString(),
    pricePerUnit: asset.pricePerUnit.toString()
  });

  const handleSave = async () => {
    if (!formData.name.trim()) {
      onShowToast('error', 'Asset name is required');
      return;
    }

    if (isNaN(parseFloat(formData.quantity)) || parseFloat(formData.quantity) <= 0) {
      onShowToast('error', 'Quantity must be a positive number');
      return;
    }

    if (isNaN(parseFloat(formData.pricePerUnit)) || parseFloat(formData.pricePerUnit) <= 0) {
      onShowToast('error', 'Price per unit must be a positive number');
      return;
    }

    try {
      const updatedAsset = {
        name: formData.name.trim(),
        quantity: parseFloat(formData.quantity),
        pricePerUnit: parseFloat(formData.pricePerUnit),
        purchasePricePerUnit: asset.purchasePricePerUnit || parseFloat(formData.pricePerUnit), // Keep original or use current
        initialInvestment: asset.initialInvestment || (parseFloat(formData.quantity) * parseFloat(formData.pricePerUnit)) // Keep original or calculate
      };

      const savedAsset = await assetService.updateAsset(asset.id, updatedAsset);
      onUpdate(savedAsset);
      setIsEditing(false);
      onShowToast('success', 'Asset updated successfully!');
    } catch (error) {
      onShowToast('error', 'Failed to update asset');
    }
  };

  const handleDelete = async () => {
    try {
      await onDelete(asset.id, asset.name);
    } catch (error) {
      onShowToast('error', 'Failed to delete asset');
    }
  };

  const currentValue = asset.quantity * asset.pricePerUnit;
  const initialInvestment = asset.initialInvestment || currentValue; // Fallback to current value if not set
  const roi = currentValue - initialInvestment;
  const roiPercentage = initialInvestment > 0 ? (roi / initialInvestment) * 100 : 0;

  return (
    <div className="bg-gradient-to-br from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-gray-700/50 rounded-2xl p-6 hover:shadow-xl hover:shadow-gray-900/50 transition-all duration-300 group">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-br from-green-500 to-emerald-600 rounded-xl flex items-center justify-center shadow-lg">
            <DollarSign size={20} className="text-white" />
          </div>
          <div>
            <h3 className="font-bold text-white text-lg group-hover:text-green-300 transition-colors duration-300">
              {asset.name}
            </h3>
            <p className="text-sm text-gray-400">Asset #{asset.id}</p>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          {isEditing ? (
            <>
              <button
                onClick={handleSave}
                className="p-2 bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors duration-200"
              >
                <Save size={16} />
              </button>
              <button
                onClick={() => setIsEditing(false)}
                className="p-2 bg-gray-600 hover:bg-gray-700 text-white rounded-lg transition-colors duration-200"
              >
                <X size={16} />
              </button>
            </>
          ) : (
            <>
              <button
                onClick={() => setIsEditing(true)}
                className="p-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors duration-200"
              >
                <Edit size={16} />
              </button>
              <button
                onClick={handleDelete}
                className="p-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors duration-200"
              >
                <Trash2 size={16} />
              </button>
            </>
          )}
        </div>
      </div>

      {/* Asset Details */}
      <div className="space-y-4">
        {isEditing ? (
          // Edit Form
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Asset Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
                className="w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Quantity</label>
              <input
                type="number"
                value={formData.quantity}
                onChange={(e) => setFormData(prev => ({ ...prev, quantity: e.target.value }))}
                className="w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500"
                step="0.000001"
                min="0"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">Current Price per Unit</label>
              <input
                type="number"
                value={formData.pricePerUnit}
                onChange={(e) => setFormData(prev => ({ ...prev, pricePerUnit: e.target.value }))}
                className="w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500"
                step="0.01"
                min="0"
              />
            </div>
          </div>
        ) : (
          // Display Mode
          <div className="space-y-4">
            {/* Basic Info */}
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                <div className="text-sm text-gray-400 mb-1">Quantity</div>
                <div className="text-lg font-bold text-white">{asset.quantity.toLocaleString()}</div>
              </div>
              <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                <div className="text-sm text-gray-400 mb-1">Current Price</div>
                <div className="text-lg font-bold text-white">${asset.pricePerUnit.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })}</div>
              </div>
            </div>

                         {/* Purchase Info */}
             <div className="grid grid-cols-2 gap-4">
               <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                 <div className="text-sm text-gray-400 mb-1">Purchase Price</div>
                 <div className="text-lg font-bold text-blue-300">${(asset.purchasePricePerUnit || asset.pricePerUnit).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })}</div>
               </div>
               <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                 <div className="text-sm text-gray-400 mb-1">Initial Investment</div>
                 <div className="text-lg font-bold text-purple-300">${(asset.initialInvestment || currentValue).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
               </div>
             </div>

            {/* Current Value and ROI */}
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                <div className="text-sm text-gray-400 mb-1">Current Value</div>
                <div className="text-lg font-bold text-white">${currentValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="bg-gray-800/50 rounded-lg p-3 border border-gray-700/50">
                <div className="text-sm text-gray-400 mb-1">ROI</div>
                <div className={`text-lg font-bold ${roi >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                  {roi >= 0 ? '+' : ''}${roi.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </div>
              </div>
            </div>

            {/* ROI Percentage */}
            <div className="bg-gradient-to-r from-gray-800/50 to-gray-700/50 rounded-lg p-3 border border-gray-700/50">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Percent size={16} className="text-gray-400" />
                  <span className="text-sm text-gray-400">ROI Percentage</span>
                </div>
                <div className={`text-lg font-bold ${roiPercentage >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                  {roiPercentage >= 0 ? '+' : ''}{roiPercentage.toFixed(2)}%
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AssetCard; 
import React, { useEffect } from 'react';
import { CheckCircle, XCircle, X } from 'lucide-react';

interface ToastProps {
  message: string;
  type: 'success' | 'error' | 'info';
  isVisible: boolean;
  onClose: () => void;
}

const Toast: React.FC<ToastProps> = ({ message, type, isVisible, onClose }) => {
  useEffect(() => {
    if (isVisible) {
      const timer = setTimeout(() => {
        onClose();
      }, 3000); // Auto close after 3 seconds

      return () => clearTimeout(timer);
    }
  }, [isVisible, onClose]);

  if (!isVisible) return null;

  const bgColor = type === 'success' 
    ? 'bg-gradient-to-r from-green-500/90 to-emerald-600/90' 
    : type === 'error'
    ? 'bg-gradient-to-r from-red-500/90 to-red-600/90'
    : 'bg-gradient-to-r from-blue-500/90 to-blue-600/90';
  
  const borderColor = type === 'success' 
    ? 'border-green-400/30' 
    : type === 'error'
    ? 'border-red-400/30'
    : 'border-blue-400/30';
  
  const iconColor = type === 'success' 
    ? 'text-green-100' 
    : type === 'error'
    ? 'text-red-100'
    : 'text-blue-100';

  return (
    <div className="fixed top-6 right-6 z-50 animate-in slide-in-from-right-full duration-300">
      <div className={`${bgColor} backdrop-blur-sm border ${borderColor} rounded-xl p-4 shadow-2xl min-w-[320px] max-w-[400px]`}>
        <div className="flex items-start gap-3">
          <div className={`flex-shrink-0 ${iconColor}`}>
            {type === 'success' ? (
              <CheckCircle size={20} className="animate-pulse" />
            ) : type === 'error' ? (
              <XCircle size={20} className="animate-pulse" />
            ) : (
              <div className="w-5 h-5 rounded-full border-2 border-current animate-pulse" />
            )}
          </div>
          
          <div className="flex-1 min-w-0">
            <p className="text-white font-medium text-sm leading-relaxed">
              {message}
            </p>
          </div>
          
          <button
            onClick={onClose}
            className="flex-shrink-0 text-white/70 hover:text-white transition-colors p-1 rounded-lg hover:bg-white/10"
          >
            <X size={16} />
          </button>
        </div>
        
        {/* Progress bar */}
        <div className="mt-3 h-1 bg-white/20 rounded-full overflow-hidden">
          <div 
            className={`h-full ${type === 'success' ? 'bg-green-100' : type === 'error' ? 'bg-red-100' : 'bg-blue-100'} rounded-full transition-all duration-3000 ease-linear`}
            style={{ width: '100%' }}
          />
        </div>
      </div>
    </div>
  );
};

export default Toast; 
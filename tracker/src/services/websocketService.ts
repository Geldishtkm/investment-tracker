import { EventEmitter } from 'events';

// For production, always use secure WebSocket (wss://)
// Force the production URL to ensure no localhost fallback
const WS_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL?.replace('https://', 'wss://')?.replace('http://', 'ws://') || 'wss://invtracker-s5ain.ondigitalocean.app';

// Debug logging
console.log('🔌 WebSocket Service Initialized (Tracker)');
console.log('🔌 WS_BASE_URL:', WS_BASE_URL);
console.log('🔌 Environment VITE_API_BASE_URL:', (import.meta as any).env?.VITE_API_BASE_URL);
console.log('🔌 Final WebSocket URL will be:', WS_BASE_URL + '/ws');

/**
 * WebSocket service for real-time market data
 * Uses native WebSocket instead of sockjs-client for better browser compatibility
 */
class WebSocketService {
    private socket: WebSocket | null = null;
    private isConnected = false;
    private subscriptions = new Map<string, (data: any) => void>();
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 3000;
    private reconnectTimer: NodeJS.Timeout | null = null;

    /**
     * Initialize WebSocket connection
     */
    async connect(): Promise<void> {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            console.log('WebSocket already connected');
            return;
        }

        return new Promise((resolve, reject) => {
            try {
                // Use native WebSocket instead of sockjs-client
                const wsUrl = WS_BASE_URL + '/ws';
                console.log('🔌 Attempting to connect to WebSocket URL:', wsUrl);
                console.log('🔌 WS_BASE_URL value:', WS_BASE_URL);
                console.log('🔌 Environment variable VITE_API_BASE_URL:', (import.meta as any).env?.VITE_API_BASE_URL);
                
                this.socket = new WebSocket(wsUrl);
                
                // Connection success handler
                this.socket.onopen = () => {
                    console.log('WebSocket connected successfully');
                    this.isConnected = true;
                    this.reconnectAttempts = 0;
                    
                    // Resubscribe to previous subscriptions
                    this.resubscribeAll();
                    resolve();
                };

                // Connection error handler
                this.socket.onerror = (error) => {
                    console.error('WebSocket error:', error);
                    this.isConnected = false;
                    reject(new Error('WebSocket connection failed'));
                };

                // Disconnect handler
                this.socket.onclose = () => {
                    console.log('WebSocket disconnected');
                    this.isConnected = false;
                    this.handleReconnect();
                };

                // Message handler
                this.socket.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        this.handleMessage(data);
                    } catch (error) {
                        console.error('Failed to parse WebSocket message:', error);
                    }
                };

            } catch (error) {
                console.error('Failed to create WebSocket connection:', error);
                reject(error);
            }
        });
    }

    /**
     * Handle incoming WebSocket messages
     */
    private handleMessage(data: any) {
        // Handle different message types
        if (data.type === 'market-data') {
            // Broadcast market data to subscribers
            const symbol = data.symbol;
            const callback = this.subscriptions.get(symbol);
            if (callback) {
                callback(data);
            }
        } else if (data.type === 'subscription-status') {
            // Handle subscription status updates
            console.log('Subscription status:', data);
        }
    }

    /**
     * Disconnect WebSocket
     */
    disconnect(): void {
        if (this.socket && this.isConnected) {
            this.socket.close();
            this.isConnected = false;
            this.subscriptions.clear();
            
            if (this.reconnectTimer) {
                clearTimeout(this.reconnectTimer);
                this.reconnectTimer = null;
            }
        }
    }

    /**
     * Subscribe to market data for specific symbol
     */
    subscribeToMarketData(symbol: string, callback: (data: any) => void): void {
        if (!this.socket || !this.isConnected) {
            console.warn('WebSocket not connected, cannot subscribe to', symbol);
            return;
        }

        try {
            // Store subscription for cleanup
            this.subscriptions.set(symbol, callback);

            // Send subscription request to server
            const message = {
                type: 'subscribe',
                symbol: symbol
            };
            
            this.socket.send(JSON.stringify(message));
            console.log('Subscribed to market data for:', symbol);

        } catch (error) {
            console.error('Failed to subscribe to', symbol, ':', error);
        }
    }

    /**
     * Unsubscribe from market data for specific symbol
     */
    unsubscribeFromMarketData(symbol: string): void {
        if (!this.socket || !this.isConnected) {
            return;
        }

        try {
            // Send unsubscription request to server
            const message = {
                type: 'unsubscribe',
                symbol: symbol
            };
            
            this.socket.send(JSON.stringify(message));

            // Remove from local subscriptions
            this.subscriptions.delete(symbol);
            console.log('Unsubscribed from market data for:', symbol);

        } catch (error) {
            console.error('Failed to unsubscribe from', symbol, ':', error);
        }
    }

    /**
     * Subscribe to subscription status updates
     */
    subscribeToStatusUpdates(_callback: (status: any) => void): void {
        // For now, we'll handle status updates through the main message handler
        // This can be enhanced later
        console.log('Status update subscription not yet implemented');
    }

    /**
     * Get connection status
     */
    getConnectionStatus(): boolean {
        return this.isConnected;
    }

    /**
     * Get list of subscribed symbols
     */
    getSubscribedSymbols(): string[] {
        return Array.from(this.subscriptions.keys());
    }

    /**
     * Handle reconnection logic
     */
    private handleReconnect(): void {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached');
            return;
        }

        this.reconnectAttempts++;
        console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

        this.reconnectTimer = setTimeout(() => {
            this.connect().catch((error) => {
                console.error('Reconnection failed:', error);
            });
        }, this.reconnectDelay * this.reconnectAttempts);
    }

    /**
     * Resubscribe to all previous subscriptions after reconnection
     */
    private resubscribeAll(): void {
        const symbols = Array.from(this.subscriptions.keys());
        symbols.forEach(symbol => {
            const callback = this.subscriptions.get(symbol);
            if (callback) {
                this.subscribeToMarketData(symbol, callback);
            }
        });
    }
}

// Export singleton instance
export const websocketService = new WebSocketService();
export default websocketService;

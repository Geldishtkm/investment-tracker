import { LoginCredentials, RegisterCredentials, RegisterResponse, AuthResponse, User, MfaSetupResponse, MfaVerificationResponse } from '../types';

const AUTH_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'https://invtracker-s5ain.ondigitalocean.app';

/** Error from the auth API, carrying the backend's machine-readable code (e.g. EMAIL_NOT_VERIFIED). */
export class AuthError extends Error {
  code?: string;
  email?: string;
  retryAfterSeconds?: number;

  constructor(message: string, body?: any) {
    super(message);
    this.name = 'AuthError';
    this.code = body?.code;
    this.email = body?.email;
    this.retryAfterSeconds = body?.retryAfterSeconds;
  }
}

async function toAuthError(response: Response, fallback: string): Promise<AuthError> {
  const text = await response.text();
  let body: any = null;
  try {
    body = JSON.parse(text);
  } catch {
    // plain-text error body
  }
  return new AuthError(body?.error || text || `${fallback} (HTTP ${response.status})`, body);
}

export const authService = {
  register: async (credentials: RegisterCredentials): Promise<RegisterResponse> => {
    const formData = new URLSearchParams();
    formData.append('username', credentials.username);
    formData.append('email', credentials.email);
    formData.append('password', credentials.password);

    const response = await fetch(`${AUTH_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: formData.toString(),
    });

    if (!response.ok) {
      throw await toAuthError(response, 'Registration failed');
    }
    return response.json();
  },

  // Confirm the 6-digit code emailed at signup
  verifyEmail: async (email: string, code: string): Promise<{ message: string; username: string }> => {
    const response = await fetch(`${AUTH_BASE_URL}/auth/verify-email`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code }),
    });
    if (!response.ok) {
      throw await toAuthError(response, 'Verification failed');
    }
    return response.json();
  },

  // Ask for a new code; the backend enforces a cooldown between sends
  resendCode: async (email: string): Promise<{ message: string; resendCooldownSeconds: number }> => {
    const response = await fetch(`${AUTH_BASE_URL}/auth/resend-code`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) {
      throw await toAuthError(response, 'Could not resend the code');
    }
    return response.json();
  },

  login: async (credentials: LoginCredentials): Promise<AuthResponse> => {
    try {
      const formData = new URLSearchParams();
      formData.append('username', credentials.username);
      formData.append('password', credentials.password);

      const response = await fetch(`${AUTH_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: formData.toString(),
      });

      if (!response.ok) {
        throw await toAuthError(response, 'Login failed');
      }

      // Parse the JSON response to get the token
      const authResponse: AuthResponse = await response.json();
      
      if (!authResponse.token) {
        throw new Error('No token received from server');
      }

      // Save the token to localStorage
      authService.saveToken(authResponse.token);
      
      console.log('Login successful, token saved:', authResponse.token.substring(0, 20) + '...');
      
      return authResponse;
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  },

  saveToken: (token: string): void => {
    localStorage.setItem('token', token);
  },

  getToken: (): string | null => {
    return localStorage.getItem('token');
  },

  removeToken: (): void => {
    localStorage.removeItem('token');
  },

  isAuthenticated: (): boolean => {
    const token = localStorage.getItem('token');
    if (!token || token.length === 0) {
      return false;
    }
    
    // Basic token validation - check if it's a valid JWT format
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.log('Invalid JWT format');
        return false;
      }
      
      // Check if token is expired (basic check)
      const payload = JSON.parse(atob(parts[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      
      if (payload.exp && payload.exp < currentTime) {
        console.log('Token expired, removing from storage');
        authService.removeToken();
        return false;
      }
      
      // Additional validation: check if token has required fields
      if (!payload.sub || !payload.iat) {
        console.log('Token missing required fields');
        authService.removeToken();
        return false;
      }
      
      return true;
    } catch (error) {
      console.error('Token validation error:', error);
      authService.removeToken();
      return false;
    }
  },

  clearAuth: (): void => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  },

  getCurrentUser: (): User | null => {
    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
      // Decode JWT token to get username (basic implementation)
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        username: payload.sub || 'Unknown',
        isAuthenticated: true
      };
    } catch (error) {
      console.error('Error decoding token:', error);
      return null;
    }
  },

  getAuthHeader: (): { Authorization: string } | {} => {
    const token = authService.getToken();
    
    if (token) {
      const header = { Authorization: `Bearer ${token}` };
      console.log('Creating auth header with token:', token.substring(0, 20) + '...');
      return header;
    } else {
      console.log('No token found, returning empty auth header');
      return {};
    }
  },

  // Add method to test authentication
  testAuth: async (): Promise<boolean> => {
    try {
      const token = authService.getToken();
      if (!token) return false;

      const response = await fetch(`${AUTH_BASE_URL}/api/assets/debug`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (response.ok) {
        const data = await response.json();
        console.log('Auth test successful:', data);
        return true;
      } else {
        console.log('Auth test failed:', response.status);
        return false;
      }
    } catch (error) {
      console.error('Auth test error:', error);
      return false;
    }
  },

  // MFA Methods
  setupMfa: async (username: string, password: string): Promise<MfaSetupResponse> => {
    try {
      const response = await fetch(`${AUTH_BASE_URL}/api/security/mfa/setup`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password })
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `MFA setup failed with status ${response.status}`);
      }

      const result: MfaSetupResponse = await response.json();
      return result;
    } catch (error) {
      console.error('MFA setup error:', error);
      throw error;
    }
  },

  verifyMfa: async (username: string, code: string): Promise<MfaVerificationResponse> => {
    try {
      const response = await fetch(`${AUTH_BASE_URL}/api/security/mfa/verify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, code })
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `MFA verification failed with status ${response.status}`);
      }

      const result: MfaVerificationResponse = await response.json();
      return result;
    } catch (error) {
      console.error('MFA verification error:', error);
      throw error;
    }
  },

  getMfaStatus: async (username: string): Promise<any> => {
    try {
      const response = await fetch(`${AUTH_BASE_URL}/api/security/mfa/status/${username}`);
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Failed to get MFA status with status ${response.status}`);
      }

      const result = await response.json();
      return result;
    } catch (error) {
      console.error('Get MFA status error:', error);
      throw error;
    }
  }
};

// Expose authService to window for debugging
if (typeof window !== 'undefined') {
  (window as any).authService = authService;
} 
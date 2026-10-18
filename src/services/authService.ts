import { LoginCredentials, RegisterCredentials, RegisterResponse, AuthResponse, User, MfaSetupResponse, MfaVerificationResponse } from '../types';
import { API_BASE_URL } from '../config';


/** Error from the auth API, carrying the backend's machine-readable code (e.g. EMAIL_NOT_VERIFIED). */
/** JSON error body returned by the auth endpoints. */
interface ApiErrorBody {
  error?: string;
  code?: string;
  email?: string;
  retryAfterSeconds?: number;
}

export class AuthError extends Error {
  code?: string;
  email?: string;
  retryAfterSeconds?: number;

  constructor(message: string, body?: ApiErrorBody | null) {
    super(message);
    this.name = 'AuthError';
    this.code = body?.code;
    this.email = body?.email;
    this.retryAfterSeconds = body?.retryAfterSeconds;
  }
}

async function toAuthError(response: Response, fallback: string): Promise<AuthError> {
  const text = await response.text();
  let body: ApiErrorBody | null = null;
  try {
    body = JSON.parse(text);
  } catch {
    // plain-text error body
  }
  return new AuthError(body?.error || text || `${fallback} (HTTP ${response.status})`, body);
}

/** Fired on window when the saved session is missing, expired or rejected by the backend. */
export const SESSION_EXPIRED_EVENT = 'auth:session-expired';

/** Thrown by authFetch when the user has to log in again; the App has already been notified. */
export class SessionExpiredError extends Error {
  constructor(message = 'Your session has expired. Please log in again.') {
    super(message);
    this.name = 'SessionExpiredError';
  }
}

/** True when the JWT's exp claim has passed (or the token can't be read). */
function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

// Several requests can fail at once when a session ends; only the first one notifies the App
let sessionEndNotified = false;

function expireSession(message?: string): SessionExpiredError {
  const error = new SessionExpiredError(message);
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  if (!sessionEndNotified) {
    sessionEndNotified = true;
    window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: error.message }));
  }
  return error;
}

export const authService = {
  register: async (credentials: RegisterCredentials): Promise<RegisterResponse> => {
    const formData = new URLSearchParams();
    formData.append('username', credentials.username);
    formData.append('email', credentials.email);
    formData.append('password', credentials.password);

    const response = await fetch(`${API_BASE_URL}/auth/register`, {
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
    const response = await fetch(`${API_BASE_URL}/auth/verify-email`, {
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
    const response = await fetch(`${API_BASE_URL}/auth/resend-code`, {
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

      const response = await fetch(`${API_BASE_URL}/auth/login`, {
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
      
      
      return authResponse;
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  },

  saveToken: (token: string): void => {
    localStorage.setItem('token', token);
    sessionEndNotified = false;
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
        return false;
      }
      
      // Check if token is expired (basic check)
      const payload = JSON.parse(atob(parts[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      
      if (payload.exp && payload.exp < currentTime) {
        authService.removeToken();
        return false;
      }
      
      // Additional validation: check if token has required fields
      if (!payload.sub || !payload.iat) {
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

  getAuthHeader: (): Record<string, string> => {
    const token = authService.getToken();
    
    if (token) {
      const header = { Authorization: `Bearer ${token}` };
      return header;
    } else {
      return {};
    }
  },

  /**
   * fetch() for authenticated endpoints: sends the saved token as "Authorization: Bearer <token>".
   * A missing or expired token, or a 401 from the backend, ends the session and returns the user
   * to the login page instead of failing with a confusing error.
   */
  authFetch: async (url: string, init: RequestInit = {}): Promise<Response> => {
    const token = authService.getToken();
    if (!token || isTokenExpired(token)) {
      throw expireSession(token ? undefined : 'Please log in to continue.');
    }

    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(url, { ...init, headers });

    if (response.status === 401) {
      const body: ApiErrorBody | null = await response.clone().json().catch(() => null);
      throw expireSession(body?.error);
    }
    return response;
  },

  // Add method to test authentication
  testAuth: async (): Promise<boolean> => {
    try {
      const token = authService.getToken();
      if (!token) return false;

      // Any authenticated endpoint works; /api/assets/health is cheap and requires a valid token
      const response = await fetch(`${API_BASE_URL}/api/assets/health`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      return response.ok;
    } catch (error) {
      console.error('Auth test error:', error);
      return false;
    }
  },

  // MFA Methods
  setupMfa: async (username: string, password: string): Promise<MfaSetupResponse> => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/security/mfa/setup`, {
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
      const response = await fetch(`${API_BASE_URL}/api/security/mfa/verify`, {
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

};
 
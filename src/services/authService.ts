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

const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
// Renew a little before the access token expires, so a request doesn't arrive just after it lapses
const EXPIRY_MARGIN_MS = 30_000;
const REFRESH_LOCK = 'auth-token-refresh';

/** True when the JWT's exp claim has passed, or will within the margin (or the token can't be read). */
function isTokenExpired(token: string, marginMs = 0): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.exp === 'number' && payload.exp * 1000 - marginMs <= Date.now();
  } catch {
    return true;
  }
}

/** Tokens returned by /auth/login and /auth/refresh. */
interface TokenResponse {
  token: string;
  refreshToken?: string;
}

function saveTokens(tokens: TokenResponse): void {
  localStorage.setItem(TOKEN_KEY, tokens.token);
  if (tokens.refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  sessionEndNotified = false;
}

/**
 * Gets a new access token with the stored refresh token. The refresh token is single-use, so
 * concurrent callers share one request, and other tabs wait on a browser lock: a tab that finds
 * the tokens already renewed by another tab uses those instead of spending a used refresh token.
 */
let refreshInFlight: Promise<string> | null = null;

function refreshAccessToken(rejectedToken: string | null): Promise<string> {
  if (!refreshInFlight) {
    const run = () => renewTokens(rejectedToken);
    // The lock resolves with the callback's result; the DOM typings model that as a nested promise
    const locked: Promise<string> = typeof navigator !== 'undefined' && navigator.locks
      ? (navigator.locks.request(REFRESH_LOCK, run) as unknown as Promise<string>)
      : run();
    refreshInFlight = locked.finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

async function renewTokens(rejectedToken: string | null): Promise<string> {
  const current = localStorage.getItem(TOKEN_KEY);
  if (current && current !== rejectedToken && !isTokenExpired(current, EXPIRY_MARGIN_MS)) {
    return current; // renewed meanwhile by another tab
  }
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) {
    throw expireSession(current ? undefined : 'Please log in to continue.');
  }

  // A network failure or server error here propagates as a normal error: the session is kept
  // and the next request tries again, instead of logging the user out over a brief outage
  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (response.status === 401 || response.status === 400) {
    const body: ApiErrorBody | null = await response.json().catch(() => null);
    throw expireSession(body?.error);
  }
  if (!response.ok) {
    throw new Error(`Could not renew the session (HTTP ${response.status}). Please try again.`);
  }
  const tokens: TokenResponse = await response.json();
  saveTokens(tokens);
  return tokens.token;
}

// Several requests can fail at once when a session ends; only the first one notifies the App
let sessionEndNotified = false;

function expireSession(message?: string): SessionExpiredError {
  const error = new SessionExpiredError(message);
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
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

      // Short-lived access token plus the refresh token that renews it
      saveTokens(authResponse);
      return authResponse;
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  },

  saveToken: (token: string): void => {
    saveTokens({ token });
  },

  getToken: (): string | null => {
    return localStorage.getItem(TOKEN_KEY);
  },

  removeToken: (): void => {
    localStorage.removeItem(TOKEN_KEY);
  },

  /**
   * Whether a session can be used without logging in: a well-formed access token is saved, or a
   * refresh token that can renew it. An expired access token alone doesn't end the session.
   */
  isAuthenticated: (): boolean => {
    if (localStorage.getItem(REFRESH_TOKEN_KEY)) {
      return true;
    }
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      return false;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return Boolean(payload.sub && payload.iat) && !isTokenExpired(token);
    } catch {
      return false;
    }
  },

  clearAuth: (): void => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem('user');
  },

  /** Ends the session on the server (so the refresh token can't be used again) and locally. */
  logout: async (): Promise<void> => {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    authService.clearAuth();
    if (!refreshToken) return;
    try {
      await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
    } catch (error) {
      // Already logged out locally; the token expires on its own
      console.warn('Logout request failed:', error);
    }
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

  /**
   * fetch() for authenticated endpoints, acting as the auth interceptor: sends the access token as
   * "Authorization: Bearer <token>", renews it with the refresh token when it has expired (before
   * sending, or after a 401) and retries the request once with the new token. Only when the refresh
   * token is also expired or invalid does the session end and the login page appear.
   */
  authFetch: async (url: string, init: RequestInit = {}): Promise<Response> => {
    const send = (accessToken: string) => {
      const headers = new Headers(init.headers);
      headers.set('Authorization', `Bearer ${accessToken}`);
      return fetch(url, { ...init, headers });
    };

    let token = authService.getToken();
    if (!token || isTokenExpired(token, EXPIRY_MARGIN_MS)) {
      token = await refreshAccessToken(token);
    }

    let response = await send(token);
    if (response.status === 401) {
      // Rejected despite looking valid (e.g. revoked, or the server's clock differs): renew and retry once
      token = await refreshAccessToken(token);
      response = await send(token);
      if (response.status === 401) {
        const body: ApiErrorBody | null = await response.clone().json().catch(() => null);
        throw expireSession(body?.error);
      }
    }
    return response;
  },

  /** Checks the saved session with the backend, renewing the access token if needed. */
  testAuth: async (): Promise<boolean> => {
    try {
      // Any authenticated endpoint works; /api/assets/health is cheap and requires a valid token
      const response = await authService.authFetch(`${API_BASE_URL}/api/assets/health`);
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
 
/**
 * API Client for Green Mobility Pass
 * Handles authentication and API calls to the FastAPI backend
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  TokenResponse,
  UserInfo,
  UserRegister,
  UserStatistics,
  JourneyCreate,
  JourneyRead,
} from './types';

// Storage keys
const ACCESS_TOKEN_KEY = '@GMP_access_token';
const REFRESH_TOKEN_KEY = '@GMP_refresh_token';

// API base URL - configure this for your backend
const API_BASE_URL = 'http://10.0.2.2:8000'; // Android emulator localhost

class ApiClient {
  private baseUrl: string;
  private accessToken: string | null = null;
  private refreshToken: string | null = null;
  private refreshPromise: Promise<boolean> | null = null;

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl;
    this.loadTokens();
  }

  /**
   * Set the API base URL
   */
  setBaseUrl(url: string): void {
    this.baseUrl = url;
  }

  /**
   * Load tokens from storage
   */
  async loadTokens(): Promise<void> {
    try {
      const [accessToken, refreshToken] = await Promise.all([
        AsyncStorage.getItem(ACCESS_TOKEN_KEY),
        AsyncStorage.getItem(REFRESH_TOKEN_KEY),
      ]);
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
    } catch (error) {
      console.error('Failed to load tokens:', error);
    }
  }

  /**
   * Save tokens to storage
   */
  private async saveTokens(tokens: TokenResponse): Promise<void> {
    try {
      await Promise.all([
        AsyncStorage.setItem(ACCESS_TOKEN_KEY, tokens.access_token),
        AsyncStorage.setItem(REFRESH_TOKEN_KEY, tokens.refresh_token),
      ]);
      this.accessToken = tokens.access_token;
      this.refreshToken = tokens.refresh_token;
    } catch (error) {
      console.error('Failed to save tokens:', error);
    }
  }

  /**
   * Clear tokens from storage
   */
  async clearTokens(): Promise<void> {
    try {
      await Promise.all([
        AsyncStorage.removeItem(ACCESS_TOKEN_KEY),
        AsyncStorage.removeItem(REFRESH_TOKEN_KEY),
      ]);
      this.accessToken = null;
      this.refreshToken = null;
    } catch (error) {
      console.error('Failed to clear tokens:', error);
    }
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return this.accessToken !== null;
  }

  /**
   * Make an authenticated request
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {},
    retry = true,
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    const headers: HeadersInit_ = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    };

    if (this.accessToken) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${this.accessToken}`;
    }

    const response = await fetch(url, {
      ...options,
      headers,
    });

    // Handle 401 - try to refresh token
    if (response.status === 401 && retry && this.refreshToken) {
      const refreshed = await this.doRefreshToken();
      if (refreshed) {
        return this.request<T>(endpoint, options, false);
      }
      throw new Error('Session expired. Please login again.');
    }

    if (!response.ok) {
      const errorBody = await response.text();
      throw new Error(`API Error ${response.status}: ${errorBody}`);
    }

    return response.json();
  }

  /**
   * Refresh the access token
   */
  private async doRefreshToken(): Promise<boolean> {
    // Avoid multiple concurrent refresh attempts
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = (async () => {
      try {
        const response = await fetch(`${this.baseUrl}/token/refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            refresh_token: this.refreshToken,
          }),
        });

        if (!response.ok) {
          await this.clearTokens();
          return false;
        }

        const tokens: TokenResponse = await response.json();
        await this.saveTokens(tokens);
        return true;
      } catch (error) {
        console.error('Token refresh failed:', error);
        await this.clearTokens();
        return false;
      } finally {
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  // ==================== AUTH ENDPOINTS ====================

  /**
   * Register a new user
   */
  async register(userData: UserRegister): Promise<TokenResponse> {
    // 1) create user
    const createResp = await fetch(`${this.baseUrl}/users`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(userData),
    });

    if (!createResp.ok) {
      const errorBody = await createResp.text();
      throw new Error(`Registration failed: ${errorBody}`);
    }

    // 2) login to get tokens
    return this.login(userData.username, userData.password);
  }

  /**
   * Login with username and password
   */
  async login(username: string, password: string): Promise<TokenResponse> {
    const formData = new URLSearchParams();
    formData.append('username', username);
    formData.append('password', password);

    const response = await fetch(`${this.baseUrl}/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: formData.toString(),
    });

    if (!response.ok) {
      const errorBody = await response.text();
      throw new Error(`Login failed: ${errorBody}`);
    }

    const tokens: TokenResponse = await response.json();
    await this.saveTokens(tokens);
    return tokens;
  }

  /**
   * Logout - clear tokens
   */
  async logout(): Promise<void> {
    await this.clearTokens();
  }

  /**
   * Get current user info
   */
  async getMe(): Promise<UserInfo> {
    return this.request<UserInfo>('/me');
  }

  // ==================== JOURNEY ENDPOINTS ====================

  /**
   * Create a new journey
   */
  async createJourney(journey: JourneyCreate): Promise<JourneyRead> {
    return this.request<JourneyRead>('/journey/', {
      method: 'POST',
      body: JSON.stringify(journey),
    });
  }

  /**
   * Get validated journeys
   */
  async getValidatedJourneys(): Promise<JourneyRead[]> {
    return this.request<JourneyRead[]>('/journey/validated');
  }

  /**
   * Get user statistics
   */
  async getStatistics(): Promise<UserStatistics> {
    return this.request<UserStatistics>('/journey/statistics/me');
  }
}

// Export singleton instance
export const apiClient = new ApiClient();

export default apiClient;

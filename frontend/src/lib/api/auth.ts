import { apiClient } from './client';
import type { User, LoginCredentials, AuthResponse } from '@/lib/auth/types';

/**
 * Authentication API service
 */
export const authApi = {
  /**
   * Login user with username and password
   */
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await apiClient.post('/auth/login', credentials);
    return response.data;
  },

  /**
   * Logout user
   */
  async logout(): Promise<void> {
    await apiClient.post('/auth/logout');
  },

  /**
   * Get current user profile
   */
  async getProfile(): Promise<User> {
    const response = await apiClient.get('/auth/me');
    return response.data;
  },

  /**
   * Refresh access token
   */
  async refreshToken(refreshToken: string): Promise<{ accessToken: string }> {
    const response = await apiClient.post('/auth/refresh', { refreshToken });
    return response.data;
  },

  /**
   * Check if token is valid
   */
  async validateToken(): Promise<{ valid: boolean }> {
    const response = await apiClient.get('/auth/validate');
    return response.data;
  }
};

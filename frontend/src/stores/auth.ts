import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import Cookies from 'js-cookie';
import { authApi } from '@/lib/api/auth';
import type { User, LoginCredentials, AuthError } from '@/lib/auth/types';

/**
 * Authentication store using Zustand
 */
interface AuthStore {
  // State
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: AuthError | null;
  
  // Actions
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
  loadUser: () => Promise<void>;
  clearError: () => void;
  setLoading: (loading: boolean) => void;
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      // Initial state
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      // Actions
      login: async (credentials: LoginCredentials) => {
        set({ isLoading: true, error: null });
        
        try {
          const authResponse = await authApi.login(credentials);
          
          // Store tokens in cookies
          Cookies.set('accessToken', authResponse.tokens.accessToken, {
            expires: authResponse.tokens.expiresIn / (24 * 60 * 60), // Convert seconds to days
            secure: process.env.NODE_ENV === 'production',
            sameSite: 'lax'
          });
          
          if (authResponse.tokens.refreshToken) {
            Cookies.set('refreshToken', authResponse.tokens.refreshToken, {
              expires: 30, // 30 days
              secure: process.env.NODE_ENV === 'production',
              sameSite: 'lax'
            });
          }
          
          set({
            user: authResponse.user,
            isAuthenticated: true,
            isLoading: false,
            error: null
          });
        } catch (error: any) {
          const authError: AuthError = {
            message: error.response?.data?.message || 'Login failed',
            code: error.response?.data?.code || 'LOGIN_ERROR',
            details: error.response?.data
          };
          
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: authError
          });
          
          throw error;
        }
      },

      logout: () => {
        // Clear cookies
        Cookies.remove('accessToken');
        Cookies.remove('refreshToken');
        
        // Clear state
        set({
          user: null,
          isAuthenticated: false,
          isLoading: false,
          error: null
        });
        
        // Call logout API (fire and forget)
        authApi.logout().catch(console.error);
      },

      loadUser: async () => {
        const token = Cookies.get('accessToken');
        if (!token) {
          set({ isAuthenticated: false, user: null });
          return;
        }

        set({ isLoading: true });
        
        try {
          const user = await authApi.getProfile();
          set({
            user,
            isAuthenticated: true,
            isLoading: false,
            error: null
          });
        } catch (error: any) {
          // Token is invalid, clear everything
          Cookies.remove('accessToken');
          Cookies.remove('refreshToken');
          
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: null
          });
        }
      },

      clearError: () => {
        set({ error: null });
      },

      setLoading: (loading: boolean) => {
        set({ isLoading: loading });
      }
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        // Only persist user data, not loading states
        user: state.user,
        isAuthenticated: state.isAuthenticated
      })
    }
  )
);

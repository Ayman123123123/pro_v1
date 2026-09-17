import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { AUTH_EXPIRED_EVENT, getAccessToken, getRefreshToken, clearAuth, setAuthTokens } from '@/stores';

/**
 * العميل الموحد للوحة الإدارة (axios) — يشارك `src/api.ts` الحي العقد نفسه:
 * - مخزن توكن واحد: وصول + مستخدم في sessionStorage، وتجديد في localStorage
 *   (المفاتيح الفيزيائية نفسها في كلا العميلين، انظر `@/stores`)
 * - حدث انتهاء واحد: `younes:auth-expired` (تستمع له App)
 * - baseURL واحد نسبي `/api` يعمل مع dev proxy (vite → 127.0.0.1:8088)
 *   ومع Nginx في الإنتاج (same-origin). يُسمح بتجاوزه عبر VITE_API_URL
 *   (نسبي أو مطلق) لبيئات بلا بروكسي فقط.
 */
function resolveBaseURL(): string {
  const raw = (import.meta.env.VITE_API_URL as string | undefined)?.trim().replace(/\/+$/, '');
  return raw || '/api';
}

const API_BASE_URL = resolveBaseURL();
const CSRF_COOKIE = 'red_admin_csrf';

function csrfToken(): string | undefined {
  try {
    return document.cookie
      .split('; ')
      .find((item) => item.startsWith(`${CSRF_COOKIE}=`))
      ?.split('=')
      .slice(1)
      .join('=');
  } catch {
    return undefined;
  }
}

class ApiClient {
  private client: AxiosInstance;
  /** حارس تزامن التجديد: وعد واحد مشترك يُصفَّر في finally (مطابق لحارس api.ts). */
  private refreshPromise: Promise<string> | null = null;

  /** عقد التوحيد — مكشوف للفحص والاختبار. */
  readonly baseURL = API_BASE_URL;
  readonly authExpiredEvent = AUTH_EXPIRED_EVENT;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
      withCredentials: true,
    });

    this.client.interceptors.request.use(this.attachAuth.bind(this));
    this.client.interceptors.response.use(
      (response) => response,
      this.handleError.bind(this)
    );
  }

  private async attachAuth(config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> {
    const token = getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  }

  private async handleError(error: AxiosError): Promise<never> {
    if (error.response?.status === 401) {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

      if (!originalRequest._retry) {
        originalRequest._retry = true;

        try {
          const newToken = await this.refreshToken();
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
          }
          return this.client.request(originalRequest);
        } catch {
          // مسح واحد + حدث واحد؛ التوجيه لشاشة الدخول عبر مستمع App للحدث
          // (بلا window.location.href القسري الذي كان يكسر SPA ويخالف العميل الحي)
          clearAuth();
        }
      } else {
        clearAuth();
      }
    }
    throw error;
  }

  private async refreshToken(): Promise<string> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = (async () => {
      const refreshToken = getRefreshToken();
      const csrf = csrfToken();
      const response = await axios.post(
        `${API_BASE_URL}/auth/refresh`,
        { refreshToken: refreshToken || '' },
        {
          headers: { ...(csrf ? { 'X-RED-CSRF': csrf } : {}) },
          withCredentials: true,
        }
      );
      const { accessToken, refreshToken: rotated } = (response.data ?? {}) as {
        accessToken?: unknown;
        refreshToken?: unknown;
      };
      if (typeof accessToken !== 'string' || !accessToken) {
        throw new Error('EMPTY_REFRESH');
      }
      // حفظ التوكن الجديد في المخزن الوحيد قبل إعادة المحاولة
      setAuthTokens(accessToken, typeof rotated === 'string' ? rotated : undefined);
      return accessToken;
    })();

    try {
      return await this.refreshPromise;
    } finally {
      this.refreshPromise = null;
    }
  }

  async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const response = await this.client.get<T>(url, { params });
    return response.data;
  }

  async post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
    const response = await this.client.post<T>(url, data, config);
    return response.data;
  }

  async put<T>(url: string, data?: unknown): Promise<T> {
    const response = await this.client.put<T>(url, data);
    return response.data;
  }

  async patch<T>(url: string, data?: unknown): Promise<T> {
    const response = await this.client.patch<T>(url, data);
    return response.data;
  }

  async delete<T>(url: string): Promise<T> {
    const response = await this.client.delete<T>(url);
    return response.data;
  }
}

export const api = new ApiClient();

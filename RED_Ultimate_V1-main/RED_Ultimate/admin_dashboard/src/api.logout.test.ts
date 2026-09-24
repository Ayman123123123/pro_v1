import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminLogout, apiFetch, authStore } from './api';

class TestStorage {
  private readonly values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

beforeEach(() => {
  vi.stubGlobal('sessionStorage', new TestStorage());
  vi.stubGlobal('localStorage', new TestStorage());
  vi.stubGlobal('document', { cookie: 'red_admin_csrf=test-csrf' });
  vi.stubGlobal('window', { dispatchEvent: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('web administrator logout', () => {
  it('posts the HttpOnly cookie and CSRF even without a JavaScript refresh token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    authStore.set('access-only', undefined, { role: 'ADMIN' });

    await adminLogout();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({
      method: 'POST', credentials: 'same-origin',
      headers: expect.objectContaining({ 'X-RED-CSRF': 'test-csrf' }),
      body: JSON.stringify({ refreshToken: '' }),
    }));
    expect(authStore.access()).toBeNull();
  });

  it('clears local access and reports a server failure rather than claiming revocation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    authStore.set('access-only', undefined, { role: 'ADMIN' });
    await expect(adminLogout()).rejects.toThrow('لم يؤكد الخادم');
    expect(authStore.access()).toBeNull();
  });

  it('cannot restore access from an in-flight rotate after logout', async () => {
    let resolveRotate!: (value: Response) => void;
    const pendingRotate = new Promise<Response>((resolve) => { resolveRotate = resolve; });
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url === '/api/private') return Promise.resolve(new Response(null, { status: 401 }));
      if (url === '/api/auth/refresh') return pendingRotate;
      if (url === '/api/auth/logout') return Promise.resolve(new Response(null, { status: 204 }));
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    authStore.set('expired-access', undefined, { role: 'ADMIN' });
    const protectedRequest = apiFetch('/api/private');
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    await adminLogout();
    resolveRotate(new Response(JSON.stringify({ accessToken: 'do-not-restore' }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }));
    await protectedRequest;
    expect(authStore.access()).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(3); // no retry after logout
  });
});

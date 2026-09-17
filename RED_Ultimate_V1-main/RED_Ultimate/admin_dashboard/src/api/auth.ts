import { authStore, adminLogin, adminLogout } from '../api';

export async function getAuthLoader() {
  if (!authStore.isAuthenticated()) {
    return null;
  }
  const user = authStore.user();
  if (!user) return null;
  return {
    id: user.id || user.username,
    username: user.username,
    displayName: user.displayName,
    role: user.role || 'USER',
    redId: user.redId,
  };
}

export { adminLogin, adminLogout, authStore };
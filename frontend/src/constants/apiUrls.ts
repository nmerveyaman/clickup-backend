let rawBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/$/, '');

// Eğer Render iç ağ host adı verdiyse (örn: clickup-analytics-backend-fm11) ve sonunda nokta/alan adı yoksa, .onrender.com ekle
if (rawBaseUrl && !rawBaseUrl.includes('.') && !rawBaseUrl.includes('localhost')) {
  rawBaseUrl = `${rawBaseUrl}.onrender.com`;
}

// Başında http/https yoksa https:// ekle
export const BASE_URL = rawBaseUrl
  ? (rawBaseUrl.startsWith('http://') || rawBaseUrl.startsWith('https://')
      ? rawBaseUrl
      : `https://${rawBaseUrl}`)
  : '';

export const API_URLS = {
  // Auth
  LOGIN: `${BASE_URL}/login`,
  AUTH: {
    EXCHANGE: `${BASE_URL}/api/v1/auth/exchange`,
    ME: `${BASE_URL}/api/v1/auth/me`,
    SELECT_WORKSPACE: `${BASE_URL}/api/v1/auth/workspace`,
  },

  // Workspace / Sprint Listesi
  WORKSPACES: {
    GET_AVAILABLE: `${BASE_URL}/api/v1/workspaces/available`,
    GET_LISTS: `${BASE_URL}/api/v1/workspaces/lists`,
    GET_MEMBERS: `${BASE_URL}/api/v1/workspaces/members`,
    GET_MEMBER_ANALYTICS: `${BASE_URL}/api/v1/workspaces/members/analytics`,
  },

  // Analitikler
  ANALYTICS: {
    SPRINT_DETAILS: (listId: string) => `${BASE_URL}/api/analytics/sprint/${encodeURIComponent(listId)}`,
    TASK_DETAILS: (taskId: string) => `${BASE_URL}/api/analytics/task/${encodeURIComponent(taskId)}`,
    USER_GANTT: (listId?: string) =>
      `${BASE_URL}/api/analytics/user/gantt${listId ? `?listId=${encodeURIComponent(listId)}` : ''}`,
  },

  // Real-time SSE
  EVENTS: {
    STREAM: `${BASE_URL}/api/events/stream`,
  },
};

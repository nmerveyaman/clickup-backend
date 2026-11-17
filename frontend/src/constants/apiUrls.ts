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

  // Workspace / Sprint Listesi
  WORKSPACES: {
    GET_AVAILABLE: (userId: string | number) => `${BASE_URL}/api/v1/workspaces/available?userId=${encodeURIComponent(userId)}`,
    GET_LISTS: (workspaceId: string) => `${BASE_URL}/api/v1/workspaces/lists?workspaceId=${encodeURIComponent(workspaceId)}`,
    GET_MEMBERS: (workspaceId: string) => `${BASE_URL}/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members`,
    GET_MEMBER_ANALYTICS: (workspaceId: string) => `${BASE_URL}/api/v1/workspaces/${encodeURIComponent(workspaceId)}/members/analytics`,
  },

  // Analitikler
  ANALYTICS: {
    SPRINT_DETAILS: (listId: string) => `${BASE_URL}/api/analytics/sprint/${encodeURIComponent(listId)}`,
    TASK_DETAILS: (taskId: string) => `${BASE_URL}/api/analytics/task/${encodeURIComponent(taskId)}`,
    USER_GANTT: (userId: number | string, listId?: string) =>
      `${BASE_URL}/api/analytics/user/${userId}/gantt${listId ? `?listId=${encodeURIComponent(listId)}` : ''}`,
  },

  // Real-time SSE
  EVENTS: {
    STREAM: (workspaceId: string) => `${BASE_URL}/api/events/stream?workspaceId=${encodeURIComponent(workspaceId)}`,
  },
};

import { API_URLS } from '../constants/apiUrls';

export async function selectWorkspace(workspaceId: string): Promise<void> {
  const currentToken = localStorage.getItem('accessToken');
  if (!currentToken) {
    throw new Error('You must sign in before selecting a workspace.');
  }

  const response = await fetch(API_URLS.AUTH.SELECT_WORKSPACE, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${currentToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ workspaceId }),
  });
  if (!response.ok) {
    throw new Error(`Workspace selection failed (${response.status}).`);
  }

  const data = await response.json() as { access_token?: string };
  if (!data.access_token) {
    throw new Error('Workspace selection returned no access token.');
  }
  localStorage.setItem('accessToken', data.access_token);
}

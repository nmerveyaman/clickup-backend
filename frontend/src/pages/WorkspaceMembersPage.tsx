import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Clock,
  ListTodo,
  Mail,
  RefreshCw,
  RotateCcw,
  Shield,
  Users,
} from 'lucide-react';
import { API_URLS } from '../constants/apiUrls';
import { selectWorkspace } from '../utils/selectWorkspace';

interface AssignedTask {
  taskId: string;
  taskName: string;
  status: string | null;
  activeWorkingTime: string | null;
  taskActiveWorkingTime: string | null;
  waitingTime: string | null;
  totalDuration: string | null;
  estimatedTime: string | null;
  estimateUsagePercentage: number | null;
  estimateVariance: string | null;
  estimateOutcome: 'WITHIN_ESTIMATE' | 'MET_ESTIMATE' | 'OVER_ESTIMATE' | 'OVER_ESTIMATE_COMPLETED' | 'UNAVAILABLE' | null;
  stalled: boolean;
  rollbackCount: number;
}

interface Member {
  id: number;
  username: string | null;
  email: string | null;
  profilePicture: string | null;
  role: string;
  tasks: AssignedTask[];
}

export default function WorkspaceMembersPage() {
  const navigate = useNavigate();
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedMemberId, setExpandedMemberId] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [workspaceId, setWorkspaceId] = useState('');
  const [workspaceSwitching, setWorkspaceSwitching] = useState(false);
  const [workspaces, setWorkspaces] = useState<{ id: string; name: string }[]>([]);

  const handleWorkspaceChange = async (nextWorkspaceId: string) => {
    if (nextWorkspaceId === workspaceId || workspaceSwitching) return;
    setWorkspaceSwitching(true);
    try {
      await selectWorkspace(nextWorkspaceId);
      setWorkspaceId(nextWorkspaceId);
      setExpandedMemberId(null);
    } catch (switchError) {
      console.error('Failed to switch workspace:', switchError);
      setError(switchError instanceof Error ? switchError.message : 'Workspace selection failed.');
    } finally {
      setWorkspaceSwitching(false);
    }
  };

  useEffect(() => {
    let active = true;
    const loadWorkspaces = async () => {
      try {
        const token = localStorage.getItem('accessToken');
        const response = await fetch(API_URLS.WORKSPACES.GET_AVAILABLE, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!response.ok) throw new Error(`Workspace request failed (${response.status}).`);
        const data = await response.json();
        const available = Array.isArray(data?.workspaces) ? data.workspaces : [];
        if (!active) return;
        setWorkspaces(available);
        if (available.length === 0) {
          setError('No accessible workspace found for this account.');
          setLoading(false);
          return;
        }
        const preferred = data.defaultWorkspaceId;
        const selectedWorkspaceId = available.some((workspace: { id: string }) => workspace.id === preferred)
          ? preferred
          : available[0]?.id || '';
        if (!selectedWorkspaceId) {
          throw new Error('No workspace is authorized for this ClickUp account.');
        }
        await selectWorkspace(selectedWorkspaceId);
        setWorkspaceId(selectedWorkspaceId);
      } catch (loadError) {
        console.error('Failed to load workspace list:', loadError);
        if (active) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load workspace list.');
          setLoading(false);
        }
      }
    };
    void loadWorkspaces();
    return () => { active = false; };
  }, []);

  const loadMembers = useCallback(async (signal: AbortSignal) => {
    if (!workspaceId) {
      if (workspaces.length === 0) return;
      setMembers([]);
      setError('No accessible workspace found.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const token = localStorage.getItem('accessToken');
      const headers: Record<string, string> = {};
      if (token) headers.Authorization = `Bearer ${token}`;

      const response = await fetch(API_URLS.WORKSPACES.GET_MEMBER_ANALYTICS, {
        headers,
        signal,
      });
      if (!response.ok) {
        throw new Error(`Failed to load workspace members (${response.status}).`);
      }
      setMembers(await response.json() as Member[]);
    } catch (fetchError) {
      if (fetchError instanceof DOMException && fetchError.name === 'AbortError') return;
      console.error('Failed to load workspace member analytics:', fetchError);
      setError(fetchError instanceof Error ? fetchError.message : 'Failed to load workspace members.');
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, [workspaceId, workspaces.length]);

  useEffect(() => {
    const controller = new AbortController();
    void loadMembers(controller.signal);
    return () => controller.abort();
  }, [loadMembers, refreshKey, workspaceId, workspaces.length]);

  return (
    <div className="min-h-screen bg-[#0d1117] p-5 font-sans text-gray-100 sm:p-8">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-gray-800 pb-5">
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-2 rounded-xl border border-gray-800 bg-[#161b22] p-2.5 text-xs font-semibold text-gray-400 transition hover:border-indigo-500/50 hover:text-white"
          >
            <ArrowLeft className="h-4 w-4" />
            Dashboard
          </button>
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-white">
              <Users className="h-6 w-6 text-indigo-400" />
              Workspace Members
            </h1>
              <p className="mt-1 text-xs text-gray-400">Workspace members, assigned tasks, statuses, and time analytics</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {workspaces.length > 1 && (
            <select
              value={workspaceId}
              onChange={(event) => { void handleWorkspaceChange(event.target.value); }}
              disabled={workspaceSwitching}
              aria-label="Select workspace"
              className="max-w-56 rounded-xl border border-gray-800 bg-[#161b22] px-3 py-2.5 text-xs text-gray-200 outline-none focus:border-indigo-500"
            >
              {workspaces.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>{workspace.name}</option>
              ))}
            </select>
          )}
          <button
            type="button"
            onClick={() => setRefreshKey((key) => key + 1)}
            disabled={loading}
            className="flex items-center gap-2 rounded-xl border border-gray-800 bg-[#161b22] p-2.5 text-xs text-gray-300 transition hover:border-gray-700 hover:text-white disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </header>

      {loading ? (
        <div className="py-24 text-center text-sm text-gray-400">Loading workspace members and task analytics...</div>
      ) : error ? (
        <div role="alert" className="rounded-2xl border border-rose-500/30 bg-rose-500/5 p-6 text-center text-sm text-rose-300">
          {error}
        </div>
      ) : members.length === 0 ? (
        <div className="py-24 text-center text-sm text-gray-500">No members found in this workspace.</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {members.map((member) => {
            const expanded = expandedMemberId === member.id;
            const username = member.username || `Member ${member.id}`;
            return (
              <section key={member.id} className="overflow-hidden rounded-2xl border border-gray-800/80 bg-[#161b22]">
                <button
                  type="button"
                  aria-expanded={expanded}
                  onClick={() => setExpandedMemberId(expanded ? null : member.id)}
                  className="flex w-full items-center gap-4 p-5 text-left transition hover:bg-gray-800/20"
                >
                  {member.profilePicture ? (
                    <img
                      src={member.profilePicture}
                      alt=""
                      className="h-12 w-12 shrink-0 rounded-full border border-gray-700 object-cover"
                    />
                  ) : (
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-indigo-500/30 bg-indigo-600/20 text-sm font-bold text-indigo-300">
                      {username.slice(0, 2).toUpperCase()}
                    </div>
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-white">{username}</span>
                    <span className="mt-1 flex items-center gap-1.5 truncate text-xs text-gray-400">
                      <Mail className="h-3.5 w-3.5 shrink-0 text-gray-500" />
                      {member.email || 'No email'}
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2.5 py-0.5 text-[10px] font-medium text-indigo-300">
                        <Shield className="h-3 w-3" />
                        {member.role || 'DEVELOPER'}
                      </span>
                      <span className="inline-flex items-center gap-1 text-[11px] text-gray-400">
                        <ListTodo className="h-3.5 w-3.5" />
                        {member.tasks.length} assigned tasks
                      </span>
                    </span>
                  </span>
                  {expanded
                    ? <ChevronUp className="h-4 w-4 shrink-0 text-gray-500" />
                    : <ChevronDown className="h-4 w-4 shrink-0 text-gray-500" />}
                </button>

                {expanded && (
                  <div className="border-t border-gray-800/80 p-4">
                    {member.tasks.length === 0 ? (
                      <p className="py-5 text-center text-xs text-gray-500">No tasks assigned to this member.</p>
                    ) : (
                      <div className="space-y-2">
                        {member.tasks.map((task) => (
                          <article key={task.taskId} className="rounded-xl border border-gray-800 bg-[#0d1117]/70 p-3">
                            <div className="flex flex-wrap items-start justify-between gap-2">
                              <div className="min-w-0">
                                <h2 className="truncate text-xs font-semibold text-gray-100">{task.taskName || task.taskId}</h2>
                                <p className="mt-1 text-[11px] text-gray-500">Status: <span className="text-gray-300">{task.status || 'Unknown'}</span></p>
                              </div>
                              <div className="flex flex-wrap gap-2 text-[10px]">
                                {task.stalled && (
                                  <span className="inline-flex items-center gap-1 rounded-full border border-rose-500/20 bg-rose-500/10 px-2 py-1 text-rose-300">
                                    <AlertTriangle className="h-3 w-3" /> Stalled
                                  </span>
                                )}
                                {task.rollbackCount > 0 && (
                                  <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-amber-300">
                                    <RotateCcw className="h-3 w-3" /> {task.rollbackCount} rollback{task.rollbackCount === 1 ? '' : 's'}
                                  </span>
                                )}
                              </div>
                            </div>
                            <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2" title="Member time is estimated from active statuses in task history; it is not ClickUp time tracking data.">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Activity className="h-3 w-3" /> Member active time</span>
                                <span className="mt-1 block text-[11px] font-medium text-emerald-300">{task.activeWorkingTime || '—'}</span>
                              </div>
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Clock className="h-3 w-3" /> Waiting time</span>
                                <span className="mt-1 block text-[11px] font-medium text-amber-200">{task.waitingTime || '—'}</span>
                              </div>
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Clock className="h-3 w-3" /> Total duration</span>
                                <span className="mt-1 block text-[11px] font-medium text-gray-300">{task.totalDuration || '—'}</span>
                              </div>
                            </div>
                            <div className="mt-2 rounded-lg border border-gray-800 bg-gray-800/20 px-2.5 py-2">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400">Task active time vs. estimate</span>
                                {task.estimatedTime ? (
                                  <span className={`rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                                    task.estimateOutcome === 'OVER_ESTIMATE' || task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED'
                                      ? 'border-rose-500/30 bg-rose-500/10 text-rose-300'
                                      : task.estimateOutcome === 'UNAVAILABLE'
                                        ? 'border-amber-500/30 bg-amber-500/10 text-amber-300'
                                        : 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                                  }`}>
                                    {task.estimateOutcome === 'OVER_ESTIMATE' ? 'Estimate exceeded'
                                      : task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED' ? 'Completed over estimate'
                                        : task.estimateOutcome === 'MET_ESTIMATE' ? 'Completed within estimate'
                                          : task.estimateOutcome === 'UNAVAILABLE' ? 'Comparison unavailable'
                                            : 'Within estimate'}
                                  </span>
                                ) : task.estimateOutcome === 'UNAVAILABLE' ? (
                                  <span className="text-[10px] text-amber-300">Estimate value could not be read</span>
                                ) : (
                                  <span className="text-[10px] text-gray-500">No estimate set</span>
                                )}
                              </div>
                              {task.estimatedTime && (
                                <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-gray-400">
                                  <span>Actual: <strong className="text-gray-200">{task.taskActiveWorkingTime || (task.estimateOutcome === 'UNAVAILABLE' ? '—' : '0 seconds')}</strong></span>
                                  <span>Estimate: <strong className="text-gray-200">{task.estimatedTime}</strong></span>
                                  {task.estimateUsagePercentage != null && (
                                    <span>Used: <strong className="text-gray-200">{task.estimateUsagePercentage}%</strong></span>
                                  )}
                                  {task.estimateVariance && (
                                    <span>{task.estimateOutcome === 'OVER_ESTIMATE' || task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED' ? 'Over by' : task.estimateOutcome === 'MET_ESTIMATE' ? 'Under by' : 'Remaining'}: <strong className="text-gray-200">{task.estimateVariance}</strong></span>
                                  )}
                                  {task.estimateOutcome === 'UNAVAILABLE' && (
                                    <span className="text-amber-300">Estimate value could not be read</span>
                                  )}
                                </div>
                              )}
                            </div>
                          </article>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}

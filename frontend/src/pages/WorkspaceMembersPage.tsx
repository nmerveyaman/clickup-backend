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

  const queryParams = new URLSearchParams(window.location.search);
  const workspaceId = queryParams.get('workspaceId') || localStorage.getItem('workspaceId');

  const loadMembers = useCallback(async (signal: AbortSignal) => {
    if (!workspaceId) {
      setMembers([]);
      setError('Workspace seçilmemiş. Dashboard üzerinden bir workspace seçin.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const token = localStorage.getItem('accessToken');
      const headers: Record<string, string> = {};
      if (token) headers.Authorization = `Bearer ${token}`;

      const response = await fetch(API_URLS.WORKSPACES.GET_MEMBER_ANALYTICS(workspaceId), {
        headers,
        signal,
      });
      if (!response.ok) {
        throw new Error(`Üye bilgileri alınamadı (${response.status}).`);
      }
      setMembers(await response.json() as Member[]);
    } catch (fetchError) {
      if (fetchError instanceof DOMException && fetchError.name === 'AbortError') return;
      console.error('Workspace kullanıcı analitiği yüklenemedi:', fetchError);
      setError(fetchError instanceof Error ? fetchError.message : 'Üye bilgileri yüklenemedi.');
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    const controller = new AbortController();
    void loadMembers(controller.signal);
    return () => controller.abort();
  }, [loadMembers, refreshKey]);

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
              Workspace kullanıcıları
            </h1>
            <p className="mt-1 text-xs text-gray-400">Üyeler, atanmış görevler ve görev durumlarına göre süreler</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setRefreshKey((key) => key + 1)}
          disabled={loading}
          className="flex items-center gap-2 rounded-xl border border-gray-800 bg-[#161b22] p-2.5 text-xs text-gray-300 transition hover:border-gray-700 hover:text-white disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Yenile
        </button>
      </header>

      {loading ? (
        <div className="py-24 text-center text-sm text-gray-400">Kullanıcılar ve görev analizleri yükleniyor...</div>
      ) : error ? (
        <div role="alert" className="rounded-2xl border border-rose-500/30 bg-rose-500/5 p-6 text-center text-sm text-rose-300">
          {error}
        </div>
      ) : members.length === 0 ? (
        <div className="py-24 text-center text-sm text-gray-500">Bu workspace için kullanıcı bulunamadı.</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {members.map((member) => {
            const expanded = expandedMemberId === member.id;
            const username = member.username || `Kullanıcı ${member.id}`;
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
                      {member.email || 'E-posta yok'}
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2.5 py-0.5 text-[10px] font-medium text-indigo-300">
                        <Shield className="h-3 w-3" />
                        {member.role || 'DEVELOPER'}
                      </span>
                      <span className="inline-flex items-center gap-1 text-[11px] text-gray-400">
                        <ListTodo className="h-3.5 w-3.5" />
                        {member.tasks.length} atanmış görev
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
                      <p className="py-5 text-center text-xs text-gray-500">Bu kullanıcıya atanmış görev yok.</p>
                    ) : (
                      <div className="space-y-2">
                        {member.tasks.map((task) => (
                          <article key={task.taskId} className="rounded-xl border border-gray-800 bg-[#0d1117]/70 p-3">
                            <div className="flex flex-wrap items-start justify-between gap-2">
                              <div className="min-w-0">
                                <h2 className="truncate text-xs font-semibold text-gray-100">{task.taskName || task.taskId}</h2>
                                <p className="mt-1 text-[11px] text-gray-500">Durum: <span className="text-gray-300">{task.status || 'Bilinmiyor'}</span></p>
                              </div>
                              <div className="flex flex-wrap gap-2 text-[10px]">
                                {task.stalled && (
                                  <span className="inline-flex items-center gap-1 rounded-full border border-rose-500/20 bg-rose-500/10 px-2 py-1 text-rose-300">
                                    <AlertTriangle className="h-3 w-3" /> Stalled
                                  </span>
                                )}
                                {task.rollbackCount > 0 && (
                                  <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-amber-300">
                                    <RotateCcw className="h-3 w-3" /> {task.rollbackCount} rollback
                                  </span>
                                )}
                              </div>
                            </div>
                            <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2" title="Bu kullanıcıya ait süre, görev geçmişindeki aktif statü kolonlarından hesaplanır; ClickUp time-tracking kaydı değildir.">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Activity className="h-3 w-3" /> Aktif süre (kişi)</span>
                                <span className="mt-1 block text-[11px] font-medium text-emerald-300">{task.activeWorkingTime || '—'}</span>
                              </div>
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Clock className="h-3 w-3" /> Bekleme</span>
                                <span className="mt-1 block text-[11px] font-medium text-amber-200">{task.waitingTime || '—'}</span>
                              </div>
                              <div className="rounded-lg bg-gray-800/40 px-2.5 py-2">
                                <span className="flex items-center gap-1 text-[10px] text-gray-500"><Clock className="h-3 w-3" /> Toplam süre</span>
                                <span className="mt-1 block text-[11px] font-medium text-gray-300">{task.totalDuration || '—'}</span>
                              </div>
                            </div>
                            <div className="mt-2 rounded-lg border border-gray-800 bg-gray-800/20 px-2.5 py-2">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400">Görev aktif süresi / tahmin</span>
                                {task.estimatedTime ? (
                                  <span className={`rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                                    task.estimateOutcome === 'OVER_ESTIMATE' || task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED'
                                      ? 'border-rose-500/30 bg-rose-500/10 text-rose-300'
                                      : task.estimateOutcome === 'UNAVAILABLE'
                                        ? 'border-amber-500/30 bg-amber-500/10 text-amber-300'
                                        : 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                                  }`}>
                                    {task.estimateOutcome === 'OVER_ESTIMATE' ? 'Tahmin aşıldı'
                                      : task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED' ? 'Tahmin aşımıyla tamamlandı'
                                        : task.estimateOutcome === 'MET_ESTIMATE' ? 'Tahmin dahilinde tamamlandı'
                                          : task.estimateOutcome === 'UNAVAILABLE' ? 'Karşılaştırılamadı'
                                            : 'Tahmin içinde'}
                                  </span>
                                ) : task.estimateOutcome === 'UNAVAILABLE' ? (
                                  <span className="text-[10px] text-amber-300">Tahmin değeri okunamadı</span>
                                ) : (
                                  <span className="text-[10px] text-gray-500">Tahmin girilmemiş</span>
                                )}
                              </div>
                              {task.estimatedTime && (
                                <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-gray-400">
                                  <span>Gerçekleşen: <strong className="text-gray-200">{task.taskActiveWorkingTime || (task.estimateOutcome === 'UNAVAILABLE' ? '—' : '0 Saniye')}</strong></span>
                                  <span>Tahmin: <strong className="text-gray-200">{task.estimatedTime}</strong></span>
                                  {task.estimateUsagePercentage != null && (
                                    <span>Kullanım: <strong className="text-gray-200">{task.estimateUsagePercentage}%</strong></span>
                                  )}
                                  {task.estimateVariance && (
                                    <span>{task.estimateOutcome === 'OVER_ESTIMATE' || task.estimateOutcome === 'OVER_ESTIMATE_COMPLETED' ? 'Aşım' : task.estimateOutcome === 'MET_ESTIMATE' ? 'Tahmin altı' : 'Kalan'}: <strong className="text-gray-200">{task.estimateVariance}</strong></span>
                                  )}
                                  {task.estimateOutcome === 'UNAVAILABLE' && (
                                    <span className="text-amber-300">Tahmin değeri okunamadı</span>
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

import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, Mail, Shield, User, ArrowLeft, RefreshCw } from 'lucide-react';
import { BASE_URL } from '../constants/apiUrls';

interface Member {
  id: number | string;
  username: string;
  email: string;
  profile_picture: string | null;
  appRole?: string;
  role_key?: string;
}

export default function WorkspaceMembersPage() {
  const navigate = useNavigate();
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const queryParams = new URLSearchParams(window.location.search);
  const workspaceId = queryParams.get('workspaceId') || localStorage.getItem('workspaceId');

  useEffect(() => {
    if (!workspaceId) return;

    const fetchMembers = async () => {
      setLoading(true);
      try {
        const token = localStorage.getItem('accessToken');
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
        };
        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }

        const res = await fetch(`${BASE_URL}/api/v1/workspaces/${workspaceId}/members`, { headers });
        if (res.ok) {
          const data = await res.json();
          setMembers(data);
        }
      } catch (err) {
        console.error('Ekip üyeleri çekilemedi:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchMembers();
  }, [workspaceId]);

  return (
    <div className="min-h-screen bg-[#0d1117] text-gray-100 p-8 font-sans">
      {/* HEADER */}
      <div className="flex justify-between items-center mb-8 pb-4 border-b border-gray-800">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/dashboard')}
            className="p-2 bg-[#161b22] border border-gray-800 hover:border-indigo-500/50 rounded-xl text-gray-400 hover:text-white transition-all flex items-center gap-2 text-xs font-semibold cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
            Dashboard'a Dön
          </button>
          <div>
            <h1 className="text-2xl font-bold text-white flex items-center gap-2">
              <Users className="w-6 h-6 text-indigo-400" />
              Workspace Üyeleri
            </h1>
            <p className="text-xs text-gray-400 mt-1">
              Workspace altındaki ekip üyeleri ve rolleri
            </p>
          </div>
        </div>

        <button
          onClick={() => window.location.reload()}
          className="p-2 bg-[#161b22] border border-gray-800 hover:border-gray-700 rounded-xl text-gray-400 hover:text-white transition-all flex items-center gap-2 text-xs cursor-pointer"
        >
          <RefreshCw className="w-4 h-4" />
          Yenile
        </button>
      </div>

      {/* MEMBER GRID */}
      {loading ? (
        <div className="flex justify-center items-center py-24 text-gray-400 text-sm">
          Üyeler yükleniyor...
        </div>
      ) : members.length === 0 ? (
        <div className="text-center py-24 text-gray-500 text-sm">
          Bu workspace için henüz üye bulunamadı.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {members.map((member) => (
            <div
              key={member.id}
              className="bg-[#161b22] border border-gray-800/80 rounded-2xl p-5 flex items-start gap-4 hover:border-indigo-500/30 transition-all"
            >
              {member.profile_picture ? (
                <img
                  src={member.profile_picture}
                  alt={member.username}
                  className="w-12 h-12 rounded-full object-cover border border-gray-700"
                />
              ) : (
                <div className="w-12 h-12 rounded-full bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 font-bold text-sm">
                  {member.username ? member.username.substring(0, 2).toUpperCase() : 'U'}
                </div>
              )}
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-white truncate">
                  {member.username}
                </h3>
                <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-1 truncate">
                  <Mail className="w-3.5 h-3.5 text-gray-500 shrink-0" />
                  <span className="truncate">{member.email || 'Email yok'}</span>
                </div>
                <div className="flex items-center gap-1.5 text-xs mt-3">
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                    <Shield className="w-3 h-3" />
                    {member.appRole || member.role_key || 'DEVELOPER'}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}


import React, { useState } from 'react';

interface UserAvatarProps {
  username?: string;
  profilePicture?: string | null;
  size?: number; // örn: 24, 32, 36
  className?: string;
}

const AVATAR_BG_COLORS = [
  'bg-indigo-600',
  'bg-blue-600',
  'bg-emerald-600',
  'bg-purple-600',
  'bg-amber-600',
  'bg-rose-600',
  'bg-cyan-600',
  'bg-pink-600',
];

export const UserAvatar: React.FC<UserAvatarProps> = ({ username, profilePicture, size = 32, className = '' }) => {
  const [imgError, setImgError] = useState(false);

  // Kullanıcı adının baş harflerini al (Örn: "Merve Yaman" -> "MY", "Ahmet" -> "A")
  const getInitials = (name?: string) => {
    if (!name || !name.trim()) return '?';
    const clean = name.replace(/^User\s*\(/i, '').replace(/\)$/, '').trim();
    const parts = clean.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    }
    return clean.slice(0, Math.min(2, clean.length)).toUpperCase();
  };

  // İsimden tutarlı bir renk seç
  const getColorClass = (name?: string) => {
    if (!name) return 'bg-indigo-700';
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const idx = Math.abs(hash) % AVATAR_BG_COLORS.length;
    return AVATAR_BG_COLORS[idx];
  };

  const fontSize = Math.max(9, Math.floor(size * 0.38));

  // Profil fotoğrafı var ve yükleme hatası almadıysa resmi göster
  if (profilePicture && !imgError) {
    return (
      <img
        src={profilePicture}
        alt={username || 'User Avatar'}
        onError={() => setImgError(true)} // Link kırıksa fallback'e düş
        className={`rounded-full object-cover shrink-0 border border-indigo-500/40 shadow-sm ${className}`}
        style={{ width: `${size}px`, height: `${size}px` }}
        title={username || 'User'}
      />
    );
  }

  // Fotoğraf yoksa veya yüklenemediyse renkli yuvarlak içinde baş harfleri göster
  return (
    <div
      className={`flex items-center justify-center rounded-full text-white font-bold shrink-0 border border-white/10 shadow-sm uppercase ${getColorClass(username)} ${className}`}
      style={{ width: `${size}px`, height: `${size}px`, fontSize: `${fontSize}px` }}
      title={username || 'User'}
    >
      {getInitials(username)}
    </div>
  );
};


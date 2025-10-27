import { useState } from "react"
import { cn } from "../lib/utils"
import { getAvatarUrl, getInitials, getUserColor } from "../lib/avatar"

interface AvatarProps {
  userId: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function Avatar({ userId, size = 'md', className }: AvatarProps) {
  const [imageError, setImageError] = useState(false);
  const avatarUrl = getAvatarUrl(userId);
  const initials = getInitials(userId);
  const colorClass = getUserColor(userId);

  const sizeClasses = {
    sm: 'w-6 h-6 text-xs',
    md: 'w-8 h-8 text-sm',
    lg: 'w-10 h-10 text-base',
  };

  // Fallback to initials if image fails to load
  if (imageError) {
    return (
      <div
        className={cn(
          "rounded-full flex items-center justify-center font-semibold text-white",
          sizeClasses[size],
          colorClass,
          className
        )}
      >
        {initials}
      </div>
    );
  }

  return (
    <img
      src={avatarUrl}
      alt={`Avatar for ${userId}`}
      className={cn(
        "rounded-full object-cover bg-muted",
        sizeClasses[size],
        className
      )}
      onError={() => setImageError(true)}
    />
  );
}

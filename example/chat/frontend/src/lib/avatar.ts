/**
 * Generate avatar URL for a user
 * Uses DiceBear API for generating unique avatars
 */

const AVATAR_STYLES = [
  'avataaars',
  'bottts',
  'fun-emoji',
  'identicon',
  'lorelei',
  'notionists',
  'pixel-art',
  'adventurer',
] as const;

// Cache to ensure consistent avatars for the same userId
const avatarCache = new Map<string, string>();

export function getAvatarUrl(userId: string): string {
  // Check cache first
  if (avatarCache.has(userId)) {
    return avatarCache.get(userId)!;
  }

  // Generate a consistent style based on userId
  const styleIndex = Math.abs(hashCode(userId)) % AVATAR_STYLES.length;
  const style = AVATAR_STYLES[styleIndex];

  // Generate avatar URL
  const avatarUrl = `https://api.dicebear.com/7.x/${style}/svg?seed=${encodeURIComponent(userId)}`;

  // Cache it
  avatarCache.set(userId, avatarUrl);

  return avatarUrl;
}

/**
 * Get initials from userId for fallback
 */
export function getInitials(userId: string): string {
  if (!userId) return '?';

  // Take first 2 characters
  return userId.substring(0, 2).toUpperCase();
}

/**
 * Generate a color based on userId for consistency
 */
export function getUserColor(userId: string): string {
  const colors = [
    'bg-red-500',
    'bg-blue-500',
    'bg-green-500',
    'bg-yellow-500',
    'bg-purple-500',
    'bg-pink-500',
    'bg-indigo-500',
    'bg-teal-500',
    'bg-orange-500',
    'bg-cyan-500',
  ];

  const index = Math.abs(hashCode(userId)) % colors.length;
  return colors[index];
}

/**
 * Simple hash function for consistency
 */
function hashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
}

import { cn } from "../lib/utils"
import { Avatar } from "./Avatar"

export interface MessageData {
  id: string;
  type: 'user' | 'other' | 'system';
  userId?: string;
  content: string;
  timestamp: Date;
}

interface MessageProps {
  message: MessageData;
}

export function Message({ message }: MessageProps) {
  const isSystem = message.type === 'system';
  const isUser = message.type === 'user';

  // System messages (centered)
  if (isSystem) {
    return (
      <div className="flex justify-center my-4">
        <div className="bg-muted text-muted-foreground text-xs px-3 py-1.5 rounded-full max-w-md text-center">
          {message.content}
        </div>
      </div>
    );
  }

  // User messages (right side) and other messages (left side)
  return (
    <div className={cn(
      "flex gap-2 mb-4 animate-in slide-in-from-bottom-2",
      isUser ? "justify-end" : "justify-start"
    )}>
      {/* Avatar for other users (left side) */}
      {!isUser && message.userId && (
        <div className="flex-shrink-0 mt-auto">
          <Avatar userId={message.userId} size="md" />
        </div>
      )}

      <div className={cn(
        "max-w-[70%] sm:max-w-[60%] flex flex-col",
        isUser ? "items-end" : "items-start"
      )}>
        {/* Username label for other users */}
        {!isUser && message.userId && (
          <div className="text-xs text-muted-foreground mb-1 px-1 font-medium">
            User {message.userId.substring(0, 6)}
          </div>
        )}

        {/* Message bubble */}
        <div className={cn(
          "rounded-2xl px-4 py-2 shadow-sm",
          isUser
            ? "bg-primary text-primary-foreground rounded-br-sm"
            : "bg-secondary text-secondary-foreground rounded-bl-sm"
        )}>
          <div className="break-words whitespace-pre-wrap">{message.content}</div>
        </div>

        {/* Timestamp */}
        <div className={cn(
          "text-xs text-muted-foreground mt-1 px-1",
          isUser && "text-right"
        )}>
          {message.timestamp.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
          })}
        </div>
      </div>

      {/* Avatar for current user (right side) */}
      {isUser && message.userId && (
        <div className="flex-shrink-0 mt-auto">
          <Avatar userId={message.userId} size="md" />
        </div>
      )}
    </div>
  );
}

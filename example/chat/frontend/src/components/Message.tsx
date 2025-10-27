import { cn } from "../lib/utils"

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

  return (
    <div
      className={cn(
        "mb-3 rounded-lg p-3 max-w-[80%] animate-in slide-in-from-bottom-2",
        isSystem && "mx-auto bg-muted text-muted-foreground text-sm text-center max-w-full",
        isUser && "ml-auto bg-primary text-primary-foreground",
        !isSystem && !isUser && "mr-auto bg-secondary text-secondary-foreground"
      )}
    >
      {!isSystem && (
        <div className="text-xs opacity-70 mb-1">
          {isUser ? "You" : message.userId}
        </div>
      )}
      <div className="break-words">{message.content}</div>
      <div className={cn("text-xs opacity-50 mt-1", isSystem && "hidden")}>
        {message.timestamp.toLocaleTimeString()}
      </div>
    </div>
  );
}

import { useEffect, useRef } from "react"
import { Message, type MessageData } from "./Message"

interface MessageListProps {
  messages: MessageData[];
}

export function MessageList({ messages }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-2">
      {messages.length === 0 ? (
        <div className="flex items-center justify-center h-full text-muted-foreground">
          <p>No messages yet. Start chatting!</p>
        </div>
      ) : (
        messages.map((message) => (
          <Message key={message.id} message={message} />
        ))
      )}
      <div ref={bottomRef} />
    </div>
  );
}

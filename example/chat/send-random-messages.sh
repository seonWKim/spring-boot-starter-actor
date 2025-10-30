#!/bin/bash

# Script to send random messages to a chat room periodically
# Usage: ./send-random-messages.sh <room-id> [user-id] [interval] [server-url]

# Default values
DEFAULT_USER_ID="random-bot"
DEFAULT_INTERVAL=5
DEFAULT_SERVER_URL="http://localhost:8080"

# Array of random messages
RANDOM_MESSAGES=(
    "Hello everyone!"
    "How's it going?"
    "Anyone there?"
    "What a great day!"
    "Testing the chat system..."
    "Random message incoming!"
    "This is an automated message"
    "Hope you're all doing well!"
    "Just checking in..."
    "Another random message here"
    "The weather is nice today"
    "Anyone up for a chat?"
    "Sending greetings from the bot"
    "This message was generated automatically"
    "Keep up the good work!"
    "How's everyone doing today?"
    "Just a friendly message"
    "Testing... 1, 2, 3..."
    "Random thought of the day"
)

# Parse command line arguments
if [ $# -lt 1 ]; then
    echo "Usage: $0 <room-id> [user-id] [interval-seconds] [server-url]"
    echo ""
    echo "Arguments:"
    echo "  room-id         : ID of the chat room to send messages to (required)"
    echo "  user-id         : ID of the user sending messages (default: $DEFAULT_USER_ID)"
    echo "  interval-seconds: Time between messages in seconds (default: $DEFAULT_INTERVAL)"
    echo "  server-url      : Base URL of the server (default: $DEFAULT_SERVER_URL)"
    echo ""
    echo "Example:"
    echo "  $0 room1"
    echo "  $0 room1 bot-user 3"
    echo "  $0 room1 bot-user 3 http://localhost:8081"
    exit 1
fi

ROOM_ID="$1"
USER_ID="${2:-$DEFAULT_USER_ID}"
INTERVAL="${3:-$DEFAULT_INTERVAL}"
SERVER_URL="${4:-$DEFAULT_SERVER_URL}"

API_ENDPOINT="${SERVER_URL}/api/chat/send"

echo "================================================"
echo "Random Message Sender"
echo "================================================"
echo "Room ID:      $ROOM_ID"
echo "User ID:      $USER_ID"
echo "Interval:     $INTERVAL seconds"
echo "Server URL:   $SERVER_URL"
echo "API Endpoint: $API_ENDPOINT"
echo "================================================"
echo ""
echo "Starting to send random messages. Press Ctrl+C to stop."
echo ""

# Counter for messages
MESSAGE_COUNTER=0

# Function to send a message
send_message() {
    local message="$1"
    local counter="$2"

    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "roomId": "$ROOM_ID",
  "userId": "$USER_ID",
  "message": "$message"
}
EOF
)

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Sending message #$counter: $message"

    # Send the message via curl
    response=$(curl -s -X POST "$API_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "$json_payload" \
        -w "\n%{http_code}")

    # Extract HTTP status code
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -eq 200 ]; then
        echo "  ✓ Message sent successfully"
    else
        echo "  ✗ Failed to send message (HTTP $http_code)"
        echo "  Response: $body"
    fi

    echo ""
}

# Function to get a random message
get_random_message() {
    local num_messages=${#RANDOM_MESSAGES[@]}
    local random_index=$((RANDOM % num_messages))
    echo "${RANDOM_MESSAGES[$random_index]}"
}

# Main loop
while true; do
    MESSAGE_COUNTER=$((MESSAGE_COUNTER + 1))

    # Get a random message
    message=$(get_random_message)

    # If it's a milestone (every 10 messages), add the counter
    if [ $((MESSAGE_COUNTER % 10)) -eq 0 ]; then
        message="Message #$MESSAGE_COUNTER: $message"
    fi

    # Send the message
    send_message "$message" "$MESSAGE_COUNTER"

    # Wait for the specified interval
    sleep "$INTERVAL"
done

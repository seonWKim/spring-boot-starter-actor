#!/bin/bash

# Script to send random messages to a chat room for load testing
# Usage: ./send-random-messages.sh <room-id> [options]

# Default values
DEFAULT_USER_ID="random-bot"
DEFAULT_INTERVAL=5
DEFAULT_SERVER_URL="http://localhost:8080"
DEFAULT_WORKERS=1
DEFAULT_RANDOM_USERS=0
DEFAULT_MAX_MESSAGES=0  # 0 means infinite

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
show_help() {
    cat <<EOF
Usage: $0 <room-id> [options]

Arguments:
  room-id         : ID of the chat room to send messages to (required)

Options:
  -u, --user-id <id>        : User ID (default: $DEFAULT_USER_ID)
  -i, --interval <seconds>  : Interval between messages in seconds (default: $DEFAULT_INTERVAL)
  -s, --server <url>        : Server URL (default: $DEFAULT_SERVER_URL)
  -w, --workers <count>     : Number of concurrent workers (default: $DEFAULT_WORKERS)
  -r, --random-users <count>: Use random user IDs. Specify count of unique users to rotate (default: disabled)
  -m, --max-messages <count>: Maximum total messages to send, 0 for infinite (default: $DEFAULT_MAX_MESSAGES)
  -b, --burst               : Burst mode - send messages as fast as possible (ignores interval)
  -q, --quiet               : Quiet mode - only show summary statistics
  -h, --help                : Show this help message

Load Testing Examples:
  # Infinite messages with random users
  $0 room1 --random-users 10

  # Burst test: 1000 messages from 50 concurrent workers with 20 random users
  $0 room1 -w 50 -r 20 -m 1000 -b

  # Sustained load: 5 workers, 100 random users, 0.1s interval
  $0 room1 -w 5 -r 100 -i 0.1 -m 10000

  # Single user, specific interval
  $0 room1 -u my-bot -i 2

EOF
    exit 0
}

# Initialize variables
ROOM_ID=""
USER_ID="$DEFAULT_USER_ID"
INTERVAL="$DEFAULT_INTERVAL"
SERVER_URL="$DEFAULT_SERVER_URL"
WORKERS="$DEFAULT_WORKERS"
RANDOM_USERS="$DEFAULT_RANDOM_USERS"
MAX_MESSAGES="$DEFAULT_MAX_MESSAGES"
BURST_MODE=false
QUIET_MODE=false

# Parse arguments
if [ $# -lt 1 ]; then
    show_help
fi

ROOM_ID="$1"
shift

while [ $# -gt 0 ]; do
    case "$1" in
        -u|--user-id)
            USER_ID="$2"
            shift 2
            ;;
        -i|--interval)
            INTERVAL="$2"
            shift 2
            ;;
        -s|--server)
            SERVER_URL="$2"
            shift 2
            ;;
        -w|--workers)
            WORKERS="$2"
            shift 2
            ;;
        -r|--random-users)
            RANDOM_USERS="$2"
            shift 2
            ;;
        -m|--max-messages)
            MAX_MESSAGES="$2"
            shift 2
            ;;
        -b|--burst)
            BURST_MODE=true
            INTERVAL=0
            shift
            ;;
        -q|--quiet)
            QUIET_MODE=true
            shift
            ;;
        -h|--help)
            show_help
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            ;;
    esac
done

API_ENDPOINT="${SERVER_URL}/api/chat/send"

# Setup statistics file and cleanup
STATS_FILE=$(mktemp)
STATS_LOCK_DIR="/tmp/send-random-messages-lock-$$"
trap "rm -f $STATS_FILE; rm -rf $STATS_LOCK_DIR" EXIT

# Initialize stats
echo "0:0:0:0" > "$STATS_FILE"  # Format: sent:success:failed:total_time_ms

# Generate random user pool if needed
RANDOM_USER_POOL=()
if [ "$RANDOM_USERS" -gt 0 ]; then
    for i in $(seq 1 "$RANDOM_USERS"); do
        RANDOM_USER_POOL+=("user-$RANDOM-$i")
    done
fi

# Display configuration
if [ "$QUIET_MODE" = false ]; then
    echo "================================================"
    echo "Random Message Sender - Load Testing Mode"
    echo "================================================"
    echo "Room ID:       $ROOM_ID"
    if [ "$RANDOM_USERS" -gt 0 ]; then
        echo "User Mode:     Random ($RANDOM_USERS unique users)"
    else
        echo "User ID:       $USER_ID"
    fi
    echo "Workers:       $WORKERS"
    echo "Interval:      $INTERVAL seconds"
    if [ "$BURST_MODE" = true ]; then
        echo "Burst Mode:    Enabled (maximum throughput)"
    fi
    if [ "$MAX_MESSAGES" -gt 0 ]; then
        echo "Max Messages:  $MAX_MESSAGES"
    else
        echo "Max Messages:  Unlimited"
    fi
    echo "Server URL:    $SERVER_URL"
    echo "API Endpoint:  $API_ENDPOINT"
    echo "================================================"
    echo ""
    echo "Starting load test. Press Ctrl+C to stop."
    echo ""
fi

# Start time for overall statistics
# Note: macOS doesn't support %N, so we use seconds * 1000 for milliseconds
START_TIME=$(($(date +%s) * 1000))

# Function to get a random user
get_random_user() {
    if [ "$RANDOM_USERS" -gt 0 ]; then
        local random_index=$((RANDOM % ${#RANDOM_USER_POOL[@]}))
        echo "${RANDOM_USER_POOL[$random_index]}"
    else
        echo "$USER_ID"
    fi
}

# Function to update statistics atomically (portable lock using mkdir)
update_stats() {
    local sent="$1"
    local success="$2"
    local failed="$3"
    local time_ms="$4"

    # Acquire lock using mkdir (atomic operation on all platforms)
    local lock_acquired=0
    local max_attempts=100
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if mkdir "$STATS_LOCK_DIR" 2>/dev/null; then
            lock_acquired=1
            break
        fi
        attempt=$((attempt + 1))
        sleep 0.01
    done

    if [ $lock_acquired -eq 0 ]; then
        # Failed to acquire lock, skip update
        return
    fi

    # Critical section - update stats
    IFS=':' read -r current_sent current_success current_failed current_time < "$STATS_FILE"
    new_sent=$((current_sent + sent))
    new_success=$((current_success + success))
    new_failed=$((current_failed + failed))
    new_time=$((current_time + time_ms))
    echo "$new_sent:$new_success:$new_failed:$new_time" > "$STATS_FILE"

    # Release lock
    rmdir "$STATS_LOCK_DIR" 2>/dev/null
}

# Function to send a message
send_message() {
    local message="$1"
    local counter="$2"
    local user_id=$(get_random_user)

    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "roomId": "$ROOM_ID",
  "userId": "$user_id",
  "message": "$message"
}
EOF
)

    if [ "$QUIET_MODE" = false ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Worker $counter sending: $message (user: $user_id)"
    fi

    # Send the message via curl and measure time
    local start_time=$(($(date +%s) * 1000))
    response=$(curl -s -X POST "$API_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "$json_payload" \
        -w "\n%{http_code}" \
        --max-time 30)
    local end_time=$(($(date +%s) * 1000))
    local elapsed=$((end_time - start_time))

    # Extract HTTP status code
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -eq 200 ]; then
        update_stats 1 1 0 "$elapsed"
        if [ "$QUIET_MODE" = false ]; then
            echo "  ✓ Success (${elapsed}ms)"
        fi
    else
        update_stats 1 0 1 "$elapsed"
        if [ "$QUIET_MODE" = false ]; then
            echo "  ✗ Failed (HTTP $http_code, ${elapsed}ms)"
            [ -n "$body" ] && echo "  Response: $body"
        fi
    fi
}

# Function to get a random message
get_random_message() {
    local num_messages=${#RANDOM_MESSAGES[@]}
    local random_index=$((RANDOM % num_messages))
    echo "${RANDOM_MESSAGES[$random_index]}"
}

# Worker function - runs in background
worker() {
    local worker_id="$1"
    local messages_per_worker="$2"

    local count=0
    while true; do
        # Check if we've reached the limit for this worker
        if [ "$messages_per_worker" -gt 0 ] && [ "$count" -ge "$messages_per_worker" ]; then
            break
        fi

        count=$((count + 1))

        # Get a random message
        local message=$(get_random_message)

        # Send the message
        send_message "$message" "$worker_id"

        # Wait for the interval (unless in burst mode)
        if [ "$INTERVAL" != "0" ] && [ "$(echo "$INTERVAL > 0" | bc -l 2>/dev/null || echo 1)" -eq 1 ]; then
            sleep "$INTERVAL"
        fi
    done
}

# Function to display statistics
show_stats() {
    IFS=':' read -r sent success failed total_time < "$STATS_FILE"
    local end_time=$(($(date +%s) * 1000))
    local elapsed_total=$((end_time - START_TIME))
    local elapsed_sec=$(echo "scale=2; $elapsed_total / 1000" | bc -l 2>/dev/null || echo "0")

    echo ""
    echo "================================================"
    echo "Load Test Summary"
    echo "================================================"
    echo "Total Messages:    $sent"
    echo "Successful:        $success"
    echo "Failed:            $failed"

    if [ "$sent" -gt 0 ]; then
        local success_rate=$(echo "scale=2; ($success * 100) / $sent" | bc -l 2>/dev/null || echo "0")
        local avg_response=$(echo "scale=2; $total_time / $sent" | bc -l 2>/dev/null || echo "0")
        echo "Success Rate:      ${success_rate}%"
        echo "Avg Response Time: ${avg_response}ms"
    fi

    echo "Total Duration:    ${elapsed_sec}s"

    if [ "$elapsed_total" -gt 0 ]; then
        local throughput=$(echo "scale=2; ($sent * 1000) / $elapsed_total" | bc -l 2>/dev/null || echo "0")
        echo "Throughput:        ${throughput} msg/s"
    fi

    echo "================================================"
}

# Cleanup function to kill all workers and show stats
cleanup() {
    echo ""
    echo "Stopping workers..."

    # Kill all background worker processes
    for pid in "${WORKER_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
        fi
    done

    # Wait a bit for workers to stop
    sleep 1

    # Force kill any remaining workers
    for pid in "${WORKER_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill -9 "$pid" 2>/dev/null
        fi
    done

    show_stats
    exit 0
}

# Trap SIGINT and SIGTERM to cleanup and show stats on exit
trap cleanup INT TERM

# Calculate messages per worker
MESSAGES_PER_WORKER=0
if [ "$MAX_MESSAGES" -gt 0 ]; then
    MESSAGES_PER_WORKER=$((MAX_MESSAGES / WORKERS))
    # Add remainder to first worker
    REMAINDER=$((MAX_MESSAGES % WORKERS))
fi

# Start workers
WORKER_PIDS=()
for i in $(seq 1 "$WORKERS"); do
    worker_messages="$MESSAGES_PER_WORKER"
    if [ "$i" -eq 1 ] && [ "$MAX_MESSAGES" -gt 0 ]; then
        worker_messages=$((MESSAGES_PER_WORKER + REMAINDER))
    fi

    worker "$i" "$worker_messages" &
    WORKER_PIDS+=($!)
done

# Monitor workers and show periodic stats
if [ "$QUIET_MODE" = true ] && [ "$MAX_MESSAGES" -gt 0 ]; then
    # In quiet mode with max messages, just wait for workers
    for pid in "${WORKER_PIDS[@]}"; do
        wait "$pid"
    done
else
    # Show periodic stats every 5 seconds
    while true; do
        sleep 5

        # Check if any workers are still running
        running=0
        for pid in "${WORKER_PIDS[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                running=1
                break
            fi
        done

        if [ "$running" -eq 0 ]; then
            break
        fi

        if [ "$QUIET_MODE" = false ]; then
            IFS=':' read -r sent success failed total_time < "$STATS_FILE"
            echo "[Progress] Messages: $sent | Success: $success | Failed: $failed"
        fi
    done
fi

# Show final stats
show_stats

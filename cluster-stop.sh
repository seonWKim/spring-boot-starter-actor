#!/bin/bash

kill_application() {
  local port=$1
  PID=$(lsof -t -i:$port)
  if [ -n "$PID" ]; then
    kill -9 $PID
    echo "Killed process on port $port"
  else
    echo "No process running on port $port"
  fi

  # Remove any matching log files
  for file in log_*_"$port".txt; do
    if [ -f "$file" ]; then
      rm "$file"
      echo "🧹 Removed file $file"
    fi
  done
}

kill_application 8080
kill_application 8081
kill_application 8082

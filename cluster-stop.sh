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

  log_file="log_${port}.txt"
  if [ -f "$log_file" ]; then
    rm "$log_file"
    echo "Removed file $log_file"
  else
    echo "No log file $log_file found"
  fi
}

kill_application 8080
kill_application 8081
kill_application 8082

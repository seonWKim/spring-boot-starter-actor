#!/bin/bash

start=$1
end=$2

count=0

for ((id=start; id<=end; id++))
do
  entityId=$((id % 10000))
  curl -s -o /dev/null -X GET "http://localhost:8080/hello?entityId=${entityId}&message=hello"
  ((count++))
  if (( count % 100 == 0 )); then
    sleep 2
    echo sleeping
  fi
done

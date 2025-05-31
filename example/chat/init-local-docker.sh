#!/bin/bash

# build jar
../../gradlew :example:chat:build

# Build image
docker build -t chat-app:latest .

# remove all the containers if exist
docker-compose down

# set up
docker-compose up -d

# check logs
# docker-compose logs -f chat-app-0

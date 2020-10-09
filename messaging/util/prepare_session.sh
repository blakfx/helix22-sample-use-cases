#!/bin/bash

# Given a desired "user name", create a working directory for the chat client
# Must run this file from /helix/ directory within Docker
CUR_DIR=$(pwd)
if [ "$CUR_DIR" != "/helix" ]; then
    echo "You must run this file from /helix directory within Docker"
    exit 1
fi

if [ "$#" -ne 1 ]; then
    echo "Usage: prepare_session <session_name>";
    exit 2
fi

SESSION=$1
CLIENT_JAR_PATH="/helix/java/chat/client/artifacts/client-1.0-SNAPSHOT.jar"

# If directory already exists, exit.
if [ -d "$SESSION" ]; then
    echo "The session" $SESSION "already exists. Try another name."
    exit 3
fi

# If no client JAR in parent, exit.
if [ ! -f "$CLIENT_JAR_PATH" ]; then
    echo "No client JAR found. Ask the admin to add it."
    exit 4
fi

# Create the directory
echo "Creating session in /helix/$SESSION ..."
mkdir "/helix/$SESSION"

# Symlink to JAR
echo "Creating symlink to client JAR"
ln -sf $CLIENT_JAR_PATH $SESSION/client.jar

# Symlink to run script
echo "Creating symlink to run script"
ln -sf /helix/util/run_client.sh $SESSION/run.sh
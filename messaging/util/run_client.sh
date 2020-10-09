#!/bin/bash

# Must run this file from /helix/ directory within Docker
CUR_DIR=$(pwd)
if [ "$CUR_DIR" == "/helix" ]; then
    echo "You must run this file from your own Helix session (/helix/<session_name>) within Docker."
    echo "Run prepare_session.sh if you have not created a session already."
    exit 1
fi

if [ "$#" -ne 1 ]; then
    echo "Usage: run <username>, where username is the user name to connect to Chat and Helix servers.";
    exit 2
fi

HOST="localhost"
PORT=8080
USER=$1

echo "Running the Java client demonstration"

# Force a fake device id of d-USER
# To avoid issues, USER must be unique
# From the guarantees of prepare_session, if this is ran from a client then just deny attempts to use different username
FORCED_NAME=`pwd | cut -f3 -d'/'`
if [ "$USER" != "$FORCED_NAME" ]; then
    echo "Since you are SSH'd into the Docker container, you share a physical device ID with anyone else that has used, is using, or will use this demo."
    echo "Helix is accommodated, up to the date of this latest modification, to work on the restraint that one physical machine can only be bound to one user at a given time."
    echo "To allow for others to seamlessly enjoy the demo as well, please connect with your username for this session:" $FORCED_NAME
    echo "If you are unhappy with this name, and want to try another, please create a separate session for it. Thank you!"
    exit 3
fi

# Create a sample message (replacing any existent one)
echo "Creating client_text.txt..."
echo "Hello there, world" > client_text.txt

# Run the code
echo "Launching chat client..."
#LD_LIBRARY_PATH="/native_dep/:$LD_LIBRARY_PATH" java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=0.0.0.0:7777 -Djava.library.path="/native_dep/" -jar client.jar -s localhost -p 8080 -u $1 -d d_$FORCED_NAME -hs 10.10.0.56 -ho 5567
LD_LIBRARY_PATH="/native_dep/:$LD_LIBRARY_PATH" java -Djava.library.path="/native_dep/" -jar client.jar -s localhost -p 8080 -u $1 -d d_$FORCED_NAME -hs 10.10.0.56 -ho 5567

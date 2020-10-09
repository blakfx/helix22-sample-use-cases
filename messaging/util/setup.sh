#!/bin/bash

# Must run this file from /helix/ directory within Docker
CUR_DIR=$(pwd)
if [ "$CUR_DIR" != "/helix" ]; then
    echo "You must run this file from /helix directory within Docker"
    exit 1
fi

# Start tomcat (without debugger)
export CATALINA_HOME=/opt/tomcat/latest
cp /helix/util/tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
$CATALINA_HOME/bin/catalina.sh start

# Prepare symlinks
# ./util/symlinks.sh

# Create Helix group
groupadd helix
# Create user for guests, and put in group
useradd -m guest
usermod -aG helix guest
usermod --password $(echo demo | openssl passwd -1 -stdin) guest

# Set up and start sshd
test -f /etc/ssh/ssh_host_ecdsa_key || /usr/bin/ssh-keygen -q -t ecdsa -f /etc/ssh/ssh_host_ecdsa_key -C '' -N ''
test -f /etc/ssh/ssh_host_rsa_key || /usr/bin/ssh-keygen -q -t rsa -f /etc/ssh/ssh_host_rsa_key -C '' -N ''
test -f /etc/ssh/ssh_host_ed25519_key || /usr/bin/ssh-keygen -q -t ed25519 -f /etc/ssh/ssh_host_ed25519_key -C '' -N ''
test -f /root/.ssh/id_rsa || /usr/bin/ssh-keygen -t rsa -f /root/.ssh/id_rsa -N ''
test -f /root/.ssh/id_rsa.pub || ssh-keygen -y -t rsa -f ~/.ssh/id_rsa > ~/.ssh/id_rsa.pub
test -f /root/.ssh/authorized_keys || /usr/bin/cp /root/.ssh/id_rsa.pub /root/.ssh/authorized_keys
/usr/sbin/sshd &

# Mark helix directory as read only by helix group, all access for root, no access for others
chgrp helix /helix -R
chmod 770 /helix
chmod 750 /helix/java -R
chmod 770 /helix/java/chat/client/artifacts/*
chmod 770 /helix/java/chat/server/artifacts/*
chmod 770 /helix/util/
chmod 740 /helix/util/*
chmod 770 /helix/util/run_client.sh
chmod 770 /helix/util/prepare_session.sh

# Messaging Helix Sample

## Table of Contents
- Description
- File structure
- Running

### Description
This is a sample showcasing some messaging application's application of Helix
for secure byte transfers (text/files). It showcases how to integrate Helix
to seamlessly offer encryption/decryption capabilities to your applications.

### File structure
```
./
    java/           (java source file contents)
        client/     (client files)
        server/     (server files)
    native_dep/     (native dependencies, including Helix-related ones)
    util/           (utility scripts to aid with running the project)
    Dockerfile      (Dockerfile to generate a running environment with)
```

### Running
A base Dockerfile is provided on `{repo}/Dockerfile`.
You should build it, and label it `helix-messaging:latest`. You can name it as
you wish, but the remainder of these instructions assume that the base has that
name.

Once the base is available, you can either modify it, or run it as is.

The utilities' directory supplied under `util/` has the up to date 
utility scripts that will aid you in running your environment seamlessly. 

After building Docker runtime image, create inside of it the following mount points:
1. the utilities' directory to `/helix/util/`
2. the java root directory to `/helix/java/chat/`

*Note:* due to how the directories are structured, you do not need to mount the artifacts directories as they are already mounted when you mount the java root directory.

```
docker run -e JPDA_ADDRESS=*:7777 -e JPDA_TRANSPORT=dt_socket 
-v /path/to/repo/java/:/helix/java/chat/ 
-v /path/to/repo/util/:/helix/util/
-p 8080:8080 -p 8000:8000 -p 7777:7777 -p 22:22 -it helix-messaging bash
```

JPDA_ADDRESS (for remote debugging) is set to 7777, and the port is exposed for it.
Port 8080 is used for the websocket endpoint, and is exposed for them.
Port 8000 is used for HTTP server, and it is exposed for it.
Port 22 is used for SSH access, and it is exposed for it.

When you're on the container, you should proceed as follows:
1. `cd /helix`
2. `./util/symlinks.sh` (should only really be done once on container start)
3. `./util/setup.sh` (should ONLY be done once on container start)
4. `cp java/chat/server/artifacts/server.war /opt/tomcat/latest/webapps` (should ONLY be done once on container start)
5. `./util/prepare_session.sh <session_name>` (done every time for a different session, refer te script for further comments)
6. `cd <session_name>` (switch into the session you want)
7. `./run.sh <session_name>` (run the chat application)

--
After this point, you can also connect additional users via SSH using the following credentials:
- Username: guest
- Password: demo
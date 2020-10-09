package com.blakfx.websocket;

import javax.enterprise.context.*;
import java.util.*;
import javax.websocket.*;
import javax.json.*;
import javax.json.spi.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.*;

import com.blakfx.model.Device;

import com.blakfx.protocols.DeviceProtocol;

/**
 * Handles most of the work dispatched to it by the <code>DeviceWebSocketServer</code>.
 * It keeps track of <code>UserSession</code>s, which consist of a session and a username,
 * as well as all the active devices on the server, and provides operations for managing these.
 */
@ApplicationScoped
public class DeviceSessionHandler {

    /**
     * Groups a session with an username, to allow for private messaging.
     * Otherwise, there's no way to identify what client is on what session.
     */
    private class UserSession {
        /**
         * The websocket session.
         */
        public Session session;
        /**
         * The user connected to the session.
         */
        public String username;

        /**
         * Constructs a partially complete user session.
         * Client must send an identification request to later bind the user
         * to the session through <code>bindUserToSession</code>.
         * @param session the session of this user session
         */
        public UserSession(Session session) {
            this.session = session;
        }
    }

    /**
     * Latest active device ID, used for assigning specific device IDs.
     */
    private static final AtomicInteger deviceId = new AtomicInteger(0);
    /**
     * The list of active user sessions on this server.
     */
    private final List<UserSession> userSessions = new ArrayList<>();
    /**
     * The set of active devices on this server.
     */
    private final Set<Device> devices = new HashSet<>();

    /**
     * This class' Logger instance.
     */
    static Logger log = LogManager.getLogger(DeviceSessionHandler.class.getName());

    /**
     * Adds the session to the user sessions list as a partially incomplete user session.
     * It also creates all active devices from the device list on that session.
     * @param session The session to add
     * @param useJSON Whether to use JSON or PB
     */
    public void addSession(Session session, boolean useJSON) {
        log.info("Adding session: " + session);
        userSessions.add(new UserSession(session));
        for (Device device : devices) {
            if(useJSON) {
                JsonObject addMessage = createAddMessage(device);
                sendToSession(session, addMessage);
            }
            else {
                DeviceProtocol.Device addMessage = createAddMessagePB(device);
                sendToSession(session, addMessage);
            }
        }
    }

    /**
     * Binds a username to a session for a user session.
     * Can fail if not unique username, or invalid session specified.
     * The error is returned as part of the JSON message in its field.
     * @param username The username to bind to the session
     * @param session The session to bind the username to
     */
    public void bindUserToSession(String username, Session session) {
        UserSession target = null;
        String error = "";
        for(UserSession userSession : userSessions) {
            boolean isUnassignedSession = userSession.username == null;
            if(!isUnassignedSession && userSession.username.equals(username)) {
                // usernames must be unique
               error = "username";
            }

            if(userSession.session.equals(session)) {
                // we've found a valid target, assign and break out
                target = userSession;
                break;
            }
        }
        if(target == null) {
            // must bind to a valid session
            error = "session";
        }
        // Assign to session only if there was no error
        if(error.isEmpty())
            target.username = username;

        // return a simple JSON message with pass/failed to client
        JsonProvider provider = JsonProvider.provider();
        JsonObject payload = provider.createObjectBuilder()
                            .add("action", "identify")
                            .add("error", error)
                            .build();
        sendToSession(session, payload);
    }

    /**
     * Remove a session (and its containing user session).
     * @param session The session to remove
     */
    public void removeSession(Session session) {
        log.info("Removing session: " + session);
        UserSession parent = null;
        for(UserSession userSession : userSessions) {
            if(userSession.session.equals(session))
                parent = userSession;
        }
        if(parent != null)
            userSessions.remove(parent);
        else {
            log.error("Error removing session: " + session + " - no suitable parent found");
        }
    }

    /**
     * Get all active devices.
     * @return List of all active devices
     */
    public List<Device> getDevices() {
        return new ArrayList<>(devices);
    }

    /**
     * Add a device, either via JSON or PB, and send it
     * to all active sessions in the server.
     * @param device The device to add
     * @param useJSON Whether to use JSON or PB
     */
    public void addDevice(Device device, boolean useJSON) {
        log.info("Adding device: " + device);
        device.setId(deviceId.incrementAndGet());
        devices.add(device);
        if(useJSON) {
            JsonObject addMessage = createAddMessage(device);
            sendToAllConnectedSessions(addMessage);
        }
        else {
            DeviceProtocol.Device addMessage = createAddMessagePB(device);
            sendToAllConnectedSessions(addMessage);
        }
    }

    /**
     * Remove a device, either via JSON or PB, and send its
     * removal to all active sessions in the server.
     * @param id The id of the device to remove
     * @param useJSON Whether to use JSON or PB
     */
    public void removeDevice(int id, boolean useJSON) {
        log.info("Removing device with id: " + id);
        Device device = getDeviceById(id);
        if (device != null) {
            devices.remove(device);
            if(useJSON) {
                JsonProvider provider = JsonProvider.provider();
                JsonObject removeMessage = provider.createObjectBuilder()
                        .add("action", "remove")
                        .add("id", id)
                        .build();
                sendToAllConnectedSessions(removeMessage);
            }
            else {
                DeviceProtocol.Device.Builder d = DeviceProtocol.Device.newBuilder();
                d.setAction("remove");
                d.setId(id);
                sendToAllConnectedSessions(d.build());
            }
        }
    }

    /**
     * Toggle a device, either via JSON or PB, and send its
     * update to all active sessions on the server.
     * @param id The id of the device to toggle
     * @param useJSON Whether to use JSON or PB
     */
    public void toggleDevice(int id, boolean useJSON) {
        log.info("Toggling device with id: " + id);
        JsonProvider provider = JsonProvider.provider();
        Device device = getDeviceById(id);
        if (device != null) {
            if ("On".equals(device.getStatus())) {
                device.setStatus("Off");
            } else {
                device.setStatus("On");
            }
            if(useJSON) {
                JsonObject updateDevMessage = provider.createObjectBuilder()
                        .add("action", "toggle")
                        .add("id", device.getId())
                        .add("status", device.getStatus())
                        .build();
                sendToAllConnectedSessions(updateDevMessage);
            }
            else {
                DeviceProtocol.Device.Builder d = DeviceProtocol.Device.newBuilder();
                d.setAction("toggle");
                d.setId(device.getId());
                d.setStatus(device.getStatus());
                sendToAllConnectedSessions(d.build());
            }
        }
    }

    /**
     * Dispatch a chat message, via JSON, from a specific sender.
     * Global chat messages are dispatched here.
     * @param sender The session that is sending the message.
     * @param message the contents to send
     */
    public void dispatchChatMessage(Session sender, JsonObject message) {
        JsonObject own = createChatMessage(message, true);
        JsonObject other = createChatMessage(message, false);

        // JSON are global messages, these are to be sent to everyone
        // no matter what - no restrictions

        for (UserSession session : userSessions) {

            if(session.session.equals(sender)) {
                sendToSession(session.session, own);
            }
            else {
                sendToSession(session.session, other);
            }
        }
    }

    /**
     * Dispatch a chat message, via PB, from a specific sender to the target 
     * specified in the message. Private chat messages are dispatched here.
     * @param sender The session that is sending the message
     * @param device The content of the message
     */
    public void dispatchChatMessage(Session sender, DeviceProtocol.Device device) {
        DeviceProtocol.ChatMsg message = device.getMessage();
        DeviceProtocol.Device other = createChatPBDevice(message, false, null);

        // only send to sender! there will be an error if encryption fails
        for (UserSession session : userSessions) {
            if(session.username != null && session.username.equals(message.getTarget())) {
                sendToSession(session.session, other);
            }
        }
    }

    /**
     * Create a global chat message, via JSON, to be sent in the JSON variant of <code>dispatchChatMessage</code>.
     * Format the message as appropriate, depending on whether sender/receiver.
     * @param msg The content to prepare the message with
     * @param isSender Whether intended for sender or receiver
     * @return The chat message to send
     */
    private JsonObject createChatMessage(JsonObject msg, Boolean isSender) {
        JsonObject message = msg.getJsonObject("message");
        String content = message.getString("content");
        content = (isSender) ? "You said: " + content : message.getString("username") + " said: " + content;
        JsonProvider provider = JsonProvider.provider();

        JsonObject updated = provider.createObjectBuilder()
                            .add("username", message.getString("username"))
                            .add("content", content)
                            .build();
        
        return provider.createObjectBuilder()
                .add("action", "chat")
                .add("message", updated)
                .build();
    }

    /**
     * Create a private chat message, via PB, to be sent in the PB variant of <code>dispatchChatMessage</code>.
     * Format the message as appropriate, depending on whether sender/receiver, and on whether file/text.
     * @param msg The content to prepare the message with
     * @param isSender Whether intended for sender or receiver
     * @param error Contains the error if any, or empty if none
     * @return The chat message to send
     */
    private DeviceProtocol.Device createChatPBDevice(DeviceProtocol.ChatMsg msg, Boolean isSender, String error) {
        // PRIVATE MESSAGE/FILE CONTENTS IS IN BYTES ONLY, APPEND SENDER/ETC ON CLIENT RECEIVE AFTER DECRYPTION
        DeviceProtocol.ChatMsg updated = DeviceProtocol.ChatMsg.newBuilder()
                                            .setUsername(msg.getUsername())
                                            .setContent(msg.getContent())
                                            .setTarget((msg.hasTarget() ? msg.getTarget() : ""))
                                            .setFileName(msg.hasFileName() ? msg.getFileName() : "")
                                            .build();
        return DeviceProtocol.Device.newBuilder()
                .setAction("chat")
                .setMessage(updated)
                .setError(((error == null || error.isEmpty()) ? "" : error))
                .build();
    }

    /**
     * Get a device by its device ID.
     * @param id The id of the device to get
     * @return the device with the specified id
     */
    private Device getDeviceById(int id) {
        for (Device device : devices) {
            if (device.getId() == id) {
                log.debug("Retrieved device with id: " + id);
                return device;
            }
        }
        log.warn("Could not retrieve device with id: " + id);
        return null;
    }

    /**
     * Create a device add message via JSON.
     * @param device The device to create the add message for
     * @return The resulting add message
     */
    private JsonObject createAddMessage(Device device) {
        JsonProvider provider = JsonProvider.provider();
        JsonObject addMessage = provider.createObjectBuilder()
                .add("action", "add")
                .add("id", device.getId())
                .add("name", device.getName())
                .add("type", device.getType())
                .add("status", device.getStatus())
                .add("description", device.getDescription())
                .build();
        log.debug("Created add message for device: " + device);
        return addMessage;
    }

    /**
     * Create a device add message via PB.
     * @param d The device to create the add message for
     * @return The resulting add message
     */
    private DeviceProtocol.Device createAddMessagePB(Device d) {
        DeviceProtocol.Device.Builder device = DeviceProtocol.Device.newBuilder();
        device.setAction("add");
        device.setId(d.getId());

        if(d.getName() != null) device.setName(d.getName());
        if(d.getStatus() != null) device.setStatus(d.getStatus());
        if(d.getType() != null) device.setType(d.getType());
        if(d.getDescription() != null) device.setDescription(d.getDescription());
        if(d.getOwner() != null) device.setOwner(d.getOwner());
        log.debug("Created add message (proto-buffer) for device: " + d);
        return device.build();
    }

    /**
     * Send a message to all connected sessions, via JSON.
     * @param message The message to send
     */
    private void sendToAllConnectedSessions(JsonObject message) {
        for (UserSession session : userSessions) {
            sendToSession(session.session, message);
        }
        log.debug("Sent message \'" + message + "\' to all connected sessions");
    }

    /**
     * Send a message to all connected sessions, via PB.
     * @param device The message to send
     */
    private void sendToAllConnectedSessions(DeviceProtocol.Device device) {
        for (UserSession session : userSessions) {
            sendToSession(session.session, device);
        }
        log.debug("Sent device \'" + device.toString() + "\' to all connected sessions");
    }

    /**
     * Send a message to a specific session, via JSON.
     * @param session The session to send the message to
     * @param message The message to send
     */
    private void sendToSession(Session session, JsonObject message) {
        try {
            session.getBasicRemote().sendText(message.toString());
        } catch (IOException ex) {
            log.error("Removing session: Exception caught trying to send message \'" + message + "\' to session \'" + session + "\' : " + ex);
            for(UserSession s : userSessions) {
                if(s.session == session)  {
                    userSessions.remove(s);
                    break;
                }
            }
        }
        log.debug("Sent message \'" + message + "\' to session: " + session);
    }

    /**
     * Send a message to a specific session, via PB.
     * @param session The session to send the message to
     * @param device The message to send
     */
    private void sendToSession(Session session, DeviceProtocol.Device device) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(device.toByteArray());
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException ex) {
            log.error("Removing session: Exception caught trying to send device \'" + device + "\' to session \'" + session + "\' : " + ex);
            for(UserSession s : userSessions) {
                if(s.session == session)  {
                    userSessions.remove(s);
                    break;
                }
            }
        }
        log.debug("Sent device \'" + device + "\' to session: " + session);
    }
}
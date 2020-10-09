package com.blakfx.websocket;

import javax.websocket.*; 
import javax.websocket.server.*;
import javax.enterprise.context.*;
import javax.inject.*;
import java.io.StringReader;
import java.nio.ByteBuffer;

import javax.json.*;
import org.apache.logging.log4j.*;

import com.blakfx.model.Device;

import com.blakfx.protocols.DeviceProtocol;

/**
 * The server endpoint for chat/device actions.
 * Receives requests from clients, and dispatches them to the <code>DeviceSessionHandler</code> to manage them.
 */
@ApplicationScoped
@ServerEndpoint(value = "/actions")
public class DeviceWebSocketServer {

    /**
     * This class' Logger instance.
     */
    private static Logger log = LogManager.getLogger(DeviceWebSocketServer.class.getName());

    /**
     * This server's session handler instance.
     */
    @Inject
    private static DeviceSessionHandler sessionHandler = new DeviceSessionHandler();

    /**
     * Called when a connection is made to the server.
     * @param session The session that just connected
     */
    @OnOpen
    public void open(Session session) {
        sessionHandler.addSession(session, false);
        log.info("Session has been created");
    }

    /**
     * Called when a connection is closed in the server.
     * @param session The session that just closed
     */
    @OnClose
    public void close(Session session) {
        sessionHandler.removeSession(session);
        log.info("Session has been closed");
    }

    /**
     * Called when an error is met in the server.
     * @param error The error met
     */
    @OnError
    public void onError(Throwable error) {
        log.error("Ran into error: " + error);
    }

    /**
     * Text message handler.
     * @param message The message received
     * @param session The session that sent it
     */
    @OnMessage
    public void handleTextMessage(String message, Session session) {
        try (JsonReader reader = Json.createReader(new StringReader(message))) {
            JsonObject jsonMessage = reader.readObject();

            // User identification - through JSON on connect only
            if ("identify".equals(jsonMessage.getString("action"))) {
                sessionHandler.bindUserToSession(jsonMessage.getString("username"), session);
            }

            if ("add".equals(jsonMessage.getString("action"))) {
                Device device = new Device();
                device.setName(jsonMessage.getString("name"));
                device.setDescription(jsonMessage.getString("description"));
                device.setType(jsonMessage.getString("type"));
                device.setStatus("Off");
                sessionHandler.addDevice(device, true);
                log.info("Device has been added");
            }

            if ("remove".equals(jsonMessage.getString("action"))) {
                int id = (int) jsonMessage.getInt("id");
                sessionHandler.removeDevice(id, true);
                log.info("Device has been removed");
            }

            if ("toggle".equals(jsonMessage.getString("action"))) {
                int id = (int) jsonMessage.getInt("id");
                sessionHandler.toggleDevice(id, true);
                log.info("Device with id " + id + " has been toggled");
            }

            if("chat".equals(jsonMessage.getString("action"))) {
                sessionHandler.dispatchChatMessage(session, jsonMessage);
            }
        }
        catch(Exception e) {
            log.error("Error while handling message \'" + message + "\' from session \'" + session + "\': " + e);
        }
    }

    /**
     * Binary message handler.
     * @param buffer The buffer received
     * @param session The session that sent it
     */
    @OnMessage
    public void handleBinaryMessage(ByteBuffer buffer, Session session) {
        log.debug("Binary handler called!");
        try {
            DeviceProtocol.Device device = DeviceProtocol.Device.parseFrom(buffer);
            String action = device.getAction();
           
            if("add".equals(action)) {
                Device d = new Device();
                d.setName(device.getName());
                d.setDescription(device.getDescription());
                d.setType(device.getType());
                d.setStatus("Off");
                sessionHandler.addDevice(d, false);
                log.info("Device has been added (via PB)");
            }

            if("remove".equals(action)) {
                int id = device.getId();
                sessionHandler.removeDevice(id, false);
                log.info("Device has been removed (via PB)");
            }

            if("toggle".equals(action)) {
                int id = device.getId();
                sessionHandler.toggleDevice(id, false);
                log.info("Device with id " + id + " has been toggled (via PB)");
            }

            if("chat".equals(action)) {
                sessionHandler.dispatchChatMessage(session, device);
            }

        }
        catch(Exception e) {
            log.error("Error while handling buffer \'" + buffer + "\' from session \'" + session + "\': " + e);
        }
    }
}
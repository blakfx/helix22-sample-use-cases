package com.blakfx.websocket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.json.spi.JsonProvider;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * Representation of a client endpoint to the chat server.
 * Allows a way to connect to the chat server, and to send
 * both text and binary content asynchronously.
 */
@ClientEndpoint
public class WebsocketClientEndpoint {

    /**
     * The session for this endpoint.
     */
    private Session session = null;
    /**
     * The MessageHandler for this endpoint.
     */
    private MessageHandler messageHandler;
    /**
     * This class' Logger instance
     */
    private static final Logger log = LogManager.getLogger(WebsocketClientEndpoint.class.getName());

    /**
     * Constructs the WebsocketClientEndpoint to the given URI.
     * @param endpointURI The URI of the chat server to connect to
     */
    public WebsocketClientEndpoint(URI endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Called when the session to the chat server is opened.
     * @param session The session that was opened
     */
    @OnOpen
    public void onOpen(Session session) {
        log.info("Opened websocket for session " + session);
        this.session = session;
    }

    /**
     * Called when the session to the chat server is closed.
     * @param session The session that was closed
     * @param reason The reason why it was closed
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        log.info("Closed websocket for session " + session);
        this.session = null;
    }

    /**
     * Text message handler.
     * @param message The text message received
     * @param session The session that sent it
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message, session);
        }
    }

    /**
     * Binary message handler.
     * @param buffer The buffer received
     * @param session The session that sent it
     */
    @OnMessage
    public void onMessage(ByteBuffer buffer, Session session) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(buffer, session);
        }
    }

    /**
     * Adds a message handler to the client's available handlers.
     * @param handler The handler to add
     */
    public void addMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    /**
     * Send a binary message.
     * @param buffer The buffer to send
     */
    public void sendMessage(ByteBuffer buffer) {
        log.info("Starting async send with buffer: {}", buffer.toString());
        this.session.getAsyncRemote().sendBinary(buffer);
    }

    /**
     * Send a text message.
     * @param message The message to send
     */
    public void sendMessage(final String message) {
        log.info("Starting async send with message: {}", message);
        this.session.getAsyncRemote().sendText(message);
    }

    /**
     * Clean up and shut down safely.
     */
    public void close() {
        log.info("Closing websocket connection");
        try {
            this.session.close();
        }
        catch(final IOException e) {
            log.error("Exception when closing chat endpoint - %s", e);
        }
    }

    /**
     * Sends an identification request to the chat server.
     * Response handled along other JSON responses in <code>receiveGlobalMessage</code>.
     * @param aUsername the name of the user to register with the chat server
     */
    public void registerIdentity(final String aUsername) {
        final JsonProvider provider = JsonProvider.provider();
        sendMessage(provider.createObjectBuilder()
            .add("action", "identify")
            .add("username", aUsername)
            .build().toString());
    }


    /**
     * Interface for a MessageHandler for a client.
     * Simply force to implement both binary and text handlers.
     */
    public static interface MessageHandler {

        /**
         * Handle a text (usually JSON) message.
         * @param message the message to handle
         * @param session the session that sent it
         */
        public void handleMessage(String message, Session session);
        /**
         * Handle a binary (usually PB) message.
         * @param buffer the buffer to handle
         * @param session the session that sent it
         */
        public void handleMessage(ByteBuffer buffer, Session session);
    }
}
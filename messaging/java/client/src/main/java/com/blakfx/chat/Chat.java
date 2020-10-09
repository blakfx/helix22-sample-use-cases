package com.blakfx.chat;

import com.blakfx.helix.HelixCryptoProvider;
import com.blakfx.helix.HelixModule;
import com.blakfx.helix.HelixModule.EncryptionException;
import com.blakfx.helix.HelixModule.HelixException;
import com.blakfx.helix.HelixModule.InvalidTargetException;
import com.blakfx.protocols.DeviceProtocol;
import com.blakfx.util.Utility;
import com.blakfx.websocket.WebsocketClientEndpoint;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.spi.JsonProvider;
import javax.websocket.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat client object, the real core of the application.
 * Upon creation, sets up connections to the chat server and helix.
 * Most of the work is done in the <code>loop</code>, where user input is processed.
 * Dispatches relevant work to <code>Utility</code>, <code>WebsocketClientEndpoint</code>,
 * and <code>HelixModule</code> where necessary.
 */
public class Chat {

    protected static final String EVERYONE = "EVERYONE";

    /**
     * The cache/history of private messages sent/received per counterparty.
     */
    private final MessageCache privateMessageCache;

    /**
     * The cache/history of global messages sent/received.
     */
    private final MessageCache sharedMessageCache;

    /**
     * The endpoint to communicate with the chat server.
     */
    private final WebsocketClientEndpoint chatEndpoint;

    /**
     * This class' Logger instance.
     */
    private static final Logger log = LogManager.getLogger(Chat.class.getName());

    /**
     * Holds number of max threads for the executor service.
     */
    private static final int MAX_EXECUTOR_THREADS = 10;

    /**
     * Responsible for performing Helix encryption/decryption tasks.
     */
    private static final ExecutorService executorService = Executors.newFixedThreadPool(MAX_EXECUTOR_THREADS);

    /**
     * The client user's display name.
     * Will be used to connect to both helix and chat servers.
     */
    private final String localChatUsername;

    /**
     * The HelixModule object for all encrypt/decrypt operations.
     */
    private final HelixModule helixModule;

    /**
     * Total number of received private messages for this client.
     * Used to assign message ID's for easier logging.
     * Logs will refer to received messages by <b>blob</b> IDs.
     */
    private static final AtomicLong totalReceived_Packets = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Packets_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Packets_SuccessfullyProcessed = new AtomicLong(0L);
    private static final AtomicLong totalReceived_IoT_Messages = new AtomicLong(0L);
    private static final AtomicLong totalReceived_IoT_Messages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalReceived_IoT_Messages_SuccessfullyProcessed = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateMessages = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateMessages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateMessages_SuccessfullyProcessed = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateFiles = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateFiles_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_PrivateFiles_SuccessfullyProcessed = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_GlobalMessages = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_GlobalMessages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalReceived_Chat_GlobalMessages_SuccessfullyProcessed = new AtomicLong(0L);

    /**
     * Total number of sent private messages for this client.
     * Used to assign message ID's for easier logging.
     * Logs will refer to sent messages by <b>message</b> IDs.
     */
    private static final AtomicLong totalSent_IoT_Messages_Initiated = new AtomicLong(0L);
    private static final AtomicLong totalSent_IoT_Messages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalSent_IoT_Messages_SuccessfullyDispatched = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateMessages_Initiated = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateMessages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateMessages_SuccessfullyDispatched = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateFile_Initiated = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateFile_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_PrivateFile_SuccessfullyDispatched = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_GlobalMessages_Initiated = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_GlobalMessages_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalSent_Chat_GlobalMessages_SuccessfullyDispatched = new AtomicLong(0L);
    private static final AtomicLong totalSent_Packets_Initiated = new AtomicLong(0L);
    private static final AtomicLong totalSent_Packets_FailedProcessing = new AtomicLong(0L);
    private static final AtomicLong totalSent_Packets_SuccessfullyDispatched = new AtomicLong(0L);

    // prompt to display to the user
    private final String prompt = "(type &help for assistance)> ";



    /**
     * Constructs the chat client, if successful.
     * Otherwise, logs error to file, prints it to screen, and terminates.
     * @param aLocalChatUsername   The username to connect to the chat server
     * @param aChatEndpoint interface to communicate with chat server
     * @param privateCache  record of private messages between user and individual recipients
     * @param sharedCache   record of shared messages between user and group (of all users)
     * @param libHelix   Instance of loaded Helix Module
     */
    public Chat(final String aLocalChatUsername, final WebsocketClientEndpoint aChatEndpoint,
                final MessageCache privateCache, final MessageCache sharedCache, HelixModule libHelix) {
        localChatUsername = aLocalChatUsername;
        chatEndpoint = aChatEndpoint;
        privateMessageCache = privateCache;
        sharedMessageCache = sharedCache;
        sharedMessageCache.addContact(EVERYONE);
        helixModule = libHelix;

        log.info("Start chat client");
        chatEndpoint.addMessageHandler(new WebsocketClientEndpoint.MessageHandler() {
            public void handleMessage(String message, Session session) {
                // JSON (Global Messages) handled here
                receiveGlobalMessage(message, session);
            }

            public void handleMessage(ByteBuffer buffer, Session session) {
                final long messageID = Chat.totalReceived_Packets.incrementAndGet();
                // PB (Private Messages) handled here
                try {
                    receivePrivateMessage(buffer, session, messageID);
                } catch(final InvalidProtocolBufferException e) {
                    com.blakfx.chat.Chat.totalReceived_Chat_PrivateFiles_FailedProcessing.incrementAndGet();
                }
            }
        });

        log.info("Attempting to register with chat server as {}", localChatUsername);
        chatEndpoint.registerIdentity(localChatUsername);

        Utility.setupChatBackupOnLocalDrive();
        log.info("Chat client started");
    }


    /**
     * Attempts to perform and complete a Helix task under a given time
     * @param task The Helix task to complete
     * @param msRefreshRate The time to wait between checking status of the task
     * @param msMaxTime The max time to complete the task in
     * @return The result, as a byte array, of the Helix action
     * @throws InterruptedException Helix task thread is waiting, sleeping, or otherwise occupied, but was interrupted, either before or during the activity
     * @throws ExecutionException Attempted to retrieve the result of a Helix task that aborted by throwing an exception
     */
    public static byte[] performHelixTask(Callable<byte[]> task, int msRefreshRate, int msMaxTime) throws InterruptedException, ExecutionException {
        Future<byte[]> result = Chat.executorService.submit(task);
        long waitAttemptsLeft = msMaxTime / msRefreshRate; // match in units (ms) and integers being divided, so it's fine
        while (waitAttemptsLeft > 0 && !result.isDone()) {
            synchronized(task) {
                task.wait(msRefreshRate);
                waitAttemptsLeft--;
            }
        }
        // return NULL if we ran out of attempts (ie time)
        return (waitAttemptsLeft > 0) ? result.get() : null;
    }


    /**
     * Process input until user chooses to quit, or an unrecoverable error arises.
     * User can enter one of the following: commands, private messages, (private) file messages, or normal messages.
     * Private (text/file) messages are secured with Helix via the <code>helixModule</code>,
     * can be viewed only by the recipient/sender, and transported via Protocol Buffers.
     * Global messages are insecure, can be seen by anyone, and are transported via JSON messages.
     */
    public final void start() {

        log.info("Begin chat loop");
        final PrintStream out = System.out;
        final InputStream in = System.in;
        final Scanner sc = new Scanner(in);

        boolean stayActive = true;
        do {
            // print formatting assumes here that the cursor is at the start of a new line
            // elsewhere, ensure that this happens
            System.out.print(prompt);
            String input = sc.nextLine();
            if (input.isBlank() || input.isEmpty()) {
                continue;
            }

            final String userInput = input.strip();
            final char inputMark = userInput.toLowerCase().charAt(0);

                switch(inputMark) {
                    case '@' : {
                        executeCmd_SecureSendMessage(localChatUsername, userInput, out); // secured with Helix
                        break;
                    }
                    case '#' : {
                        executeCmd_SecureSendFile(localChatUsername, userInput, out); // secured with Helix
                        break;
                    }
                    case '&' : {
                        stayActive = executeCmd_ChatControl(out, userInput);
                        break;
                    }
                    case '$' : {
                        executeCmd_RetrieveChatHistory(userInput, out);
                        break;
                    }
                    default: {
                        // broadcast a global (insecure) message
                        log.info("Attempting to send insecure global message with content: {}", () -> userInput);
                        // Prepare the global chat message, as a separate-thread task
                        SendGlobalChatTask task = new SendGlobalChatTask(localChatUsername, userInput);
                        final Future<?> result = Chat.executorService.submit(task);
                        break;
                    }
                }//eo user-input switch
        } while (stayActive);
        sc.close();
        log.info("End chat loop");
        shutdown();
    }//eo keep chat active

    /**
     * Execute a "chat control" command, ie one unrelated to actual messaging capabilities.
     * @param anOutStream The output stream to print with
     * @param aChatCommand The command entered by the user
     * @return Whether chat will continue to be active or not
     */
    protected boolean executeCmd_ChatControl(@NotNull PrintStream anOutStream, String aChatCommand) {
        boolean stayActive = true;

        //user asked for control action with chat-command
        final String userCommand = aChatCommand.substring(1).trim().toLowerCase();
        switch (userCommand) {
            case "quit": {
                stayActive = false;
                break;
            }
            case "help": {
                executeCommand_Help(anOutStream);
                break;
            }
            default: {
                log.info("User entered an unknown command: {}", userCommand);
                anOutStream.printf("!! Unknown command: %s\n", userCommand);
                break;
            }
        }//eo chat-command switch

        return stayActive;
    }

    /**
     * Execute the help command, printing help for the chat user.
     * @param anOutStream The output stream to write the help to
     */
    protected void executeCommand_Help(@NotNull PrintStream anOutStream) {
        anOutStream.println("?? HELP ??");
        anOutStream.println("-----");
        anOutStream.println("You can enter commands as &command, private text messages as @target content, private text messages as #target filename");
        anOutStream.println("Commands:\n\thelp: this help dialog\n\tquit: quit the application");
        anOutStream.println("Private messages are 1-1, secure, and delivered via PB");
        anOutStream.println("Global messages are to everyone, insecure, and delivered via JSON");
        anOutStream.println("You can view past message history by using $contact, where contact is either blank (for shared messages) or the contact to print the history of");
        anOutStream.println("-----");
    }

    /**
     * Shutdown actions for the chat application. 
     * First, close the WS endpoint. 
     * Then, shutdown the thread pool. 
     * Finally, shut down helix module.
     */
    protected void shutdown() {
        chatEndpoint.close();
        executorService.shutdown();
        helixModule.shutdown();
    }

    /**
     * Execute the chat history command, which allows to print chat history with a private user or the shared chat.
     * If the contact name is blank/empty, it is interpreted that the user chose to get the shared cache history.
     * @param userInput The input entered by the user
     * @param out The print stream to write to
     */
    protected void executeCmd_RetrieveChatHistory(final String userInput,
                                             @NotNull PrintStream out) {
        String contactName = userInput.substring(1);
        if (contactName.isEmpty() || contactName.isBlank()) {
            // shared cache
            log.info("Retrieving chat history for shared messages");
            out.println();
            final List<String> allScreenMessages = sharedMessageCache.getContactMessages("EVERYONE");
            if(allScreenMessages.isEmpty()) {
                out.println("- The shared chat history is empty.");
            }
            else {
                for(String screenMessage: allScreenMessages) {
                    out.printf("%s\n", screenMessage);
                }
            }
        }
        else {
            if(privateMessageCache.contactExists(contactName)) {
                // private cache
                log.info("Retrieving chat history for {}", contactName);
                out.println();
                final List<String> allScreenMessages = privateMessageCache.getContactMessages(contactName);
                if(allScreenMessages.isEmpty()) {
                    out.printf("- The chat history with {} is empty\n", contactName);
                }
                else {
                    for(String screenMessage: allScreenMessages) {
                        out.printf("%s\n", screenMessage);
                    }
                }
            }
            else {
                log.info("Cannot get chat history for invalid contact {}", contactName);
                out.println("Error: contact does not exist");
            }
        }
    }

    /**
     * Execute the secure send file command, allowing an user to securely send a private file to another via Helix and PB.
     * @param aLocalChatUsername The user's local chat name
     * @param userInput The input the user entered
     * @param out The print stream to write to
     */
    protected void executeCmd_SecureSendFile(final String aLocalChatUsername, final String userInput,
                                             @NotNull PrintStream out) {
        log.info("Processing private file message {}", userInput);

        final Pattern fileTag = Pattern.compile("#(\\S+)");
        Matcher m = fileTag.matcher(userInput);
        if (m.find(0)) {
            final String recipientName = m.group().substring(1);
            if (recipientName.length() != userInput.length() - 1) {
                final String fileName = userInput.substring(m.end() + 1);

                // This function should interpret paths as relative
                if(! Utility.checkFileExists(fileName) ) {
                    log.warn("Could not find user specified file: {}", fileName);
                    out.printf("Error: cannot find file '%s'\n", fileName);
                    return;
                }

                if(recipientName.equals(aLocalChatUsername)) {
                    log.warn("Attempted to secure send file {} to self", fileName);
                    out.printf("Error: cannot secure send file %s to yourself\n", fileName);
                    return;
                }

                final long messageID = Chat.totalSent_Packets_Initiated.incrementAndGet();
                Chat.totalSent_Chat_PrivateFile_Initiated.incrementAndGet();
                privateMessageCache.saveContactMessage(recipientName, Utility.formatPrivateFileNotice(fileName, recipientName, false), true);

                log.info("Sending to {} an encrypted file {}", recipientName, fileName);
                final SendPMFileTask sendPMFileTask = new SendPMFileTask(aLocalChatUsername, recipientName, fileName, messageID);
                final Future<?> promiseToSendPMFileChat = Chat.executorService.submit(sendPMFileTask);

                // TODO: feel free to perform any other action here

                if(promiseToSendPMFileChat.isDone()) {
                    log.debug("While updating screen with PM message to {}, actual file was already encrypted and dispatched", recipientName);
                } else{
                    log.debug("After updating screen with PM message to {}, actual file is still being processed", recipientName);
                }
            } else {
                log.warn("Attempted to send empty private file message");
                out.println("You cannot send an empty file private message");
            }
        } else {
            out.println("Syntax: #target <file_name>");
        }
    }

    /**
     * Execute the secure send message command, allowing an user to securely send a private message to another via Helix and PB.
     * @param aLocalChatUsername The user's local chat name
     * @param aSecretTextMessage The text message content to send
     * @param out The print stream to write to
     */
    protected void executeCmd_SecureSendMessage(final String aLocalChatUsername, final String aSecretTextMessage,
                                                @NotNull PrintStream out) {
        log.info("Processing private message: {}", aSecretTextMessage);
        final Pattern tag = Pattern.compile("@(\\w+)");
        Matcher m = tag.matcher(aSecretTextMessage);
        if (m.find(0)) {
            final String recipientName = m.group().substring(1);
            if (recipientName.length() != aSecretTextMessage.length() - 1) {
                final String secretMessage = aSecretTextMessage.substring(m.end() + 1);

                if(recipientName.equals(aLocalChatUsername)) {
                    log.warn("Attempted to secure send message {} to self", aSecretTextMessage);
                    out.println("Error: cannot secure send messages to yourself");
                    return;
                }

                final long messageID = Chat.totalSent_Packets_Initiated.incrementAndGet();
                Chat.totalSent_Chat_PrivateMessages_Initiated.incrementAndGet();
                privateMessageCache.saveContactMessage(recipientName, Utility.formatPrivateMessageNotice(secretMessage, recipientName, false), true);

                log.info("Sending to {} a private message: {}", recipientName, secretMessage);
                final SendPMChatTask sendPMTask = new SendPMChatTask(aLocalChatUsername, recipientName, secretMessage, messageID);
                final Future<?> promiseToSendPMChat = Chat.executorService.submit(sendPMTask);

                // TODO: feel free to perform any other action here

                if(promiseToSendPMChat.isDone()) {
                    log.debug("While updating screen with PM message to {}, actual message was already encrypted and dispatched", recipientName);
                } else{
                    log.debug("After updating screen with PM message to {}, actual message is still being processed", recipientName);
                }
            } else {
                log.warn("Attempted to send empty private message");
                out.println("You cannot send an empty message");
            }
        } else {
            out.println("Syntax: @recipient 'message to send'");
        }
    }

    /**
     * Sends some plaintext message to everyone as a global message.
     *
     * @param aSenderName    the user sending the message
     * @param aPlaintext the plaintext contents of the message
     */
    private void sendGlobalMessage(final String aSenderName, final String aPlaintext) {
        totalSent_Chat_GlobalMessages_Initiated.incrementAndGet();
        log.info("Preparing message payload");
        final JsonObject payload = prepareGlobalPayload(aSenderName, aPlaintext);
        log.info("Prepare message payload - returned {}", payload.toString());
        chatEndpoint.sendMessage(payload.toString());
        sharedMessageCache.saveContactMessage("EVERYONE", Utility.formatGlobalMessageNotice(aPlaintext, false), false);
        Chat.totalSent_Packets_SuccessfullyDispatched.incrementAndGet();
        log.info("Payload sent");
        totalSent_Chat_GlobalMessages_SuccessfullyDispatched.incrementAndGet();
    }

    /**
     * Task to send a global chat message, as a Runnable.
     * Will run on one of the threads managed by the thread pool, <code>executorService</code>.
     */
    public class SendGlobalChatTask implements Runnable {

        /**
         * The sender's chat name.
         */
        private final String localChatUsername;
        /**
         * The message content to be sent.
         */
        private final String messageContent;

        /**
         * Constructor for the send global chat message task.
         * @param aLocalChatUsername The sender's chat name
         * @param aMessageContent The message content to be sent
         */
        public SendGlobalChatTask(final String aLocalChatUsername, final String aMessageContent) {
            this.localChatUsername = aLocalChatUsername;
            this.messageContent = aMessageContent;
        }

        /**
         * The task to perform when running this task.
         * In this case, run the <code>sendGlobalMessage</code> function.
         */
        @Override
        public void run() {
            sendGlobalMessage(localChatUsername, messageContent);
        }
    }

    /**
     * Prepares a global payload, in JSON format, to send to everyone in <code>sendGlobalMessage</code>.
     * No encryption here, contents sent as-is for everyone.
     *
     * @param aSender    the user sending the message
     * @param aPlaintext the plaintexts contents of the message
     * @return the payload to send, as a JsonObject
     */
    private JsonObject prepareGlobalPayload(final String aSender, final String aPlaintext) {
        final JsonProvider provider = JsonProvider.provider();

        final JsonObject _message = provider.createObjectBuilder()
                .add("username", aSender)
                .add("content", aPlaintext)
                .build();

        return provider.createObjectBuilder()
                .add("action", "chat")
                .add("message", _message)
                .build();
    }


    /**
     * Receive JSON String message from a given session.
     *
     * @param message The JSON string containing the payload received
     * @param session The Session that sent the message
     */
    private void receiveGlobalMessage(final String message, final Session session) {
        log.info("Parsing JSON message");
        try (JsonReader reader = Json.createReader(new StringReader(message))) {
            JsonObject jsonMessage = reader.readObject();

            // Process a global chat message
            if ("chat".equals(jsonMessage.getString("action"))) {
                totalReceived_Chat_GlobalMessages.incrementAndGet();
                final ProcessGlobalChatTask task = new ProcessGlobalChatTask(this.sharedMessageCache, jsonMessage);
                final Future<Void> result = Chat.executorService.submit(task);
            }

            // Process a server identification request response
            if ("identify".equals(jsonMessage.getString("action"))) {
                log.info("Processing identification request");
                String error = jsonMessage.getString("error");
                if (!error.isEmpty()) {
                    log.error("Username is already taken. Please rejoin with another one");
                    shutdown();
                }
                log.info("Identification success");
            }
            log.info("JSON Message parsed");
        } catch (RuntimeException e) {
            log.error("Error while receiving global message '{}' from session '{}': {}", message, session, e);
            Chat.totalReceived_Packets_FailedProcessing.incrementAndGet();
        }
    }

    /**
     * Task to process a global chat message, as a callable.
     * Will run on one of the threads managed by the thread pool, <code>executorService</code>.
     */
    public class ProcessGlobalChatTask implements Callable<Void> {
        /**
         * The JSON message received.
         */
        private final JsonObject message;
        /**
         * The message cache that this message belongs to.
         */
        private final MessageCache messageCache;

        /**
         * Constructor for a global chat message processing task.
         * @param aCacheService The cache that this message belongs to
         * @param aJSONMessage The JSON message to be processed
         */
        public ProcessGlobalChatTask(final MessageCache aCacheService,
                                 final JsonObject aJSONMessage) {
            this.messageCache = aCacheService;
            this.message = aJSONMessage;
        }

        /**
         * What to do when calling this task.
         * In this case, handle the message, with some housekeeping actions.
         * Note: own messages (a message sent from me and received by me) are ignored, as they were already processed upon send.
         */
        @Override
        public Void call() {
            JsonObject msg = message.getJsonObject("message");
            boolean ownMessage = msg.getString("username").equals(localChatUsername);

            if(ownMessage) {
                log.info("Received a global message that looks like was sent by me - ignoring it");
                totalReceived_Chat_GlobalMessages_FailedProcessing.incrementAndGet();
                return null;
            }

            log.info("Adding chat message from {} to global cache", msg.getString("username"));
            messageCache.saveContactMessage(EVERYONE, Utility.formatGlobalMessageNotice(msg.getString("content"), true), true);
            System.out.print(prompt);
            Chat.totalReceived_Chat_GlobalMessages_SuccessfullyProcessed.incrementAndGet();
            return null;
        }
    }


    /**
     * Sends a private message to a target.
     * Can be file or text, but not both.
     * Contents of either one are secure, encrypted by Helix.
     *  @param senderName    The user sending the private message
     * @param recipientName    The target of the private message
     * @param plaintext Contains the plaintext content to send as text, or null if file
     * @param fileName  Contains the path to the file to send, or null if text
     * @param aMessageID A unique counter of message (per direction) for internal tracking
     */
    private void sendPrivateMessage(final String senderName, final String recipientName,
                                    final String plaintext, final String fileName, long aMessageID)
    {

        try {
            log.info("Preparing message payload (message #{})", aMessageID);

            DeviceProtocol.Device payload = preparePrivatePayload(senderName, recipientName, plaintext, fileName, aMessageID);
            log.info("Prepare message payload (message #{}) - received: {}", aMessageID, payload.toString());
            chatEndpoint.sendMessage(ByteBuffer.wrap(payload.toByteArray()));

            Chat.totalSent_Packets_SuccessfullyDispatched.incrementAndGet();
            if(fileName == null) {
                Chat.totalSent_Chat_PrivateMessages_SuccessfullyDispatched.incrementAndGet();
            } else {
                Chat.totalSent_Chat_PrivateFile_SuccessfullyDispatched.incrementAndGet();
            }
        }
        // Encryption/Helix related exceptions will be thrown from another thread since the operations are done there
        catch (ExecutionException e) {
            Chat.totalSent_Packets_FailedProcessing.incrementAndGet();
            if(fileName == null) {
                Chat.totalSent_Chat_PrivateMessages_FailedProcessing.incrementAndGet();
            } else {
                Chat.totalSent_Chat_PrivateFile_FailedProcessing.incrementAndGet();
            }

            Throwable t = e.getCause();
            if (t instanceof EncryptionException) {
                log.warn("Encryption issue with private message (message #{}) - {}", aMessageID, t.getMessage());
            } else if (t instanceof InvalidTargetException) {
                log.warn("Invalid private message target {} (message #{})", recipientName, aMessageID);
                //System.out.println("!! Invalid private message target " + recipientName);
            } else if (t instanceof HelixException) {
                log.fatal("Unrecoverable error when sending private message (message #{}) - {}", aMessageID, t.getMessage());
            } else {
                log.fatal("Unrecoverable error when sending private message (message #{}) - {}", aMessageID, t.getMessage());
            }
        } catch (IOException e) {
            Chat.totalSent_Packets_FailedProcessing.incrementAndGet();
            log.warn("Invalid private message filename {} to target {} (message #{}) - {}", fileName, recipientName, aMessageID, e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Chat.totalSent_Packets_FailedProcessing.incrementAndGet();
            log.fatal("Unhandled exception when sending private message: {}", e);
        }
    }

    /**
     * Prepares a private payload, in PB format, to send to a specific target.
     * Plaintext content to encrypt is first prepared, whether from a file or text.
     * <code>EncryptTask</code> abstraction does the heavy lifting with respect to encryption.
     * The result is then used as the text content field in the <code>ChatMsg</code> protocol buffer device.
     * @param senderName    The user sending the private message
     * @param recipientName    The target of the private message
     * @param plaintext Contains the plaintext content to send as text, or null if file
     * @param fileName  Contains the path to the file to send, or null if text
     * @param messageID Contains the message ID assigned for this private message
     * @return The Device PB Message to send
     * @throws IOException    The file specified does not exist on the local machine of the sender
     * @throws InterruptedException Helix encrypt task thread is waiting, sleeping, or otherwise occupied, but was interrupted, either before or during the activity
     * @throws ExecutionException Attempted to retrieve the result of a Helix encrypt task that aborted by throwing an exception
     * @throws HelixException Something, either fatal/non-fatal, unexpected occurred during the encryption
     */
    private DeviceProtocol.Device preparePrivatePayload(String senderName,
                                                                             String recipientName,
                                                                             String plaintext,
                                                                             String fileName,
                                                                             long messageID)
            throws IOException, InterruptedException, ExecutionException, HelixException
    {
        // Programmer error, prohibit sending both a file and message at the same time
        assert plaintext == null || fileName == null;

        DeviceProtocol.Device.Builder payload = DeviceProtocol.Device.newBuilder();

        log.info("Preparing a {} based content (message #{})", (fileName == null) ? "message" : "file", messageID);

        // Prepare what "plaintext" Helix is to encrypt
        byte[] plainMessageBytes = null;
        if( plaintext != null ) {
            plainMessageBytes = plaintext.getBytes();
        } else {
            if(fileName != null) {
                plainMessageBytes = Utility.readFromFile(fileName);
            }
        }
        assert plainMessageBytes != null;

        // Encrypt method is "unaware" that it is encrypting a file - it doesn't care
        EncryptTask encryptTask = new EncryptTask(this.helixModule, plainMessageBytes, recipientName, messageID);
        int msWaitInterval = 1 * 500; // 0.5 minutes
        int msMaxWaitTime = 5 * 60 * 1000; // 5 minutes
        byte[] content = Chat.performHelixTask(encryptTask, msWaitInterval, msMaxWaitTime); 

        if(content == null) {
            throw new EncryptionException("#" + messageID, "Could not complete the encrypt operation successfully under the allotted max time (" + msMaxWaitTime + ") ms");
        }

        // Here we account for file/no file distinction
        // Receiver will eventually receive a ChatMsg that will allow them to determine
        // How to interpret the source/type of the encrypted (to decrypt) content
        DeviceProtocol.ChatMsg chatMsg = DeviceProtocol.ChatMsg.newBuilder()
                .setUsername(senderName)
                .setContent(ByteString.copyFrom(content))
                .setTarget(recipientName)
                .setFileName((fileName == null ? "" : fileName))
                .build();

        return payload.setAction("chat")
                .setMessage(chatMsg)
                .build();
    }

    /**
     * Receives a private payload, in PB format, from a given session.
     * If text, it's decrypted and printed on screen.
     * If file, it's written out to the user's shared contact directory in both encrypted and decrypted form.
     *
     * @param incomingPacket  The private payload received
     * @param aSession The session that sent the private payload
     * @param aMessageID The message's internal ID for logging
     * @throws InvalidProtocolBufferException The PB received with this message is invalid
     */
    private void receivePrivateMessage(final ByteBuffer incomingPacket, final Session aSession,
                                       final long aMessageID) throws InvalidProtocolBufferException
    {
        log.info("Starting to process packet (blob #{})", aMessageID);
        DeviceProtocol.Device device = DeviceProtocol.Device.parseFrom(incomingPacket);

        final String action = device.getAction();
        if(action == null || action.isEmpty() || action.isBlank()) {
            log.error("Packet is malformed, missing [action] field; packet is: {}", () -> device.toString());
            Chat.totalReceived_Packets_FailedProcessing.incrementAndGet();
            return;
        }

        if (action.trim().toLowerCase().equals("chat")) {
            // Avoid further processing if there was an error with the private (file/text) message
            final String error = device.getError();
            if (! error.isEmpty()) {
                log.error("Received invalid private message from Chat Server (blob {}) with code: {}", aMessageID, error);
                Chat.totalReceived_Chat_PrivateMessages_FailedProcessing.incrementAndGet();
                return;
            }

            //TODO: verify memory buffer will not be deallocated by WS internal threads after Callable task is scheduled
            // if so, we need to take copy of data to pass into the callable
            DeviceProtocol.ChatMsg aWSPacket = device.getMessage();

            final ProcessPMChatTask task = new ProcessPMChatTask(this.helixModule, this.privateMessageCache,
                                                                 localChatUsername, aWSPacket, aMessageID);
            final Future<Void> result = Chat.executorService.submit(task);

            // TODO: if task returns results, use "result.wait" and "result.isDone" here
        }
    }//eo receive private message

    /**
     * A Helix encryption task, as a Callable.
     * It uses the help of the Helix module to encrypt some plain byte content for a specific target.
     */
    public class EncryptTask implements Callable<byte[]> {
        /**
         * The Helix module to aid with encryption.
         */
        private final HelixCryptoProvider helixModule;
        /**
         * The plain byte contents to encrypt.
         */
        private final byte[] plain;
        /**
         * The name of the user to encrypt it for.
         */
        private final String recipientName;
        /**
         * The internal ID of the message for logging.
         */
        private final long messageID;

        /**
         * Constructor for a Helix encryption task.
         * @param aCryptoService The Helix module to aid with encryption
         * @param plainBytes The plain byte contents to encrypt
         * @param aRecipientName The name of the user to encrypt it for
         * @param messageID The internal ID of the message for logging
         */
        public EncryptTask(final HelixCryptoProvider aCryptoService, final byte[] plainBytes,
                           final String aRecipientName, final long messageID) {
            this.helixModule = aCryptoService;
            this.plain = plainBytes;
            this.recipientName = aRecipientName;
            this.messageID = messageID;
        }

        /**
         * What to do when calling this task.
         * In this case, use the help of the <code>helixModule</code>'s <code>encrypt</code> function to encrypt the content.
         * @return The encrypted byte contents
         * @throws HelixException Something, either fatal/non-fatal, unexpected occurred during the encryption
         */
        @Override
        public final byte[] call() throws HelixException {
            return helixModule.encrypt(plain, recipientName, messageID);
        }
    }

    /**
     * Helix private chat action processing task, as a Callable.
     * Will process either a private file/text action received.
     */
    public class ProcessPMChatTask implements Callable<Void> {
        /**
         * The received protocol buffer message.
         */
        private final DeviceProtocol.ChatMsg message;
        /**
         * The Helix module to aid with decryption.
         */
        private final HelixCryptoProvider helixModule;
        /**
         * The cache that this message belongs to.
         */
        private final MessageCache messageCache;
        /**
         * The name of the user receiving this message.
         */
        private final String localChatUserName;
        /**
         * The internal ID of this message for logging.
         */
        private final long messageID;

        /**
         * Constructor for PM processing task.
         * @param aCryptoService The Helix module to aid with decryption
         * @param aCacheService The cache that this message belongs to
         * @param aLocalChatUserName The name of the user receiving this message
         * @param aWSMessage The received protocol buffer message
         * @param aMessageID The internal ID of this message for logging
         */
        public ProcessPMChatTask(final HelixCryptoProvider aCryptoService, final MessageCache aCacheService,
                                 final String aLocalChatUserName,
                                 final DeviceProtocol.ChatMsg aWSMessage,
                                 final long aMessageID) {
            this.helixModule = aCryptoService;
            this.messageCache = aCacheService;
            this.localChatUserName = aLocalChatUserName;
            this.message = aWSMessage;
            this.messageID = aMessageID;
        }

        /**
         * What to do when this task gets called.
         * In this case, use the help of the Helix module's <code>decrypt</code> function to aid with decryption.
         * If it's a file that was received, do the necessary preparations to write out both decrypted and encrypted variants to disk.
         * If it's text, there is no writing to disk involved.
         * In either case, the message itself (or a display message if it's a file) is added to the appropriate cache
         */
        @Override
        public Void call() {
            final boolean myOwnMessage = message.getUsername().equals(localChatUserName);
            if(myOwnMessage) {
                //TODO: should be decrement the count for "totalReceived_Packets" ?
                log.info("Received a PM message that looks like form me to me - ignoring it");
                return null;
            }

            // Note: eventually would want to use a specific password, but for now left null (no password)
            final String recipient = message.getTarget();
            final String sender = message.getUsername();

            log.info("Received private message (blob #{}) from contact {}", messageID, sender);

            // Decrypt method is "unaware" that it is decrypting a file - it doesn't care
            byte[] encryptedBytesReceived = message.getContent().toByteArray();
            byte[] plainData = helixModule.decrypt(encryptedBytesReceived, messageID);

            String onScreenMessage;

            // Determine if we processing a message or a file
            final String fileName = message.getFileName();
            if (fileName.isEmpty()) {
                Chat.totalReceived_Chat_PrivateMessages_SuccessfullyProcessed.incrementAndGet();
                onScreenMessage = Utility.formatPrivateMessageNotice(new String(plainData), sender, true);
            } else {
                log.info("Private message (blob #{}) contains a file", messageID);

                // Create the contact directory under shared if not exists
                final String currentDirectory = Paths.get("").toAbsolutePath().toString();
                Path contactDirectory = Path.of(currentDirectory, "shared", sender);
                log.info("Creating contact directory " + contactDirectory.toString() + "(blob #" + messageID + ")");
                Utility.createFolderIfNotExists(contactDirectory);

                // Write the received encrypted content into its respective file
                String encryptedFileName = fileName + "-" + Utility.getTimeStamp(true) + "-encrypted.helix";
                Path encryptedFilePath = Path.of(currentDirectory, "shared", sender, encryptedFileName);
                log.info("Writing encrypted private file message (blob #" + messageID + ") to " + encryptedFilePath.toString());
                try {
                    Utility.writeToFile(encryptedFilePath.toString(), encryptedBytesReceived);
                } catch (final IOException e) {
                    log.error("Failed to write file {} with error trace {}", encryptedFilePath.toString(), e.getMessage());
                }

                // Write the decrypted file content to its respective file
                String decryptedFileName = fileName + "-" + Utility.getTimeStamp(true) + "-decrypted.helix";
                Path decryptedFilePath = Path.of(currentDirectory, "shared", sender, decryptedFileName);

                log.info("Writing decrypted private file message (blob #" + messageID + ") to " + decryptedFilePath.toString());
                try {
                    Utility.writeToFile(decryptedFilePath.toString(), plainData);
                } catch (final IOException e) {
                    log.error("Failed to write file {} with error trace {}", decryptedFilePath.toString(), e.getMessage());
                }

                log.info("Corresponding files successfully written to disk (blob #{})", messageID);
                Chat.totalReceived_Chat_PrivateFiles_SuccessfullyProcessed.incrementAndGet();

                // Format chat message to be received by users 
                onScreenMessage = Utility.formatPrivateFileNotice(fileName, sender, true);
            }

            // Send either the text message or file notification for the user
            messageCache.saveContactMessage(sender, onScreenMessage, true);
            System.out.print(prompt);
            Chat.totalReceived_Packets_SuccessfullyProcessed.incrementAndGet();
            return null;
        }
    }

    /**
     * Task to send a private chat text message, as a Runnable.
     * Will run on one of the threads managed by the thread pool, <code>executorService</code>.
    */
    public class SendPMChatTask implements Runnable {
        /**
         * The sender's chat user name.
         */
        private final String localChatUsername;
        /**
         * The target's chat user name.
         */
        private final String recipientName;
        /**
         * The contents of the message, decrypted at this point.
         */
        private final String secretMessage;
        /**
         * The internal ID of the message, for logging.
         */
        private final long messageID;

        /**
         * Constructor for a send private chat message task.
         * @param aLocalChatUsername The sender's chat user name
         * @param aRecipientName The target's chat user name
         * @param aSecretMessage The contents of the message
         * @param aMessageID The internal ID of the message, for logging
         */
        public SendPMChatTask(final String aLocalChatUsername, final String aRecipientName, final String aSecretMessage, long aMessageID) {
            this.localChatUsername = aLocalChatUsername;
            this.recipientName = aRecipientName;
            this.secretMessage = aSecretMessage;
            this.messageID = aMessageID;
        }

        /**
         * What to do when this task is ran.
         * In this case, simply call the function <code>sendPrivateMessage</code> with the necessary info.
         */
        @Override
        public void run() {
            sendPrivateMessage(localChatUsername, recipientName, secretMessage, null, messageID);
        }
    }

    /**
     * Task to send a private chat file message, as a Runnable.
     * Will run on one of the threads managed by the thread pool, <code>executorService</code>.
    */
    private class SendPMFileTask implements Runnable {
        /**
         * The sender's chat user name.
         */
        private final String localChatUsername;
        /**
         * The target's chat user name.
         */
        private final String recipientName;
        /**
         * The file to send.
         */
        private final String fileName;
        /**
         * The internal ID of the message, for logging.
         */
        private final long messageID;

        /**
         * Constructor for a send private file message task.
         * @param aLocalChatUsername The sender's chat user name
         * @param aRecipientName The target's chat user name
         * @param aFileName The file to send
         * @param aMessageID The internal ID of the message, for logging
         */
        public SendPMFileTask(final String aLocalChatUsername, final String aRecipientName,
                              final String aFileName, final long aMessageID) {
            this.localChatUsername = aLocalChatUsername;
            this.recipientName = aRecipientName;
            this.fileName = aFileName;
            this.messageID = aMessageID;
        }

        /**
         * What to do when this task is ran.
         * In this case, simply call the function <code>sendPrivateMessage</code> with the necessary info.
         */
        @Override
        public final void run() {
            sendPrivateMessage(localChatUsername, recipientName, null, fileName, messageID);
        }
    }

}//eo chat class
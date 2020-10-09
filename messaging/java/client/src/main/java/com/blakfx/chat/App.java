package com.blakfx.chat;

import com.blakfx.helix.HelixModule;
import com.blakfx.websocket.WebsocketClientEndpoint;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import net.sourceforge.argparse4j.impl.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Simple driver program for the chat client.
 * Will handle arguments, and then create a Chat object as appropriate.
 * Upon successful creation, all work is managed inside Chat's <code>loop</code>.
 */
public class App {

    /**
     * This class' logger instance
     */
    private static final Logger log = LogManager.getLogger(App.class.getName());

    /**
     * Main function, handles arguments and creates Chat object.
     * Hands off control to the Chat object at that point, and only
     * returns upon client exit.
     * @param args Command line arguments to be parsed via argparse4j
     * @throws URISyntaxException URI to access the chat server was malformed
     */
    public static void main(String[] args) throws URISyntaxException {
        // Handle arguments
        ArgumentParser parser = ArgumentParsers.newFor("demo").build()
						.defaultHelp(true)
						.description("Demo chat using Helix library for encryption and decryption");

		parser.addArgument("-s", "--server").required(true).nargs("?").type(String.class)
                .help("IP address of chat server");
		parser.addArgument("-p", "--port").required(true).nargs("?").type(Integer.class).setDefault(8886)
				.choices(Arguments.range(1025, 65535))
                .help("Port of chat server");
		parser.addArgument("-u", "--username").required(true).nargs("?").type(String.class).setDefault("HELIX-JAVA")
                .help("Your Chat username (also used to login into Helix account)");
        parser.addArgument("-hp", "--helix_password").required(false).nargs("?").type(String.class)
                .help("Your Helix account password");
        parser.addArgument("-d", "--device").required(false).nargs("?").type(String.class).setDefault("")
                .help("Your custom device");
        parser.addArgument("-hs", "--helix_server").required(false).nargs("?").type(String.class)
                .setDefault("service.blakfx.us")
                .help("Helix Key-Server IP");
        parser.addArgument("-ho", "--helix_port").required(false).nargs("?").type(Integer.class)
                .choices(Arguments.range(1,65535)).setDefault(5567)
                .help("Helix Key-Server port");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        final String chatServerIP = ns.getString("server");
        int chatServerPort = ns.getInt("port");
        final String chatUsername = ns.getString("username");
        final String device = ns.getString("device");
        final String helixKeyServerIP = ns.getString("helix_server");
        int helixKeyServerPort = ns.getInt("helix_port");
        final String helixUserPassword = ns.getString("helix_password");

        log.info("Attempting to run chat application on {}:{} as user {} on custom device {}",
                 chatServerIP, chatServerPort, chatUsername, device);

        // initiate Helix module
        log.info("Starting Helix Module");
        final HelixModule libHelix = new HelixModule(helixKeyServerIP, helixKeyServerPort, device);
        if(! libHelix.initialize()) {
            final String err_msg = "Failed to initialize Helix module";
            log.fatal(err_msg);
            throw new RuntimeException(err_msg);
        }
        if(! libHelix.loginAsUser(chatUsername, helixUserPassword)) {
            final String err_msg = "Failed to login into Helix account";
            log.fatal(err_msg);
            throw new RuntimeException(err_msg);
        }
        if( ! libHelix.connectToKeyServer() ) {
            final String err_msg = "Failed to Connect to Helix Key Server";
            log.fatal(err_msg);
            throw new RuntimeException(err_msg);
        }


        final MessageCache screenCache = new ConflatedMessageCacheInMemory(1600, chatUsername, System.out);
        final MessageCache privateCache = new MessageCacheInMemory(200, screenCache);
        final MessageCache sharedCache = new MessageCacheInMemory(600, screenCache);

        final String chatServerURL = String.format("ws://%s:%d/server/actions", chatServerIP, chatServerPort);
        log.info("Connecting chat client to server at: {}", chatServerURL);
        final WebsocketClientEndpoint chatEndpoint = new WebsocketClientEndpoint(new URI(chatServerURL));


        // Create Chat object with appropriate values from arguments
        final Chat chat = new Chat(chatUsername, chatEndpoint, privateCache, sharedCache, libHelix);

        // Initiate the chat
        chat.start();

        log.info("Exit application");
        System.out.println("Goodbye!");
    }
}
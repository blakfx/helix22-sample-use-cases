package com.blakfx.helix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.blakfx.crypto.logLevel_t;
import com.blakfx.crypto.*;

import java.math.BigInteger;
import java.util.concurrent.Callable;

/**
 * Acts as a facilitator between the Chat client and the Helix server.
 * Revolves around two primary objectives: encrypt and decrypt content.
 * Helix itself is not aware of the business logic of your application to do its job,
 * nor should it. The module is what allows the integration of Helix to your application 
 * to be seamless.
 */
public class HelixModule implements HelixCryptoProvider {
    private static final Logger log = LogManager.getLogger(HelixModule.class.getName());

    // Load Helix Library to process space
    static {
		try {
			System.loadLibrary("HelixForJAVA");
			log.info("Loaded Helix java interface version: {}",  helix.HELIX_INTERFACE_VERSION);
			// helix.logger_setLogLevel(logLevel_t.NO_LOG);
			// log.info("Set Helix library log level to match to the current process, at {}", helix.logger_getLogLevel());
		} catch (final UnsatisfiedLinkError e) {
			log.fatal("Native Helix library failed to load with error: {}", e.getMessage());
			throw new RuntimeException("Failed to load Helix library", e);
		}
	}


    /**
     * The IP address/hostname of the Helix server to connect to.
     */
    private String keyServerIP;

    /**
     * The port number of the Helix server to connect to.
     */
    private Integer keyServerPort;

    /**
     * An id to emulate a device (dev and test only).
     */
    private String emulatedDevice;
    /**
     * The Helix account bound to this chat username. 
     */
    private String bound_to_helix_account;

    /**
     * The password to use with this Helix account.
     */
    private String helixAccountPassword;
    /**
     * Get this Helix account's password
     * @return the password
     */
    public String getHelixAccountPassword() {
        return helixAccountPassword;
    }


    /**
     * Indicates a fatal Helix error.
     */
    public static class HelixException extends Exception {
        public HelixException(final String anErrorMessage) {
            super(String.format("Helix Exception: %s", anErrorMessage));
        }
    }

    /**
     * Indicates a non-fatal invalid Helix target error.
     */
    public static class InvalidTargetException extends HelixException {
        public InvalidTargetException(final String aRecipientName) {
            super("Invalid recipient: " + aRecipientName);
        }
    }

    /**
     * Indicates a non-fatal Helix encryption error.
     */
    public static class EncryptionException extends HelixException {
        public EncryptionException(String messageResponsible, String comment) {
            super("Error encrypting message: " + messageResponsible + (comment == null ? "" : " " + comment));
        }
    }

    /**
     * Indicates a non-fatal Helix decryption error.
     */
    public static class DecryptionException extends HelixException {
        public DecryptionException(String blobResponsible, String comment) {
            super("Error decrypting blob: " + blobResponsible + (comment == null ? "" : " " + comment));
        }
    }



    /**
     * Constructs the Helix module object with the given arguments.
     * @param aKeyServerIP The IP address/hostname of the Helix key-server
     * @param aKeyServerPort The port number of the Helix server
     */
    public HelixModule(String aKeyServerIP, int aKeyServerPort) {
        new HelixModule(aKeyServerIP, aKeyServerPort, null);
    }

    /**
     * Constructs the Helix module object with the given arguments through a fake device.
     * @param aKeyServerIP The IP address/hostname of the Helix server
     * @param aKeyServerPort The port number of the Helix server
     * @param aDeviceID The fake device to emulate for this client
     */
    public HelixModule(final String aKeyServerIP, final int aKeyServerPort, final String aDeviceID) {
        keyServerIP = aKeyServerIP;
        keyServerPort = aKeyServerPort;
        emulatedDevice = null;
        if(aDeviceID != null && !aDeviceID.isBlank()) {
            emulatedDevice = aDeviceID.trim();
        }
    }

    /**
     * Initializes the Helix module, and connects to the Helix server.
     * @return whether the operation succeeded
     */
    @Override
    public final boolean initialize() {
        boolean initialized = false;

        final invokeStatus_t promisedStartup = helix.jCrypto_apiStartup_Advanced(keyServerIP, keyServerPort, emulatedDevice, 0L);

        log.info("helix module initialization returned code {}", promisedStartup.swigValue());
        if(invokeStatus_t.INVOKE_STATUS_TRUE == promisedStartup) {
            initialized = true;
        } else {
            log.fatal("Failed to initialise Helix Module");
        }
        return initialized;
    }

    /**
     * Shuts down the helix module gracefully.
     */
    @Override
    public final void shutdown() {
        log.info("Begin close");

        //TODO: remove this once we want to allow returning users
        if(this.bound_to_helix_account != null) {
            log.info("Deleting user");
            // Delete the account we exiting from
            final long deleteResult = helix.jCrypto_accountDelete(this.bound_to_helix_account);
            log.info("Delete user - returned code {}", deleteResult);
            if (0 != deleteResult) {
               log.error("Could not delete user {} successfully", this.bound_to_helix_account);
            }
        }

        disconnectFromKeyServer();

        log.info("Shutting down Helix module");
        helix.jCrypto_apiShutdown();
    }

    /**
     * Connect to the Helix key server.
     * @return whether the connection was successful or not
     */
    @Override
    public final boolean connectToKeyServer() {
        boolean connected = false;

        log.info("Connecting to key server");
        final invokeStatus_t promisedServerConnect = helix.jCrypto_serverConnect();

        log.info("Received connection promise id {}", promisedServerConnect.swigValue());
        if(invokeStatus_t.INVOKE_STATUS_TRUE == promisedServerConnect) {
            connected = true;
        } else {
            log.error("Could not connect to Helix Key Server");
        }
        return connected;
    }

    /**
     * Disconnect from the Helix key server.
     * @return whether the disconnection was successful or not
     */
    @Override
    public final boolean disconnectFromKeyServer() {
        boolean complete = false;
        log.info("Disconnecting from Helix server");

        final invokeStatus_t promisedServerDisconnect = helix.jCrypto_serverDisconnect();
        log.info("Received disconnect promise id {}", promisedServerDisconnect.swigValue());
        if(invokeStatus_t.INVOKE_STATUS_TRUE == promisedServerDisconnect) {
            complete = true;
        } else {
            log.error("Could not disconnect successfully from the Helix Server");
        }
        return complete;
    }

    /**
     * Identify a user on the Helix Server.
     * @param aHelixUsername The username to create and login as
     * @param aHelixAccountPassword The password for the Helix account
     * @return whether the identification was successful or not
     */
    @Override
    public final boolean loginAsUser(final String aHelixUsername, final String aHelixAccountPassword) {
        boolean allowed = false;

        //TODO: remove this once we stop deleting accounts on shutdown
        this.bound_to_helix_account = aHelixUsername;
        this.helixAccountPassword = aHelixAccountPassword;

        final invokeStatus_t freshLoginResult = helix.jCrypto_accountLogin(aHelixUsername);
        log.info("Attempted to Login as returning user with result {}", freshLoginResult);
        if(invokeStatus_t.INVOKE_STATUS_TRUE == freshLoginResult) {
            allowed = true; // Login succeeded, we're done
            log.info("Identification succeeded - Login as returning user {}", aHelixUsername);
        } else {
            // Login as returning user failed, delete account and recreate it
            final long deleteResult = helix.jCrypto_accountDelete(aHelixUsername);
            log.info("Delete account - received code {}", deleteResult);

            // Regardless whether delete failed/succeeded, attempt to create a new account
            final invokeStatus_t createResult = helix.jCrypto_accountCreate(aHelixUsername);
            log.info("Create new account - received code {}", createResult);

            if (invokeStatus_t.INVOKE_STATUS_TRUE == createResult) {
                // Create succeeded, login to it
                final invokeStatus_t loginResult = helix.jCrypto_accountLogin(aHelixUsername);
                log.info("Login to new account - returned code {}", loginResult);
                if (invokeStatus_t.INVOKE_STATUS_TRUE == loginResult) {
                    allowed = true; // Login succeeded, we're done
                    log.info("New Account creation succeeded with code {}", loginResult);
                } else {
                    log.fatal("Could not login to successfully created new account for {}", aHelixUsername);
                }
            } else {
                log.fatal("Could not create new account for {} after a failed login as returning user", aHelixUsername);
            }
        }
        return allowed;
    }

    /**
     * Encrypt a plaintext string intended for a target username.
     * Allows any application an abstract way of using Helix to encrypt
     * a plaintext string. Application can decide how to supply it, and
     * what to do with the result.
     * @param plainData The plaintext content to encrypt
     * @param aRecipientAccountID The account of the recipient on the Helix server to receive this content
     * @param messageID The messageID for this specific communication
     * @return Encrypted byte contents as a string
     * @throws HelixModule.HelixException Not connected to the Helix server
     * @throws HelixModule.InvalidTargetException Invalid target specified
     * @throws HelixModule.EncryptionException Encryption failed to complete successfully
     */
    @Override
    public final byte[] encrypt(byte[] plainData, final String aRecipientAccountID, long messageID) throws HelixException {
        byte[] encryptedBlob = null;
        log.info("Begin encrypt from string for message #{} to recipient{}, with content {}", messageID, aRecipientAccountID, plainData);

        // Check if connected
        log.info("Checking server connection (message #{})", messageID);
        final invokeStatus_t isConnected = helix.jCrypto_serverIsConnected();
        log.info("Check server connection (message #{}) - returned {}", messageID, isConnected.swigValue());
        if(isConnected == invokeStatus_t.INVOKE_STATUS_TRUE) {
            log.info("Attempting to find recipient {} (message #{})", aRecipientAccountID, messageID);

            // Attempt to find recipient
            long ms_timeout = 15 *1000; // TODO: parameterize this wait-time and monitor for its dynamic
            final BigInteger recipientID = helix.jCrypto_simpleSearchForRecipientByName(aRecipientAccountID, ms_timeout);
            final promiseStatusAndFlags_t foundRecipient = helix.jCrypto_waitEventStatus(recipientID);
            log.info("Search for recipient {} (message #{}) - returned {}", aRecipientAccountID, messageID, foundRecipient.swigValue());
            if( promiseStatusAndFlags_t.PROMISE_DATA_AVAILABLE != foundRecipient) {
                log.error("Failed to find Helix account for the specified recipient: {}", aRecipientAccountID);
                throw new InvalidTargetException(aRecipientAccountID);
            }
            log.info("Getting encryption handle (message #{})", messageID);

            // Get encryption handle
            // Note: eventually would want to implement password field, for now left null (no password)
            String tmp = new String(plainData);
            final BigInteger encryptionHandle = helix.jCrypto_encryptStart(recipientID, tmp, (long) tmp.length(),
                                                                           this.helixAccountPassword);
            helix.jCrypto_waitEvent(encryptionHandle, promiseStatusAndFlags_t.PROMISE_INFINITE.swigValue());
            log.info("Get encryption handle (message #{}) - returned {}", messageID, encryptionHandle);

            log.info("Encrypting (message #{})", messageID);
            if (helix.jCrypto_waitEventStatus(encryptionHandle) == promiseStatusAndFlags_t.PROMISE_DATA_AVAILABLE) {

                // Check encryption succeeded, and return the output if successful
                encryptedBlob = helix.jCrypto_encryptGetOutputData(encryptionHandle);
                log.info("Encrypt (message #{}) - returned blob of length{}", messageID, encryptedBlob.length);
                if (encryptedBlob.length > 0) {
                    log.info("Encryption success");
                } else {
                    log.error("Successfully encrypted non-empty plaintext buffer is reported to produce a 0 length output");
                    throw new EncryptionException(String.valueOf(messageID), "Empty output buffer on encryption");
                }
            } else {
                log.error("Failed to extract encrypted data from Helix library");
                throw new EncryptionException(String.valueOf(messageID), "Failed to extract encrypted data from Helix library");
            }
        } else {
            log.error("Helix reported loss of connection with key-server");
        }
        return encryptedBlob;
    }//eo encrypt


    /**
     * Decrypts a string blob associated to a specific contact.
     * Allows any application an abstract way of using Helix to decrypt
     * a plaintext string. Application can decide how to supply it, and
     * what to do with the result.
     * @param encryptedData Contains the encrypted bytes to decrypt
     * @param messageID The messageID associated with this blob
     * @return The content as a plaintext decrypted string
     */
    @Override
    public final byte[] decrypt(byte[] encryptedData, long messageID) {
        byte[] decrypted = null;
        log.debug("Begin decrypt from blob #{}", messageID);

        // Get decryption handle
        final BigInteger decryptionHandle = helix.jCrypto_decryptStart(encryptedData, this.helixAccountPassword);
        log.info("Got decryption handle {} for (blob #{})", decryptionHandle, messageID );

        // Wait (infinitely) for the decryption to end
        // Note: eventually would want to put a time restriction
        helix.jCrypto_waitEvent(decryptionHandle, promiseStatusAndFlags_t.PROMISE_INFINITE.swigValue());

        log.info("Ensure decryption as successful (blob #{})", messageID);
        promiseStatusAndFlags_t decryptionStatus = helix.jCrypto_waitEventStatus(decryptionHandle);
        if (promiseStatusAndFlags_t.PROMISE_DATA_AVAILABLE == decryptionStatus) {
            decrypted = helix.jCrypto_decryptGetOutputData(decryptionHandle); // Get decrypted data
            log.info("Decrypt for (blob #{}) - returned buffer of {} bytes", messageID, decrypted.length);
        } else {
            log.error("Decryption of blob {} returned promise code {}", messageID, decryptionStatus.swigValue());
        }
        return decrypted;
    }

}

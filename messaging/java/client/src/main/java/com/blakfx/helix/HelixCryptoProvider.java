package com.blakfx.helix;

public interface HelixCryptoProvider {

    /**
     * Initializes the Helix module, and connects to the Helix server.
     * @return whether the operation succeeded
     */
    public boolean initialize();
    /**
     * Shuts down the helix module gracefully.
     */
    public void shutdown();

    /**
     * Connect to the Helix key server.
     * @return whether the connection was successful or not
     */
    public boolean connectToKeyServer();
    /**
     * Disconnect from the Helix key server.
     * @return whether the disconnection was successful or not
     */
    public boolean disconnectFromKeyServer();


    /**
     * Identify a user on the Helix Server.
     * @param aHelixUsername The username to create and login as
     * @param aHelixAccountPassword The password for the Helix account
     * @return whether the identification was successful or not
     */
    public boolean loginAsUser(final String aHelixUsername, final String aHelixAccountPassword);


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
    public byte[] encrypt(final byte[] plainData, final String aRecipientAccountID, long messageID) throws HelixModule.HelixException;

    /**
     * Decrypts a string blob associated to a specific contact.
     * Allows any application an abstract way of using Helix to decrypt
     * a plaintext string. Application can decide how to supply it, and
     * what to do with the result.
     * @param encryptedData Contains the encrypted bytes to decrypt
     * @param messageID The messageID associated with this blob
     * @return The content as a plaintext decrypted string
     */
    public byte[] decrypt(final byte[] encryptedData, long messageID);

}

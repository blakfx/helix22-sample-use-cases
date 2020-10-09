package com.blakfx.chat;

import java.util.List;
import java.util.Set;

/**
 * Interface for a Message Cache abstraction.
 * Basically, keep track of a contact list, and their respective messages.
 */
public interface MessageCache {

    /**
     * Add a contact to the cache.
     * @param aContactName the name of the contact
     * @return whether the operation succeeded or not
     */
    boolean addContact(final String aContactName);
    /**
     * Check for whether a contact exists in the cache.
     * @param aContactName the name of the contact
     * @return whether the contact exists or not
     */
    boolean contactExists(final String aContactName);

    /**
     * Add a contact message, either a file notification or text content, to this contact cache.
     *
     * @param aContactName The contact associated with this message
     * @param aMessage The file notification or text content to add
     * @param shouldPrint Whether this add operation should be followed by a print of the cache
     * @return whether the operation succeeded
     */
    boolean saveContactMessage(final String aContactName, final String aMessage, boolean shouldPrint);

    /**
     * Get all the active contacts on the cache.
     * @return A set of strings with the contact names
     */
    Set<String> getActiveContacts();
    /**
     * Get all the messages associated with a given contact.
     * @param aContactName the contact whose messages to get
     * @return A list of strings of each message for this contact
     */
    List<String> getContactMessages(final String aContactName);

}

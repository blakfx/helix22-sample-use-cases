package com.blakfx.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * An implementation of a MessageCache in memory.
 * Shared message cache, and private message cache both are instances of this class.
 */
public class MessageCacheInMemory implements MessageCache {
    /**
     * This class' private Logger instance.
     */
    private static final Logger log = LogManager.getLogger(MessageCacheInMemory.class.getName());

    /**
     * The maximum number of messages (per contact) the private cache can hold at a given moment.
     */
    private final int MAX_CACHE_ELEMENTS;

    /**
     * The cache/history of private messages (on a per-user basis) sent/received.
     */
    private final Map<String, List<String>> privateCache;
    /**
     * The parent cache of this cache.
     */
    private final MessageCache parentCache;

    /**
     * Constructor for a MessageCache in memory.
     * @param cacheSize the max number of elements the cache can hold
     * @param aParentCache the parent cache for the cache being created
     */
    public MessageCacheInMemory(int cacheSize, final MessageCache aParentCache) {
        MAX_CACHE_ELEMENTS = cacheSize;
        privateCache = new HashMap<>();
        parentCache = aParentCache;
    }

    /**
     * Add a contact to the cache. 
     * Will create the contact if it's untracked so far, else return existing one.
     * @param aContactName the name of the contact
     * @return whether the operation succeeded or not
     */
    @Override
    public boolean addContact(String aContactName) {
        boolean contactAdded = false;
        if (! contactExists(aContactName)) {
            privateCache.put(aContactName, new ArrayList<>());
            contactAdded = true;
        }
        return contactAdded;
    }

    /**
     * Check for whether a contact exists in the cache.
     * @param aContactName the name of the contact
     * @return whether the contact exists or not
     */
    @Override
    public boolean contactExists(String aContactName) {
        return privateCache.containsKey(aContactName);
    }

    /**
     * Add a contact message, either a file notification or text content, to this contact cache.
     *
     * @param aContactName The contact associated with this message
     * @param aMessage The file notification or text content to add
     * @param shouldPrint Whether this add operation should be followed by a print of the cache
     */
    @Override
    public boolean saveContactMessage(String aContactName, String aMessage, boolean shouldPrint) {
        boolean inserted = false;

        // If this contact doesn't exist (we haven't received any messages from them yet), add them first
        if(!contactExists(aContactName)) {
            addContact(aContactName);
        }
        
        List<String> contactCache = getContactMessages(aContactName);
        if (contactCache.size() > MAX_CACHE_ELEMENTS) {
            log.info("User [{}] private cache is full - dropping oldest message to make space", aContactName);
            contactCache.remove(0);
        }

        inserted = contactCache.add(aMessage);
        if(parentCache != null) {
            parentCache.saveContactMessage(aContactName, aMessage, shouldPrint);
        }
        return inserted;
    }

    /**
     * Get all the active contacts on the cache.
     * @return A set of strings with the contact names
     */
    @Override
    public Set<String> getActiveContacts() {
        return privateCache.keySet();
    }

    /**
     * Get all the messages associated with a given contact.
     * @param aContactName the contact whose messages to get
     * @return A list of strings of each message for this contact
     */
    @Override
    public List<String> getContactMessages(String aContactName) {
        return privateCache.get(aContactName);
    }
}

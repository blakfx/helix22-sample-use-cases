package com.blakfx.chat;

import java.io.PrintStream;
import java.util.List;

/**
 * A parent cache to other caches, that is responsible for printing all contents.
 * In our implementation, the screen cache serves this functionality.
 */
public class ConflatedMessageCacheInMemory extends MessageCacheInMemory {
    /**
     * The stream through which to print output.
     */
    private final PrintStream out;
    /**
     * The name of the account responsible for this cache.
     */
    private final String conflatedAccountName;

    /**
     * Constructor for a Conflated Message Cache in memory.
     * @param aCacheSize the number of max elements that this cache will hold
     * @param aConflatedAccountName the name of the account responsible for this cache
     * @param aOutStream the stream through which to print output
     */
    public ConflatedMessageCacheInMemory(int aCacheSize, final String aConflatedAccountName, final PrintStream aOutStream) {
        super(aCacheSize, null);
        out = aOutStream;
        conflatedAccountName = aConflatedAccountName;
    }
    
    /**
     * Add a contact message, either a file notification or text content, to this contact cache.
     * Note: all messages added here are added to the separate screen cache
     * They are all added under the name of the "self contact"
     * @param aContactName The contact associated with this message
     * @param aMessage The file notification or text content to add
     * @param shouldPrint Whether this add operation should be followed by a print of the cache
     */
    @Override
    public boolean saveContactMessage(String aContactName, String aMessage, boolean shouldPrint) {
        boolean wasAdded = super.saveContactMessage(conflatedAccountName, aMessage, shouldPrint);
        if(wasAdded && shouldPrint) {
            // There may be already something written in the terminal (prompt/typed message/etc), put a new line for formatting here
            // This is the only set of print operations that arrives "sporadically" (all the receive-variants)
            // TODO: incorporate some wrappers around output stream to save "previously typed" input? It's included in nextLine() and is confusing for users
            out.println();
            final List<String> allScreenMessages = super.getContactMessages(conflatedAccountName);
            for(String screenMessage: allScreenMessages) {
                out.printf("%s\n", screenMessage);
            }
        }
        return wasAdded;
    }

}

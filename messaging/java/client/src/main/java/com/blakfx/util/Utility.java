package com.blakfx.util;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Contains utilities that help with the chat application.
 * Mainly, file and formatting tools.
 */
public class Utility {
	/**
	 * This class' private Logger instance.
	 */
	private static final Logger log = getLogger(Utility.class.getName());

	/**
	 * A formatter for chat messages.
	 */
	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss");
	/**
	 * A formatter for file/log messages.
	 */
	private static final DateTimeFormatter dtfFile = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss");
	
	/**
	 * The current directory from where the application was launched.
	 */
	private static final String currentDirectory = Paths.get("").toAbsolutePath().toString();


	/**
	 * Print a given message with a timestamp before it.
	 * @param message The message to apply the timestamp to
	 */
    public static void printWithTime(String message) {
        System.out.println("[ " + getTimeStamp(false) + " ] " + message);
	}
	
	/**
	 * Returns a timestamp for a file or for text as requested.
	 * @param forFile Whether to produce the timestamp for a file or not
	 * @return The timestamp, using the appropriate pattern, as a string
	 */
	public static String getTimeStamp(boolean forFile) {
		return (forFile) ? dtfFile.format(LocalDateTime.now()) : dtf.format(LocalDateTime.now());
	}

	/**
	 * Prepares a formatted private message notice.
	 * @param aMessage the message being received/sent
	 * @param contact the contact associated with the message
	 * @param received whether this was received or sent
	 * @return the formatted notice
	 */
	public static String formatPrivateMessageNotice(final String aMessage, final String contact, boolean received) {
		final String tm = Utility.getTimeStamp(false);
		return String.format("[%s](private - %s) %s said: %s", tm, contact, received ? contact : "You", aMessage);
	}

	/**
	 * Prepares a formatted private file notice.
	 * @param aFilename the name of the file being received/sent
	 * @param contact the contact associated with the file
	 * @param received whether this was received or sent
	 * @return the formatted notice
	 */
	public static String formatPrivateFileNotice(final String aFilename, final String contact, boolean received) {
		final String tm = Utility.getTimeStamp(false);
		return String.format("[%s](private - %s): %s secure sent file '%s'", tm, contact, received ? contact : "You", aFilename);
	}

	/**
	 * Prepares a formatted global message notice.
	 * @param aMessage the message sent/received
	 * @param received whether this was received or sent
	 * @return the formatted notice
	 */
	public static String formatGlobalMessageNotice(final String aMessage, boolean received) {
		final String tm = Utility.getTimeStamp(false);
		return String.format("[%s](global)%s%s", tm, received ? " " : " You said: ", aMessage);
	}

    /**
	 * Writes bytes to a file.
	 * @param aFileName The name of the file to write to
	 * @param fileContents The bytes to write into the file
	 * @throws IOException Invalid path to file
	 */
	public static void writeToFile(final String aFileName, byte[] fileContents) throws IOException {
        final Path fullpath = Paths.get(aFileName);
        Files.write(fullpath, fileContents);
	}

	/**
	 * Reads bytes from a file.
	 * @param aFileName The name of the file to read from
	 * @return The bytes read from the file if successful, or null if not successful
	 * @throws IOException Invalid path to file
	 */
	public static byte[] readFromFile(final String aFileName) throws IOException {
		byte[] fileContents;
        final Path fullpath = Path.of(currentDirectory, aFileName);
        fileContents = Files.readAllBytes(fullpath);
		return fileContents;
	}

	/**
	 * Check whether a file exists locally or not (relative path from the current directory).
	 * @param aFileName the file name to check
	 * @return whether file exists or not
	 */
	public static boolean checkFileExists(final String aFileName) {
		final Path filepath = Path.of(currentDirectory, aFileName);
		return Files.isReadable(filepath);
	}

	/**
	 * Creates a local folder if it doesn't exist already.
	 * @param path The path of the folder
	 */
	public static void createFolderIfNotExists(final Path path) {
		if(Files.notExists(path)) {
			boolean created = path.toFile().mkdir();
		}
	}

	/**
	 * Creates the chat shared folder for files received.
	 */
	public static void setupChatBackupOnLocalDrive() {
		// Create the shared folder if it doesn't exist
		Path sharedFolder = Path.of(currentDirectory, "shared");
		Utility.createFolderIfNotExists(sharedFolder);
	}

}
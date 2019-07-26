package com.chocohead.loom.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

import com.google.common.io.MoreFiles;

import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsIO;

public class DownloadUtil {
	/**
	 * Download from the given {@link URL} to the given {@link Path} so long as there are differences between them
	 *
	 * @param from The URL of the file to be downloaded
	 * @param to The destination to be saved to, and compared against if it exists
	 *
	 * @throws IOException If an exception occurs during the process
	 */
	public static void downloadIfChanged(URL from, Path to) throws IOException {
		downloadIfChanged(from, to, false);
	}

	/**
	 * Download from the given {@link URL} to the given {@link Path} so long as there are differences between them
	 *
	 * @param from The URL of the file to be downloaded
	 * @param to The destination to be saved to, and compared against if it exists
	 * @param quiet Whether to only print warnings (when <code>true</code>) or everything
	 *
	 * @throws IOException If an exception occurs during the process
	 */
	public static void downloadIfChanged(URL from, Path to, boolean quiet) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) from.openConnection();

		//If the output already exists we'll use it's last modified time
		if (Files.exists(to)) connection.setIfModifiedSince(Files.getLastModifiedTime(to).toMillis());

		//Try use the ETag if there's one for the file we're downloading
		String etag = loadETag(to);
		if (etag != null) connection.setRequestProperty("If-None-Match", etag);

		//We want to download gzip compressed stuff
		connection.setRequestProperty("Accept-Encoding", "gzip");

		//We shouldn't need to set a user agent, but it's here just in case
		//connection.setRequestProperty("User-Agent", null);

		//Try make the connection, it will hang here if the connection is bad
		connection.connect();

		int code = connection.getResponseCode();
		if ((code < 200 || code > 299) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//Didn't get what we expected
			throw new IOException(connection.getResponseMessage());
		}

		long modifyTime = connection.getHeaderFieldDate("Last-Modified", -1);
		if (Files.exists(to) && (code == HttpURLConnection.HTTP_NOT_MODIFIED || modifyTime > 0 && Files.getLastModifiedTime(to).toMillis() >= modifyTime)) {
			if (!quiet) JkLog.info("'" + to + "' Not Modified, skipping.");
			return; //What we've got is already fine
		}

		long contentLength = connection.getContentLengthLong();
		if (!quiet && contentLength >= 0) JkLog.info("'" + to + "' Changed, downloading " + toNiceSize(contentLength));

		try (OutputStream out = Files.newOutputStream(to, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {//Try download to the output
			JkUtilsIO.copy(connection.getInputStream(), out);
		} catch (IOException e) {
			deleteAfterCrash(to, e);
			throw e;
		}

		//Set the modify time to match the server's (if we know it)
		if (modifyTime > 0) Files.setLastModifiedTime(to, FileTime.fromMillis(modifyTime));

		//Save the ETag (if we know it)
		String eTag = connection.getHeaderField("ETag");
		if (eTag != null) {
			//Log if we get a weak ETag and we're not on quiet
			if (!quiet && eTag.startsWith("W/")) JkLog.warn("Weak ETag found.");

			saveETag(to, eTag);
		}
	}

	/**
	 * Creates a new path in the same directory as the given path with <code>.etag</code> on the end of the name
	 *
	 * @param file The {@link Path} to produce the ETag for
	 *
	 * @return The (uncreated) ETag path for the given path
	 */
	private static Path getETagFile(Path file) {
		return file.resolveSibling(file.getFileName() + ".etag");
	}

	/**
	 * Attempt to load an ETag for the given path, if it exists
	 *
	 * @param to The path to load an ETag for
	 *
	 * @return The ETag for the given path, or <code>null</code> if it doesn't exist
	 */
	private static String loadETag(Path to) {
		Path eTagFile = getETagFile(to);
		if (Files.notExists(eTagFile)) return null;

		try {
			return MoreFiles.asCharSource(eTagFile, StandardCharsets.UTF_8).read();
		} catch (IOException e) {
			JkLog.warn("Error reading ETag file '" + eTagFile + "'.");
			e.printStackTrace(new PrintStream(JkLog.getErrorStream()));
			return null;
		}
	}

	/**
	 * Saves the given ETag for the given path, replacing it if it already exists
	 *
	 * @param to The path to save the ETag for
	 * @param eTag The ETag to be saved
	 */
	private static void saveETag(Path to, String eTag) {
		Path eTagFile = getETagFile(to);
		try {
			//if (Files.notExists(eTagFile)) Files.createFile(eTagFile);
			MoreFiles.asCharSink(eTagFile, StandardCharsets.UTF_8).write(eTag);
		} catch (IOException e) {
			JkLog.warn("Error saving ETag file '" + eTagFile + "'.");
			e.printStackTrace(new PrintStream(JkLog.getErrorStream()));
		}
	}

	/**
	 * Format the given number of bytes as a more human readable string
	 *
	 * @param bytes The number of bytes
	 *
	 * @return The given number of bytes formatted to kilobytes, megabytes or gigabytes if appropriate
	 */
	public static String toNiceSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return bytes / 1024 + " KB";
		} else if (bytes < 1024 * 1024 * 1024) {
			return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		} else {
			return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
		}
	}

	/**
	 * Delete the path along with the corresponding ETag, if it exists.
	 *
	 * @param file The path to delete.
	 *
	 * @throws IOException If an exception occurs during deletion
	 */
	public static void delete(Path file) throws IOException {
		Files.deleteIfExists(file);
		Files.deleteIfExists(getETagFile(file));
	}

	/**
	 * Ensures the given {@link Path} is deleted after the given exception is thrown using {@link Files#deleteIfExists(Path)}
	 *
	 * @param file The path that should be deleted, if it exists
	 * @param t The original exception thrown from a previous operation
	 */
	public static void deleteAfterCrash(Path file, Throwable t) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			t.addSuppressed(e);
		}
	}
}
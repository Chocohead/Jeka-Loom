package com.chocohead.loom.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;

public class FileUtils {
	/**
	 * Checks whether the contents of the given {@link Path} matches the given SHA1 checksum
	 *
	 * @param file The path to have the checksum calculated from
	 * @param checksum The expected checksum the path should have
	 *
	 * @return Whether the given checksum matches the checksum of the path
	 */
	public static boolean matchesSHA1(Path file, String checksum) {
		if (file == null || Files.notExists(file)) {
			return false;
		}

		try {
			@SuppressWarnings("deprecation")
			HashCode hash = MoreFiles.asByteSource(file).hash(Hashing.sha1());

			StringBuilder builder = new StringBuilder();
			for (byte hashBytes : hash.asBytes()) {
				builder.append(Integer.toString((hashBytes & 0xFF) + 0x100, 16).substring(1));
			}

			return checksum.contentEquals(builder);
		} catch (IOException e) {
			throw new RuntimeException("Unexpected error calculating file hash", e);
		}
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
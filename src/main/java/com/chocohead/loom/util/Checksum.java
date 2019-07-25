package com.chocohead.loom.util;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;

public class Checksum {
	public static boolean equals(Path file, String checksum) {
		if (file == null) {
			return false;
		}

		try {
			@SuppressWarnings("deprecation")
			HashCode hash = MoreFiles.asByteSource(file).hash(Hashing.sha1());

			StringBuilder builder = new StringBuilder();
			for (byte hashBytes : hash.asBytes()) {
				builder.append(Integer.toString((hashBytes & 0xFF) + 0x100, 16).substring(1));
			}

			return builder.toString().equals(checksum);
		} catch (IOException e) {
			throw new RuntimeException("Unexpected error calculating file hash", e);
		}
	}
}
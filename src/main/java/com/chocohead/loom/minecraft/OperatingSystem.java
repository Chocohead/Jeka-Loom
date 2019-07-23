package com.chocohead.loom.minecraft;

import java.util.Locale;

import com.google.gson.annotations.SerializedName;

public enum OperatingSystem {
	@SerializedName(value = "windows", alternate = "win")
	WINDOWS("win"),
	@SerializedName(value = "osx", alternate = "mac")
	OSX("mac"),
	@SerializedName(value = "linux", alternate = "unix")
	LINUX("linux", "unix");

	private final String[] names;

	private OperatingSystem(String... names) {
		this.names = names;
	}

	public static OperatingSystem get() {
		String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

		for (OperatingSystem os : values()) {
			for (String name : os.names) {
				if (osName.contains(name)) {
					return os;
				}
			}
		}

		throw new IllegalStateException("Unable to find OS for current system: " + osName);
	}

	public static boolean is64Bit() {
		return System.getProperty("sun.arch.data.model").contains("64");
	}

	public static String getArch() {
		if (is64Bit()) {
			return "64";
		} else {
			return "32";
		}
	}
}
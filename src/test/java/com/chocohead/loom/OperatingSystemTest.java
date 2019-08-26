package com.chocohead.loom;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;

import com.chocohead.loom.minecraft.OperatingSystem;

class OperatingSystemTest {
	private static OS getCurrentOS() {
		for (OS os : OS.values()) {
			if (os.isCurrentOs()) return os;
		}

		return fail("Unable to resolve current operating system?");
	}

	@Test
	void testGet() {
		OperatingSystem os = OperatingSystem.get();

		switch (getCurrentOS()) {
		case WINDOWS:
			assertTrue(os == OperatingSystem.WINDOWS, "Wrong operating system detected");
			break;

		case MAC:
			assertTrue(os == OperatingSystem.OSX, "Wrong operating system detected");
			break;

		case LINUX:
			assertTrue(os == OperatingSystem.LINUX, "Wrong operating system detected");
			break;

		default: //Presumably AIX and SOLARIS won't work at all with vanilla
			fail("Current operating system is unexpected: " + getCurrentOS() + " having detected " + os);
		}
	}
}
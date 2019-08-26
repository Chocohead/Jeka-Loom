package com.chocohead.loom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.minecraft.EnigmaLiveDependency;

class EnigmaDownloadingTest {
	@BeforeAll
	static void setup() {
		JkLog.registerHierarchicalConsoleHandler();
		//JkLog.setVerbosity(Verbosity.VERBOSE);
	}

	@AfterEach
	void after() {
		while (JkLog.getCurrentNestedLevel() > 0) JkLog.endTask("Abrupt end after %d milliseconds."); //Flatten out any hanging tasks
		JkLog.info(""); //New line between tests
	}

	@Test
	void testCreateForBranch() {
		JkDependency dependency = EnigmaLiveDependency.createForBranch("19w34a");
		assertTrue(dependency instanceof JkComputedDependency);

		List<Path> files = assertDoesNotThrow(() -> {
			return ((JkComputedDependency) dependency).getFiles();
		}, "Failed to download dependency");

		assertTrue(files.size() == 1);
		assertTrue(Files.exists(files.get(0)));
	}

	@Test
	void testCreate() {
		JkDependency dependency = EnigmaLiveDependency.create("3ae7d9f");
		assertTrue(dependency instanceof JkComputedDependency);

		List<Path> files = assertDoesNotThrow(() -> {
			return ((JkComputedDependency) dependency).getFiles();
		}, "Failed to download dependency");

		assertTrue(files.size() == 1);
		assertTrue(Files.exists(files.get(0)));
	}
}
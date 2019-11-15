package com.chocohead.loom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.file.JkPathTree;
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

	@Test
	void testRead() throws IOException {
		Path file = assertDoesNotThrow(() -> {
			return ((JkComputedDependency) EnigmaLiveDependency.createForBranch("19w34a")).getFiles();
		}, "Failed to download dependency").get(0);

		Set<String> javaWalking;
		try (Stream<Path> stream = Files.find(FileSystems.newFileSystem(file, null).getPath("/"),
				Integer.MAX_VALUE,
				(path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".mapping"),
				FileVisitOption.FOLLOW_LINKS)) {
			javaWalking = stream.map(Path::toString).collect(Collectors.toSet());
		}

		Set<String> jekaWalking;
		try (Stream<Path> stream = JkPathTree.ofZip(file)/*.withMatcher(JkPathMatcher.ofNoDirectory())*/.andMatching("**.mapping").stream(FileVisitOption.FOLLOW_LINKS)) {
			jekaWalking = stream.map(Path::toString).collect(Collectors.toSet());
		}

		//Compare by string not by size as the paths come from different zip providers
		assertEquals(javaWalking, jekaWalking, "Size difference: " + Math.abs(javaWalking.size() - jekaWalking.size()));
	}
}
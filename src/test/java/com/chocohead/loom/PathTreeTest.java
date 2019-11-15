package com.chocohead.loom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jeka.core.api.file.JkPathMatcher;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.system.JkLog;

class PathTreeTest {
	@TempDir
	Path testDir;

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
	void test() throws IOException {
		Path zip = testDir.resolve("testZip.zip");
		Path file = testDir.resolve("test.txt");

		assertTrue(Files.notExists(file));
		Files.write(file, Collections.singletonList("Testing 123"), StandardOpenOption.CREATE_NEW);
		assertTrue(Files.exists(file));

		assertTrue(Files.notExists(zip));
		try (JkPathTree tree = JkPathTree.ofZip(zip)) {
			tree.goTo("test/").createIfNotExist();
			tree.importFile(file, "test/file.txt", StandardCopyOption.REPLACE_EXISTING);
		}
		assertTrue(Files.exists(zip));

		assertDoesNotThrow(() -> {
			try (Stream<Path> stream = JkPathTree.ofZip(zip).withMatcher(JkPathMatcher.ofNoDirectory()).andMatching("**.txt").stream()) {
				stream.forEach(System.out::println);
			}
		}, "The bug remains unfixed: https://github.com/jerkar/jeka/issues/149");
	}
}
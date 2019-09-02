package com.chocohead.loom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScopeMapping;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.minecraft.OperatingSystem;

class ResolutionTest {
	private static JkDependencyResolver makeResolver() {//ModuleHolder is optional, but does appear to speed the whole test class up a little
		return JkDependencyResolver.of(JkRepo.of("https://libraries.minecraft.net"), JkRepo.ofMavenCentral(), JkRepo.ofMavenJCenter()).withModuleHolder(JkVersionedModule.of("com.chocohead.loom:test:1"));
	}

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
	void testNormal() {
		JkModuleDependency dependency = JkModuleDependency.of("org.lwjgl:lwjgl:3.2.2");
		JkScopeMapping mapping = JkScopeMapping.of(JkJavaDepScopes.PROVIDED).to("default(*)");
		JkScopedDependency scoped = JkScopedDependency.of(dependency, JkJavaDepScopes.PROVIDED).withScopeMapping(mapping);

		assertFalse(scoped.isInvolvedIn(JkJavaDepScopes.COMPILE));
		assertTrue(scoped.isInvolvedInAnyOf(JkJavaDepScopes.SCOPES_FOR_COMPILATION));
		assertFalse(scoped.isInvolvedIn(JkJavaDepScopes.RUNTIME));

		JkResolveResult result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Collections.singleton(scoped)), JkJavaDepScopes.SCOPES_FOR_COMPILATION).assertNoError();
		}, "Completely failed to resolve");

		assertTrue(result.getFiles().getEntries().size() == 1, "Resolved to multiple artifacts! " + result.getFiles().getEntries());

		JkLog.info("Normal resolved to " + result.getFiles().getEntries());
	}

	@Test
	void testNormalCombined() {
		JkModuleDependency dependency = JkModuleDependency.of("org.lwjgl:lwjgl:3.2.2");
		JkScopeMapping mapping = JkScopeMapping.of(JkJavaDepScopes.PROVIDED, JkJavaDepScopes.RUNTIME).to("default(*)");
		JkScopedDependency scoped = JkScopedDependency.of(dependency, JkJavaDepScopes.PROVIDED).withScopeMapping(mapping);

		assertFalse(scoped.isInvolvedIn(JkJavaDepScopes.COMPILE));
		assertTrue(scoped.isInvolvedInAnyOf(JkJavaDepScopes.SCOPES_FOR_COMPILATION));
		assertTrue(scoped.isInvolvedIn(JkJavaDepScopes.RUNTIME));

		JkResolveResult result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Collections.singleton(scoped)), JkJavaDepScopes.SCOPES_FOR_COMPILATION).assertNoError();
		}, "Completely failed to resolve");

		assertTrue(result.getFiles().getEntries().size() == 1, "Resolved to multiple artifacts! " + result.getFiles().getEntries());

		JkLog.info("Normal combined resolved to " + result.getFiles().getEntries());
	}

	private static String nativeName() {
		switch (OperatingSystem.get()) {
		case WINDOWS:
			return "natives-windows";

		case OSX:
			return "natives-macos";

		case LINUX:
			return "natives-linux";

		default:
			return fail("How did we get here?");
		}
	}

	@Test
	void testNative() {
		JkModuleDependency dependency = JkModuleDependency.of("org.lwjgl:lwjgl:3.2.2").withClassifier(nativeName());
		JkScopeMapping mapping = JkScopeMapping.of(JkJavaDepScopes.RUNTIME).to("default(*)");
		JkScopedDependency scoped = JkScopedDependency.of(dependency, JkJavaDepScopes.RUNTIME).withScopeMapping(mapping);

		assertFalse(scoped.isInvolvedIn(JkJavaDepScopes.COMPILE));
		assertFalse(scoped.isInvolvedInAnyOf(JkJavaDepScopes.SCOPES_FOR_COMPILATION));
		assertTrue(scoped.isInvolvedIn(JkJavaDepScopes.RUNTIME));

		JkResolveResult result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Collections.singleton(scoped)), JkJavaDepScopes.RUNTIME).assertNoError();
		}, "Completely failed to resolve");

		assertTrue(result.getFiles().getEntries().size() == 1, "Resolved to multiple artifacts! " + result.getFiles().getEntries());

		JkLog.info("Native resolved to " + result.getFiles().getEntries());
	}

	@Test
	void testBoth() {
		JkModuleDependency normalDependency = JkModuleDependency.of("org.lwjgl:lwjgl:3.2.2");
		JkScopeMapping mapping = JkScopeMapping.of(JkJavaDepScopes.PROVIDED).to("default(*)");
		JkScopedDependency normalScoped = JkScopedDependency.of(normalDependency, JkJavaDepScopes.PROVIDED).withScopeMapping(mapping);

		assertFalse(normalScoped.isInvolvedIn(JkJavaDepScopes.COMPILE));
		assertTrue(normalScoped.isInvolvedInAnyOf(JkJavaDepScopes.SCOPES_FOR_COMPILATION));
		assertFalse(normalScoped.isInvolvedIn(JkJavaDepScopes.RUNTIME));

		JkModuleDependency nativeDependency = JkModuleDependency.of("org.lwjgl:lwjgl:3.2.2").withClassifier(nativeName());
		mapping = JkScopeMapping.of(JkJavaDepScopes.RUNTIME).to("default(*)");
		JkScopedDependency nativeScoped = JkScopedDependency.of(nativeDependency, JkJavaDepScopes.RUNTIME).withScopeMapping(mapping);

		assertFalse(nativeScoped.isInvolvedIn(JkJavaDepScopes.COMPILE));
		assertFalse(nativeScoped.isInvolvedInAnyOf(JkJavaDepScopes.SCOPES_FOR_COMPILATION));
		assertTrue(nativeScoped.isInvolvedIn(JkJavaDepScopes.RUNTIME));


		JkResolveResult result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Arrays.asList(normalScoped, nativeScoped)), JkJavaDepScopes.SCOPES_FOR_COMPILATION).assertNoError();
		}, "Completely failed to resolve");
		assertTrue(result.getFiles().getEntries().size() == 1, "Resolved to multiple artifacts! " + result.getFiles().getEntries());

		result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Arrays.asList(normalScoped, nativeScoped)), JkJavaDepScopes.RUNTIME).assertNoError();
		}, "Completely failed to resolve");
		assertTrue(result.getFiles().getEntries().size() == 2, "Failed to resolved both artifacts! " + result.getFiles().getEntries());

		result = assertDoesNotThrow(() -> {
			return makeResolver().resolve(JkDependencySet.of(Arrays.asList(normalScoped, nativeScoped)), JkJavaDepScopes.COMPILE).assertNoError();
		}, "Completely failed to resolve");
		assertTrue(result.getFiles().getEntries().isEmpty(), "Resolved artifacts out of requested scope! " + result.getFiles().getEntries());
	}
}
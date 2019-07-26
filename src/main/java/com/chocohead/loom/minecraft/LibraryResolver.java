package com.chocohead.loom.minecraft;

import java.util.ArrayList;
import java.util.List;

import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.file.JkPathSequence;

import com.chocohead.loom.minecraft.MinecraftVersion.Library;

public class LibraryResolver {
	public static JkDependencySet go(List<Library> libraries) {
		List<JkScopedDependency> dependencies = new ArrayList<>();

		for (Library library : libraries) {
			if (!library.shouldUse()) continue;

			dependencies.add(JkScopedDependency.of(JkModuleDependency.of(library.name), JkJavaDepScopes.PROVIDED));
		}

		return JkDependencySet.of(dependencies);
	}

	public static JkRepoSet getRepo() {
		return JkRepoSet.of("https://libraries.minecraft.net"); //Could check all the libraries start with this URL?
	}

	public static JkPathSequence resolve(JkDependencySet dependencies) {
		JkResolveResult result = JkDependencyResolver.of(getRepo()).resolve(dependencies, JkJavaDepScopes.COMPILE_AND_RUNTIME);
		result.assertNoError();

		return result.getFiles();
	}
}
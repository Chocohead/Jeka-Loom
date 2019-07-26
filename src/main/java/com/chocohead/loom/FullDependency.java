package com.chocohead.loom;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.utils.JkUtilsAssert;

public final class FullDependency {
	private final JkDependencySet dependencies;
	private final JkRepoSet repos;
	private final JkScope[] scopes;
	private JkDependencyResolver resolver;

	private FullDependency(JkDependencySet dependencies, JkRepoSet repos, JkScope[] scopes) {
		this.dependencies = dependencies;
		this.repos = repos;
		this.scopes = scopes;
	}

	public static FullDependency of(JkDependencySet dependencies, JkRepoSet repos, JkScope... scopes) {
		JkUtilsAssert.notNull(scopes, "FullDependency can't be created with null scope, use an empty array instead");
		return new FullDependency(dependencies, repos, scopes);
	}

	public static FullDependency of(JkDependency dependency, JkRepo repo, JkScope... scopes) {
		return of(JkDependencySet.of(Collections.singletonList(JkScopedDependency.of(dependency))), repo.toSet(), scopes);
	}

	public FullDependency withDependencies(JkDependencySet dependencies) {
		return of(dependencies, repos, scopes);
	}

	public FullDependency andDependencies(JkDependencySet dependencies) {
		return withDependencies(this.dependencies.and(dependencies));
	}

	public FullDependency withRepos(JkRepoSet repos) {
		return of(dependencies, repos, scopes);
	}

	public FullDependency andRepos(JkRepoSet repos) {
		return withRepos(this.repos.and(repos));
	}

	public FullDependency withScopes(JkScope... scopes) {
		return of(dependencies, repos, scopes);
	}


	public JkDependencySet getDependencies() {
		return dependencies;
	}

	public JkRepoSet getRepos() {
		return repos;
	}

	public JkDependencyResolver getResolver() {
		if (resolver == null) {
			resolver = JkDependencyResolver.of(repos);
		}

		return resolver;
	}

	public JkResolveResult resolve() {
		return getResolver().resolve(dependencies, scopes);
	}

	public JkPathSequence resolveToSequence() {
		return resolve().assertNoError().getFiles();
	}

	public List<Path> resolveToPaths() {
		return resolveToSequence().getEntries();
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder().append(dependencies).append(" from ").append(repos);
		if (scopes.length > 0) out.append(" for ").append(Arrays.toString(scopes));
		return out.toString();
	}
}
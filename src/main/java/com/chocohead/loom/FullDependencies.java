package com.chocohead.loom;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsIterable;

public class FullDependencies {
	private final JkDependencySet dependencies;
	final JkRepoSet repos;
	final JkScope[] scopes;
	private JkDependencyResolver resolver;

	FullDependencies(JkDependencySet dependencies, JkRepoSet repos, JkScope[] scopes) {
		this.dependencies = dependencies;
		this.repos = repos;
		this.scopes = scopes;
	}

	public static FullDependencies of(JkDependencySet dependencies, JkRepoSet repos, JkScope... scopes) {
		JkUtilsAssert.notNull(scopes, "FullDependency can't be created with null scope, use an empty array instead");
		return new FullDependencies(dependencies, repos, scopes);
	}

	public static FullDependency of(JkDependency dependency, JkRepo repo, JkScope... scopes) {
		return FullDependency.of(dependency, repo, scopes);
	}

	public FullDependencies withDependencies(JkDependencySet dependencies) {
		if (dependencies.toList().size() == 1) {
			return new FullDependency(dependencies, repos, scopes);
		}
		return of(dependencies, repos, scopes);
	}

	public FullDependencies andDependencies(JkDependencySet dependencies) {
		return withDependencies(this.dependencies.and(dependencies));
	}

	public FullDependencies withRepos(JkRepoSet repos) {
		return of(dependencies, repos, scopes);
	}

	public FullDependencies andRepos(JkRepoSet repos) {
		return withRepos(this.repos.and(repos));
	}

	public FullDependencies withScopes(JkScope... scopes) {
		return of(dependencies, repos, scopes);
	}

	public FullDependencies and(FullDependencies that) {
		Set<JkScope> scopes = JkUtilsIterable.setOf(this.scopes);
		JkUtilsIterable.addAllWithoutDuplicate(scopes, Arrays.asList(that.scopes));
		return of(dependencies.and(that.dependencies), repos.and(that.repos), scopes.toArray(new JkScope[0]));
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

	public void setModuleHolder(JkVersionedModule module) {
		resolver = getResolver().withModuleHolder(module);
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
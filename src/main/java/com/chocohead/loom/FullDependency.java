package com.chocohead.loom;

import java.util.Arrays;
import java.util.Collections;

import com.google.common.collect.Iterables;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.depmanagement.JkVersion;

public final class FullDependency extends FullDependencies {
	private final JkScopedDependency dependency;

	FullDependency(JkDependencySet dependency, JkRepoSet repos, JkScope[] scopes) {
		super(dependency, repos, scopes);

		this.dependency = Iterables.getOnlyElement(dependency);
	}

	private FullDependency(JkScopedDependency dependency, JkRepoSet repos, JkScope[] scopes) {
		super(JkDependencySet.of(Collections.singletonList(dependency)), repos, scopes);

		this.dependency = dependency;
	}

	public static FullDependency of(JkScopedDependency dependency, JkRepoSet repos, JkScope... scopes) {
		return new FullDependency(dependency, repos, scopes);
	}

	public static FullDependency of(JkDependency dependency, JkRepoSet repo, JkScope... scopes) {
		return of(JkScopedDependency.of(dependency, scopes), repo, scopes);
	}

	public static FullDependency of(JkDependency dependency, JkRepo repo, JkScope... scopes) {
		return of(dependency, repo.toSet(), scopes);
	}

	@Override
	public FullDependency withRepos(JkRepoSet repos) {
		return of(dependency, repos, scopes);
	}

	@Override
	public FullDependency andRepos(JkRepoSet repos) {
		return withRepos(this.repos.and(repos));
	}

	@Override
	public FullDependency withScopes(JkScope... scopes) {
		return of(dependency, repos, scopes);
	}


	public JkScopedDependency getDependency() {
		return dependency;
	}

	public boolean isModule() {
		return dependency.getDependency() instanceof JkModuleDependency;
	}

	public JkModuleId getModuleID() {
		JkDependency dependency = this.dependency.getDependency();

		if (dependency instanceof JkModuleDependency) {
			return ((JkModuleDependency) dependency).getModuleId();
		} else {
			throw new IllegalArgumentException("Tried to get the module ID for a non-module dependency: " + dependency);
		}
	}

	public JkVersion getResolvedVersion() {
		return resolve().assertNoError().getVersionOf(getModuleID());
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder().append(dependency).append(" from ").append(repos);
		if (scopes.length > 0) out.append(" for ").append(Arrays.toString(scopes));
		return out.toString();
	}
}
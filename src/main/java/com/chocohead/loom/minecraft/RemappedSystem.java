package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencyNode;
import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScope;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.java.project.JkJavaProject;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import com.chocohead.loom.minecraft.MappingResolver.MappingFactory;
import com.chocohead.loom.util.FileUtils;

public final class RemappedSystem {
	private final Path repoRoot;
	private final Path[] classpath;
	private IMappingProvider mappings;
	private JkDependencySet dependencies = JkDependencySet.of();
	private JkRepoSet repos = JkRepoSet.of(JkRepo.of("https://maven.fabricmc.net"), JkRepo.of("https://libraries.minecraft.net"), JkRepo.ofMavenCentral(), JkRepo.ofMavenJCenter());

	RemappedSystem(Path repoRoot, Path... classpath) {
		this.repoRoot = repoRoot;
		this.classpath = classpath;
	}

	public RemappedSystem withMappings(MappingFactory factory, String from, String to) throws IOException {
		return withMappings(factory.create(from, to));
	}

	public RemappedSystem withMappings(IMappingProvider mappings) {
		this.mappings = mappings;

		return this;
	}

	public RemappedSystem withFabricLoader(String version) {
		return withImplementation(JkModuleDependency.of("net.fabricmc", "fabric-loader", version));
	}

	public RemappedSystem withFabricAPI(String version) {
		return withImplementation(JkModuleDependency.of("net.fabricmc.fabric-api", "fabric-api", version));
	}

	public RemappedSystem withImplementation(JkDependency dependency) {
		dependencies = dependencies.and(dependency, JkJavaDepScopes.PROVIDED);

		return this;
	}

	public RemappedSystem withAPI(JkDependency dependency) {
		dependencies = dependencies.and(dependency, JkJavaDepScopes.COMPILE_AND_RUNTIME);

		return this;
	}

	public RemappedSystem withRuntime(JkDependency dependency) {
		dependencies = dependencies.and(dependency, JkJavaDepScopes.RUNTIME);

		return this;
	}

	public RemappedSystem withRepo(JkRepo repo) {
		repos = repos.and(repo);

		return this;
	}

	public RemappedSystem withRepos(JkRepoSet repos) {
		this.repos = this.repos.and(repos);

		return this;
	}

	public JkDependencySet getDependencies() {
		return dependencies;
	}

	public JkRepoSet getRepos() {
		return repos;
	}

	public void copyTo(JkJavaProject project) {
		repos.getRepoList().forEach(project.getMaker()::addDownloadRepo);
		project.getMaker().addDownloadRepo(JkRepo.ofMaven(repoRoot));

		JkDependencyResolver resolver = JkDependencyResolver.of(repos).withModuleHolder(JkVersionedModule.ofUnspecifiedVerion(JkModuleId.of("com.chocohead.loom.remapped", repoRoot.getFileName().toString())));
		Map<Path, Path> remappedToSource = new HashMap<>();

		for (JkScope scope : dependencies.getDeclaredScopes()) {
			JkResolveResult result = resolver.resolve(dependencies, scope).assertNoError();

			result.getDependencyTree().toFlattenList().stream().<Stream<JkDependencyNode>>map(node -> {
				switch (node.getNodeInfo().getFiles().size()) {
				case 0: //Empty dependency apparently?
					assert false: node;
				return Stream.empty();

				case 1:
					return Stream.of(node);

				default:
					assert !node.isModuleNode();
					assert node.getNodeInfo().getDeclaredScopes().contains(scope);
					return node.getNodeInfo().getFiles().stream().map(JkFileSystemDependency::of).map(dep -> JkDependencyNode.ofFileDep(dep, Collections.singleton(scope)));
				}
			}).flatMap(Function.identity()).collect(Collectors.mapping(node -> {
				Path artifact = Iterables.getOnlyElement(node.getNodeInfo().getFiles());
				assert Files.exists(artifact);

				Path remap, source;
				if (node.isModuleNode()) {
					JkVersionedModule module = node.getModuleInfo().getResolvedVersionedModule();

					remap = remapName(module);
					source = repos.get(JkModuleDependency.of(module).withClassifier("sources"));
				} else {
					String name = MoreFiles.getNameWithoutExtension(artifact);
					assert "jar".equals(MoreFiles.getFileExtension(artifact));

					remap = remapName(JkVersionedModule.ofUnspecifiedVerion(JkModuleId.of("synthetic", name)));
					source = artifact.resolveSibling(name + "-sources.jar");
				}

				if (Files.notExists(remap)) {
					try {
						MoreFiles.createParentDirectories(remap);
						TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(mappings).renameInvalidLocals(true).build();

						try (OutputConsumerPath outputConsumer = new OutputConsumerPath(remap)) {
							outputConsumer.addNonClassFiles(artifact);
							remapper.readClassPath(remappedToSource.keySet().toArray(new Path[0]));
							remapper.readClassPath(classpath);
							remapper.readInputs(artifact);

							remapper.apply(outputConsumer);
						}

						remapper.finish();
					} catch (IOException e) {
						FileUtils.deleteAfterCrash(remap, e);
						throw new RuntimeException("Failed to remap jar", e);
					}
				}
				assert Files.exists(remap);

				assert !remappedToSource.containsKey(remap) || Objects.equals(remappedToSource.get(remap), source);
				remappedToSource.put(remap, source);

				return remap;
			}, Collectors.toList()));
		}
	}

	Path remapName(JkVersionedModule module) {
		StringBuilder path = new StringBuilder("net.fabricmc.remapped.");

		path.append(module.getModuleId().getGroup());
		path.append('/');
		path.append(module.getModuleId().getName());

		path.append('/');
		path.append(module.getModuleId().getName());
		if (!module.getVersion().isUnspecified()) {
			path.append('-');
			path.append(module.getVersion().getValue());
		}

		return repoRoot.resolve(path.append(".jar").toString());
	}
}
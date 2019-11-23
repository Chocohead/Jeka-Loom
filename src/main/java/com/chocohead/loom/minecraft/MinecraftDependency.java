package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;

import com.chocohead.loom.FullDependencies;
import com.chocohead.loom.FullDependency;
import com.chocohead.loom.JkPluginLoom;
import com.chocohead.loom.minecraft.MappingResolver.MappingType;
import com.chocohead.loom.minecraft.MinecraftVersions.Version;
import com.chocohead.loom.util.DownloadUtil;

public class MinecraftDependency {
	private interface VersionFactory {
		MinecraftVersion make(boolean offline);
	}

	public static class Builder {
		final Path cache;
		private final VersionFactory versionMaker;
		FullDependency mappingDependency;
		MappingType mappings;
		Supplier<Path> remapCache;
		boolean splitMerge, offline;

		public Builder(Path cache, VersionFactory versionMaker) {
			this.cache = cache;
			this.versionMaker = versionMaker;
		}

		public Builder withMappings(String dependency) {
			return withMappings(JkModuleDependency.of(dependency));
		}

		public Builder withMappings(JkDependency dependency) {
			return withMappings(FullDependency.of(dependency, JkRepo.of("https://maven.fabricmc.net"), JkJavaDepScopes.COMPILE_AND_RUNTIME));
		}

		public Builder withMappings(FullDependency dependency) {
			mappingDependency = dependency;
			mappings = null;
			return this;
		}

		public Builder withMappings(MappingType mappings) {
			this.mappings = mappings;
			mappingDependency = null;
			return this;
		}

		public Builder withRemapCache(Supplier<Path> remapCacheFactory) {
			remapCache = remapCacheFactory;
			return this;
		}

		public Builder splitMerge(boolean remapFirst) {
			splitMerge = remapFirst;
			return this;
		}

		public Builder runOffline(boolean offline) {
			this.offline = offline;
			return this;
		}

		public Builder accept(JkPluginLoom loom) {
			return withRemapCache(loom::remapCache).runOffline(loom.runOffline);
		}

		public MinecraftDependency build() {
			if (mappings == null && mappingDependency == null) {
				throw new IllegalStateException("No mappings set");
			}

			return new MinecraftDependency(versionMaker.make(offline), this);
		}
	}

	private static final String FULL_MANIFEST = "version_manifest.json";
	private static final String VERSION_MANIFEST = "minecraft-%s-info.json";

	public static Builder standard(Path cache, String version) {
		return new Builder(cache, runOffline -> {
			try {
				MinecraftVersions versions = MinecraftVersions.get(cache.resolve(FULL_MANIFEST), runOffline);

				Version mcVersion = versions.getVersion(version);
				if (mcVersion == null) {
					throw new JkException("Failed to find Minecraft version: " + version);
				}

				return mcVersion.get(cache.resolve(String.format(VERSION_MANIFEST, version)), runOffline);
			} catch (IOException e) {
				throw new UncheckedIOException("Error resolving Minecraft version: " + version, e);
			}
		});
	}

	public static Builder fromManifest(Path cache, String version, URL manifest) {
		return new Builder(cache, offline -> {
			Path manifestPath = cache.resolve(String.format(VERSION_MANIFEST, version));
			try {
				DownloadUtil.downloadIfChanged(manifest, manifestPath);

				try (Reader reader = Files.newBufferedReader(manifestPath)) {
					return MinecraftVersions.GSON.fromJson(reader, MinecraftVersion.class);
				}
			} catch (IOException e) {
				throw new UncheckedIOException("Error resolving Minecraft version: " + version, e);
			}
		});
	}

	public static Builder fromManifest(Path cache, Path manifest) {
		return new Builder(cache, offline -> {
			try (Reader reader = Files.newBufferedReader(manifest)) {
				return MinecraftVersions.GSON.fromJson(reader, MinecraftVersion.class);
			} catch (IOException e) {
				throw new UncheckedIOException("Error resolving Minecraft version at: " + manifest, e);
			}
		});
	}

	private final MinecraftResolver resolver;
	private final MappingResolver mappings;
	private final Supplier<Path> remapCache;

	public MinecraftDependency(MinecraftVersion version, Builder settings) {
		JkLog.startTask("Preparing Minecraft dependencies");
		if (version.id == null) throw new IllegalArgumentException("Minecraft version supplied has null id!");
		SpecialCases.enhanceVersion(version);

		JkLog.startTask("Fetching Minecraft jars");
		resolver = new MinecraftResolver(settings.cache.resolve(version.id), version, settings.splitMerge, settings.offline);
		JkLog.endTask();

		JkLog.startTask("Resolving mappings");
		if (settings.mappings != null) {
			mappings = new MappingResolver(settings.mappings, settings.offline);
		} else {
			mappings = new MappingResolver(resolver.cache, version.id, settings.mappingDependency, settings.offline);
		}
		JkLog.endTask();

		JkLog.startTask("Merging Minecraft jars");
		resolver.makeIntermediary(mappings.getIntermediaries());
		JkLog.endTask();

		JkLog.startTask("Preparing mappings");
		mappings.postMerge(resolver.getMerged());
		JkLog.endTask();

		JkLog.startTask("Remapping Minecraft jar");
		resolver.makeMapped(mappings.getMappingName(), mappings.getNamed());
		JkLog.endTask();

		remapCache = settings.remapCache;
		JkLog.endTask();
	}

	public FullDependencies asDependency() {
		return resolver.getLibraries().andDependencies(JkDependencySet.of()
				.and(JkFileSystemDependency.of(resolver.getMapped()))
				.and(mappings.asDependency())
				.withDefaultScopes(JkJavaDepScopes.PROVIDED));
	}

	public Path remapCache() {
		if (remapCache == null) throw new UnsupportedOperationException("No remap cache directory supplied");
		Path cache = remapCache.get().resolve(mappings.getMappingName());
		JkUtilsPath.createDirectories(cache);
		return cache;
	}

	public RemappedSystem remapper() {
		List<Path> libraries = resolver.getLibraries().withScopes(JkJavaDepScopes.SCOPES_FOR_COMPILATION).resolveToPaths();

		Path[] classpath = new Path[libraries.size() + 1];
		libraries.toArray(classpath);
		classpath[libraries.size()] = resolver.getIntermediary();

		try {
			return new RemappedSystem(remapCache(), classpath).withMappings(mappings.getNamed(), "intermediary", "named");
		} catch (IOException e) {
			throw new UncheckedIOException("Error getting Intermediary -> Named mappings", e);
		}
	}
}
package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.gson.Gson;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.JkPlugin;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import com.chocohead.loom.FullDependency;
import com.chocohead.loom.JkPluginLoom;
import com.chocohead.loom.minecraft.MinecraftVersions.Version;
import com.chocohead.loom.util.FileUtils;

@JkDoc("Adds Minecraft support to Loom")
@JkDocPluginDeps(JkPluginLoom.class)
public class JkPluginMinecraft extends JkPlugin {
	private static final String FULL_MANIFEST = "version_manifest.json";
	private static final String VERSION_MANIFEST = "minecraft-%s-info.json";

	public static final Gson GSON = new Gson();
	protected final JkPluginLoom loom = getCommands().getPlugin(JkPluginLoom.class);

	@JkDoc("The Minecraft version to use")
	public String version;
	@JkDoc("The mappings to use")
	public String yarnModule;
	/** The actual Yarn mappings to be used, will create a {@link JkModuleDependency} of {@link #yarnModule} if <code>null</code> */
	public FullDependency yarn;
	@JkDoc("Whether to remap the jars then merge (true) or merge the jars then remap (false)")
	public boolean splitMerge = false;

	protected MinecraftResolver resolver;
	protected MappingResolver mappings;

	protected JkPluginMinecraft(JkCommands commands) {
		super(commands);
	}

	@Override
	protected void init() {
		JkLog.info("Minecraft init");
	}

	@Override
	protected void activate() {
		JkLog.info("Minecraft activate");

		//We need a Minecraft version to start with
		if (version == null) throw new JkException("Minecraft version not set");

		//If the Yarn dependency isn't directly set, check for the module string
		if (yarn == null) {
			if (yarnModule == null) {
				throw new JkException("Yarn version is not set for " + version);
			} else {
				JkLog.trace("Implying Yarn dependency from provided module: " + yarnModule);
				yarn = FullDependency.of(JkModuleDependency.of(yarnModule), JkRepo.of("https://maven.fabricmc.net"), JkJavaDepScopes.COMPILE_AND_RUNTIME);
			}
		} else {
			JkLog.trace("Using supplied Yarn dependency: " + yarn);
		}

		JkLog.startTask("Preparing Minecraft dependencies");
		JkLog.startTask("Fetching Minecraft manifests");
		MinecraftVersion minecraft = resolveMinecraftVersion();
		JkLog.endTask();

		JkLog.startTask("Fetching Minecraft jars");
		resolver = new MinecraftResolver(loom.globalCache.resolve(version), minecraft, splitMerge, loom.runOffline);
		JkLog.endTask();

		JkLog.startTask("Resolving mappings");
		mappings = new MappingResolver(resolver.cache, version, yarn, loom.runOffline);
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
		JkLog.endTask();
	}

	private MinecraftVersion resolveMinecraftVersion() {
		try {
			MinecraftVersions versions = MinecraftVersions.get(loom.globalCache.resolve(FULL_MANIFEST), loom.runOffline);

			Version version = versions.getVersion(this.version);
			if (version == null) {
				throw new JkException("Failed to find Minecraft version: " + this.version);
			}

			return version.get(loom.globalCache.resolve(String.format(VERSION_MANIFEST, this.version)), loom.runOffline);
		} catch (IOException e) {
			throw new UncheckedIOException("Error resolving Minecraft version: " + version, e);
		}
	}

	public Path remapCache() {
		Path cache = loom.remapCache().resolve(mappings.getMappingName());
		JkUtilsPath.createDirectories(cache);
		return cache;
	}

	public JkDependency modCompile(FullDependency dependency) {
		Map<Path, Path> versions;
		if (dependency.isModule()) {
			versions = Collections.singletonMap(Iterables.getOnlyElement(dependency.resolveToPaths()),
					remapName(dependency.getModuleID().withVersion(dependency.getResolvedVersion())));
		} else {
			versions = dependency.resolveToPaths().stream().collect(Collectors.toMap(Function.identity(), origin -> {
				String name = MoreFiles.getNameWithoutExtension(origin);
				return remapName(JkVersionedModule.ofUnspecifiedVerion(JkModuleId.of("net.fabricmc.synthetic", name)));
			}));
		}

		return JkComputedDependency.of(() -> {
			for (Entry<Path, Path> entry : versions.entrySet()) {
				Path jar = entry.getKey();
				Path output = entry.getValue();

				if (Files.exists(output)) return; //Nothing to do (probably)

				try {
					TinyRemapper remapper = TinyRemapper.newRemapper()
							.withMappings(mappings.getNamed().create("intermediary", "named"))
							.renameInvalidLocals(true)
							.build();

					try (OutputConsumerPath outputConsumer = new OutputConsumerPath(output)) {
						outputConsumer.addNonClassFiles(jar);
						remapper.readClassPath(resolver.getLibraries().withScopes(JkJavaDepScopes.SCOPES_FOR_COMPILATION).resolveToPaths().toArray(new Path[0]));
						remapper.readClassPath(resolver.getIntermediary());
						remapper.readInputs(jar);

						remapper.apply(outputConsumer);
					}

					remapper.finish();
				} catch (IOException e) {
					FileUtils.deleteAfterCrash(output, e);
					throw new RuntimeException("Failed to remap jar", e);
				}
			}
		}, versions.values().toArray(new Path[0]));
	}

	Path remapName(JkVersionedModule module) {
		StringBuilder path = new StringBuilder(module.getModuleId().getDotedName());

		if (!module.getVersion().isUnspecified()) {
			path.append('-');
			path.append(module.getVersion().getValue());
		}

		return remapCache().resolve(path.append(".jar").toString());
	}
}
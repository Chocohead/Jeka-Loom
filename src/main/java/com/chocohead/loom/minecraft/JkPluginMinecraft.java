package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.google.gson.Gson;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.JkPlugin;

import com.chocohead.loom.JkPluginLoom;
import com.chocohead.loom.minecraft.MinecraftVersions.Version;

@JkDoc("Adds Minecraft support to Loom")
@JkDocPluginDeps(JkPluginLoom.class)
public class JkPluginMinecraft extends JkPlugin {
	public static final String FULL_MANIFEST = "version_manifest.json";
	public static final String VERSION_MANIFEST = "minecraft-%s-info.json";

	public static final Gson GSON = new Gson();
	protected final JkPluginLoom loom = getCommands().getPlugin(JkPluginLoom.class);

	@JkDoc("The Minecraft version to use")
	public String version;
	@JkDoc("The mappings to use")
	public String yarnModule;
	/** The actual Yarn mappings to be used, will create a {@link JkModuleDependency} of {@link #yarnModule} if <code>null</code> */
	public JkDependency yarn;
	@JkDoc("Whether to remap the jars then merge (true) or merge the jars then remap (false)")
	public boolean splitMerge = false;

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
				yarn = JkModuleDependency.of(yarnModule);
			}
		} else {
			JkLog.trace("Using supplied Yarn dependency: " + yarn);
		}

		JkLog.startTask("Fetching Minecraft manifests");
		MinecraftVersion minecraft = resolveMinecraftVersion();
		JkLog.endTask();

		JkLog.startTask("Fetching and merging Minecraft jars");
		MinecraftResolver resolver = new MinecraftResolver(loom.globalCache.resolve(version), minecraft, splitMerge, loom.runOffline);
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
}
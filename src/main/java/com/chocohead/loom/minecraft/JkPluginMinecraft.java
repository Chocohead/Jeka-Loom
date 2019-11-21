package com.chocohead.loom.minecraft;

import java.net.URL;
import java.nio.file.Path;

import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.JkPlugin;

import com.chocohead.loom.JkPluginLoom;
import com.chocohead.loom.minecraft.MinecraftDependency.Builder;

@JkDoc("Adds Minecraft support to Loom")
@JkDocPluginDeps(JkPluginLoom.class)
public class JkPluginMinecraft extends JkPlugin {
	protected final JkPluginLoom loom = getCommands().getPlugin(JkPluginLoom.class);

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
	}

	public Builder standard(String version) {
		return MinecraftDependency.standard(loom.globalCache, version).accept(loom);
	}

	public Builder fromManifest(String version, URL manifest) {
		return MinecraftDependency.fromManifest(loom.globalCache, version, manifest).accept(loom);
	}

	public Builder fromManifest(Path manifest) {
		return MinecraftDependency.fromManifest(loom.globalCache, manifest).accept(loom);
	}
}
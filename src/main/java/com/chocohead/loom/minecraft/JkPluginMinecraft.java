package com.chocohead.loom.minecraft;

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

@JkDoc("Adds Minecraft support to Loom")
@JkDocPluginDeps(JkPluginLoom.class)
public class JkPluginMinecraft extends JkPlugin {
	public static final Gson GSON = new Gson();
	protected final JkPluginLoom loom = getCommands().getPlugin(JkPluginLoom.class);

	@JkDoc("The Minecraft version to use")
	public String version;
	@JkDoc("The mappings to use")
	public String yarnModule;

	public JkDependency yarn;

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


	}
}
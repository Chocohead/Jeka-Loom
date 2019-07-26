package com.chocohead.loom;

import java.nio.file.Path;

import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkConstants;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.JkPlugin;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

@JkDocPluginDeps(JkPluginJava.class)
public class JkPluginLoom extends JkPlugin {
	/** Cache for persistently storing anything related to the project */
	public final Path globalCache, projectCache;

	@JkDoc("Whether to avoid accessing remote resources in favour of local caches.")
	public boolean runOffline = false;

	protected JkPluginLoom(JkCommands commands) {
		super(commands);

		globalCache = JkLocator.getJekaUserHomeDir().resolve("cache/fabric-loom");
		JkUtilsPath.createDirectories(globalCache);

		projectCache = commands.getBaseDir().resolve(JkConstants.JEKA_DIR).resolve("fabric-loom");
		JkUtilsPath.createDirectories(projectCache);
	}

	@Override
	protected void init() {
		JkLog.info("Loom init");
	}

	@Override
	protected void activate() {
		JkLog.info("Loom activate");
	}
}
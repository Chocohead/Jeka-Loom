package com.chocohead.loom;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkScopedDependency;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsIO;
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

	protected final JkDependencyResolver depResolver = getCommands().getRunDependencyResolver();

	@JkDoc("The url to display content.")
	public String url = "https://www.google.com/";

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

	public JkResolveResult resolveDependency(JkDependency dependency) {
		return depResolver.resolve(JkDependencySet.of(Collections.singletonList(JkScopedDependency.of(dependency)))).assertNoError();
	}

	@JkDoc("Display source cotent of the url option on the console.")
	public void displayContent() throws MalformedURLException {
		String content = JkUtilsIO.read(new URL(url));
		System.out.println(content);
	}

}
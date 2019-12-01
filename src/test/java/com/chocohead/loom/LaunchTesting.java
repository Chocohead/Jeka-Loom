package com.chocohead.loom;

import java.nio.file.Path;
import java.util.List;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkInit;

import com.chocohead.loom.minecraft.EnigmaLiveDependency;
import com.chocohead.loom.minecraft.JkPluginMinecraft;

/** Rough example for how things can be interacted with, designed for debugging */
public class LaunchTesting extends JkCommands {
	public static void main(String[] args) {
		JkPluginMinecraft mcPlugin = JkInit.instanceOf(LaunchTesting.class, args).getPlugin(JkPluginMinecraft.class);

		JkDependency mappings = EnigmaLiveDependency.createForBranch("Blayyke/yarn", "1.2.5");
		FullDependencies minecraftDep = mcPlugin.standard("1.2.5").withMappings(mappings).splitMerge(true).build().asDependency();

		List<Path> classpath = minecraftDep.resolveToPaths();
		System.out.println("Classpath resolved to:");
		for (Path dependency : classpath) {
			System.out.print('\t');
			System.out.println(dependency);
		}
	}
}
package com.chocohead.loom;

import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkInit;

public class LoomCommands extends JkCommands {
	public static void main(String[] args) throws Exception {
		LoomCommands commands = JkInit.instanceOf(LoomCommands.class, args);
		commands.clean();
	}

	public void genSources() {

	}

	public void build() {

	}
}
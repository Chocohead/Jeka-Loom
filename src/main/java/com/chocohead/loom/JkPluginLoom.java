package com.chocohead.loom;

import java.net.MalformedURLException;
import java.net.URL;

import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkDocPluginDeps;
import dev.jeka.core.tool.JkPlugin;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

@JkDocPluginDeps(JkPluginJava.class)
public class JkPluginLoom extends JkPlugin {
	@JkDoc("The url to display content.")
	public String url = "https://www.google.com/";

	protected JkPluginLoom(JkCommands commands) {
		super(commands);
	}

	@JkDoc("Display source cotent of the url option on the console.")
	public void displayContent() throws MalformedURLException {
		String content = JkUtilsIO.read(new URL(url));
		System.out.println(content);
	}

}
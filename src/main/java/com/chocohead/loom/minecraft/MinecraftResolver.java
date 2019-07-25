package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.util.Checksum;
import com.chocohead.loom.util.DownloadUtil;

public class MinecraftResolver {
	private static final String CLIENT_JAR = "client";
	private static final String SERVER_JAR = "server";
	private static final String MERGED_JAR = "merged";

	protected final MinecraftVersion version;
	protected final boolean split;

	protected final Path cache;
	protected final Path clientJar, serverJar, mergedJar;
	protected Path intermediaryJar, mappedJar;

	public MinecraftResolver(Path cache, MinecraftVersion version, boolean split, boolean offline) {
		this.version = version;
		this.cache = cache;
		this.split = split;

		clientJar = cache.resolve(CLIENT_JAR);
		serverJar = cache.resolve(SERVER_JAR);
		mergedJar = cache.resolve(MERGED_JAR);

		if (offline) {
			if (Files.exists(clientJar) && Files.exists(serverJar)) {
				JkLog.trace("Found client and server jars, presuming up-to-date");
			} else if (Files.exists(mergedJar)) {
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				JkLog.warn("Missing game jar but merged jar present, things might end badly");
			} else {
				throw new JkException("Missing jar(s); Client: " + Files.exists(clientJar) + ", Server: " + Files.exists(serverJar));
			}
		} else {
			downloadIfNeeded("client", clientJar);
			downloadIfNeeded("server", serverJar);
		}
	}

	private void downloadIfNeeded(String jarName, Path jar) {
		if (Files.notExists(jar) || !Checksum.equals(jar, version.downloads.get(jarName).hash)) {
			try {
				JkLog.trace("Downloading Minecraft " + version.id + ' ' + jarName + " jar");
				DownloadUtil.downloadIfChanged(version.downloads.get(jarName).url, jar);
			} catch (IOException e) {
				throw new RuntimeException("Unexpected error downloading " + jarName + " jar", e);
			}
		}
	}

	public Path getClient() {
		return clientJar;
	}

	public Path getServer() {
		return serverJar;
	}

	public Path getMerged() {
		return mergedJar;
	}

	public void makeIntermediary(MappingResolver mappings) {
		/*try (JarMerger jarMerger = new JarMerger(MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}*/
	}

	public Path getIntermediary() {
		return split ? mergedJar : intermediaryJar;
	}

	public void makeMapped(MappingResolver mappings) {

	}

	public Path getMapped() {
		return mappedJar;
	}
}
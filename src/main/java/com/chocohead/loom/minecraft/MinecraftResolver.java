package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsPath;

import com.chocohead.loom.minecraft.MappingResolver.MappingFactory;
import com.chocohead.loom.util.Checksum;
import com.chocohead.loom.util.DownloadUtil;

import net.fabricmc.stitch.merge.JarMerger;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class MinecraftResolver {
	private static final String CLIENT_JAR = "client.jar";
	private static final String SERVER_JAR = "server.jar";
	private static final String MERGED_JAR = "merged.jar";
	private static final String CLIENT_INTER_JAR = "client-intermediary.jar";
	private static final String SERVER_INTER_JAR = "server-intermediary.jar";
	private static final String MERGED_INTER_JAR = "merged-intermediary.jar";
	private static final String MAPPED_JAR = "merged-%s.jar";

	protected final MinecraftVersion version;
	protected final JkDependencySet libraries;
	protected final boolean split;

	protected final Path cache;
	protected final Path clientJar, serverJar, mergedJar;
	protected Path intermediaryJar, mappedJar;

	public MinecraftResolver(Path cache, MinecraftVersion version, boolean split, boolean offline) {
		this.version = version;
		this.cache = cache;
		this.split = split;

		JkUtilsPath.createDirectories(cache);
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

		libraries = LibraryResolver.go(version.libraries);
	}

	private void downloadIfNeeded(String jarName, Path jar) {
		if (Files.notExists(jar) || !Checksum.equals(jar, version.downloads.get(jarName).hash)) {
			try {
				JkLog.trace("Downloading Minecraft " + version.id + ' ' + jarName + " jar");
				DownloadUtil.downloadIfChanged(version.downloads.get(jarName).url, jar);
			} catch (IOException e) {
				//Shouldn't need to delete the jar here, DownloadUtil will do it if it reached that stage
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

	public void makeIntermediary(MappingFactory mappings) {
		if (split) {
			Path clientRemapped = cache.resolve(CLIENT_INTER_JAR);
			remapIfMissing(clientJar, clientRemapped, mappings, "client", "intermediary");

			Path serverRemapped = cache.resolve(SERVER_INTER_JAR);
			remapIfMissing(serverJar, serverRemapped, mappings, "server", "intermediary");

			if (Files.notExists(mergedJar)) {
				try (JarMerger jarMerger = new JarMerger(clientRemapped.toFile(), serverRemapped.toFile(), mergedJar.toFile())) {
					jarMerger.enableSyntheticParamsOffset();
					jarMerger.merge();
				} catch (IOException e) {
					DownloadUtil.deleteAfterCrash(mergedJar, e);
					throw new RuntimeException("Error merging client and server jars", e);
				}
			}
		} else {
			if (Files.notExists(mergedJar)) {//Primitive APIs needing Files rather than Paths
				try (JarMerger jarMerger = new JarMerger(clientJar.toFile(), serverJar.toFile(), mergedJar.toFile())) {
					jarMerger.enableSyntheticParamsOffset();
					jarMerger.merge();
				} catch (IOException e) {
					DownloadUtil.deleteAfterCrash(mergedJar, e);
					throw new RuntimeException("Error merging client and server jars", e);
				}
			}

			remapIfMissing(mergedJar, intermediaryJar = cache.resolve(MERGED_INTER_JAR), mappings, "official", "intermediary");
		}
	}

	private void remapIfMissing(Path jar, Path output, MappingFactory mappings, String from, String to) {
		if (Files.exists(output)) return; //Nothing to do (probably)

		try {
			TinyRemapper remapper = TinyRemapper.newRemapper()
					.withMappings(mappings.create(from, to))
					.renameInvalidLocals(true)
					.rebuildSourceFilenames(true)
					.build();

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath(jar)) {
				outputConsumer.addNonClassFiles(jar);
				remapper.readClassPath(LibraryResolver.resolve(libraries).getEntries().toArray(new Path[0]));
				remapper.readInputs(jar);

				remapper.apply(outputConsumer);
			}

			remapper.finish();
		} catch (IOException e) {
			DownloadUtil.deleteAfterCrash(output, e);
			throw new RuntimeException("Failed to remap jar", e);
		}
	}

	public Path getIntermediary() {
		return split ? mergedJar : intermediaryJar;
	}

	public void makeMapped(MappingFactory mappings) {

	}

	public Path getMapped() {
		return mappedJar;
	}
}
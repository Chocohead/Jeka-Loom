package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;

import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.util.DownloadUtil;

public class MinecraftVersions {
	public static class Version {
		String id;
		private URL url;

		public String getID() {
			return id;
		}

		public URL getURL() {
			return url;
		}

		public MinecraftVersion get(Path from, boolean offline) throws IOException {
			if (offline) {
				if (Files.exists(from)) {
					//If the manifest exists already we'll presume that's good enough
					JkLog.trace("Found Minecraft " + id + " manifest, presuming up-to-date");
				} else {
					//If we don't have the manifests then there's nothing more we can do
					throw new JkException("Minecraft " + id + " manifest not found at " + from);
				}
			} else {
				JkLog.trace("Downloading Minecraft " + id + " manifest");
				DownloadUtil.downloadIfChanged(url, from);
			}

			try (Reader reader = new InputStreamReader(Files.newInputStream(from), StandardCharsets.UTF_8)) {
				return GSON.fromJson(reader, MinecraftVersion.class);
			}
		}
	}

	static final Gson GSON = new Gson();
	private List<Version> versions = new ArrayList<>();

	public static MinecraftVersions get(Path from, boolean offline) throws IOException {
		if (offline) {
			if (Files.exists(from)) {
				//If the manifests exist already we'll presume that's good enough
				JkLog.trace("Found version manifests, presuming up-to-date");
			} else {
				//If we don't have the manifests then there's nothing more we can do
				throw new JkException("Version manifests not found at " + from);
			}
		} else {
			JkLog.trace("Downloading version manifests");
			DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), from);
		}

		try (Reader reader = new InputStreamReader(Files.newInputStream(from), StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, MinecraftVersions.class);
		}
	}

	public List<Version> getVersions() {
		return Collections.unmodifiableList(versions);
	}

	public Version getVersion(String id) {
		for (Version version : versions) {
			if (version.id.equals(id)) {
				return version;
			}
		}

		return null;
	}
}
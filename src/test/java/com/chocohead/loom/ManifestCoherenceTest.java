package com.chocohead.loom;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;

import com.chocohead.loom.minecraft.MinecraftVersion;
import com.chocohead.loom.minecraft.MinecraftVersions;
import com.chocohead.loom.minecraft.MinecraftVersions.Version;

public class ManifestCoherenceTest {
	public static void main(String[] args) throws IOException {
		Path manifest = Files.createTempFile("minecraft-version-manifest", ".json");
		Files.deleteIfExists(manifest); //Get out of the way for DownloadUtil
		Gson GSON = new Gson();

		for (Version versionManifest : MinecraftVersions.get(manifest, false).getVersions()) {
			try (Reader reader = new InputStreamReader(versionManifest.getURL().openStream(), StandardCharsets.UTF_8)) {
				MinecraftVersion version = GSON.fromJson(reader, MinecraftVersion.class);

				if (!version.downloads.containsKey("client")) {//Que?
					throw new IllegalStateException(version.id + " is missing a client download link?!");
				}

				if (!version.downloads.containsKey("server")) {
					if (hasAlternateDownload(version.id)) {
						System.out.println(version.id + " is missing a server download link");
					} else {
						System.err.println(version.id + " is missing any server download links");
					}
				}
			}
		}
	}

	public static boolean hasAlternateDownload(String version) {
		try {
			HttpURLConnection con = (HttpURLConnection) new URL("https://betacraft.ovh/server-archive/minecraft/" + UrlEscapers.urlPathSegmentEscaper().escape(version) + ".jar").openConnection();
			con.setRequestMethod("HEAD");
			return con.getResponseCode() == HttpURLConnection.HTTP_OK;
		} catch (IOException e) {
			//e.printStackTrace();
			return false;
		}
	}
}
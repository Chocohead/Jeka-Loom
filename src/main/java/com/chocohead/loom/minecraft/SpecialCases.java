package com.chocohead.loom.minecraft;

import java.net.MalformedURLException;
import java.net.URL;

import com.google.common.net.UrlEscapers;

import dev.jeka.core.api.system.JkException;

import com.chocohead.loom.minecraft.MinecraftVersion.Download;

/** It's not hard coded, it's just inflexible */
class SpecialCases {
	static void enhanceVersion(MinecraftVersion version) {
		fixMissingServer(version);
	}

	private static void fixMissingServer(MinecraftVersion version) {
		if (version.downloads.containsKey("server")) return;

		String serverVersion;
		switch (version.id) {
		case "1.2.4":
		case "1.2.3":
		case "1.2.2":
		case "1.2.1":
		case "1.1":
		case "b1.8.1":
		case "b1.8":
		case "b1.7.3":
		case "b1.7.2":
		case "b1.7":
		case "b1.6.6":
		case "b1.6.5":
		case "b1.6.4":
		case "b1.6.3":
		case "b1.6.2":
		case "b1.6.1":
		case "b1.5_01":
		case "b1.5":
		case "b1.4_01":
		case "b1.4":
		case "b1.2_01":
		case "b1.2":
		case "b1.1_02":
		case "b1.1_01":
			serverVersion = version.id;
			break;

		case "1.0": //Expects 1.0.0, although servers had an additional hot patch version too
			serverVersion = "1.0.1";
			break;

		case "b1.6":
			serverVersion = "b1.6-2";
			break;

		case "b1.3_01": //The server didn't seem to get a hot patch despite the client doing so
		case "b1.3b":
			serverVersion = "b1.3-2";
			break;

		case "b1.2_02": //The server didn't seem to get a second hot patch despite the client doing so
			serverVersion = "b1.2_01";
			break;

		case "b1.0.2": //The server didn't seem to get a version bump despite the client doing so
		case "b1.0_01":
			serverVersion = "b1.0-2";
			break;

		case "b1.0":
			serverVersion = "b1.0-1";
			break;

		case "a1.2.6":
			serverVersion = "0.2.8";
			break;

		case "a1.2.5":
			serverVersion = "0.2.7";
			break;

		case "a1.2.4_01":
			serverVersion = "0.2.6_02";
			break;

		case "a1.2.3_05":
			serverVersion = "0.2.6";
			break;

		case "a1.2.3_04":
			serverVersion = "0.2.5_02";
			break;

		case "a1.2.3_02":
			serverVersion = "0.2.5_01";
			break;

		case "a1.2.3_01":
			serverVersion = "0.2.5-2";
			break;

		case "a1.2.3":
			serverVersion = "0.2.5-1";
			break;

		case "a1.2.2b": //The server didn't seem to get a hot patch despite the client doing so
		case "a1.2.2a":
			serverVersion = "0.2.4";
			break;

		case "a1.2.1_01": //The server didn't seem to get a hot patch despite the client doing so
		case "a1.2.1":
			serverVersion = "0.2.3";
			break;

		case "a1.2.0_02": //The server didn't seem to get a hot patch despite the client doing so
		case "a1.2.0_01":
			serverVersion = "0.2.2_01";
			break;

		case "a1.2.0":
			serverVersion = "0.2.2";
			break;

		case "a1.1.2_01": //The server didn't seem to get a hot patch despite the client doing so
		case "a1.1.2":
			serverVersion = "0.2.1";
			break;

		case "a1.1.0":
			serverVersion = "0.2.0_01";
			break;

		case "a1.0.17_04":
		case "a1.0.17_02":
			serverVersion = "0.1.4";
			break;

		case "a1.0.16":
			serverVersion = "0.1.2_01";
			break;

		case "a1.0.15":
			serverVersion = "0.1.0";
			break;

		case "a1.0.14":
		case "a1.0.11":
		case "a1.0.5_01":
		case "a1.0.4":
		case "c0.30_01c":
		case "c0.0.13a":
		case "c0.0.13a_03":
		case "c0.0.11a":
			throw new JkException("Minecraft version does't have an (obvious) server equivalent");

		case "inf-20100618":
		case "rd-161348":
		case "rd-160052":
		case "rd-20090515":
		case "rd-132328":
		case "rd-132211":
			throw new JkException("Loom doesn't (currently) support client only use");

		default:
			throw new JkException("Unexpected Minecraft version " + version.id);
		}

		Download server = new Download();
		try {
			server.url = new URL("https://betacraft.ovh/server-archive/minecraft/" + serverVersion + ".jar");
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Unable to create server download URL?", e);
		}
		version.downloads.put("server", server);
	}

	static String intermediaries(String version) {
		switch (version) {
		case "1.2.5":
			return "https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5 Merge.tiny".replace(" ", "%20");

		case "b1.7.3":
			return "https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta 1.7.3 Merge.tiny".replace(" ", "%20");

		default:
			return "https://github.com/FabricMC/intermediary/raw/master/mappings/" + UrlEscapers.urlPathSegmentEscaper().escape(version) + ".tiny";
		}
	}
}
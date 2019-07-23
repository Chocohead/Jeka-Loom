package com.chocohead.loom.minecraft;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class MinecraftVersion {
	public static class Library {
		public static class Rule {
			public enum Action {
				@SerializedName("allow")
				ALLOW,
				@SerializedName("disallow")
				DISALLOW;
			}

			public static class OS {
				public OperatingSystem name;
				public String version;
			}

			public Action action = Action.ALLOW;
			public OS os;
		}

		public static class Extraction {
			public List<String> exclude = Collections.emptyList();

			public boolean isSpecial() {
				return !exclude.isEmpty();
			}
		}

		public static class Downloads {
			public Download artifact;
			public Map<String, Download> classifiers = Collections.emptyMap();

			public Download getDownload() {
				return getDownload(null);
			}

			public boolean isSpecial() {
				return !classifiers.isEmpty();
			}

			public Download getDownload(String classifier) {
				return classifier == null ? artifact : classifiers.get(classifier);
			}
		}

		public String name;
		public Rule[] rules;
		public Map<OperatingSystem, String> natives;
		public Extraction extract;
		public Downloads downloads;
	}

	public static class Download {
		public URL url;
		@SerializedName("sha1")
		public String hash;
		public int size;
	}

	public static class AssetIndex {
		public String id;
		public URL url;
		@SerializedName("sha1")
		public String hash;

		public String getId(String version) {
			return id.equals(version) ? version : version + '-' + id;
		}
	}

	public List<Library> libraries;
	public Map<String, Download> downloads;
	public AssetIndex assetIndex;
}
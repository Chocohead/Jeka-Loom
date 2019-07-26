package com.chocohead.loom.minecraft;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.annotations.SerializedName;

import com.chocohead.loom.minecraft.MinecraftVersion.Library.Rule.Action;

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
				public String arch;

				public boolean doesMatch() {
					if (name != null && name != OperatingSystem.get()) {
						return false;
					}

					if (version != null && !Pattern.matches(version, System.getProperty("os.version"))) {
						return false;
					}

					if (arch != null && !Pattern.matches(arch, System.getProperty("os.arch"))) {
						return false;
					}

					return true;
				}
			}

			public Action action = Action.ALLOW;
			public OS os;

			public boolean doesRuleApply() {
				return os == null || os.doesMatch();
			}
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

		public boolean shouldUse() {
			if (rules == null || rules.length == 0) return true;
			boolean out = false;

			for (Rule rule : rules) {
				if (rule.doesRuleApply()) {
					out = rule.action == Action.ALLOW;
				}
			}

			return out;
		}
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

	public String id;
	public List<Library> libraries;
	public Map<String, Download> downloads;
	public AssetIndex assetIndex;
}
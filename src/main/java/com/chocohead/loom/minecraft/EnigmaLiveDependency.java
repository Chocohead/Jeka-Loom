package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.utils.JkUtilsPath;

import com.chocohead.loom.util.DownloadUtil;

public class EnigmaLiveDependency {
	private static final String DEFAULT_REPO = "FabricMC/yarn";
	private static final String DOWNLOAD_URL = "https://api.github.com/repos/%s/zipball/%s";

	public static JkDependency createForBranch(String branch) {
		return createForBranch(DEFAULT_REPO, branch);
	}

	public static JkDependency createForBranch(String repo, String branch) {
		return createFrom(repo + '/' + branch, String.format(DOWNLOAD_URL, repo, branch));
	}

	public static JkDependency create(String commit) {
		return create(DEFAULT_REPO, commit);
	}

	public static JkDependency create(String repo, String commit) {
		return createFrom(repo + '/' + commit, String.format(DOWNLOAD_URL, repo, commit));
	}

	private static JkDependency createFrom(String name, String origin) {
		Path cache = JkLocator.getJekaUserHomeDir().resolve("cache/fabric-loom/github-pulls");
		Path destination = cache.resolve(name + ".zip");
		JkUtilsPath.createDirectories(destination.getParent());

		return JkComputedDependency.of(() -> {
			assert Files.notExists(destination);

			try {
				DownloadUtil.downloadIfChanged(new URL(origin), destination);
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("Invalid origin URL: " + origin, e);
			} catch (IOException e) {
				throw new RuntimeException("Unable to download " + name + " repo from " + origin, e);
			}
		}, destination);
	}
}
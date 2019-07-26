package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiPredicate;

import com.chocohead.loom.FullDependency;

import net.fabricmc.tinyremapper.IMappingProvider;

public class MappingResolver {
	/** An {@link IOException} throwing {@link BiPredicate}{@code <}String, String, IMappingProvider{@code >} */
	public interface MappingFactory {
		IMappingProvider create(String fromMapping, String toMapping) throws IOException;
	}

	public MappingResolver(Path cache, FullDependency yarn, boolean offline) {

	}

	public MappingFactory getIntermediaries() {
		return null;
	}

	public void postMerge(Path mergedJar) {

	}

	public MappingFactory getNamed() {
		return null;
	}
}
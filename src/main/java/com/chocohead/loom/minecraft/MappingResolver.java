package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.Iterables;

import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.FullDependency;

import net.fabricmc.tinyremapper.IMappingProvider;

public class MappingResolver {
	/** An {@link IOException} throwing {@link BiPredicate}{@code <}String, String, IMappingProvider{@code >} */
	public interface MappingFactory {
		IMappingProvider create(String fromMapping, String toMapping) throws IOException;
	}

	public enum MappingType {
		TINY {
			@Override
			public boolean isMissingFiles(Path cache) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void populateCache(Path cache, Path mappings, boolean offline) {
				// TODO Auto-generated method stub

			}

			@Override
			public MappingFactory makeIntermediaryMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void enhanceMappings(Path cache, Path mergedJar) {
				// TODO Auto-generated method stub

			}

			@Override
			public MappingFactory makeNamedMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}
		},
		TINY_GZ {
			@Override
			public boolean isMissingFiles(Path cache) {
				return TINY.isMissingFiles(cache);
			}

			@Override
			public void populateCache(Path cache, Path mappings, boolean offline) {
				// TODO Auto-generated method stub

			}

			@Override
			public MappingFactory makeIntermediaryMapper(Path cache) {
				return TINY.makeIntermediaryMapper(cache);
			}

			@Override
			public void enhanceMappings(Path cache, Path mergedJar) {
				TINY.enhanceMappings(cache, mergedJar);
			}

			@Override
			public MappingFactory makeNamedMapper(Path cache) {
				return TINY.makeNamedMapper(cache);
			}
		},
		ENIGMA {
			@Override
			public boolean isMissingFiles(Path cache) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void populateCache(Path cache, Path mappings, boolean offline) {
				// TODO Auto-generated method stub

			}

			@Override
			public MappingFactory makeIntermediaryMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void enhanceMappings(Path cache, Path mergedJar) {
				// TODO Auto-generated method stub

			}

			@Override
			public MappingFactory makeNamedMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		public abstract boolean isMissingFiles(Path cache);

		public abstract void populateCache(Path cache, Path mappings, boolean offline);

		public abstract MappingFactory makeIntermediaryMapper(Path cache);

		public abstract void enhanceMappings(Path cache, Path mergedJar);

		public abstract MappingFactory makeNamedMapper(Path cache);
	}

	protected final Path cache;
	protected final MappingType type;
	protected MappingFactory intermediaryMapper, namedMapper;

	public MappingResolver(Path cache, FullDependency yarn, boolean offline) {
		this.cache = cache;

		List<Path> paths = yarn.resolveToPaths();
		System.out.println(paths);

		Path origin;
		switch (paths.size()) {
		case 0: //Didn't get anything back (resolution should have failed with an ISE already if it couldn't find anything)
			throw new JkException("Empty mapping dependency supplied, found no files from " + yarn.getDependencies());

		case 1:
			origin = Iterables.getOnlyElement(paths);
			break;

		default: //We'll cross this bridge if it is ever come to, can't think of any obvious situations where this could happen
			throw new JkException("Ambigous mapping dependency supplied, expected one file but found " + paths.size() + ": " + paths);
		}

		JkLog.trace("Found mapping dependency at " + origin);
		switch (FilenameUtils.getExtension(origin.getFileName().toString())) {
		case "zip":
			type = MappingType.ENIGMA;
			break;

		case "gz":
			type = MappingType.TINY_GZ;
			break;

		case "jar":
			type = MappingType.TINY;
			break;

		default:
			throw new JkException("Unexpected mappings base type: " + FilenameUtils.getExtension(origin.getFileName().toString()) + " (from " + origin + ')');
		}
	}

	public MappingFactory getIntermediaries() {
		if (intermediaryMapper == null) {
			intermediaryMapper = type.makeIntermediaryMapper(cache);
		}

		return intermediaryMapper;
	}

	public void postMerge(Path mergedJar) {
		type.enhanceMappings(cache, mergedJar);
	}

	public MappingFactory getNamed() {
		if (namedMapper == null) {
			namedMapper = type.makeNamedMapper(cache);
		}

		return namedMapper;
	}
}
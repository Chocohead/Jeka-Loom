package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.BiPredicate;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.Iterables;

import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.FullDependency;

import net.fabricmc.tinyremapper.IMappingProvider;

public class MappingResolver {
	/** An {@link IOException} throwing {@link BiPredicate}{@code <}String, String, IMappingProvider{@code >} */
	public interface MappingFactory {
		IMappingProvider create(String fromMapping, String toMapping) throws IOException;
	}

	public static final class Mappings {
		public final Path mappings;
		public final JkVersionedModule version;
		private final String minecraft;
		private final String mappingVersion, mappingMC;

		public Mappings(Path mappings, JkVersionedModule version, String minecraft) {
			this.mappings = mappings;
			this.version = version;
			this.minecraft = minecraft;

			if (hasVersion()) {
				String mappingVersion = getVersion();

				if (mappingVersion.contains("+build.")) {
					mappingMC = mappingVersion.substring(0, mappingVersion.lastIndexOf('+'));
					this.mappingVersion = mappingVersion.substring(mappingVersion.lastIndexOf('.') + 1);
				} else {
					int split = mappingVersion.lastIndexOf(mappingVersion.indexOf('-') > 0 ? '-' : '.');
					mappingMC = mappingVersion.substring(0, split++);
					this.mappingVersion = mappingVersion.substring(split);
				}

				if (minecraft != null && !mappingMC.equals(minecraft)) {
					//Not strictly a problem, but is very likely unintentional given the potential lack of intermediaries for some things
					JkLog.warn("Running with Minecraft " + minecraft + ", but mappings are designed for " + mappingMC);
				}
			} else {
				mappingVersion = mappingMC = null;
			}
		}

		public String getName() {
			return version.getModuleId().getDotedName();
		}

		public boolean hasVersion() {
			return !version.getVersion().isUnspecified();
		}

		public String getFullVersion() {
			assert hasVersion();
			return version.getVersion().getValue();
		}

		public String getVersion() {
			assert hasVersion();
			return mappingVersion;
		}

		public String getMC() {
			return minecraft;
		}

		public String getMappingMC() {
			assert hasVersion();
			return mappingMC;
		}

		public FileTime creationTime() {
			try {
				return Files.getLastModifiedTime(mappings);
			} catch (IOException e) {
				throw new UncheckedIOException("Error getting last modification time of mappings", e);
			}
		}

		@Override
		public String toString() {
			return version.toString() + '@' + mappings;
		}
	}

	public enum MappingType {
		TINY {
			private boolean missingOrOld(Path path, FileTime creation) {
				if (Files.notExists(path)) return true;

				try {
					return Files.getLastModifiedTime(path).compareTo(creation) < 0;
				} catch (IOException e) {
					throw new UncheckedIOException("Error getting last modification time of path", e);
				}
			}

			@Override
			public boolean isMissingFiles(Path cache, Mappings mappings) {
				if (mappings.hasVersion()) {
					String mapped = mappings.getName() + "-tiny-" + mappings.getVersion(); //Rely on having a concrete version to determine whether the file is out of date
					return Files.notExists(cache.resolve(mapped + "-base.jar")) /*|| Files.notExists(cache.resolve(mapped + ".jar"))*/;
				} else {
					String mapped = mappings.getName() + "-tiny";
					return missingOrOld(cache.resolve(mapped + "-base.jar"), mappings.creationTime()) /*|| missingOrOld(cache.resolve(mapped + ".jar"), mappings.creationTime())*/;
				}
			}

			@Override
			public void populateCache(Path cache, Mappings mappings, boolean offline) {
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
			public boolean isMissingFiles(Path cache, Mappings mappings) {
				return TINY.isMissingFiles(cache, mappings);
			}

			@Override
			public void populateCache(Path cache, Mappings mappings, boolean offline) {
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
			public boolean isMissingFiles(Path cache, Mappings mappings) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public void populateCache(Path cache, Mappings mappings, boolean offline) {
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

		public abstract boolean isMissingFiles(Path cache, Mappings mappings);

		public abstract void populateCache(Path cache, Mappings mappings, boolean offline);

		public abstract MappingFactory makeIntermediaryMapper(Path cache);

		public abstract void enhanceMappings(Path cache, Path mergedJar);

		public abstract MappingFactory makeNamedMapper(Path cache);
	}

	protected final Path cache;
	protected final MappingType type;
	protected MappingFactory intermediaryMapper, namedMapper;

	public MappingResolver(Path cache, String minecraft, FullDependency yarn, boolean offline) {
		this.cache = cache;

		JkResolveResult result = yarn.resolve().assertNoError();
		List<Path> paths = result.getFiles().getEntries();
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

		JkVersionedModule version;
		if (yarn.isModule()) {
			JkModuleId module = yarn.getModuleID();
			version = module.withVersion(result.getVersionOf(module));
		} else {
			version = JkVersionedModule.ofUnspecifiedVerion(JkModuleId.of("net.fabricmc.synthetic", FilenameUtils.removeExtension(origin.getFileName().toString())));
		}
		Mappings mappings = new Mappings(origin, version, minecraft);

		if (type.isMissingFiles(cache, mappings)) {
			type.populateCache(cache, mappings, offline);
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
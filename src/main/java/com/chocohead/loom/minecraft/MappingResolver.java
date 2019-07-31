package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.Iterables;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import com.chocohead.loom.FullDependency;

import net.fabricmc.stitch.commands.CommandProposeFieldNames;
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
			@Override
			public void populateCache(Path cache, Mappings mappings, boolean offline) {
				Path base = makeBase(cache, mappings);
				//JkUtilsPath.deleteIfExists(base);

				try (FileSystem fs = FileSystems.newFileSystem(mappings.mappings, null)) {
					Files.copy(fs.getPath("mappings/mappings.tiny"), base, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new UncheckedIOException("Error extracting mappings", e);
				}
			}

			@Override
			public MappingFactory makeIntermediaryMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public MappingFactory makeNamedMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public JkDependency asDependency(Path cache, Mappings mappings) {
				return JkFileSystemDependency.of(mappings.mappings);
			}
		},
		TINY_GZ {
			@Override
			public void populateCache(Path cache, Mappings mappings, boolean offline) {
				Path base = makeBase(cache, mappings);
				//JkUtilsPath.deleteIfExists(base);

				try (InputStream in = new GZIPInputStream(Files.newInputStream(mappings.mappings))) {
					Files.copy(in, base, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new UncheckedIOException("Error extracting mappings", e);
				}
			}

			@Override
			public MappingFactory makeIntermediaryMapper(Path cache) {
				return TINY.makeIntermediaryMapper(cache);
			}

			@Override
			public MappingFactory makeNamedMapper(Path cache) {
				return TINY.makeNamedMapper(cache);
			}

			@Override
			public JkDependency asDependency(Path cache, Mappings mappings) {
				Path jar = cache.resolve(makePath(mappings, "tiny").append(".jar").toString());

				return JkComputedDependency.of(() -> {
					Path base = makeBase(cache, mappings); //Strictly it doesn't need doing, but it's logical for it to have been done
					if (Files.notExists(base)) throw new IllegalStateException("Need mappings extracting before creating dependency");

					try (FileSystem fs = FileSystems.newFileSystem(new URI("jar:" + jar.toUri()), Collections.singletonMap("create", "true"))) {
						Path destination = fs.getPath("mappings/mappings.tiny");

						Files.createDirectories(destination.getParent());
						Files.copy(base, destination, StandardCopyOption.REPLACE_EXISTING);
					} catch (URISyntaxException e) {
						throw new IllegalStateException("Cannot convert jar to URI?", e);
					} catch (IOException e) {
						throw new UncheckedIOException("Error creating mappings", e);
					}
				}, jar);
			}
		},
		ENIGMA {
			private final Path makeInters(Path cache, Mappings mappings) {
				StringBuilder path = new StringBuilder(mappings.getName());
				path.append("-intermediary-");

				if (mappings.hasVersion()) {
					path.append(mappings.getMappingMC());
				} else {
					path.append(mappings.getMC());
				}

				return cache.resolve(path.append(".tiny").toString());
			}

			private final Path makeParams(Path cache, Mappings mappings) {
				return cache.resolve(makePath(mappings, "params").append("-base.tiny").toString());
			}

			@Override
			public boolean isMissingFiles(Path cache, Mappings mappings) {
				Path inters = makeInters(cache, mappings);
				Path base = makeBase(cache, mappings);
				Path params = makeParams(cache, mappings);

				//The Intermediary and parameter map files are both generated as needed, so even without a version it's hard to say when they're too old by creation time
				return Files.notExists(inters) || (mappings.hasVersion() ? Files.notExists(base) : missingOrOld(base, mappings.creationTime())) || Files.notExists(params);
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
			public MappingFactory makeNamedMapper(Path cache) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public JkDependency asDependency(Path cache, Mappings mappings) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		protected final boolean missingOrOld(Path path, FileTime creation) {
			if (Files.notExists(path)) return true;

			try {
				return Files.getLastModifiedTime(path).compareTo(creation) < 0;
			} catch (IOException e) {
				throw new UncheckedIOException("Error getting last modification time of path", e);
			}
		}

		protected final StringBuilder makePath(Mappings mappings, String name) {
			StringBuilder path = new StringBuilder(mappings.getName());
			path.append('-');
			path.append(name);

			if (mappings.hasVersion()) {
				path.append('-');
				path.append(mappings.getVersion());
			}

			return path;
		}

		protected final Path makeBase(Path cache, Mappings mappings) {
			return cache.resolve(makePath(mappings, "tiny").append("-base.tiny").toString());
		}

		public boolean isMissingFiles(Path cache, Mappings mappings) {
			Path mapped = makeBase(cache, mappings);

			if (mappings.hasVersion()) {//Rely on having a concrete version to determine whether the file is out of date
				return Files.notExists(mapped);
			} else {
				return missingOrOld(mapped, mappings.creationTime());
			}
		}

		public abstract void populateCache(Path cache, Mappings mappings, boolean offline);

		public abstract MappingFactory makeIntermediaryMapper(Path cache);

		protected final Path makeNormal(Path cache, Mappings mappings) {
			return cache.resolve(makePath(mappings, "tiny").append(".jar").toString());
		}

		public void enhanceMappings(Path cache, Path mergedJar, Mappings mappings) {
			try {
				CommandProposeFieldNames.run(mergedJar.toFile(), makeBase(cache, mappings).toFile(), makeNormal(cache, mappings).toFile(), "intermediary", "named");
			} catch (IOException e) {
				throw new UncheckedIOException("Error proposing field names", e);
			}
		}

		public abstract MappingFactory makeNamedMapper(Path cache);

		public abstract JkDependency asDependency(Path cache, Mappings mappings);
	}

	protected final Path cache;
	protected final MappingType type;
	protected final Mappings mappings;
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
		mappings = new Mappings(origin, version, minecraft);

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
		type.enhanceMappings(cache, mergedJar, mappings);
	}

	public MappingFactory getNamed() {
		if (namedMapper == null) {
			namedMapper = type.makeNamedMapper(cache);
		}

		return namedMapper;
	}

	public JkDependency asDependency() {
		return type.asDependency(cache, mappings);
	}
}
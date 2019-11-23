package com.chocohead.loom.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.zip.GZIPInputStream;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.common.net.UrlEscapers;

import dev.jeka.core.api.depmanagement.JkComputedDependency;
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.depmanagement.JkModuleId;
import dev.jeka.core.api.depmanagement.JkResolveResult;
import dev.jeka.core.api.depmanagement.JkVersionedModule;
import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import net.fabricmc.mappings.ClassEntry;
import net.fabricmc.mappings.EntryTriple;
import net.fabricmc.mappings.FieldEntry;
import net.fabricmc.mappings.Mappings;
import net.fabricmc.mappings.MappingsProvider;
import net.fabricmc.mappings.MethodEntry;
import net.fabricmc.stitch.commands.CommandMergeTiny;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.MemberInstance;

import com.chocohead.loom.FullDependency;
import com.chocohead.loom.util.MappingReaders;
import com.chocohead.loom.util.TinyParamWriter;
import com.chocohead.loom.util.TinyWriter;

public class MappingResolver {
	/** An {@link IOException} throwing {@link BiPredicate}{@code <}String, String, IMappingProvider{@code >} */
	public interface MappingFactory {
		IMappingProvider create(String fromMapping, String toMapping) throws IOException;
	}

	public static abstract class MappingType {
		public final Path cache;
		protected final Path mappings;
		public final JkModuleId module;
		private final String minecraft;
		private final boolean hasVersion;
		private final String version, mappingMC;

		MappingType(Path cache, Path mappings, JkVersionedModule version, String minecraft) {
			this.cache = cache;
			this.mappings = mappings;
			module = version.getModuleId();
			this.minecraft = minecraft;

			if (hasVersion = !version.getVersion().isUnspecified()) {
				String mappingVersion = version.getVersion().getValue();

				if (mappingVersion.contains("+build.")) {
					mappingMC = mappingVersion.substring(0, mappingVersion.lastIndexOf('+'));
					this.version = mappingVersion.substring(mappingVersion.lastIndexOf('.') + 1);
				} else {
					int split = mappingVersion.lastIndexOf(mappingVersion.indexOf('-') > 0 ? '-' : '.');
					mappingMC = mappingVersion.substring(0, split++);
					this.version = mappingVersion.substring(split);
				}

				if (minecraft != null && !mappingMC.equals(minecraft)) {
					//Not strictly a problem, but is very likely unintentional given the potential lack of intermediaries for some things
					JkLog.warn("Running with Minecraft " + minecraft + ", but mappings are designed for " + mappingMC);
				}
			} else {
				this.version = mappingMC = null;
			}
		}

		protected MappingType(Path cache, Path mappings, String minecraft, JkModuleId mappingModule, String mappingVersion, String mappingMC) {
			this.cache = cache;
			this.mappings = mappings;
			module = mappingModule;
			this.minecraft = minecraft;

			if (mappingVersion != null && mappingMC != null) {
				hasVersion = true;
				this.mappingMC = mappingMC;
				version = mappingVersion;
			} else if (mappingVersion == null && mappingMC == null) {
				hasVersion = false;
				version = this.mappingMC = null;
			} else {
				throw new IllegalArgumentException("Must either set both mapping version and mapping Minecraft version or neither");
			}
		}

		public String getName() {
			return module.getDotedName();
		}

		public boolean hasVersion() {
			return hasVersion;
		}

		public String getVersion() {
			assert hasVersion();
			return version;
		}

		public String getMC() {
			return minecraft;
		}

		public String getMappingMC() {
			assert hasVersion();
			return mappingMC;
		}

		protected FileTime creationTime() {
			try {
				return Files.getLastModifiedTime(mappings);
			} catch (IOException e) {
				throw new UncheckedIOException("Error getting last modification time of mappings", e);
			}
		}


		protected static boolean missingOrOld(Path path, FileTime creation) {
			if (Files.notExists(path)) return true;

			try {
				return Files.getLastModifiedTime(path).compareTo(creation) < 0;
			} catch (IOException e) {
				throw new UncheckedIOException("Error getting last modification time of path", e);
			}
		}

		protected final StringBuilder makePath(String name) {
			StringBuilder path = new StringBuilder(getName());
			path.append('-');
			path.append(name);

			if (hasVersion()) {
				path.append('-');
				path.append(getVersion());
				path.append('-');
				path.append(getMappingMC());
			}

			return path;
		}

		protected final Path makeBase() {
			return cache.resolve(makePath("tiny").append("-base.tiny").toString());
		}

		public boolean isMissingFiles() {
			Path mapped = makeBase();

			if (hasVersion()) {//Rely on having a concrete version to determine whether the file is out of date
				return Files.notExists(mapped);
			} else {
				return missingOrOld(mapped, creationTime());
			}
		}

		public abstract void populateCache(boolean offline);

		protected static final IMappingProvider makeProvider(Mappings mappings, String from, String to) {
			return (classes, fields, methods) -> {
				for (ClassEntry entry : mappings.getClassEntries()) {
					String fromName = entry.get(from);
					if (fromName == null) continue;
					classes.put(fromName, entry.get(to));
				}
				assert !classes.containsKey(null);
				assert !classes.containsValue(null);

				for (FieldEntry entry : mappings.getFieldEntries()) {
					EntryTriple fromTriple = entry.get(from);
					if (fromTriple == null) continue;
					fields.put(fromTriple.getOwner() + '/' + MemberInstance.getFieldId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
				}
				assert !fields.containsKey(null);
				assert !fields.containsValue(null);

				for (MethodEntry entry : mappings.getMethodEntries()) {
					EntryTriple fromTriple = entry.get(from);
					if (fromTriple == null) continue;
					methods.put(fromTriple.getOwner() + '/' + MemberInstance.getMethodId(fromTriple.getName(), fromTriple.getDesc()), entry.get(to).getName());
				}
				assert !methods.containsKey(null);
				assert !methods.containsValue(null);
			};
		}

		public abstract MappingFactory makeIntermediaryMapper();

		protected final Path makeNormal() {
			return cache.resolve(makePath("tiny").append(".tiny").toString());
		}

		public void enhanceMappings(Path mergedJar) {
			Path normal = makeNormal();

			if (Files.notExists(normal)) {
				try {
					CommandProposeFieldNames.run(mergedJar.toFile(), makeBase().toFile(), normal.toFile(), "intermediary", "named");
				} catch (IOException e) {
					throw new UncheckedIOException("Error proposing field names", e);
				}
			}
		}

		public abstract MappingFactory makeNamedMapper();

		public abstract JkDependency asDependency();


		@Override
		public String toString() {
			return getClass().getSimpleName() + '[' + (version != null ? version.toString() : "local") + '@' + mappings + ']';
		}
	}

	public static class TinyMappings extends MappingType {
		private SoftReference<Mappings> interMappings, namedMappings;

		TinyMappings(Path cache, Path mappings, JkVersionedModule version, String minecraft) {
			super(cache, mappings, version, minecraft);
		}

		public TinyMappings(Path cache, Path mappings, String minecraft, JkModuleId mappingModule, String mappingVersion, String mappingMC) {
			super(cache, mappings, minecraft, mappingModule, mappingVersion, mappingMC);
		}

		@Override
		public void populateCache(boolean offline) {
			Path base = makeBase();
			//JkUtilsPath.deleteIfExists(base);

			try (FileSystem fs = FileSystems.newFileSystem(mappings, null)) {
				Files.copy(fs.getPath("mappings/mappings.tiny"), base, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException("Error extracting mappings", e);
			}
		}

		@Override
		public MappingFactory makeIntermediaryMapper() {
			return (from, to) -> {
				Mappings mappings = interMappings == null ? null : interMappings.get();

				if (mappings == null) {
					try (InputStream in = Files.newInputStream(makeBase())) {
						mappings = MappingsProvider.readTinyMappings(in, false);
					} catch (IOException e) {
						throw new UncheckedIOException("Error reading mappings", e);
					}

					interMappings = new SoftReference<>(mappings);
				}

				return makeProvider(mappings, from, to);
			};
		}

		@Override
		public MappingFactory makeNamedMapper() {
			return (from, to) -> {
				Mappings mappings = namedMappings == null ? null : namedMappings.get();

				if (mappings == null) {
					try (InputStream in = Files.newInputStream(makeNormal())) {
						mappings = MappingsProvider.readTinyMappings(in, false);
					} catch (IOException e) {
						throw new UncheckedIOException("Error reading mappings", e);
					}

					namedMappings = new SoftReference<>(mappings);
				}

				return makeProvider(mappings, from, to);
			};
		}

		@Override
		public JkDependency asDependency() {
			return JkFileSystemDependency.of(mappings);
		}
	}

	public static class TinyGzMappings extends TinyMappings {
		TinyGzMappings(Path cache, Path mappings, JkVersionedModule version, String minecraft) {
			super(cache, mappings, version, minecraft);
		}

		protected TinyGzMappings(Path cache, Path mappings, String minecraft, JkModuleId mappingModule, String mappingVersion, String mappingMC) {
			super(cache, mappings, minecraft, mappingModule, mappingVersion, mappingMC);
		}

		@Override
		public void populateCache(boolean offline) {
			Path base = makeBase();
			//JkUtilsPath.deleteIfExists(base);

			try (InputStream in = new GZIPInputStream(Files.newInputStream(mappings))) {
				Files.copy(in, base, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new UncheckedIOException("Error extracting mappings", e);
			}
		}

		@Override
		public JkDependency asDependency() {
			Path jar = cache.resolve(makePath("tiny").append(".jar").toString());

			return JkComputedDependency.of(() -> {
				Path base = makeBase(); //Strictly it doesn't need doing, but it's logical for it to have been done
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
	}

	public static class EnigmaMappings extends MappingType {
		private SoftReference<Mappings> interMappings, namedMappings;

		EnigmaMappings(Path cache, Path mappings, JkVersionedModule version, String minecraft) {
			super(cache, mappings, version, minecraft);
		}

		public EnigmaMappings(Path cache, Path mappings, String minecraft, JkModuleId mappingModule, String mappingVersion, String mappingMC) {
			super(cache, mappings, minecraft, mappingModule, mappingVersion, mappingMC);
		}

		private final Path makeInters() {
			return cache.resolve(makePath("intermediary").append(".tiny").toString());
		}

		private final Path makeParams() {
			return cache.resolve(makePath("params").append(".param").toString());
		}

		@Override
		public boolean isMissingFiles() {
			Path inters = makeInters();
			Path base = makeBase();
			Path params = makeParams();

			//The Intermediary and parameter map files are both generated as needed, so even without a version it's hard to say when they're too old by creation time
			return Files.notExists(inters) || (hasVersion() ? Files.notExists(base) : missingOrOld(base, creationTime())) || Files.notExists(params);
		}

		@Override
		public void populateCache(boolean offline) {
			Path inters = makeInters();
			Path base = makeBase();
			Path params = makeParams();

			if (Files.notExists(inters)) {
				String minecraft = hasVersion() ? getMappingMC() : getMC();

				try (InputStream in = new URL("https://github.com/FabricMC/intermediary/raw/master/mappings/" + UrlEscapers.urlPathSegmentEscaper().escape(minecraft) + ".tiny").openStream()) {
					Files.copy(in, inters, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new UncheckedIOException("Error downloading Intermediaries", e);
				}
			}

			if (Files.notExists(base) || Files.notExists(params)) {
				try {
					Files.deleteIfExists(base);
					Files.deleteIfExists(params);

					try (TinyWriter processor = new TinyParamWriter(base, params, "intermediary", "named")) {
						MappingReaders.readEnigmaFrom(mappings, processor);
					}
				} catch (IOException e) {
					throw new UncheckedIOException("Error processing Engima mappings", e);
				}
			}
		}

		@Override
		public MappingFactory makeIntermediaryMapper() {
			return (from, to) -> {
				Mappings mappings = interMappings == null ? null : interMappings.get();

				if (mappings == null) {
					try (InputStream in = Files.newInputStream(makeInters())) {
						mappings = MappingsProvider.readTinyMappings(in, false);
					} catch (IOException e) {
						throw new UncheckedIOException("Error reading mappings", e);
					}

					interMappings = new SoftReference<>(mappings);
				}

				return makeProvider(mappings, from, to);
			};
		}

		@Override
		public MappingFactory makeNamedMapper() {
			return (from, to) -> {
				Mappings mappings = namedMappings == null ? null : namedMappings.get();

				final Mappings usedMappings;
				if (mappings == null) {
					try (InputStream in = Files.newInputStream(makeNormal())) {
						usedMappings = mappings = MappingsProvider.readTinyMappings(in, false);
					} catch (IOException e) {
						throw new UncheckedIOException("Error reading mappings", e);
					}

					namedMappings = new SoftReference<>(mappings);
				} else {
					usedMappings = mappings;
				}

				return new IMappingProvider() {
					private final IMappingProvider normal = makeProvider(usedMappings, from, to);
					private final Map<String, String[]> locals = MappingReaders.readParamsFrom(makeParams());

					@Override
					public void load(Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap, Map<String, String[]> localMap) {
						load(classMap, fieldMap, methodMap);
						localMap.putAll(locals);
					}

					@Override
					public void load(Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap) {
						normal.load(classMap, fieldMap, methodMap);
					}
				};
			};
		}

		@Override
		public JkDependency asDependency() {
			Path merged = cache.resolve(makePath("merged").append(".tiny").toString());

			return JkComputedDependency.of(() -> {
				try {
					CommandMergeTiny.run(makeInters(), makeNormal(), merged, "intermediary", false);
				} catch (Exception e) {
					throw new RuntimeException("Error merging mappings", e);
				}
			}, merged);
		}
	}

	protected final MappingType type;
	protected MappingFactory intermediaryMapper, namedMapper;

	MappingResolver(Path cache, String minecraft, FullDependency yarn, boolean offline) {
		JkResolveResult result = yarn.resolve().assertNoError();
		List<Path> paths = result.getFiles().getEntries();

		Path origin;
		switch (paths.size()) {
		case 0: //Didn't get anything back (resolution should have failed with an ISE already if it couldn't find anything)
			throw new JkException("Empty mapping dependency supplied, found no files from " + yarn.getDependencies());

		case 1:
			origin = Iterables.getOnlyElement(paths);
			JkLog.trace("Found mapping dependency at " + origin);
			break;

		default: //We'll cross this bridge if it is ever come to, can't think of any obvious situations where this could happen
			throw new JkException("Ambigous mapping dependency supplied, expected one file but found " + paths.size() + ": " + paths);
		}

		JkVersionedModule version;
		if (yarn.isModule()) {
			JkModuleId module = yarn.getModuleID();
			version = module.withVersion(result.getVersionOf(module));
		} else {
			version = JkVersionedModule.ofUnspecifiedVerion(JkModuleId.of("net.fabricmc.synthetic", MoreFiles.getNameWithoutExtension(origin)));
		}

		switch (MoreFiles.getFileExtension(origin)) {
		case "zip":
			type = new EnigmaMappings(cache, origin, version, minecraft);
			break;

		case "gz":
			type = new TinyGzMappings(cache, origin, version, minecraft);
			break;

		case "jar":
			type = new TinyMappings(cache, origin, version, minecraft);
			break;

		default:
			throw new JkException("Unexpected mappings base type: " + MoreFiles.getNameWithoutExtension(origin) + " (from " + origin + ')');
		}
		JkLog.trace("Mappings are of type " + type);

		if (type.isMissingFiles()) {
			type.populateCache(offline);
		}
	}

	public MappingResolver(MappingType mappings, boolean offline) {
		type = mappings;

		if (type.isMissingFiles()) {
			type.populateCache(offline);
		}
	}

	public String getMappingName() {
		StringBuilder path = new StringBuilder(type.getName());

		if (type.hasVersion()) {
			path.append('-');
			path.append(type.getVersion());
		}

		return path.toString();
	}

	public MappingFactory getIntermediaries() {
		if (intermediaryMapper == null) {
			intermediaryMapper = type.makeIntermediaryMapper();
		}

		return intermediaryMapper;
	}

	protected void postMerge(Path mergedJar) {
		type.enhanceMappings(mergedJar);
	}

	public MappingFactory getNamed() {
		if (namedMapper == null) {
			namedMapper = type.makeNamedMapper();
		}

		return namedMapper;
	}

	public JkDependency asDependency() {
		return type.asDependency();
	}
}
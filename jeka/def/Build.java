import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import proguard.ClassPath;
import proguard.ClassPathEntry;
import proguard.Configuration;
import proguard.ParseException;
import proguard.ProGuard;

import dev.jeka.core.api.depmanagement.JkArtifactId;
import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.java.project.JkJavaProjectMaker;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsJdk;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkImport;
import dev.jeka.core.tool.JkImportRepo;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

@JkImport("net.sf.proguard:proguard-base:6.2.0")
@JkImport("com.github.Chocohead:tiny-remapper:c13c04c")
@JkImportRepo("https://jitpack.io/")
class Build extends JkCommands {
	private final JkPluginJava javaPlugin = getPlugin(JkPluginJava.class);

	public static void main(String[] args) throws Exception {
		JkInit.instanceOf(Build.class, args).javaPlugin.clean().pack();
	}

	@Override
	protected void setup() {
		JkJavaProject project = javaPlugin.getProject();
		project.addDependencies(JkDependencySet.of()
				.andFile(JkLocator.getJekaJarPath(), JkJavaDepScopes.PROVIDED)
				.and("com.google.guava:guava:28.0-jre", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("com.google.code.gson:gson:2.8.5", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("com.github.Chocohead:Stitch:ab75b5d", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("com.github.Chocohead:tiny-remapper:74df1c7", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("org.junit.jupiter:junit-jupiter:5.5.1", JkJavaDepScopes.TEST)
				.and("org.junit.platform:junit-platform-launcher:1.5.1", JkJavaDepScopes.TEST));

		JkJavaProjectMaker maker = project.getMaker();
		maker.setDependencyResolver(maker.getDependencyResolver().andRepos(JkRepoSet.of("https://maven.fabricmc.net", "https://jitpack.io/")));

		JkArtifactId originalID = JkArtifactId.of("original", "jar");
		maker.putArtifact(originalID, () -> maker.getTasksForPackaging().createBinJar(maker.getArtifactPath(originalID)));

		JkArtifactId fatID = JkArtifactId.of("fat", "jar");
		maker.putArtifact(fatID, () -> {
			Path jar = maker.getArtifactPath(fatID);
			maker.getTasksForPackaging().createFatJar(jar);
			JkPathTree.ofZip(jar).andMatching(true, "META-INF/**").deleteContent().close();
		});

		JkArtifactId shrunkID = JkArtifactId.of("thin", "jar");
		maker.putArtifact(shrunkID, () -> {
			maker.makeMissingArtifacts(fatID);

			Path in = maker.getArtifactPath(fatID);
			Path out = maker.getArtifactPath(shrunkID);
			Configuration config = new Configuration();

			try (ConfigParser parser = new ConfigParser(Build.class.getResourceAsStream("/build.pro"), System.getProperties())) {
				parser.parse(config);
			} catch (IOException | ParseException e) {
				throw new RuntimeException("Error parsing config", e);
			}

			assert config.programJars == null;
			config.programJars = new ClassPath();
			assert config.libraryJars == null;
			config.libraryJars = new ClassPath();

			config.programJars.add(new ClassPathEntry(in.toFile(), false));
			config.programJars.add(new ClassPathEntry(out.toFile(), true));

			if (JkUtilsJdk.runningMajorVersion() >= 9) {
				ClassPathEntry entry = new ClassPathEntry(JkUtilsJdk.javaHome().resolve("jmods").resolve("java.base.jmod").toFile(), false);
				entry.setFilter(Collections.singletonList("!module-info.class"));
				entry.setJarFilter(Collections.singletonList("!**.jar"));
				config.libraryJars.add(entry);
			} else {
				config.libraryJars.add(new ClassPathEntry(JkUtilsJdk.javaHome().resolve("lib").toFile(), false));
			}

			for (Path library : maker.getDependencyResolver().resolve(JkDependencySet.of()
					.andFile(JkLocator.getJekaJarPath(), JkJavaDepScopes.PROVIDED)
					.and("net.fabricmc:sponge-mixin:0.7.12.39", JkJavaDepScopes.PROVIDED)
					.and("cuchaz:enigma:0.14.2.143", JkJavaDepScopes.PROVIDED),
					JkJavaDepScopes.PROVIDED).assertNoError().getFiles()) {
				config.libraryJars.add(new ClassPathEntry(library.toFile(), false));
			}

			Thread t = new Thread(() -> {
				try {
					new ProGuard(config).execute();
				} catch (IOException e) {
					throw new RuntimeException("Error running ProGuard", e);
				}
			});
			JkLog.startTask("Shrinking output jar");
			try {
				t.start();
				t.join();
			} catch (InterruptedException e) {
				throw new RuntimeException("Unexpectedly bailed out of thread", e);
			} finally {
				JkLog.endTask();
			}
		});

		JkArtifactId mainID = maker.getMainArtifactId();
		maker.putArtifact(mainID, () -> {
			maker.makeMissingArtifacts(shrunkID);

			Path in = maker.getArtifactPath(shrunkID);
			Path out = maker.getMainArtifactPath();

			TinyRemapper remapper = TinyRemapper.newRemapper().withMappings((classes, fields, methods) -> {
				for (Path path : JkPathTree.ofZip(in).andMatching(false, "com/chocohead/**").andMatching("**.class").getRelativeFiles()) {
					StringBuilder name = new StringBuilder(path.toString());

					assert name.length() >= 7: "Path name too short: " + path;
					name.setLength(name.length() - 6);

					classes.put(name.toString(), name.insert(0, "com/chocohead/loom/repackaged/").toString());
				}
			})/*.rebuildSourceFilenames(true)*/.build(); //Rebuilding source names causes trouble with GSON's $ prefixed class names

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath(out)) {
				outputConsumer.addNonClassFiles(in);
				remapper.readInputs(in);

				remapper.apply(outputConsumer);
			} catch (IOException e) {
				throw new RuntimeException("Failed to repackage thin jar", e);
			}

			remapper.finish();
		});
	}

	@JkDoc("Produces the main build arifact, with all necessary parent artifacts")
	public void build() {
		javaPlugin.clean().getProject().getMaker().makeMainArtifact();
	}

	@JkDoc("Produces all build artifacts that would come from clean java#pack")
	public void buildAll() {
		JkJavaProjectMaker maker = javaPlugin.clean().getProject().getMaker();
		maker.makeMissingArtifacts(JkArtifactId.of("original", "jar"), maker.getMainArtifactId());
	}
}
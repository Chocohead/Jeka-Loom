import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.java.project.JkJavaProjectMaker;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

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
				.and("com.github.Chocohead:Stitch:529051f", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("com.github.Chocohead:tiny-remapper:c13c04c", JkJavaDepScopes.SCOPES_FOR_COMPILATION)
				.and("org.junit.jupiter:junit-jupiter:5.5.1", JkJavaDepScopes.TEST)
				.and("org.junit.platform:junit-platform-launcher:1.5.1", JkJavaDepScopes.TEST));

		JkJavaProjectMaker maker = project.getMaker();
		maker.setDependencyResolver(maker.getDependencyResolver().andRepos(JkRepoSet.of("https://maven.fabricmc.net", "https://jitpack.io/")));
	}
}
import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.tool.JkInit;

import com.chocohead.loom.FullDependencies;
import com.chocohead.loom.minecraft.EnigmaLiveDependency;
import com.chocohead.loom.minecraft.JkPluginMinecraft;

/** Rough example for how things can be interacted with, designed for debugging */
public class Testing {
	public static void main(String[] args) {
		JkPluginMinecraft mcPlugin = JkInit.instanceOf(Build.class, args).getPlugin(JkPluginMinecraft.class);

		JkDependency mappings = EnigmaLiveDependency.createForBranch("Blayyke/yarn", "1.2.5");
		FullDependencies minecraftDep = mcPlugin.standard("1.2.5").withMappings(mappings).splitMerge(true).build().asDependency();

		System.out.println(minecraftDep.resolveToPaths());
	}
}
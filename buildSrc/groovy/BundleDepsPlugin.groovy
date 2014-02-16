
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

import aQute.bnd.main.bnd
import aQute.lib.osgi.Analyzer


public class BundleDepsPlugin implements Plugin<Project> {

	public static final String TASK_BUNDLE_DEPS = 'bundleDeps'
	//public static final String TASK_CLEAN_BUNDLES = "cleanBundles"
	public static final Pattern versionPattern = Pattern.compile('(^[\\w]+(?:-[\\w]*)*)(?:-)(\\d+([\\.-][\\w|?:(\\w+\\.)]+)*)(?:\\.jar$)')
	public static final String sourcesSuffix = '-sources.jar'
	
	private Project project

	public BundleDepsPlugin() {
		def bnd = new bnd()
	}

	@Override
	public void apply(Project project) {
		this.project = project
		if(!project.getPlugins().findPlugin('java'))
		project.apply(plugin:'java')

		Task tbd = project.task(TASK_BUNDLE_DEPS)
		tbd.dependsOn('buildNeeded')
		tbd.doFirst(new Action(){
			@Override
			public void execute(Object target) {
				assert project

				Configuration config = project.getConfigurations().getByName('runtime')

				List jarFiles = config.collect()
				String targetDir = "wrapped-jars"

				if(jarFiles.isEmpty()) {
					project.logger.warn "${getClass().getSimpleName()}: NO DEPENDENCIES COULD BE FOUND!"
					return
				} else project.logger.info("wrapping ${jarFiles.size()} plain jars:")

				def bnd = new bnd()
				jarFiles.findAll{it.getName().endsWith('.pom')}.each{
						jarFiles.remove(it);
				}

				jarFiles.each {
					assert it instanceof File
						def outputFileName = project.file(targetDir + File.separator + it.getName())

						
						if(it.getName().endsWith(sourcesSuffix)) {
							project.logger.info "...dependency ${it} seems to be a source file, just copying to ${outputFileName}"
							//if source.jar just copy it to target folder
							( new AntBuilder ( ) ).copy ( file : it , tofile : outputFileName )
						
						} else {
							//assert it instanceof File && it.name.endsWith(".jar")
							project.logger.info "...wrapping ${it} to ${outputFileName}"
							bnd.doWrap(null, it, outputFileName as File, jarFiles as File[], 0, extractAdditional(it))
						}
				}

				project.logger.info "...contents of ${targetDir} updated, please reset your target platform"
			}
		})
	}

	protected Map extractAdditional(File jarFile) {
		project.logger.debug "guessJarVersion: from filename ${jarFile}"

		Map additional =[:]
		List versionTokens = []
		List qualifierTokens = []
		String name;
		try {
			final Matcher m = versionPattern.matcher(jarFile.getName()).with{
				it.matches() ? it : it
			}
			(m.group(2).split('-') as List).with{
				versionTokens.addAll(it.head().split('\\.'))
				qualifierTokens.addAll(it.tail())
				qualifierTokens.each{
					it.replaceAll('\\.','_')

				}
			}
			project.logger.debug("versionTokens:${versionTokens}\n")
			project.logger.debug("qualifierTokens:${qualifierTokens}\n")
			name = m.group(1)
		} catch (Exception e) {
			project.logger.warn "${getClass().getSimpleName()}: COULD NOT GUESS VERSION AND/OR NAME FROM FILENAME ${jarFile}:\n   CAUSE: ${e}"
		} finally {
			qualifierTokens.push('autowrapped')
			while(versionTokens.size()<3) versionTokens.push('0')
			additional.put(Analyzer.BUNDLE_VERSION, versionTokens.take(3).join('.') + '.' + versionTokens.drop(3).plus(qualifierTokens).join('-'))
			if(name && !name.trim().isEmpty()) { // don't override bnd default for bundle name if we don't extract anything useful here
				additional.put(Analyzer.BUNDLE_NAME,name)
				additional.put(Analyzer.BUNDLE_SYMBOLICNAME, name)
			}
			project.logger.info("...using additional properties: ${additional}")
			return additional
		}
	}
}

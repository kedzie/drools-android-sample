package com.github.kedzie.drools;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.drools.core.common.DroolsObjectOutputStream;
import org.drools.core.util.DroolsStreamUtils;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.io.ResourceFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * @author kedzie
 *
 */
@Mojo(name = "compile-rules",
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      requiresProject = true,
      defaultPhase = LifecyclePhase.COMPILE,
      configurator = "include-project-dependencies")
public class Compiler extends AbstractMojo {

    /**
     * DRL rule package
     */
    @Parameter(property = "compile-rules.ruleFiles",required = true)
    private List<String> ruleFiles;

    /**
     * Output folder
     */
    @Parameter(property = "compile-rules.outputDirectory", defaultValue = "${project.basedir}/res/raw" )
    private String outputDirectory;

    /**
     * KnowledgeBuilderConfiguration properties
     */
    @Parameter(property = "compile-rules.properties" )
    private Properties properties;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            KnowledgeBuilderConfiguration kbConfig =
                    KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(properties);
            final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder(kbConfig);

            for(String ruleFile : ruleFiles) {
                getLog().info("Compiling drl: " + ruleFile);
                kbuilder.add(ResourceFactory.newClassPathResource(ruleFile), ResourceType.DRL);
            }
            if (kbuilder.hasErrors()) {
                getLog().error("=====Errors=====");
                for(KnowledgeBuilderError error : kbuilder.getErrors()) {
                    getLog().error(error.toString());
                }
                throw new MojoFailureException(kbuilder.getErrors().toString());
            }
            final Collection<KnowledgePackage> pkgs = kbuilder.getKnowledgePackages();
            File outputFolder = new File(outputDirectory);
            outputFolder.mkdirs();
            for(KnowledgePackage pkg : pkgs) {
                //serialize knowledge package
                getLog().info("Writing package: " + pkg.getName());
                File drlFile = new File(outputFolder, pkg.getName().replace('.', '_'));
                drlFile.getParentFile().mkdirs();
                FileOutputStream out = new FileOutputStream(drlFile);
                DroolsStreamUtils.streamOut(out, pkg);
                out.close();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("error", e);
        }
    }
}

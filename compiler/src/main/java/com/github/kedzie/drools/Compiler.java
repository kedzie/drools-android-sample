package com.github.kedzie.drools;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.drools.core.common.DroolsObjectOutputStream;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.io.ResourceFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Created by kedzie on 6/5/14.
 */
@Mojo(name = "compile-rules")
public class Compiler extends AbstractMojo {

    /**
     * DRL rule package
     */
    @Parameter( property = "compile-rules.ruleFiles" )
    private List<String> ruleFiles;

    /**
     * Output folder
     */
    @Parameter( property = "compile-rules.outputDirectory", defaultValue = "${project.basedir}/res/raw" )
    private String outputDirectory;

    /**
     * KnowledgeBuilderConfiguration properties
     */
    @Parameter( property = "compile-rules.properties" )
    private Properties properties;

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
                throw new MojoFailureException(kbuilder.getErrors().toString());
            }
            final Collection<KnowledgePackage> pkgs = kbuilder.getKnowledgePackages();
            File outputFolder = new File(outputDirectory);
            outputFolder.mkdirs();
            for(KnowledgePackage pkg : pkgs) {
                //serialize knowledge package
                File drlFile = new File(outputFolder, pkg.getName().replace('.', '_'));
                drlFile.getParentFile().mkdirs();
                FileOutputStream out = new FileOutputStream(drlFile);
                DroolsObjectOutputStream oos = new DroolsObjectOutputStream(out);
                oos.writeObject(pkg);
                oos.close();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("error", e);
        }
    }
}

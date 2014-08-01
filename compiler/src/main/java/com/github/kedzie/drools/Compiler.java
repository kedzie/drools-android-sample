package com.github.kedzie.drools;

import org.drools.core.common.DroolsObjectOutputStream;
import org.drools.examples.helloworld.HelloWorldExample;
import org.kie.api.io.ResourceType;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * Created by kedzie on 6/5/14.
 */
public class Compiler {
    private static Logger log = LoggerFactory.getLogger(Compiler.class);

    public static void main(String []args) {
        log.info("Compiling rules");
        try {
            Properties properties = new Properties();
            KnowledgeBuilderConfiguration kbConfig =
                    KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(properties);
            final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder(kbConfig);

            kbuilder.add(ResourceFactory.newClassPathResource("org/drools/examples/helloworld/HelloWorld.drl",
                    HelloWorldExample.Message.class), ResourceType.DRL);

            if (kbuilder.hasErrors()) {
                throw new RuntimeException(kbuilder.getErrors().toString());
            }
            final Collection<KnowledgePackage> pkgs = kbuilder
                    .getKnowledgePackages();

            //serialize knowledge package
            FileOutputStream out = new FileOutputStream("../sample/res/raw/helloworld_pkg");
            DroolsObjectOutputStream oos = new DroolsObjectOutputStream(out);
            oos.writeObject(pkgs);
            oos.close();

            final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
            kbase.addKnowledgePackages(pkgs);

            //serialize knowledge base
            out = new FileOutputStream("../sample/res/raw/helloworld_kbase");
            oos = new DroolsObjectOutputStream(out);
            oos.writeObject(kbase);
            oos.close();

            final StatefulKnowledgeSession ksession = kbase
                    .newStatefulKnowledgeSession();

            ksession.setGlobal("list",
                    new ArrayList<Object>());

            final HelloWorldExample.Message message = new HelloWorldExample.Message();
            message.setMessage( "Hello World" );
            message.setStatus( HelloWorldExample.Message.HELLO );
            ksession.insert(message);

            ksession.fireAllRules();

            ksession.dispose();
        } catch (Exception e) {
            log.error("error", e);
        }
    }
}

import org.drools.core.common.DroolsObjectOutputStream;
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
import java.util.Collection;
import java.util.Properties;

import implement_measurement.ImplementMeasurementConfig;

/**
 * Created by kedzie on 6/5/14.
 */
public class main {
   private static Logger log = LoggerFactory.getLogger(main.class);

   public static void main(String []args) {
      log.info("Compiling rules");
      try {
         Properties properties = new Properties();
         KnowledgeBuilderConfiguration kbConfig =
               KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(properties);
         final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder(kbConfig);

         kbuilder.add(ResourceFactory.newClassPathResource("com/cnh/android/implement_setup/measurement/measurement.drl",
               ImplementMeasurementConfig.class), ResourceType.DRL);

         if (kbuilder.hasErrors()) {
            throw new RuntimeException(kbuilder.getErrors().toString());
         }
         final Collection<KnowledgePackage> pkgs = kbuilder
               .getKnowledgePackages();

         final FileOutputStream out = new FileOutputStream("/home/kedzie/Development/git/CNH/implement_setup/res/raw/measurements.pkg");
         DroolsObjectOutputStream oos = new DroolsObjectOutputStream(out);
         oos.writeObject(pkgs);
         oos.close();

         final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
         kbase.addKnowledgePackages(pkgs);

         final StatefulKnowledgeSession ksession = kbase
               .newStatefulKnowledgeSession();
         ksession.setGlobal("$logger", log);

         ImplementMeasurementConfig config = new ImplementMeasurementConfig();
         config.numRows = 12;
         config.rowWidth = 300;
         config.skip = -5;
         config.overlap = 2;
         config.implementType = "Self-Propelled Planter";
         config.skipOverlapMode = "Skip";

         ksession.insert(config);


         ksession.fireAllRules();

         log.info("Result Configuration  : " + config);

         ksession.dispose();
      } catch (Exception e) {
         log.error("error", e);
      }
   }
}

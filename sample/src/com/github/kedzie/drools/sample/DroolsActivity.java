package com.github.kedzie.drools.sample;

import org.drools.core.android.DroolsAndroidContext;
import org.drools.core.common.DroolsObjectInputStream;
import org.kie.api.definition.rule.Rule;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.mvel2.optimizers.OptimizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;

public class DroolsActivity extends Activity {

   private static final Logger logger = LoggerFactory.getLogger(DroolsActivity.class);

	private KnowledgeBase mKnowledgeBase;
   
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
		//Initialize android context and set system properties
		DroolsAndroidContext.setContext(this);
		//load serialized KnowledgePackages from res/raw/helloworld.pkg
		new LoadRulesTask().execute(R.raw.helloworld);

		RelativeLayout layout = new RelativeLayout(this);
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.LayoutParams.CENTER_IN_PARENT);
		Button button = new Button(this);
		button.setText("Fire Rules");
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				new FireRulesTask().execute();
			}
		});
		layout.addView(button, lp);
		setContentView(layout);
   }


	private class LoadRulesTask extends AsyncTask<Integer, Void, KnowledgeBase> {

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         getActivity().setProgressBarIndeterminateVisibility(true);
      }

      @Override
      protected KnowledgeBase doInBackground(Integer... params) {
			DroolsObjectInputStream dois = null;         
		try {
            logger.debug("Loading knowledge base");
            
               InputStream iis = getResources().openRawResource(params[0]);
               dois = new DroolsObjectInputStream(iis);
               final Collection<KnowledgePackage> pkgs = (Collection<KnowledgePackage>) dois.readObject();

               logger.debug("Loaded rule packages: " + pkgs);
               for(KnowledgePackage pkg : pkgs) {
                  logger.debug("Loaded rule package: " + pkg.toString());
                  for(Rule rule : pkg.getRules()) {
                     logger.debug("Rule: " + rule);
                  }
               }

//               System.setProperty("java.version", "1.6");
//               System.setProperty("mvel2.disable.jit", "true");
//               OptimizerFactory.setDefaultOptimizer("reflective");

               final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
               kbase.addKnowledgePackages(pkgs);
				return kbase;
         }catch(Exception e) {
            logger.error("Drools exception", e);
			return null;
         } finally {
			if(dois!=null)
				dois.close();
		}
      }

      @Override
      protected void onPostExecute(KnowledgeBase result) {
			mKnowledgeBase = result;
         getActivity().setProgressBarIndeterminateVisibility(false);
      }
   }

   private class FireRulesTask extends AsyncTask<Void, Void, Void> {

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         logger.debug("Processing rules");
         getActivity().setProgressBarIndeterminateVisibility(true);
      }

      @Override
      protected Void doInBackground(Void... params) {
         try {
            logger.debug("Firing rules");
            
               final StatefulKnowledgeSession ksession = mKnowledgeBase.newStatelessKnowledgeSession();
               ksession.setGlobal("$logger", logger);
               ksession.insert(params[0]);
               ksession.fireAllRules();
               ksession.dispose();
         }catch(Exception e) {
            logger.error("Drools exception", e);
         }
		return null;
      }

      @Override
      protected void onPostExecute(Void result) {
         getActivity().setProgressBarIndeterminateVisibility(false);
      }
   }

   

}

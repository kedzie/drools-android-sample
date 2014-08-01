package com.github.kedzie.drools.sample;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import org.drools.core.android.DroolsAndroidContext;
import org.drools.core.common.DroolsObjectInputStream;
import org.drools.examples.helloworld.HelloWorldExample;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.event.rule.DebugRuleRuntimeEventListener;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class DroolsActivity extends Activity {

    private static final Logger logger = LoggerFactory.getLogger(DroolsActivity.class);

    private KnowledgeBase mKnowledgeBase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Initialize android context and set system properties
        DroolsAndroidContext.setContext(this);
        //load serialized KnowledgePackages from res/raw/helloworld.pkg
//        ;
//        new LoadRulesTask().execute(getClass().getResourceAsStream("META-INF/HelloWorldKB/kbase.cache"));
        new LoadRulesTask().execute(getResources().openRawResource(R.raw.helloworld_kbase));

        RelativeLayout layout = new RelativeLayout(this);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
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


    private class LoadRulesTask extends AsyncTask<InputStream, Void, KnowledgeBase> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            DroolsActivity.this.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected KnowledgeBase doInBackground(InputStream... params) {
            DroolsObjectInputStream dois = null;
            try {
                logger.debug("Loading knowledge base");

                dois = new DroolsObjectInputStream(params[0]);
                final KnowledgeBase kbase = (KnowledgeBase)dois.readObject();
//                final Collection<KnowledgePackage> pkgs = (Collection<KnowledgePackage>) dois.readObject();
//                final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
//                kbase.addKnowledgePackages(pkgs);

//               System.setProperty("java.version", "1.6");
//               System.setProperty("mvel2.disable.jit", "true");
//               OptimizerFactory.setDefaultOptimizer("reflective");

                for(KnowledgePackage pkg : kbase.getKnowledgePackages()) {

                        logger.debug("Loaded rule package: " + pkg.toString());
                        for (Rule rule : pkg.getRules()) {
                            logger.debug("Rule: " + rule);
                        }
                }

                return kbase;
            }catch(Exception e) {
                logger.error("Drools exception", e);
                return null;
            } finally {
                if(dois!=null) {
                    try {
                        dois.close();
                    } catch (IOException e) {}
                }
            }
        }

        @Override
        protected void onPostExecute(KnowledgeBase result) {
            mKnowledgeBase = result;
            DroolsActivity.this.setProgressBarIndeterminateVisibility(false);
        }
    }

    private class FireRulesTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            logger.debug("Processing rules");
            DroolsActivity.this.setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                logger.debug("Firing rules");

                final StatefulKnowledgeSession ksession = mKnowledgeBase.newStatefulKnowledgeSession();

                ksession.addEventListener( new DebugAgendaEventListener() );
                ksession.addEventListener( new DebugRuleRuntimeEventListener() );

                ksession.setGlobal("list", new ArrayList<Object>());

                final HelloWorldExample.Message message = new HelloWorldExample.Message();
                message.setMessage( "Hello World" );
                message.setStatus( HelloWorldExample.Message.HELLO );
                ksession.insert(message);
                ksession.fireAllRules();
                ksession.dispose();
            }catch(Exception e) {
                logger.error("Drools exception", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            DroolsActivity.this.setProgressBarIndeterminateVisibility(false);
        }
    }



}

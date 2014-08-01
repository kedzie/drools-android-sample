/*
 * Copyright (C) 2014 CNH Industrial NV. All rights reserved.
 *
 * This software contains proprietary information of CNH Industrial NV. Neither
 * receipt nor possession thereof confers any right to reproduce, use, or
 * disclose in whole or in part any such information without written
 * authorization from CNH Industrial NV.
 *
 */

package com.cnh.android.implement_setup;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.cnh.android.vip.VehicleImplementDatabase;
import com.cnh.android.widget.activity.BaseTabActivity;
import com.cnh.android.widget.control.SegmentedToggleButtonGroup;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
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

import implement_measurement.ImplementMeasurementConfig;
import implement_measurement.MeasurementRuleService;

import static com.cnh.android.vip.VehicleImplementDatabase.*;
import static java.lang.String.format;

public class ImplementMeasurementsFragment extends Fragment implements TextView.OnEditorActionListener, LoaderManager.LoaderCallbacks<Cursor> {
   private static final String TAG = "ImplementMeasurements";
   private static final Logger logger = LoggerFactory.getLogger(ImplementMeasurementsFragment.class);

   private static final int VEHICLE_LOADER = 0;
   private static final int IMPLEMENT_LOADER = 1;

   private EditText centerOffsetEditText;
   private EditText barDistanceEditText;
   private EditText numRowsEditText;
   private EditText rowWidthEditText;
   private TextView implementWidthEditText;
   private SegmentedToggleButtonGroup hitchToggleGroup;
   private ImageView implementDiagram;
   private SegmentedToggleButtonGroup skipOverlapToggleGroup;
   private EditText overlapEditText;
   private EditText skipEditText;

   private ImplementMeasurementConfig config;
   private boolean isProcessing;

   private boolean mUseSSL;
   private String mHost = "192.168.88.151";
   private int mPort = 10000;
   private MeasurementRuleService.Client mClient;
   private TTransport mTransport;

   private static String column(String table, String column) {
      return new StringBuffer(table).append(".").append(column)
            .append(" as ").append(table).append("_").append(column).toString();
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
   }

   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      ViewGroup v = (ViewGroup) inflater.inflate(R.layout.measurement_fragment, container, false);

      centerOffsetEditText = (EditText) v.findViewById(R.id.center_offset_edit);
      centerOffsetEditText.addTextChangedListener(new SimpleTextWatcher() {

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(isProcessing) return;
            try {
               config.setCenterOffset(Float.parseFloat(s.toString()));
            } catch (NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               centerOffsetEditText.setError(e.getMessage());
            }
         }
      });
      centerOffsetEditText.setOnEditorActionListener(this);

      barDistanceEditText = (EditText) v.findViewById(R.id.bar_distance_edit);
      barDistanceEditText.addTextChangedListener(new SimpleTextWatcher() {

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            try {
               if(isProcessing) return;
               config.setBarDistance(Float.parseFloat(s.toString()));
            } catch (NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               barDistanceEditText.setError(e.getMessage());
            }
         }
      });
      barDistanceEditText.setOnEditorActionListener(this);

      numRowsEditText = (EditText) v.findViewById(R.id.num_rows_edit);
      numRowsEditText.addTextChangedListener(new SimpleTextWatcher() {
         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(isProcessing) return;
            try {
               config.setNumRows(Integer.parseInt(s.toString()));
            } catch (NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               numRowsEditText.setError(e.getMessage());
            }
         }
      });
      numRowsEditText.setOnEditorActionListener(this);

      rowWidthEditText = (EditText) v.findViewById(R.id.row_width_edit);
      rowWidthEditText.addTextChangedListener(new SimpleTextWatcher() {
         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(isProcessing) return;
            try {
               config.setRowWidth(Float.parseFloat(s.toString()));
            } catch(NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               rowWidthEditText.setError(e.getMessage());
            }
         }
      });
      rowWidthEditText.setOnEditorActionListener(this);

      implementWidthEditText = (TextView) v.findViewById(R.id.implement_width_edit);

      hitchToggleGroup = (SegmentedToggleButtonGroup) v.findViewById(R.id.hitch_type_group);
      hitchToggleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
         @Override
         public void onCheckedChanged(RadioGroup group, int checkedId) {
            if(checkedId>0 && !isProcessing) {
               CompoundButton button = (CompoundButton) group.findViewById(checkedId);
               config.setHitchType(button.getText().toString());
               update();
            }
         }
      });

      implementDiagram = (ImageView) v.findViewById(R.id.implement_diagram);

      skipOverlapToggleGroup = (SegmentedToggleButtonGroup) v.findViewById(R.id.skip_overlap_group);
      skipOverlapToggleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
         @Override
         public void onCheckedChanged(RadioGroup group, int checkedId) {
            if(checkedId>0 && !isProcessing) {
               CompoundButton button = (CompoundButton) group.findViewById(checkedId);
               config.setSkipOverlapMode(button.getText().toString());
               update();
            }
         }
      });

      skipEditText = (EditText) v.findViewById(R.id.skip_edit);
      skipEditText.addTextChangedListener(new SimpleTextWatcher() {
         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(isProcessing) return;
            try {
               config.setSkip(Integer.parseInt(s.toString()));
            } catch(NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               skipEditText.setError(e.getMessage());
            }
         }
      });
      skipEditText.setOnEditorActionListener(this);

      overlapEditText = (EditText) v.findViewById(R.id.overlap_edit);
      overlapEditText.addTextChangedListener(new SimpleTextWatcher() {
         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            if(isProcessing) return;
            try {
               config.setOverlap(Integer.parseInt(s.toString()));
            } catch(NumberFormatException e) {
               Log.w(TAG, "format Exception: " + e.getMessage());
               overlapEditText.setError(e.getMessage());
            }
         }
      });
      overlapEditText.setOnEditorActionListener(this);
      return v;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState) {
      super.onActivityCreated(savedInstanceState);
      getActivity().setProgressBarIndeterminate(true);
   }

   private static String [] VEHICLE_COLUMNS = {
         column(VEHICLE_MODEL, VehicleModelColumns.MAKE),
         column(VEHICLE_MODEL, VehicleModelColumns.MODEL),
         column(VEHICLE, VehicleColumns.NAME)
   };

   private static String [] IMPLEMENT_COLUMNS = {
         column(IMPLEMENT_MODEL, ImplementModelColumns.MAKE),
         column(IMPLEMENT_MODEL, ImplementModelColumns.MODEL),
         column(IMPLEMENT, ImplementColumns.NAME),
         column(IMPLEMENT_TYPE, ImplementTypeColumns.IMPLEMENT_TYPE_ID),
         column(IMPLEMENT_TYPE, ImplementTypeColumns.NAME) };

   @Override
   public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      Log.i(TAG, "onCreateLoader: " + id);

      switch(id) {
         case VEHICLE_LOADER:
            return new CursorLoader(getActivity(), VEHICLE_CURRENT_JOIN_URI,
                  VEHICLE_COLUMNS,
                  "", null, null);
         case IMPLEMENT_LOADER:
            return new CursorLoader(getActivity(), IMPLEMENT_CURRENT_JOIN_URI,
                  IMPLEMENT_COLUMNS,
                  "", null, null);
      }
      return null;
   }

   @Override
   public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      update();
      return false;
   }

   @Override
   public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
      Log.i(TAG, "onLoadFinished:" + loader.getId());

      Log.i(TAG, "Columns: " + c);

      if(loader.getId()==VEHICLE_LOADER) {
         if (c!=null && c.moveToFirst()) {
            String vehicleMake = c.getString(c.getColumnIndex(VEHICLE_MODEL+"_"+VehicleModelColumns.MAKE));
            String vehicleModel = c.getString(c.getColumnIndex(VEHICLE_MODEL+"_"+VehicleModelColumns.MODEL));
            String vehicleName = c.getString(c.getColumnIndex(VEHICLE+"_"+VehicleColumns.NAME));
            ((BaseTabActivity)getActivity()).setTabActivitySubheaderLeft(
                  format("%s/%s - %s", vehicleMake, vehicleModel, vehicleName));
         }
      }else if(loader.getId()==IMPLEMENT_LOADER) {
         if (c!=null && c.moveToFirst()) {
            config = new ImplementMeasurementConfig();
            config.setMake(c.getString(c.getColumnIndex(IMPLEMENT_MODEL+"_"+ImplementModelColumns.MAKE)));
            config.setModel(c.getString(c.getColumnIndex(IMPLEMENT_MODEL+"_"+ImplementModelColumns.MODEL)));
            config.setImplementType(c.getString(c.getColumnIndex(IMPLEMENT_TYPE+"_"+ImplementTypeColumns.IMPLEMENT_TYPE_ID)));
            config.setImplementName(c.getString(c.getColumnIndex(IMPLEMENT+"_"+ImplementColumns.NAME)));

            config.getRightHeaderControl().setText(format("%s/%s - %s", config.getMake(), config.getModel(), config.implementName));

            config.setBarDistance(10);
            config.setNumRows(12);
            config.setRowWidth(20);
            config.setSkip(5);
            config.setOverlap(2);
            config.setCenterOffset(0);
            config.setImplementType("Self-Propelled Planter");
            config.setSkipOverlapMode("Skip");
            config.setHitchType("Drawbar");
            config.setImplementWidth(0);
            update();
         }
      }
   }

   @Override
   public void onLoaderReset(Loader<Cursor> loader) {}

   private void update() {
      new ProcessTask().execute(config);
   }

   @Override
   public void onPause() {
      if(isConnected())
         new DisconnectTask().execute();
      super.onPause();
   }

   @Override
   public void onResume() {
      super.onResume();
      if(!isConnected())
         new ConnectTask().execute(mHost, mPort+"");
   }

   private boolean isConnected() {
      return mClient!=null;
   }

   private void setTextView(int id, String text) {
      ((TextView)getView().findViewById(id)).setText(text);
   }

   private class ProcessTask extends AsyncTask<ImplementMeasurementConfig, Void, ImplementMeasurementConfig> {

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         Log.v(TAG, "Processing rules");
         getActivity().setProgressBarIndeterminateVisibility(true);
      }

      @Override
      protected ImplementMeasurementConfig doInBackground(ImplementMeasurementConfig... params) {
         try {
            Log.v(TAG, "Firing rules");
            DroolsAndroidContext.setContext(getActivity());
            try {
               InputStream iis = getResources().openRawResource(R.raw.measurements);
               DroolsObjectInputStream dois = new DroolsObjectInputStream(iis);
               final Collection<KnowledgePackage> pkgs = (Collection<KnowledgePackage>) dois.readObject();
               dois.close();

               Log.v(TAG, "Loaded rule packages: " + pkgs);
               for(KnowledgePackage pkg : pkgs) {
                  Log.v(TAG, "Loading rule package: " + pkg.toString());
                  for(Rule rule : pkg.getRules()) {
                     Log.v(TAG, "Rule: " + rule);
                  }
               }

               System.setProperty("java.version", "1.6");
               System.setProperty("mvel2.disable.jit", "true");
               OptimizerFactory.setDefaultOptimizer("reflective");

               final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
               kbase.addKnowledgePackages(pkgs);

               final StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
               ksession.setGlobal("$logger", logger);
               ksession.insert(params[0]);
               ksession.fireAllRules();

               ksession.dispose();
            } catch (Exception e) {
               Log.e(TAG, "Rule problem:", e);
            }
            return params[0];
         }catch(Exception e) {
            Log.e(TAG, "Drools exception", e);
            return params[0];
         }
//         try {
//            if(mClient==null) {
//               mTransport = new TSocket(mHost, mPort);
//               mTransport.open();
//               mClient = new MeasurementRuleService.Client(new TBinaryProtocol(mTransport));
//            }
//            return mClient.process(params[0]);
//         } catch (TException e) {
//            Log.e(TAG, "Thrift error: " + e.getMessage());
//            config.getRightHeaderControl().setText(format("%s/%s - %s", config.getMake(), config.getModel(), config.implementName));
//
//            if("Twin-Row Planter".equals(config.getImplementType())) {
//               config.getNumRowsControl().setLabel("Number of Twin Rows");
//               config.getNumRowsControl().setText(format("%d", config.getNumRows()/2));
//               config.getRowWidthControl().setText(format("%d", config.getRowWidth()*2));
//            }
//
//            config.setImplementWidth(config.getNumRows() * config.getRowWidth());
//            if("Skip".equals(config.getSkipOverlapMode())) {
//               config.setImplementWidth(config.getImplementWidth()+config.getSkip());
//            } else if("Overlap".equals(config.getSkipOverlapMode())) {
//               config.setImplementWidth(config.getImplementWidth()-config.getOverlap());
//            }
//            return config;
//         }
      }

      @Override
      protected void onPostExecute(ImplementMeasurementConfig result) {
         getActivity().setProgressBarIndeterminateVisibility(false);
         isProcessing = true;
         if(result!=null) {
            ImplementMeasurementsFragment.this.config = result;
         }

         if(config.isSetNumRows()) {
            numRowsEditText.setText(format("%d", config.getNumRows()));
         }
         if(config.isSetRowWidth()) {
            rowWidthEditText.setText(format("%.2f", config.getRowWidth()));
         }
         if(config.isSetImplementWidth()) {
            implementWidthEditText.setText(format("%.2f", config.getImplementWidth()));
         }
         if(config.isSetCenterOffset()) {
            centerOffsetEditText.setText(format("%.2f", config.getCenterOffset()));
         }
         if(config.isSetBarDistance()) {
            barDistanceEditText.setText(format("%.2f", config.getBarDistance()));
         }
         if(config.isSetSkip()) {
            skipEditText.setText(format("%d", config.getSkip()));
         }
         if(config.isSetOverlap()) {
            overlapEditText.setText(format("%d", config.getOverlap()));
         }

         if(config.isSetRightHeaderControl()) {
            if(config.rightHeaderControl.isSetText()) {
               ((BaseTabActivity)getActivity()).setTabActivitySubheaderRight(
                     config.rightHeaderControl.text);
            }
         }
         if(config.isSetCenterOffsetControl()) {
            if (config.centerOffsetControl.isSetLabel())
               setTextView(R.id.center_offset_label, config.centerOffsetControl.label);

            centerOffsetEditText.setError(config.centerOffsetControl.error);

            if(config.centerOffsetControl.isSetText())
               centerOffsetEditText.setText(config.centerOffsetControl.text);
         }
         if(config.isSetBarDistanceControl()) {
            if (config.barDistanceControl.isSetLabel())
               setTextView(R.id.bar_distance_label, config.barDistanceControl.label);

            barDistanceEditText.setError(config.barDistanceControl.error);

            if(config.barDistanceControl.isSetText())
               barDistanceEditText.setText(config.barDistanceControl.text);
         }
         if(config.isSetHitchTypeControl()) {
            if (config.hitchTypeControl.isSetLabel())
               setTextView(R.id.hitch_type_label, config.hitchTypeControl.label);
            if(config.hitchTypeControl.isSetOptions()) {
               hitchToggleGroup.removeAllViews();
               for(String option : config.hitchTypeControl.options) {
                  int id = hitchToggleGroup.addButton(option);
                  if(config.hitchType.equals(option))
                     hitchToggleGroup.check(id);
               }
            }
         }
         if(config.isSetNumRowsControl()) {
            if (config.numRowsControl.isSetLabel())
               setTextView(R.id.num_rows_label, config.numRowsControl.label);

            numRowsEditText.setError(config.numRowsControl.error);

            if(config.numRowsControl.isSetText())
               numRowsEditText.setText(config.numRowsControl.text);
         }
         if(config.isSetRowWidthControl()) {
            if (config.rowWidthControl.isSetLabel())
               setTextView(R.id.row_width_label, config.rowWidthControl.label);

            rowWidthEditText.setError(config.rowWidthControl.error);

            if(config.rowWidthControl.isSetText())
               rowWidthEditText.setText(config.rowWidthControl.text);
         }
         if(config.isSetImplementWidthControl()) {
            if (config.implementWidthControl.isSetLabel())
               setTextView(R.id.implement_width_label, config.implementWidthControl.label);

            implementWidthEditText.setError(config.implementWidthControl.error);

            if(config.implementWidthControl.isSetText())
               implementWidthEditText.setText(config.implementWidthControl.text);
         }
         if(config.isSetSkipOverlapModeControl()) {
            if (config.skipOverlapModeControl.isSetLabel())
               setTextView(R.id.skip_overlap_label, config.skipOverlapModeControl.label);

            if(config.skipOverlapModeControl.isSetOptions()) {
               skipOverlapToggleGroup.removeAllViews();
               for(String option : config.skipOverlapModeControl.options) {
                  int id = skipOverlapToggleGroup.addButton(option);
                  if(config.skipOverlapMode.equals(option))
                     skipOverlapToggleGroup.check(id);
               }
            }
         }
         if(config.isSetSkipControl()) {
            skipEditText.setError(config.skipControl.error);
            if(config.skipControl.isSetText())
               skipEditText.setText(config.skipControl.text);
         }
         if(config.isSetOverlapControl()) {
            overlapEditText.setError(config.overlapControl.error);
            if(config.overlapControl.isSetText())
               overlapEditText.setText(config.overlapControl.text);
         }

         isProcessing = false;
      }
   }

   private class ConnectTask extends AsyncTask<String, Void, Boolean> {

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         getActivity().setProgressBarIndeterminateVisibility(true);
      }

      @Override
      protected Boolean doInBackground(String... params) {
         try {
            String host = params[0];
            int port = Integer.valueOf(params[1]);

            mTransport = new TSocket(host, port);
            mTransport.open();
            mClient = new MeasurementRuleService.Client(new TBinaryProtocol(mTransport));
            return true;
         } catch (TException e) {
            Log.e(TAG, "Connection exception", e);
         }
         return false;
      }

      @Override
      protected void onPostExecute(Boolean result) {
         getActivity().setProgressBarIndeterminateVisibility(true);
         final String msg = result ? "Connected" : "Connection Failed";
         Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
         Log.i(TAG, msg);
         getLoaderManager().initLoader(VEHICLE_LOADER, null, ImplementMeasurementsFragment.this);
         getLoaderManager().initLoader(IMPLEMENT_LOADER, null, ImplementMeasurementsFragment.this);
      }
   }

   private class DisconnectTask extends AsyncTask<Void, Void, Boolean> {

      @Override
      protected void onPreExecute() {
         super.onPreExecute();
         getActivity().setProgressBarIndeterminateVisibility(true);
      }

      @Override
      protected Boolean doInBackground(Void... params) {
         mTransport.close();
         mTransport = null;
         mClient = null;
         return true;
      }

      @Override
      protected void onPostExecute(Boolean result) {
         getActivity().setProgressBarIndeterminateVisibility(true);
         final String msg = result ? "Disconnected" : "Disconnect Failed";
         Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
         Log.i(TAG, msg);
      }
   }

   private static class SimpleTextWatcher implements TextWatcher {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged(Editable s) {

      }
   }
}

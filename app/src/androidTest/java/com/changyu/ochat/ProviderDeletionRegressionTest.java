package com.changyu.ochat;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProviderDeletionRegressionTest {
 private JSONObject provider(String id,String model)throws Exception{
  return new JSONObject().put("id",id).put("name",id).put("api","https://"+id+".invalid/v1").put("apiKey","key-"+id)
   .put("protocol",ChatApiClient.CHAT_COMPLETIONS).put("selectedModelId",model).put("models",new JSONArray().put(ModelStore.model(model)));
 }
 @Test public void deletingLastProviderClearsActiveValuesAndMigrationDoesNotRecreateIt()throws Exception{
  SharedPreferences prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("provider-delete-test",0);prefs.edit().clear().commit();
  try{
   JSONObject provider=provider("only","m1");ModelStore.activate(prefs,new JSONArray().put(provider),provider);
   prefs.edit().putString("sessions","keep-history").putString("models_only","cached").putBoolean("topicEnabled",true).putString("topicProviderId","only").commit();
   ModelStore.deleteProvider(prefs,"only");ModelStore.migrate(prefs);
   assertEquals(0,ModelStore.read(prefs).length());assertEquals("",prefs.getString("providerId",""));assertEquals("",prefs.getString("apiKey",""));assertEquals("",prefs.getString("modelId",""));
   assertEquals("keep-history",prefs.getString("sessions",""));assertFalse(prefs.contains("models_only"));assertFalse(prefs.getBoolean("topicEnabled",true));
  }finally{prefs.edit().clear().commit();}
 }
 @Test public void activeDeletionFallsBackAndNonActiveDeletionPreservesCurrent()throws Exception{
  SharedPreferences prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("provider-delete-test",0);prefs.edit().clear().commit();
  try{
   JSONObject first=provider("first","m1"),second=provider("second","m2"),third=provider("third","m3");
   ModelStore.activate(prefs,new JSONArray().put(first).put(second).put(third),first);ModelStore.deleteProvider(prefs,"third");
   assertEquals("first",prefs.getString("providerId",""));assertEquals("key-first",prefs.getString("apiKey",""));
   ModelStore.deleteProvider(prefs,"first");
   assertEquals("second",prefs.getString("providerId",""));assertEquals("m2",prefs.getString("modelId",""));assertEquals("key-second",prefs.getString("apiKey",""));
   JSONObject fresh=provider("new","m4");ModelStore.saveProvider(prefs,fresh);
   assertEquals(2,ModelStore.read(prefs).length());assertEquals("new",prefs.getString("providerId",""));
  }finally{prefs.edit().clear().commit();}
 }
}

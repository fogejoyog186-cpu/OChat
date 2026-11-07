package com.changyu.ochat;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TopicModelChoiceRegressionTest {
 private Spinner find(View view,String description){
  if(view instanceof Spinner&&description.contentEquals(view.getContentDescription()))return(Spinner)view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Spinner result=find(((ViewGroup)view).getChildAt(i),description);if(result!=null)return result;}return null;
 }
 private JSONObject provider(String id,String... models)throws Exception{
  JSONArray configured=new JSONArray();for(String model:models)configured.put(new JSONObject().put("id",model).put("displayName","显示 "+model));
  return new JSONObject().put("id",id).put("name","供应商 "+id).put("selectedModelId",models.length==0?"":models[0]).put("models",configured);
 }
 @Test public void providerSwitchUsesOnlyItsConfiguredModelsAndDoesNotSaveDraft()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("topic-choice-test",0);
  prefs.edit().clear().putString("providers",new JSONArray().put(provider("a","a1","a2")).put(provider("b","b1")).toString()).putString("providerId","a").putString("topicProviderId","__current__").putString("topicModel","a2").commit();
  try{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   TopicModelChoices choices=new TopicModelChoices(context,prefs);assertEquals("a2",choices.modelId());assertTrue(choices.validSelection());
   Spinner model=find(choices,"命名模型");assertNotNull(model);assertTrue(model instanceof ChoiceSpinner);
   assertTrue(model.getSelectedItem().toString().contains("显示 a2"));
   Spinner provider=find(choices,"命名供应商");provider.setSelection(2);provider.getOnItemSelectedListener().onItemSelected(provider,null,2,2);
   assertEquals("b",choices.providerId());assertEquals("b1",choices.modelId());assertTrue(choices.validSelection());
   for(int i=0;i<model.getAdapter().getCount();i++)assertFalse(model.getAdapter().getItem(i).toString().contains("a1"));
   assertEquals("a2",prefs.getString("topicModel",""));assertEquals("__current__",prefs.getString("topicProviderId",""));
  });}finally{prefs.edit().clear().commit();}
 }
 @Test public void emptyProviderRefreshesAfterModelIsAdded()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("topic-choice-test",0);
  prefs.edit().clear().putString("providers",new JSONArray().put(provider("empty")).toString()).putString("topicProviderId","empty").commit();
  try{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   TopicModelChoices choices=new TopicModelChoices(context,prefs);assertFalse(choices.validSelection());assertFalse(find(choices,"命名模型").isEnabled());
   try{prefs.edit().putString("providers",new JSONArray().put(provider("empty","added")).toString()).commit();}catch(Exception error){throw new AssertionError(error);}
   choices.refresh();assertTrue(choices.validSelection());assertEquals("added",choices.modelId());assertTrue(find(choices,"命名模型").isEnabled());
  });}finally{prefs.edit().clear().commit();}
 }
 @Test public void removedSavedModelRequiresReselection()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("topic-choice-test",0);
  prefs.edit().clear().putString("providers",new JSONArray().put(provider("a","a1")).toString()).putString("topicProviderId","a").putString("topicModel","removed").commit();
  try{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   TopicModelChoices choices=new TopicModelChoices(context,prefs);assertFalse(choices.validSelection());assertEquals("",choices.modelId());assertEquals("removed",prefs.getString("topicModel",""));
  });}finally{prefs.edit().clear().commit();}
 }
}

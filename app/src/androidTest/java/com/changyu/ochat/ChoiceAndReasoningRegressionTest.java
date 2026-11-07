package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChoiceAndReasoningRegressionTest {
 private Context context;private SharedPreferences prefs,appPrefs;private Map<String,?> backup;
 @Before public void before()throws Exception{
  context=InstrumentationRegistry.getInstrumentation().getTargetContext();appPrefs=context.getSharedPreferences("chat",0);SessionRepository.get(appPrefs).flush();backup=new HashMap<>(appPrefs.getAll());appPrefs.edit().clear().commit();
  prefs=context.getSharedPreferences("choice-reasoning-test",0);SessionRepository.get(prefs).flush();prefs.edit().clear().commit();
 }
 @After public void after()throws Exception{
  SessionRepository.get(prefs).flush();prefs.edit().clear().commit();SessionRepository.get(appPrefs).flush();SharedPreferences.Editor e=appPrefs.edit().clear();
  for(Map.Entry<String,?> entry:backup.entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();
 }
 private ActivityScenario<MainActivity> screen(){return ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("resumeGeneration",true));}
 private ListView list(View view){if(view instanceof ListView)return(ListView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ListView found=list(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
 private int color(View view){Drawable background=view.getBackground();background.jumpToCurrentState();Bitmap bitmap=Bitmap.createBitmap(240,120,Bitmap.Config.ARGB_8888);try{background.setBounds(0,0,240,120);background.draw(new Canvas(bitmap));return bitmap.getPixel(120,60);}finally{bitmap.recycle();}}
 private View row(ListView list,int position,View recycled){return list.getAdapter().getView(position,recycled,list);}
 private void click(ListView list,int position,View view){list.performItemClick(view,position,list.getAdapter().getItemId(position));}
 private JSONObject provider()throws Exception{return new JSONObject().put("id","p").put("name","test").put("api","https://unused.invalid/v1").put("apiKey","test").put("protocol",ChatApiClient.CHAT_COMPLETIONS).put("models",new JSONArray().put(ModelStore.model("gpt-6.1-sol")).put(ModelStore.model("gemini-3-pro-preview"))).put("selectedModelId","gpt-6.1-sol");}
 private void install()throws Exception{JSONObject p=provider();ModelStore.activate(prefs,new JSONArray().put(p),p);}
 @Test public void singleChoiceClearsOldBackgroundEvenWhenListViewChangesSelectedState(){
  try(ActivityScenario<MainActivity> scenario=screen()){scenario.onActivity(a->{
   ThemePalette palette=ThemePalette.resolve(a);AtomicInteger committed=new AtomicInteger(0);PanelDialog dialog=ChoiceDialogs.single(a,"选择",new String[]{"first","second","third"},0,committed::set);
   ListView list=list(dialog.getWindow().getDecorView());View first=row(list,0,null),second=row(list,1,null);
   assertNotEquals(palette.surface,color(first));assertEquals(palette.surface,color(second));click(list,1,second);
   // AbsListView can alter selected independently of our pending choice.
   first.setSelected(false);second.setSelected(true);first=row(list,0,first);second=row(list,1,second);
   assertEquals("old highlight must disappear",palette.surface,color(first));assertNotEquals(palette.surface,color(second));
   assertFalse(((CompoundButton)((ViewGroup)first).getChildAt(1)).isChecked());assertTrue(((CompoundButton)((ViewGroup)second).getChildAt(1)).isChecked());
   assertEquals(0,committed.get());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(1,committed.get());
  });}
 }
 @Test public void multipleChoiceClearsUncheckedHighlightAndRecycledRows(){
  try(ActivityScenario<MainActivity> scenario=screen()){scenario.onActivity(a->{
   ThemePalette p=ThemePalette.resolve(a);boolean[] original={true,false,true};PanelDialog dialog=ChoiceDialogs.multiple(a,"选择",new String[]{"a","b","c"},original,v->{});
   ListView list=list(dialog.getWindow().getDecorView());View first=row(list,0,null);assertNotEquals(p.surface,color(first));click(list,0,first);first.setSelected(false);first=row(list,0,first);assertEquals(p.surface,color(first));
   View reused=row(list,2,first);assertNotEquals(p.surface,color(reused));reused.setSelected(false);reused=row(list,1,reused);assertEquals(p.surface,color(reused));dialog.cancel();assertTrue(original[0]);assertTrue(original[2]);assertFalse(original[1]);
  });}
 }
 @Test public void modelPickerRepaintsAndOnlyConfirmedSwitchResetsEffort()throws Exception{
  install();prefs.edit().putString("reasoningEffort","high").commit();
  try(ActivityScenario<MainActivity> scenario=screen()){scenario.onActivity(a->{
   AtomicInteger changes=new AtomicInteger();PanelDialog cancelled=ModelPicker.show(a,prefs,changes::incrementAndGet,()->{});ListView list=list(cancelled.getWindow().getDecorView());View first=row(list,1,null),second=row(list,2,null);ThemePalette p=ThemePalette.resolve(a);
   click(list,2,second);first.setSelected(false);second.setSelected(true);first=row(list,1,first);second=row(list,2,second);assertEquals(p.surface,color(first));assertNotEquals(p.surface,color(second));
   cancelled.cancel();assertEquals("gpt-6.1-sol",prefs.getString("modelId",""));assertEquals("high",prefs.getString("reasoningEffort",""));assertEquals(0,changes.get());
   PanelDialog confirmed=ModelPicker.show(a,prefs,changes::incrementAndGet,()->{});ListView next=list(confirmed.getWindow().getDecorView());click(next,2,row(next,2,null));confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
   assertEquals("gemini-3-pro-preview",prefs.getString("modelId",""));assertEquals("default",prefs.getString("reasoningEffort",""));assertEquals(1,changes.get());
  });}
 }
 @Test public void eachActualModelSwitchStartsAutomaticIncludingReturningToPreviousModel()throws Exception{
  install();prefs.edit().putString("reasoningEffort","high").commit();JSONObject p=ModelStore.read(prefs).getJSONObject(0);p.put("selectedModelId","gemini-3-pro-preview");ModelStore.activate(prefs,new JSONArray().put(p),p);assertEquals("default",prefs.getString("reasoningEffort",""));
  prefs.edit().putString("reasoningEffort","low").commit();p.put("selectedModelId","gpt-6.1-sol");ModelStore.activate(prefs,new JSONArray().put(p),p);assertEquals("default",prefs.getString("reasoningEffort",""));
 }
 @Test public void staleSavedEffortIsSanitizedOnReentryWhileValidSameModelChoicePersists()throws Exception{
  install();JSONObject session=new JSONObject().put("id","s").put("messages",new JSONArray());SessionConfig first=new SessionConfig(prefs,session);
  assertEquals("default",first.getString("reasoningEffort",""));first.edit().putString("reasoningEffort","medium").apply();assertEquals("medium",new SessionConfig(prefs,session).getString("reasoningEffort",""));
  first.values.put("reasoningEffort","none");assertEquals("default",new SessionConfig(prefs,session).getString("reasoningEffort",""));
  first.edit().putString("reasoningEffort","high").apply();first.edit().putString("modelId","gemini-3-pro-preview").apply();assertEquals("default",first.getString("reasoningEffort",""));first.edit().putString("reasoningEffort","xhigh").apply();assertEquals("default",first.getString("reasoningEffort",""));
 }
 @Test public void initialSessionIgnoresPreviousGlobalManualSetting()throws Exception{
  install();prefs.edit().putString("reasoningEffort","high").commit();JSONObject session=new JSONObject().put("id","new").put("messages",new JSONArray());SessionConfig config=new SessionConfig(prefs,session);assertEquals("default",config.getString("reasoningEffort",""));
  JSONObject legacy=new JSONObject().put("id","legacy").put("messages",new JSONArray()).put("config",new JSONObject().put("providerId","p").put("modelId","gemini-3-pro-preview").put("reasoningEffort","max"));
  assertEquals("default",new SessionConfig(prefs,legacy).getString("reasoningEffort",""));
 }
}

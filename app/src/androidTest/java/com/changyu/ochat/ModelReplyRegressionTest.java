package com.changyu.ochat;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Global defaults must not overwrite an existing session, and request IDs belong in details. */
@RunWith(AndroidJUnit4.class)
public class ModelReplyRegressionTest {
 @Test public void labelsAndReplyMetadataSurviveModelChangesAndRestore()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("chat",0);SessionRepository.get(prefs).flush();Map<String,?> backup=new HashMap<>(prefs.getAll());
  try{
   JSONObject model=ModelStore.model("request-model").put("displayName","Custom name"),provider=new JSONObject().put("id","p").put("name","Test provider").put("api","https://unused.invalid/v1").put("apiKey","test").put("models",new JSONArray().put(model)).put("selectedModelId","request-model");
   prefs.edit().clear().commit();ModelStore.activate(prefs,new JSONArray().put(provider),provider);
   prefs.edit().putString("sessions",new JSONArray().put(new JSONObject().put("id","regression").put("title","test").put("messages",new JSONArray())).toString()).putString("currentSessionId","regression").commit();
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("resumeGeneration",true))){
    screen.onActivity(activity->{try{
     assertTrue(((TextView)field(activity,"modelSelector")).getText().toString().contains("Custom name"));
     Class<?> type=Class.forName("com.changyu.ochat.MainActivity$ReplyContext");Constructor<?> constructor=type.getDeclaredConstructor(String.class,String.class,String.class);constructor.setAccessible(true);Object reply=constructor.newInstance("regression","request-model","low");
     prefs.edit().putString("modelId","new-model").putString("reasoningEffort","high").apply();
     assertTrue(((TextView)field(activity,"modelSelector")).getText().toString().contains("Custom name"));assertFalse(((TextView)field(activity,"modelLabel")).getText().toString().contains("high"));
     Method complete=MainActivity.class.getDeclaredMethod("completeReply",type,String.class,boolean.class);complete.setAccessible(true);complete.invoke(activity,reply,"reply",false);complete.invoke(activity,reply,"duplicate",false);
     JSONObject session=MessageEdits.findSession(SessionRepository.get(prefs).read(),"regression");JSONArray messages=session.getJSONArray("messages");assertEquals(1,messages.length());assertEquals("request-model",messages.getJSONObject(0).getString("modelId"));assertTrue(messages.getJSONObject(0).getLong("timestamp")>0);
     messages.put(new JSONObject().put("id","legacy").put("text","legacy answer").put("user",false));Method restore=MainActivity.class.getDeclaredMethod("restoreSession",JSONObject.class);restore.setAccessible(true);restore.invoke(activity,session);
     String visible=text((View)field(activity,"messageList"));assertTrue(visible.contains("legacy answer"));assertFalse(visible.contains("request-model"));assertFalse(visible.contains("时间未知"));
    }catch(Exception error){throw new AssertionError(error);}});
   }
  }finally{
   SessionRepository.get(prefs).flush();SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:backup.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);}edit.commit();
  }
 }
 private Object field(Object o,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private String text(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText());if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(text(((ViewGroup)view).getChildAt(i)));return out.toString();}
}

package com.changyu.ochat;

import android.content.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConversationEntryRegressionTest {
 private Object field(MainActivity activity,String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
 @Test public void freshEntryStartsNewAndTitleTracksSwitchRenameAndRecreation()throws Exception{
  Assume.assumeTrue(ChatGenerationService.active()==null);
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();android.content.SharedPreferences prefs=context.getSharedPreferences("chat",0);Map<String,?> backup=new HashMap<>(prefs.getAll());
  try{
   JSONObject old=new JSONObject().put("id","old-entry").put("title","旧对话").put("titleManual",true).put("messages",new JSONArray().put(new JSONObject().put("id","m1").put("user",true).put("text","历史消息")));
   prefs.edit().clear().putString("sessions",new JSONArray().put(old).toString()).putString("currentSessionId","old-entry").putString("homeText_headerSubtitle","不应显示的副标题").commit();SessionLaunchPolicy.markTaskRemoved();
   String[] fresh={""};
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(activity->{try{fresh[0]=prefs.getString("currentSessionId","");assertNotEquals("old-entry",fresh[0]);assertEquals("新对话",((TextView)field(activity,"conversationTitle")).getText().toString());}catch(Exception error){throw new AssertionError(error);}});
    scenario.recreate();
    scenario.onActivity(activity->{try{
     assertEquals(fresh[0],prefs.getString("currentSessionId",""));
     Method restore=MainActivity.class.getDeclaredMethod("restoreSession",JSONObject.class);restore.setAccessible(true);restore.invoke(activity,old);
     assertEquals("旧对话",((TextView)field(activity,"conversationTitle")).getText().toString());
     Method rename=MainActivity.class.getDeclaredMethod("updateSession",String.class,String.class);rename.setAccessible(true);rename.invoke(activity,"old-entry","新名字");
     assertEquals("新名字",((TextView)field(activity,"conversationTitle")).getText().toString());
    }catch(Exception error){throw new AssertionError(error);}});
   }
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(activity->{
    assertNotEquals("old-entry",prefs.getString("currentSessionId",""));
    try{assertEquals("新名字",MessageEdits.findSession(new JSONArray(prefs.getString("sessions","[]")),"old-entry").optString("title"));}catch(Exception error){throw new AssertionError(error);}
   });}
  }finally{
   SharedPreferences.Editor editor=prefs.edit().clear();for(Map.Entry<String,?> item:backup.entrySet()){
    Object value=item.getValue();String key=item.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
   }editor.commit();
  }
 }
}

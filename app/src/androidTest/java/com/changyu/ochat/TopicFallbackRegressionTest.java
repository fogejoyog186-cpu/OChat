package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TopicFallbackRegressionTest {
 @Test public void trimmedTwentyCodepointsAndAttachments() throws Exception {
  assertEquals("12345678901234567890",MainActivity.fallbackTitle(message(" 12345678901234567890 ")));
  assertEquals("12345678901234567890",MainActivity.fallbackTitle(message("12345678901234567890extra")));
  String emoji="😀";String twenty=new String(new char[20]).replace("\0",emoji);
  assertEquals(twenty,MainActivity.fallbackTitle(message("  "+twenty+"more  ")));
  assertEquals("附件：notes.txt",MainActivity.fallbackTitle(message("已添加附件").put("attachment","notes.txt")));
  assertEquals("附件对话",MainActivity.fallbackTitle(message("  ")));
 }
 @Test public void disabledUnconfiguredAndUnusableFallbackWithoutRenamingLaterMessages() throws Exception {
  withActivity((activity,prefs)->{
   for(int mode=0;mode<3;mode++){
    prefs.edit().clear().putBoolean("topicEnabled",mode!=0).putString("api",mode==2?"http://":"").putString("apiKey","test").putString("modelId",mode==2?"model":"").commit();
    invoke(activity,"saveCurrentMessage",new Class[]{String.class,boolean.class,String.class},"  first message  ",true,null);
    JSONObject first=session(prefs);assertEquals("first message",first.getString("title"));assertTrue(first.getBoolean("titleFallback"));
    invoke(activity,"saveCurrentMessage",new Class[]{String.class,boolean.class,String.class},"later message",true,null);
    assertEquals("first message",session(prefs).getString("title"));
   }
  });
 }
 @Test public void legacyUnnamedMigratesButManualGeneratedAndLegacyNamedStayProtected() throws Exception {
  withActivity((activity,prefs)->{
   JSONObject s=new JSONObject().put("id","legacy").put("title","新对话").put("messages",new JSONArray().put(message("original message")));
   store(prefs,s);request(activity);assertEquals("original message",session(prefs).getString("title"));
   invoke(activity,"updateSession",new Class[]{String.class,String.class},"legacy","新对话");request(activity);
   assertEquals("新对话",session(prefs).getString("title"));assertFalse(session(prefs).getBoolean("titleFallback"));
   s.put("title","Existing generated").put("titleAutomatic",true);store(prefs,s);request(activity);assertEquals("Existing generated",session(prefs).getString("title"));
   s.remove("titleAutomatic");s.put("title","Legacy custom");store(prefs,s);request(activity);assertEquals("Legacy custom",session(prefs).getString("title"));
  });
 }
 @Test public void configuredPendingFallbackRemainsReplaceableAndManualWins() throws Exception {
  withActivity((activity,prefs)->{
   prefs.edit().putBoolean("topicEnabled",true).putString("api","https://example.com/v1").putString("apiKey","test").putString("topicModel","naming-model").commit();
   assertEquals("naming-model",new TopicConfig(prefs).model);
   JSONObject s=new JSONObject().put("id","legacy").put("title","新对话").put("messages",new JSONArray().put(message("pending message")));store(prefs,s);
   Field f=MainActivity.class.getDeclaredField("topicRequestsInFlight");f.setAccessible(true);((Set<String>)f.get(activity)).add("legacy");
   request(activity);s=session(prefs);assertEquals("pending message",s.getString("title"));assertTrue(MainActivity.canAutomaticallyTitle(s));
   s.put("title","Model result").put("titleAutomatic",true).put("titleFallback",false);assertFalse(MainActivity.canAutomaticallyTitle(s));
   invoke(activity,"updateSession",new Class[]{String.class,String.class},"legacy","Manual title");assertFalse(MainActivity.canAutomaticallyTitle(session(prefs)));
  });
 }
 private interface Checked {void run(MainActivity a,SharedPreferences p)throws Exception;}
 private void withActivity(Checked test)throws Exception {
  // Isolated preferences and an unattached Activity keep these persistence tests offline.
  SharedPreferences prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("topic-fallback-regression",Context.MODE_PRIVATE);
  prefs.edit().clear().commit();Throwable[] failure={null};
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   MainActivity activity=new MainActivity();Field f=MainActivity.class.getDeclaredField("prefs");f.setAccessible(true);f.set(activity,prefs);test.run(activity,prefs);
  }catch(Throwable e){failure[0]=e;}});
  prefs.edit().clear().commit();if(failure[0]!=null)throw new AssertionError(failure[0]);
 }
 private static JSONObject message(String text)throws Exception{return new JSONObject().put("text",text).put("user",true);}
 private static JSONObject session(SharedPreferences p)throws Exception{return new JSONArray(p.getString("sessions","[]")).getJSONObject(0);}
 private static void store(SharedPreferences p,JSONObject s){p.edit().putString("sessions",new JSONArray().put(s).toString()).putString("currentSessionId","legacy").commit();}
 private static void request(MainActivity a)throws Exception{invoke(a,"requestTopicTitle",new Class[]{String.class,String.class},"original message","legacy");}
 private static Object invoke(MainActivity a,String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);}
}

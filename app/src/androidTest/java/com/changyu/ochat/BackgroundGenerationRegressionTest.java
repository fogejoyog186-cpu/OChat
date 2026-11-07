package com.changyu.ochat;

import android.content.*;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Exercise the real service and Activity lifecycle without an external API or network policy. */
@RunWith(AndroidJUnit4.class)
public class BackgroundGenerationRegressionTest {
 static final class Transport extends ChatApiClient {
  final CountDownLatch started=new CountDownLatch(1);volatile StreamCallback callback;int requests;
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){
   this.callback=callback;requests++;started.countDown();
  }
 }
 private void run(boolean stop)throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("chat",0);
  Map<String,?> backup=new HashMap<>(prefs.getAll());Transport transport=new Transport();
  ChatGenerationService.Run[] request=new ChatGenerationService.Run[1];
  try{
   JSONObject session=new JSONObject().put("id","background-test").put("title","test").put("messages",new JSONArray());
   prefs.edit().clear().putString("sessions",new JSONArray().put(session).toString()).putString("currentSessionId","background-test").commit();
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(activity->{
     ChatGenerationService.Run run=new ChatGenerationService.Run("background-test","mock","default",1234);
     run.client=transport;run.base="https://unused.invalid/v1";run.key="test";run.protocol=ChatApiClient.CHAT_COMPLETIONS;
     run.history=new JSONArray();run.session=session;request[0]=run;ChatGenerationService.launch(activity,run);
    });
    assertTrue(transport.started.await(5,TimeUnit.SECONDS));
    scenario.moveToState(Lifecycle.State.CREATED);
    transport.callback.onDelta("后台继续");
    assertFalse(request[0].cancel.isCancelled());
    scenario.moveToState(Lifecycle.State.RESUMED);scenario.recreate();
    assertSame(request[0],ChatGenerationService.active());assertFalse(request[0].cancel.isCancelled());assertEquals(1,transport.requests);
    transport.callback.onDelta("生成");
    if(stop)scenario.onActivity(activity->ChatGenerationService.stop(activity,request[0]));else transport.callback.onDone();
    long until=android.os.SystemClock.uptimeMillis()+5000;
    while(ChatGenerationService.active()!=null&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    assertNull(ChatGenerationService.active());assertTrue(request[0].cancel.isCancelled());
    JSONArray messages=new JSONArray(prefs.getString("sessions","[]")).getJSONObject(0).getJSONArray("messages");
    assertEquals(1,messages.length());assertEquals("后台继续生成",messages.getJSONObject(0).getString("text"));
    assertEquals(stop,messages.getJSONObject(0).getBoolean("interrupted"));
   }
  }finally{
   if(ChatGenerationService.active()!=null){ChatGenerationService.stop(context,ChatGenerationService.active());Thread.sleep(200);}
   SharedPreferences.Editor editor=prefs.edit().clear();
   for(Map.Entry<String,?> item:backup.entrySet()){
    Object value=item.getValue();String key=item.getKey();
    if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
    else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
    else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
   }
   editor.commit();
  }
 }
 @Test public void generationSurvivesBackgroundAndActivityRecreation()throws Exception{run(false);}
 @Test public void stopKeepsPartialTextExactlyOnce()throws Exception{run(true);}
}

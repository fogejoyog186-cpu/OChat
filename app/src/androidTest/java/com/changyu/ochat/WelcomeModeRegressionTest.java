package com.changyu.ochat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WelcomeModeRegressionTest {
 @Test public void exclusiveRenderingAndSettingsPreserveAndResetDrafts() throws Exception {
  Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  Context context=instrumentation.getTargetContext();
  SharedPreferences prefs=context.getSharedPreferences("chat",Context.MODE_PRIVATE);
  Map<String,?> backup=new HashMap<>(prefs.getAll());
  Activity home=null,settings=null;
  try {
   prefs.edit().clear().putString("homeText_welcomeTitle","Welcome title test").putString("homeText_welcomeBody","Welcome body test").putString("welcomeImageUri","content://missing/image").putString("welcomeImageShape","square").commit();
   home=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Activity screen=home;
   onMain(instrumentation,()->{
    assertFalse(HomeCopy.isIconMode(prefs));
    String text=renderHome(screen);assertTrue(text.contains("Welcome title test"));assertTrue(text.contains("Welcome body test"));assertFalse(text.contains("✦"));
    prefs.edit().putString(HomeCopy.WELCOME_DISPLAY_MODE,"icon").commit();
    text=renderHome(screen);assertTrue(text.contains("✦"));assertFalse(text.contains("Welcome title test"));assertFalse(text.contains("Welcome body test"));
   });
   settings=instrumentation.startActivitySync(new Intent(context,SettingsOptionActivity.class).putExtra("mode","theme").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Activity options=settings;
   onMain(instrumentation,()->{
    View[] controls=(View[])field(options,"copyControls");assertEquals(View.VISIBLE,((View)field(options,"iconControls")).getVisibility());assertEquals(View.GONE,controls[3].getVisibility());assertEquals(View.VISIBLE,controls[0].getVisibility());
    RadioGroup modes=(RadioGroup)field(options,"welcomeModes");modes.check(((View)field(options,"textMode")).getId());
    assertEquals(View.GONE,((View)field(options,"iconControls")).getVisibility());assertEquals(View.VISIBLE,controls[3].getVisibility());assertEquals("content://missing/image",field(options,"welcomeUri"));assertEquals("square",field(options,"welcomeShape"));
    assertEquals("Welcome title test",((EditText[])field(options,"homeCopyFields"))[3].getText().toString());
    modes.check(modes.getChildAt(1).getId());
    String preview=text((View)field(options,"homeWelcomePreview"));assertTrue(preview.contains("✦"));
    TextView reset=find(options.getWindow().getDecorView(),"恢复默认欢迎内容");assertNotNull(reset);reset.performClick();
    assertEquals("text",field(options,"welcomeDisplayMode"));assertEquals("",field(options,"welcomeUri"));assertEquals("circle",field(options,"welcomeShape"));assertEquals(HomeCopy.DEFAULTS[3],((EditText[])field(options,"homeCopyFields"))[3].getText().toString());
    TextView save=find(options.getWindow().getDecorView(),"保存并重启");assertNotNull(save);save.performClick();
    assertEquals("text",prefs.getString(HomeCopy.WELCOME_DISPLAY_MODE,""));assertEquals("",prefs.getString("welcomeImageUri","missing"));assertEquals("circle",prefs.getString("welcomeImageShape",""));assertEquals(HomeCopy.DEFAULTS[3],HomeCopy.get(prefs,"welcomeTitle"));
   });
  } finally {
   if(settings!=null){Activity a=settings;instrumentation.runOnMainSync(a::finish);}
   if(home!=null){Activity a=home;instrumentation.runOnMainSync(a::finish);}
   instrumentation.waitForIdleSync();SharedPreferences.Editor edit=prefs.edit().clear();
   for(Map.Entry<String,?> entry:backup.entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);}
   edit.commit();
  }
 }
 private interface Checked {void run() throws Exception;}
 private static void onMain(Instrumentation i,Checked test){Throwable[] failure={null};i.runOnMainSync(()->{try{test.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
 private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static String renderHome(Activity a)throws Exception{Method m=MainActivity.class.getDeclaredMethod("buildConversation");m.setAccessible(true);return text((View)m.invoke(a));}
 private static String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int j=0;j<g.getChildCount();j++)s.append(text(g.getChildAt(j)));}return s.toString();}
 private static TextView find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int j=0;j<g.getChildCount();j++){TextView found=find(g.getChildAt(j),label);if(found!=null)return found;}}return null;}
}

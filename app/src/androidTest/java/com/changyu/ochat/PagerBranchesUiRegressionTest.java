package com.changyu.ochat;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.viewpager2.widget.ViewPager2;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PagerBranchesUiRegressionTest {
 private Object field(Activity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 private void swipe(Instrumentation ins,ViewPager2 pager,boolean right){android.os.SystemClock.sleep(350);ins.waitForIdleSync();int[] bounds=new int[2];ins.runOnMainSync(()->pager.getLocationOnScreen(bounds));float start=bounds[0]+pager.getWidth()*(right?.25f:.75f),end=bounds[0]+pager.getWidth()*(right?.75f:.25f),y=bounds[1]+pager.getHeight()*.45f;long down=android.os.SystemClock.uptimeMillis();for(int i=0;i<=24;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==24?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,start+(end-start)*i/24f,y,0);ins.sendPointerSync(e);e.recycle();android.os.SystemClock.sleep(16);}android.os.SystemClock.sleep(500);ins.waitForIdleSync();}
 private View described(View v,String desc){if(desc.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View hit=described(g.getChildAt(i),desc);if(hit!=null)return hit;}}return null;}
 @Test public void fullPageSwipeArticleAndBranchGuard()throws Exception{
  Instrumentation ins=InstrumentationRegistry.getInstrumentation();Context c=ins.getTargetContext();SharedPreferences prefs=c.getSharedPreferences("chat",0);Map<String,?> backup=new HashMap<>(prefs.getAll());Activity activity=null;
  try{
   JSONObject s=new JSONObject().put("id","pager-fixture").put("title","manual").put("titleManual",true).put("messages",new JSONArray().put(new JSONObject().put("id","u").put("user",true).put("text","question")).put(new JSONObject().put("id","a").put("user",false).put("text","article").put("modelId","fixture").put("timestamp",123)));
   ConversationBranches.edit(new JSONArray().put(s),"pager-fixture","u","revised",false);
   prefs.edit().clear().putString("sessions",new JSONArray().put(s).toString()).putString("currentSessionId","pager-fixture").commit();
   activity=ins.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity a=activity;ins.waitForIdleSync();ViewPager2 pager=(ViewPager2)field(a,"pager");
   swipe(ins,pager,true);ins.waitForIdleSync();ins.runOnMainSync(()->{assertEquals(0,pager.getCurrentItem());View settings=described(a.getWindow().getDecorView(),"打开设置");assertNotNull(settings);assertTrue(settings.isShown());assertFalse(settings.getParent() instanceof ScrollView);});
   swipe(ins,pager,false);ins.waitForIdleSync();ins.runOnMainSync(()->{assertEquals(1,pager.getCurrentItem());try{
    LinearLayout list=(LinearLayout)field(a,"messageList");LinearLayout article=(LinearLayout)list.getChildAt(2);assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,article.getChildAt(0).getLayoutParams().width);assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,((LinearLayout)article.getChildAt(0)).getChildAt(0).getLayoutParams().width);
    View previous=described(list,"上一个消息版本");assertNotNull(previous);assertTrue(previous.isEnabled());LinearLayout nav=(LinearLayout)previous.getParent();LinearLayout slot=(LinearLayout)nav.getParent();assertEquals("revision-slot",slot.getTag());assertEquals(1,slot.getChildCount());LinearLayout actions=list.findViewWithTag("message-actions");assertEquals(3,actions.getChildCount());assertEquals(actions.getHeight(),nav.getHeight());int[] navPos=new int[2],actionPos=new int[2];nav.getLocationOnScreen(navPos);actions.getLocationOnScreen(actionPos);assertTrue(navPos[0]+nav.getWidth()<=actionPos[0]);assertFalse(described(list,"下一个消息版本").isEnabled());previous.performClick();JSONArray latest=new JSONArray(prefs.getString("sessions","[]"));assertEquals("question",latest.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getString("text"));
    Field sending=MainActivity.class.getDeclaredField("sending");sending.setAccessible(true);sending.setBoolean(a,true);Method switchVersion=MainActivity.class.getDeclaredMethod("switchVersion",String.class,int.class);switchVersion.setAccessible(true);switchVersion.invoke(a,"pager-fixture",1);assertEquals("question",new JSONArray(prefs.getString("sessions","[]")).getJSONObject(0).getJSONArray("messages").getJSONObject(0).getString("text"));sending.setBoolean(a,false);
   }catch(Exception e){throw new AssertionError(e);}});
  }finally{
   if(activity!=null){Activity a=activity;ins.runOnMainSync(a::finish);ins.waitForIdleSync();}
   SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:backup.entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();
  }
 }
}

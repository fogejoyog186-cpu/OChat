package com.changyu.ochat;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReasoningSliderRegressionTest {
 private <T extends View> T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T result=find(((ViewGroup)view).getChildAt(i),type);if(result!=null)return result;}return null;
 }
 @Test public void firstLowEffortStillAnimatesAndThumbUsesTheSameTouchRange()throws Exception{
  org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled());
  final PanelDialog[] dialog={null};final ReasoningSlider[] slider={null};
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{ModelCapabilities caps=new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,new org.json.JSONObject());dialog[0]=ReasoningSheet.show(activity,"GPT",caps,"low",v->{});slider[0]=find(dialog[0].getWindow().getDecorView(),ReasoningSlider.class);});
   android.os.SystemClock.sleep(250);androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
   scenario.onActivity(activity->{try{
    assertEquals(0,slider[0].getProgress());java.lang.reflect.Field field=ReasoningSlider.class.getDeclaredField("flow");field.setAccessible(true);ReasoningParticleFlow flow=(ReasoningParticleFlow)field.get(slider[0]);assertTrue("Low is a thinking level, not Off",flow.distanceDp()>0);
    touch(slider[0],slider[0].getWidth()-slider[0].getPaddingRight());assertEquals(slider[0].getMax(),slider[0].getProgress());
    touch(slider[0],slider[0].getPaddingLeft());assertEquals("first visible stop must select Low",0,slider[0].getProgress());dialog[0].dismiss();
   }catch(Exception error){throw new AssertionError(error);}});
  }
 }
 private void touch(ReasoningSlider slider,float x){long now=android.os.SystemClock.uptimeMillis();for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,action,x,slider.getHeight()/2f,0);slider.dispatchTouchEvent(event);event.recycle();}}
 @Test public void sliderSupportsDefaultAndOnlyCommitsOnConfirm(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(activity->{
   AtomicReference<String> value=new AtomicReference<>("low");ModelCapabilities gpt=new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,new org.json.JSONObject());ModelCapabilities claude=new ModelCapabilities("claude-opus-4-6",ChatApiClient.ANTHROPIC,new org.json.JSONObject());
   PanelDialog cancelled=ReasoningSheet.show(activity,"GPT",gpt,value.get(),value::set);
   SeekBar slider=find(cancelled.getWindow().getDecorView(),SeekBar.class);assertNotNull(slider);assertEquals(4,slider.getMax());slider.setProgress(3);
   assertEquals("low",value.get());cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals("low",value.get());
   PanelDialog confirmed=ReasoningSheet.show(activity,"GPT",gpt,value.get(),value::set);
   find(confirmed.getWindow().getDecorView(),SeekBar.class).setProgress(3);confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("xhigh",value.get());
   PanelDialog defaults=ReasoningSheet.show(activity,"Claude",claude,"high",value::set);
   assertEquals(4,find(defaults.getWindow().getDecorView(),SeekBar.class).getMax());
   find(defaults.getWindow().getDecorView(),SwitchCompat.class).setChecked(true);defaults.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("default",value.get());
  });}
 }
}

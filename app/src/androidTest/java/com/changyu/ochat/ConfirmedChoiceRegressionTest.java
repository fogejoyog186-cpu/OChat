package com.changyu.ochat;

import android.app.AlertDialog;
import android.view.*;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConfirmedChoiceRegressionTest {
 private ListView list(View view){
  if(view instanceof ListView)return(ListView)view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ListView child=list(((ViewGroup)view).getChildAt(i));if(child!=null)return child;}return null;
 }
 private void choose(PanelDialog dialog,int index){ListView list=list(dialog.getWindow().getDecorView());assertNotNull(list);list.performItemClick(list.getAdapter().getView(index,null,list),index,index);}
 @Test public void singleChoiceOnlyCommitsWhenConfirmed(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(activity->{
   AtomicInteger selected=new AtomicInteger(0);String[] labels={"Chat Completions","Responses"};
   PanelDialog cancelled=ChoiceDialogs.single(activity,"协议",labels,0,selected::set);choose(cancelled,1);
   assertEquals(0,selected.get());assertNotNull(cancelled.getButton(AlertDialog.BUTTON_NEGATIVE));cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,selected.get());
   PanelDialog confirmed=ChoiceDialogs.single(activity,"协议",labels,0,selected::set);choose(confirmed,1);confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(1,selected.get());
  });}
 }
 @Test public void multipleChoiceDoesNotMutateInitialFlagsOnCancel(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(activity->{
   boolean[] original={true,false};AtomicInteger callbacks=new AtomicInteger();
   PanelDialog cancelled=ChoiceDialogs.multiple(activity,"模型",new String[]{"a","b"},original,values->callbacks.incrementAndGet());choose(cancelled,1);cancelled.cancel();
   assertTrue(original[0]);assertFalse(original[1]);assertEquals(0,callbacks.get());
   PanelDialog confirmed=ChoiceDialogs.multiple(activity,"模型",new String[]{"a","b"},original,values->{assertTrue(values[0]);assertTrue(values[1]);callbacks.incrementAndGet();});choose(confirmed,1);confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(1,callbacks.get());
  });}
 }
}

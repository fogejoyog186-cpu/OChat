package com.changyu.ochat;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProtocolAndToolSettingsRegressionTest {
 private <T extends View>T find(View view,Class<T> type){if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T match=find(((ViewGroup)view).getChildAt(i),type);if(match!=null)return match;}return null;}
 private TextView text(View view,String value){if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView match=text(((ViewGroup)view).getChildAt(i),value);if(match!=null)return match;}return null;}
 private EditText api(SettingsActivity activity){try{Field field=SettingsActivity.class.getDeclaredField("api");field.setAccessible(true);return (EditText)field.get(activity);}catch(Exception error){throw new AssertionError(error);}}
 private void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
 @Test public void protocolChoiceShowsRootExamplesAndPreservesCustomGateway()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences();ActivityScenario<SettingsActivity> screen=ActivityScenario.launch(new Intent(state.context,SettingsActivity.class).putExtra("newProvider",true))){
   screen.onActivity(activity->find(activity.getWindow().getDecorView(),Spinner.class).setSelection(2));idle();
   screen.onActivity(activity->{assertEquals("https://api.anthropic.com",api(activity).getText().toString());assertTrue(((TextView)activity.getWindow().getDecorView().findViewWithTag("protocol-address-hint")).getText().toString().contains("/v1/messages"));find(activity.getWindow().getDecorView(),Spinner.class).setSelection(3);});idle();
   screen.onActivity(activity->{assertEquals("https://generativelanguage.googleapis.com",api(activity).getText().toString());assertTrue(((TextView)activity.getWindow().getDecorView().findViewWithTag("protocol-address-hint")).getText().toString().contains("/v1beta"));find(activity.getWindow().getDecorView(),Spinner.class).setSelection(0);});idle();
   screen.onActivity(activity->{assertEquals("https://api.openai.com/v1",api(activity).getText().toString());api(activity).setText("https://proxy.example.org/custom");find(activity.getWindow().getDecorView(),Spinner.class).setSelection(2);});idle();
   screen.onActivity(activity->assertEquals("https://proxy.example.org/custom",api(activity).getText().toString()));
  }
 }
 @Test public void toolOptInsSurviveRotationAndOnlySaveOnConfirmation()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences();ActivityScenario<SettingsOptionActivity> screen=ActivityScenario.launch(new Intent(state.context,SettingsOptionActivity.class).putExtra("mode","search"))){
   screen.onActivity(activity->{View root=activity.getWindow().getDecorView();find(root,Spinner.class).setSelection(1);((SwitchCompat)root.findViewWithTag("builtin-url-context")).setChecked(true);((SwitchCompat)root.findViewWithTag("builtin-code-execution")).setChecked(true);});
   assertFalse(state.prefs.getBoolean(SearchOptions.CODE_PREF,false));screen.recreate();idle();
   screen.onActivity(activity->{View root=activity.getWindow().getDecorView();assertTrue(((SwitchCompat)root.findViewWithTag("builtin-url-context")).isChecked());assertTrue(((SwitchCompat)root.findViewWithTag("builtin-code-execution")).isChecked());assertEquals(1,find(root,Spinner.class).getSelectedItemPosition());text(root,"保存并返回").performClick();});idle();
   assertEquals(SearchOptions.BUILTIN,state.prefs.getString(SearchOptions.PREF,""));assertTrue(state.prefs.getBoolean(SearchOptions.URL_PREF,false));assertTrue(state.prefs.getBoolean(SearchOptions.CODE_PREF,false));
  }
 }
}

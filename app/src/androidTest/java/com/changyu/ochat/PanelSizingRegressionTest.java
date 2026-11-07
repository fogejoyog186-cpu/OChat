package com.changyu.ochat;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Check measured content, not just Window.LayoutParams: framework alert wrappers may shrink it. */
@RunWith(AndroidJUnit4.class)
public class PanelSizingRegressionTest {
 private View root(PanelDialog dialog){
  try{Field field=PanelDialog.class.getDeclaredField("root");field.setAccessible(true);return(View)field.get(dialog);}
  catch(Exception e){throw new AssertionError(e);}
 }
 @Test public void editorAndPreviewActuallyFillThreeQuarterWindow(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   for(boolean preview:new boolean[]{false,true}){
    PanelDialog[] shown=new PanelDialog[1];View[] body=new View[1];int[] expected=new int[1];
    scenario.onActivity(activity->{
     Rect frame=new Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
     expected[0]=Math.round(frame.height()*.75f);
     if(preview)body[0]=new WebView(activity);
     else{EditText editor=new EditText(activity);editor.setShowSoftInputOnFocus(false);body[0]=editor;}
     shown[0]=new PanelDialog(activity,"Panel","Sizing regression",body[0]);
     shown[0].action(AlertDialog.BUTTON_POSITIVE,"完成",null);
     shown[0].getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
     shown[0].show();
    });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    try{scenario.onActivity(activity->{
     int tolerance=UiStyle.dp(activity,4);
     assertEquals(expected[0],shown[0].getWindow().getAttributes().height,tolerance);
     assertEquals(expected[0],root(shown[0]).getHeight(),tolerance);
     assertTrue("Content must use the large panel instead of wrapping its intrinsic height",body[0].getHeight()>expected[0]*.45f);
    });}finally{scenario.onActivity(activity->{shown[0].dismiss();if(body[0] instanceof WebView)((WebView)body[0]).destroy();});}
   }
  }
 }
}

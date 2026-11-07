package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.widget.EditText;
import androidx.core.graphics.ColorUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EditorDialogThemeRegressionTest {
 @Test public void darkAndLightEditorsAreThemedBeforeShow(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();android.content.SharedPreferences prefs=c.getSharedPreferences("chat",0);
   String previous=prefs.getString("themeMode",null),previousText=prefs.getString("themeText",null),previousSurface=prefs.getString("themeSurface",null);
   try{for(String mode:new String[]{"dark","light"}){
    prefs.edit().putString("themeMode",mode).remove("themeText").remove("themeSurface").commit();
    Context themed=EditorDialogs.themedContext(c);EditText editor=EditorDialogs.editor(themed);editor.setText("unsaved draft");
    AlertDialog dialog=new AlertDialog.Builder(themed).setTitle("Editor").setView(editor).setPositiveButton("Apply",null).setNegativeButton("Cancel",null).create();
    EditorDialogs.prepare(dialog,editor);assertFalse(dialog.isShowing());
    ThemePalette p=ThemePalette.resolve(c);
    assertEquals(p.surface,((ColorDrawable)dialog.getWindow().getDecorView().getBackground()).getColor());
    assertEquals(p.input,((ColorDrawable)editor.getBackground()).getColor());assertEquals(p.ink,editor.getCurrentTextColor());
    assertTrue(ColorUtils.calculateContrast(editor.getCurrentTextColor(),p.input)>4.5);
    assertEquals(p.primary,dialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
    TypedValue value=new TypedValue();assertTrue(themed.getTheme().resolveAttribute(android.R.attr.colorBackground,value,true));
    assertEquals("dark".equals(mode)?Color.parseColor("#211F26"):Color.WHITE,value.data);
    assertEquals("unsaved draft",editor.getText().toString());dialog.dismiss();
   }}finally{android.content.SharedPreferences.Editor e=prefs.edit();if(previous==null)e.remove("themeMode");else e.putString("themeMode",previous);if(previousText==null)e.remove("themeText");else e.putString("themeText",previousText);if(previousSurface==null)e.remove("themeSurface");else e.putString("themeSurface",previousSurface);e.commit();}
  });
 }
}

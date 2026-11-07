package com.changyu.ochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ButtonContrastRegressionTest {
 private final Instrumentation ins=InstrumentationRegistry.getInstrumentation();
 private View find(View view,String label){
  if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
  if(view instanceof TextView && (label.contentEquals(((TextView)view).getText()) || label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return (TextView)view;
  if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View t=find(g.getChildAt(i),label);if(t!=null)return t;}}return null;
 }
 // Draw the actual Drawable, not its raw ColorDrawable color: retained tint changes pixels.
 private int background(View view){
  Bitmap b=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);
  Drawable drawable=view.getBackground();
  if(drawable==null)return view.getParent() instanceof View?background((View)view.getParent()):Color.WHITE;
  Rect old=new Rect(drawable.getBounds());drawable.setBounds(0,0,120,80);drawable.setState(view.getDrawableState());drawable.draw(canvas);drawable.setBounds(old);
  int color=b.getPixel(60,40);b.recycle();
  return Color.alpha(color)==255?color:ColorUtils.compositeColors(color,view.getParent() instanceof View?background((View)view.getParent()):Color.WHITE);
 }
 private void contrast(View button){
  assertNotNull(button);int bg=background(button);
  if(button instanceof ImageButton){ImageButton icon=(ImageButton)button;assertNotNull(icon.getDrawable());assertNotNull(icon.getContentDescription());assertTrue(ColorUtils.calculateContrast(icon.getImageTintList().getDefaultColor(),bg)>=3);return;}
  TextView text=(TextView)button;assertFalse("empty action",text.getText().toString().isEmpty());int fg=text.getCurrentTextColor();
  assertTrue(text.getText()+" fg="+Integer.toHexString(fg)+" bg="+Integer.toHexString(bg),ColorUtils.calculateContrast(fg,bg)>=4.5);
 }
 private void activity(Class<? extends Activity> type,String mode,String... actions){
  Context c=ins.getTargetContext();Intent intent=new Intent(c,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(mode!=null)intent.putExtra("mode",mode);
  Activity activity=ins.startActivitySync(intent);ins.waitForIdleSync();
  try{ins.runOnMainSync(()->{for(String action:actions)contrast(find(activity.getWindow().getDecorView(),action));});}
  finally{ins.runOnMainSync(activity::finish);ins.waitForIdleSync();}
 }
 @Test public void actualActionColorsInBothModesAndOverrides(){
  Context c=ins.getTargetContext();SharedPreferences prefs=c.getSharedPreferences("chat",0);Map<String,?> backup=new HashMap<>(prefs.getAll());
  try{for(String mode:new String[]{"light","dark"})for(boolean override:new boolean[]{false,true}){
   SharedPreferences.Editor e=prefs.edit().clear().putString("themeMode",mode);
   // Adversarial saved theme: formerly white text on a white surface/primary.
   if(override)e.putString("themePrimary","#FFFFFF").putString("themeSurface","#FFFFFF").putString("themeBackground","#FAF8FC").putString("themeText","#FFFFFF");e.commit();
   ins.runOnMainSync(()->{
    Context themed=EditorDialogs.themedContext(c);EditText editor=EditorDialogs.editor(themed);editor.setText("untouched draft");

    AlertDialog dialog=PaletteDialogs.builder(themed).setTitle("Editor").setView(editor).setNegativeButton("Cancel",null).setNeutralButton("Collapse",null).setPositiveButton("Apply",null).create();
    // Simulate an OEM/theme buttonStyle tint that setBackgroundColor alone retains.
    for(int id:new int[]{-1,-2,-3})dialog.getButton(id).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemePalette.resolve(c).primary));
    EditorDialogs.prepare(dialog,editor);assertFalse(dialog.isShowing());
    for(int id:new int[]{-1,-2,-3}){Button button=dialog.getButton(id);assertNull(button.getBackgroundTintList());contrast(button);button.setPressed(true);contrast(button);button.setPressed(false);}
    assertEquals("untouched draft",editor.getText().toString());dialog.dismiss();
    AlertDialog ordinary=PaletteDialogs.builder(c).setTitle("Confirmation").setMessage("No editor").setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
    assertFalse(ordinary.isShowing());contrast(ordinary.getButton(-1));contrast(ordinary.getButton(-2));ordinary.dismiss();
   });
   activity(MainActivity.class,null,"发送消息","新建对话","展开输入框");
   activity(SettingsActivity.class,null,"手动添加模型 ID","Fetch Models","保存供应商");
   activity(SettingsOptionActivity.class,"conversation","保存并返回");
   activity(SettingsOptionActivity.class,"topic","测试命名模型","保存并返回");
   activity(SettingsOptionActivity.class,"theme","保存并重启","恢复自适应默认颜色","恢复默认首页文字");
   activity(ProviderListActivity.class,null,"＋ 新建供应商");
  }}finally{
   SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:backup.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();
  }
 }
}

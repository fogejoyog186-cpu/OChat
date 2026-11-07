package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class AppearanceAndAttachmentsRegressionTest {
 private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 private boolean text(View view,String wanted){if(view instanceof TextView&&((TextView)view).getText().toString().contains(wanted))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),wanted))return true;return false;}
 @Test public void sheetUsesEnglishAndLeavesAppearanceInThemeSettings()throws Exception{
  Context c=context();SharedPreferences prefs=c.getSharedPreferences("chat",Context.MODE_PRIVATE);String mode=prefs.getString(ReasoningAppearance.MODE,null);
  final PanelDialog[] sheet={null};
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   prefs.edit().putString(ReasoningAppearance.MODE,"purple").commit();
   scenario.onActivity(a->{
    sheet[0]=ReasoningSheet.show(a,"GPT · 6.1 Sol",new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,new JSONObject()),"xhigh",v->{});
    View root=sheet[0].getWindow().getDecorView();assertNotNull(caption(root,"xhigh"));assertNull(caption(root,"很高"));assertNull(description(root,"思考强度动画颜色"));
    try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("attachmentChoices");field.setAccessible(true);LinearLayout choices=(LinearLayout)field.get(a);assertEquals(2,choices.getChildCount());assertEquals("图片",((TextView)choices.getChildAt(0)).getText().toString());assertEquals("文件",((TextView)choices.getChildAt(1)).getText().toString());}catch(Exception error){throw new AssertionError(error);}
   });
   capture(c,"reasoning-appearance");
   scenario.onActivity(a->{sheet[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals("purple",prefs.getString(ReasoningAppearance.MODE,""));PanelDialog auto=ReasoningSheet.show(a,"GPT",new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,new JSONObject()),"default",v->{});assertNotNull(caption(auto.getWindow().getDecorView(),"Auto"));auto.dismiss();});
  }finally{SharedPreferences.Editor e=prefs.edit();if(mode==null)e.remove(ReasoningAppearance.MODE);else e.putString(ReasoningAppearance.MODE,mode);e.commit();}
 }
 private void capture(Context c,String name)throws Exception{
  android.os.SystemClock.sleep(350);android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);
  try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name+".png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
 }
 private <T extends View> T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T found=find(((ViewGroup)root).getChildAt(i),type);if(found!=null)return found;}return null;
 }
 private View caption(View root,String value){
  if(root instanceof TextView&&((TextView)root).getText().toString().equals(value))return root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=caption(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;
 }
 private View description(View root,String value){
  if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=description(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;
 }
 private void chooseColor(View parent,String name){
  org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT>=29);
  android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  instrumentation.runOnMainSync(()->{View row=description(parent,"思考强度动画颜色");assertNotNull(row);row.performClick();});
  instrumentation.waitForIdleSync();
  instrumentation.runOnMainSync(()->{
   View choices=null;for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root!=parent&&caption(root,"动画配色")!=null)choices=root;
   assertNotNull(choices);ListView list=find(choices,ListView.class);assertNotNull(list);int selected=-1;for(int i=0;i<list.getAdapter().getCount();i++)if(name.equals(list.getAdapter().getItem(i)))selected=i;assertTrue(selected>=0);
   list.performItemClick(list.getChildAt(selected-list.getFirstVisiblePosition()),selected,list.getAdapter().getItemId(selected));View confirm=caption(choices,"确认");assertNotNull(confirm);assertTrue(confirm.isEnabled());confirm.performClick();
  });instrumentation.waitForIdleSync();
 }
 @Test public void presetsUseSeparateSolidOfficialColorsAndCustomHex(){
  ReasoningAppearance a=new ReasoningAppearance(context());ThemePalette p=ThemePalette.resolve(context());a.mode="blue";assertEquals(0xff0285ff,a.color(p));a.mode="purple";assertEquals(0xff9859ef,a.color(p));
  a.mode="custom";a.custom=ReasoningAppearance.parse("#123456");assertEquals(0xff123456,a.color(p));assertEquals(0xffabcdef,ReasoningAppearance.parse("ABCDEF"));try{ReasoningAppearance.parse("#12345Z");fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void themeColorDraftSurvivesRotationAndOnlySavesWithTheTheme()throws Exception{
  Context c=context();SharedPreferences prefs=c.getSharedPreferences("chat",Context.MODE_PRIVATE);String old=prefs.getString(ReasoningAppearance.MODE,null);final View[] root={null};
  try{
   prefs.edit().putString(ReasoningAppearance.MODE,"cyan").commit();
   Intent intent=new Intent(c,SettingsOptionActivity.class).putExtra("mode","theme");
   try(ActivityScenario<SettingsOptionActivity> scenario=ActivityScenario.launch(intent)){
    scenario.onActivity(a->root[0]=a.getWindow().getDecorView());chooseColor(root[0],"GPT 紫色");assertEquals("cyan",prefs.getString(ReasoningAppearance.MODE,""));
    scenario.recreate();scenario.onActivity(a->{assertTrue(text(a.getWindow().getDecorView(),"GPT 紫色"));assertEquals("cyan",prefs.getString(ReasoningAppearance.MODE,""));});
   }
   assertEquals("cyan",prefs.getString(ReasoningAppearance.MODE,""));
   try(ActivityScenario<SettingsOptionActivity> scenario=ActivityScenario.launch(intent)){
    scenario.onActivity(a->root[0]=a.getWindow().getDecorView());chooseColor(root[0],"GPT 蓝色");capture(c,"reasoning-theme");
    scenario.onActivity(a->{View save=caption(a.getWindow().getDecorView(),"保存并重启");assertNotNull(save);save.performClick();});
    assertEquals("blue",prefs.getString(ReasoningAppearance.MODE,""));
   }
   prefs.edit().putString(ReasoningAppearance.MODE,"gpt").commit();assertEquals("blue",new ReasoningAppearance(c).mode);
  }finally{SharedPreferences.Editor e=prefs.edit();if(old==null)e.remove(ReasoningAppearance.MODE);else e.putString(ReasoningAppearance.MODE,old);e.commit();}
 }
 @Test public void managerChooserAcceptsMultipleOpenableDocuments(){
  Intent chooser=AttachmentSelection.managers();assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());Intent target=chooser.getParcelableExtra(Intent.EXTRA_INTENT);assertNotNull(target);assertEquals(Intent.ACTION_GET_CONTENT,target.getAction());assertEquals("*/*",target.getType());assertTrue(target.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(target.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));assertTrue((target.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
  assertEquals(Intent.ACTION_OPEN_DOCUMENT,AttachmentSelection.documents(false).getAction());
 }
 @Test public void temporaryDocumentCopySurvivesTheOriginalBeingDeleted()throws Exception{
  org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT>=29);Context c=context();ContentResolver resolver=c.getContentResolver();ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"ochat-temporary-"+System.nanoTime()+".txt");values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download");
  Uri source=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(source);JSONObject saved=null;
  try{
   try(OutputStream out=resolver.openOutputStream(source)){assertNotNull(out);out.write("temporary manager document".getBytes(StandardCharsets.UTF_8));}
   saved=AttachmentSelection.retain(c,source,Intent.FLAG_GRANT_READ_URI_PERMISSION);assertTrue(saved.optBoolean("importedCopy"));assertEquals("text/plain",saved.optString("mime"));assertNotEquals(source.toString(),saved.optString("uri"));assertEquals(source.toString(),saved.optString("sourceUri"));
   resolver.delete(source,null,null);source=null;JSONObject reloaded=new JSONObject(saved.toString());assertEquals("temporary manager document",AttachmentBatch.payload(resolver,reloaded));
  }finally{if(source!=null)resolver.delete(source,null,null);if(saved!=null)AttachmentSelection.discard(c,saved);}
 }
 @Test public void unsafeRawFileUrisAreRejected()throws Exception{
  try{AttachmentSelection.retain(context(),Uri.parse("file:///sdcard/test.txt"),0);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("content://"));}
 }
}

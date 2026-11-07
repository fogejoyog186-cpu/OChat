package com.changyu.ochat;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttachmentPresentationRegressionTest {
 private static JSONObject file(String uri,String name,String mime){
  try{return new JSONObject().put("uri","content://com.changyu.ochat.test.attachments/"+uri).put("displayName",name).put("mime",mime).put("type",mime.startsWith("image/")?"image":"document");}
  catch(Exception error){throw new AssertionError(error);}
 }
 private static final class Screen implements AutoCloseable {
  final ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class);
  AttachmentUi ui;PanelDialog panel;View content;
  Screen(){run(a->ui=new AttachmentUi(a));}
  void run(Consumer<MainActivity> action){scenario.onActivity(action::accept);}
  View decor(){return panel.getWindow().getDecorView();}
  @Override public void close(){run(a->{if(panel!=null)panel.dismiss();ui.close();});scenario.close();}
 }
 private static <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T result=find(((ViewGroup)view).getChildAt(i),type);if(result!=null)return result;}return null;
 }
 private static boolean text(View view,String value){
  if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return true;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),value))return true;return false;
 }
 private static View described(View view,String description){
  if(description.equals(view.getContentDescription()))return view;
  if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=described(((ViewGroup)view).getChildAt(i),description);if(result!=null)return result;}return null;
 }
 private static void capture(String name){
  // Window transitions can still be composited after the view tree becomes idle.
  android.os.SystemClock.sleep(400);
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  android.graphics.Bitmap image=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
  assertNotNull(image);
  java.io.File directory=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("ui-review");
  assertNotNull(directory);
  try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
  catch(java.io.IOException error){throw new AssertionError(error);}finally{image.recycle();}
 }
 private static void await(Screen screen,BooleanSupplier condition){
  for(int attempt=0;attempt<150;attempt++){boolean[] ready={false};screen.run(a->ready[0]=condition.getAsBoolean());if(ready[0])return;android.os.SystemClock.sleep(50);}fail("Attachment preview did not finish loading");
 }
 @Test public void photosAreBareAndPendingItemsKeepTheirOpenAndRemoveActions(){
  try(Screen screen=new Screen()){
   AtomicInteger opened=new AtomicInteger(),removed=new AtomicInteger();
   screen.run(a->{
    LinearLayout samples=new LinearLayout(a);samples.setOrientation(LinearLayout.VERTICAL);
    JSONObject photo=file("photo-inline-v2","private-photo.png","image/png");
    View image=screen.ui.message(photo,opened::incrementAndGet);
    View pending=screen.ui.pending(photo,false,opened::incrementAndGet,removed::incrementAndGet);
    View document=screen.ui.message(file("notes","notes.txt","text/plain"),opened::incrementAndGet);
    samples.addView(image,new LinearLayout.LayoutParams(-1,-2));samples.addView(pending);samples.addView(document);
    screen.content=samples;screen.panel=new PanelDialog(a,"附件",null,samples);screen.panel.show();
    assertNull(image.getBackground());assertFalse(text(samples,"private-photo.png"));assertTrue(text(samples,"notes.txt"));
    assertFalse(text(samples,"点击预览"));image.performClick();assertEquals(1,opened.get());
    described(pending,"移除附件 private-photo.png").performClick();assertEquals(1,removed.get());
   });
   await(screen,()->find(screen.content.findViewWithTag("attachment-image"),ImageView.class).getDrawable()!=null);
   capture("attachment-inline");
  }
 }
 @Test public void composerUsesACompactHorizontalTrayAndCanRemovePhotos(){
  try(Screen screen=new Screen()){
   screen.run(a->{try{
    java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("pendingAttachments");pending.setAccessible(true);
    java.util.List<JSONObject> files=(java.util.List<JSONObject>)pending.get(a);
    files.add(file("photo-composer-v2","private-photo.png","image/png"));files.add(file("notes","notes.txt","text/plain"));
    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderAttachmentPreview");render.setAccessible(true);render.invoke(a);
    screen.content=a.getWindow().getDecorView();assertTrue(screen.content.findViewWithTag("attachment-tray") instanceof HorizontalScrollView);
    assertTrue(text(screen.content,"notes.txt"));assertFalse(text(screen.content,"private-photo.png"));
   }catch(Exception error){throw new AssertionError(error);}});
   await(screen,()->find(screen.content.findViewWithTag("attachment-image"),ImageView.class).getDrawable()!=null);
   capture("attachment-composer");
   screen.run(a->{described(screen.content,"移除附件 private-photo.png").performClick();
    assertNull(screen.content.findViewWithTag("attachment-image"));assertTrue(text(screen.content,"notes.txt"));
   });
  }
 }
 @Test public void imageAndTextOpenInThemedPanelsWithoutAnImageFilename(){
  try(Screen screen=new Screen()){
   screen.run(a->screen.panel=screen.ui.preview(file("photo-preview-v2","private-photo.png","image/png")));
   await(screen,()->find(screen.decor(),AttachmentImageView.class).getDrawable()!=null);
   capture("attachment-image-preview");
   screen.run(a->{
    assertTrue(text(screen.decor(),"图片"));assertFalse(text(screen.decor(),"private-photo.png"));
    screen.panel=screen.ui.preview(file("notes","notes.txt","text/plain"));
   });
   await(screen,()->text(screen.decor(),"Fixture contents notes"));
   capture("attachment-text-preview");
   screen.run(a->{assertTrue(text(screen.decor(),"notes.txt"));assertNotNull(screen.panel.getButton(AlertDialog.BUTTON_NEUTRAL));});
  }
 }
 @Test public void pdfStaysInTheSharedPanelAndCanChangePages(){
  try(Screen screen=new Screen()){
   screen.run(a->screen.panel=screen.ui.preview(file("pdf-preview-v2","report.pdf","application/pdf")));
   await(screen,()->text(screen.decor(),"1 / 2"));
   screen.run(a->{assertTrue(text(screen.decor(),"report.pdf"));described(screen.decor(),"下一页").performClick();});
   await(screen,()->text(screen.decor(),"2 / 2"));
   capture("attachment-pdf-preview");
   screen.run(a->{assertFalse(described(screen.decor(),"下一页").isEnabled());described(screen.decor(),"上一页").performClick();});
   await(screen,()->text(screen.decor(),"1 / 2"));
  }
 }
}

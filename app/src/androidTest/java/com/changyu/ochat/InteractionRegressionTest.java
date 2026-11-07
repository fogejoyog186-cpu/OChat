package com.changyu.ochat;

import android.content.*;
import android.text.*;
import android.text.style.ClickableSpan;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InteractionRegressionTest {
 private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
 @Test public void composerKeepsEditableFocusCursorAndCompositionAcrossDelayedTasks()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{EditText editor=(EditText)field(a,"composer");editor.requestFocus();editor.setText("");InputConnection connection=editor.onCreateInputConnection(new EditorInfo());assertNotNull(connection);connection.commitText("a",1);connection.commitText("b",1);connection.setComposingText("拼音",1);assertTrue(editor.hasFocus());assertEquals("ab拼音",editor.getText().toString());assertEquals(2,BaseInputConnection.getComposingSpanStart(editor.getText()));});
   Thread.sleep(350);
   scenario.onActivity(a->{EditText editor=(EditText)field(a,"composer");assertTrue(editor.hasFocus());assertEquals(4,editor.getSelectionStart());assertEquals(2,BaseInputConnection.getComposingSpanStart(editor.getText()));InputConnection c=editor.onCreateInputConnection(new EditorInfo());c.finishComposingText();c.commitText("cd",1);assertEquals("ab拼音cd",editor.getText().toString());assertEquals(6,editor.getSelectionEnd());editor.setSelection(1,3);});
   Thread.sleep(250);
   scenario.onActivity(a->{EditText editor=(EditText)field(a,"composer");assertTrue(editor.hasFocus());assertEquals(1,editor.getSelectionStart());assertEquals(3,editor.getSelectionEnd());});
  }
 }
 @Test public void expandedEditorKeepsCompositionWithoutMutatingMainDraft()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{EditText main=(EditText)field(a,"composer");main.setText("draft");try{Method open=MainActivity.class.getDeclaredMethod("showExpandedComposer");open.setAccessible(true);open.invoke(a);}catch(Exception e){throw new AssertionError(e);} });
   scenario.onActivity(a->{android.app.AlertDialog dialog=(android.app.AlertDialog)field(a,"expandedComposerDialog");EditText editor=findEditor(dialog.getWindow().getDecorView());editor.requestFocus();InputConnection connection=editor.onCreateInputConnection(new EditorInfo());connection.commitText("abc",1);connection.setComposingText("拼音",1);assertTrue(editor.hasFocus());});
   Thread.sleep(350);
   scenario.onActivity(a->{android.app.AlertDialog dialog=(android.app.AlertDialog)field(a,"expandedComposerDialog");EditText editor=findEditor(dialog.getWindow().getDecorView());assertTrue(editor.hasFocus());assertEquals("draftabc拼音",editor.getText().toString());assertEquals(editor.length(),editor.getSelectionEnd());assertEquals(8,BaseInputConnection.getComposingSpanStart(editor.getText()));dialog.dismiss();});
   scenario.onActivity(a->assertEquals("draft",((EditText)field(a,"composer")).getText().toString()));
  }
 }
 private static EditText findEditor(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText result=findEditor(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
 static final class Capture extends ContextWrapper {Intent opened;Capture(Context base){super(base);}@Override public void startActivity(Intent intent){opened=intent;}}
 private static void tapSpan(TextView view,Spanned text,ClickableSpan span,long duration){int offset=text.getSpanStart(span)+1;Layout layout=view.getLayout();int line=layout.getLineForOffset(offset);float x=layout.getPrimaryHorizontal(offset)+view.getTotalPaddingLeft(),y=(layout.getLineTop(line)+layout.getLineBottom(line))/2f+view.getTotalPaddingTop();long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+duration,MotionEvent.ACTION_UP,x,y,0);view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();}
 @Test public void actualMarkdownTouchOpensCapturedBrowserAndCopyUsesRawCode(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{
   Capture context=new Capture(a);TextView view=new TextView(context);MarkdownRenderer.render(MarkdownRenderer.create(context),view,"[source](https://example.com/path)\n\n```java\nString s = \"quoted\";\n```");a.setContentView(view);view.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.AT_MOST));view.layout(0,0,900,view.getMeasuredHeight());
   Spanned text=(Spanned)view.getText();ClickableSpan link=null;MarkdownRenderer.CodeCopySpan copy=null;for(ClickableSpan span:text.getSpans(0,text.length(),ClickableSpan.class)){if(span instanceof MarkdownRenderer.CodeCopySpan)copy=(MarkdownRenderer.CodeCopySpan)span;else link=span;}assertNotNull(link);assertNotNull(copy);assertTrue(view.isTextSelectable());tapSpan(view,text,link,40);assertNotNull(context.opened);assertEquals(Intent.ACTION_VIEW,context.opened.getAction());assertTrue(context.opened.hasCategory(Intent.CATEGORY_BROWSABLE));assertEquals("https://example.com/path",context.opened.getDataString());context.opened=null;
   // Long-duration release must never execute a link action.
   tapSpan(view,text,link,ViewConfiguration.getLongPressTimeout()+20);assertNull(context.opened);
   Selection.removeSelection((Spannable)view.getText());tapSpan(view,text,copy,40);android.content.ClipboardManager clipboard=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);assertEquals("String s = \"quoted\";\n",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
  });}
 }
 @Test public void followIsLatchedAndPendingOutputCannotOverrideUserViewport()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{ScrollView scroll=(ScrollView)field(a,"conversationScroll");LinearLayout list=(LinearLayout)field(a,"messageList");TextView large=new TextView(a);large.setText("fixture");list.addView(large,new LinearLayout.LayoutParams(-1,6000));});Thread.sleep(200);
   scenario.onActivity(a->{FollowOutput follow=(FollowOutput)field(a,"follow");follow.resume();});Thread.sleep(250);
   scenario.onActivity(a->{FollowOutput follow=(FollowOutput)field(a,"follow");ScrollView scroll=(ScrollView)field(a,"conversationScroll");assertTrue(follow.nearBottom());follow.changed();follow.detach();scroll.scrollTo(0,123);follow.changed();assertFalse(follow.following());});Thread.sleep(300);
   scenario.onActivity(a->{assertEquals(123,((ScrollView)field(a,"conversationScroll")).getScrollY());FollowOutput follow=(FollowOutput)field(a,"follow");follow.begin();assertFalse(follow.following());});
  }
 }
 @Test public void bottomArrowUsesDistanceHysteresisAndManualBottomDoesNotResume()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{FollowOutput f=(FollowOutput)field(a,"follow");f.detach();LinearLayout list=(LinearLayout)field(a,"messageList");list.addView(new TextView(a),new LinearLayout.LayoutParams(-1,6000));});Thread.sleep(200);
   scenario.onActivity(a->{FollowOutput f=(FollowOutput)field(a,"follow");ScrollView scroll=(ScrollView)field(a,"conversationScroll");View arrow=(View)field(a,"bottomButton");float d=a.getResources().getDisplayMetrics().density;scroll.scrollTo(0,f.bottom());assertEquals(View.INVISIBLE,arrow.getVisibility());f.detach();assertEquals(View.INVISIBLE,arrow.getVisibility());int threshold=(int)Math.max(160*d,scroll.getHeight()*.25f);scroll.scrollTo(0,f.bottom()-threshold+1);assertEquals(View.INVISIBLE,arrow.getVisibility());scroll.scrollTo(0,f.bottom()-threshold-10);assertEquals(View.VISIBLE,arrow.getVisibility());assertTrue(arrow instanceof ImageButton);assertEquals(40,Math.round(arrow.getWidth()/d));assertEquals("回到底部",arrow.getContentDescription());scroll.scrollTo(0,f.bottom()-(int)(80*d));assertEquals(View.VISIBLE,arrow.getVisibility());scroll.scrollTo(0,f.bottom()-(int)(24*d));assertEquals(View.INVISIBLE,arrow.getVisibility());assertFalse(f.following());scroll.scrollTo(0,f.bottom()-threshold-10);assertEquals(View.VISIBLE,arrow.getVisibility());assertTrue(arrow.performClick());assertTrue(f.following());assertEquals(View.INVISIBLE,arrow.getVisibility());});Thread.sleep(350);
   scenario.onActivity(a->assertTrue(((FollowOutput)field(a,"follow")).nearBottom()));
  }
 }
 @Test public void structuredSourcesFilterDeduplicatePersistAndGround()throws Exception{
  JSONArray array=new JSONArray().put(new JSONObject().put("title","One").put("url","https://example.com/a").put("content","Grounding fact")).put(new JSONObject().put("url","https://example.com/a")).put(new JSONObject().put("url","javascript:bad"));SearchSources sources=SearchSources.parse(array);assertEquals(1,sources.items.length());assertTrue(sources.grounding().contains("Grounding fact"));assertEquals(1,SearchSources.parse(new JSONArray(sources.items.toString())).items.length());assertEquals(1,SearchSources.legacy("- Historical\n 来源：https://example.com/a").items.length());assertEquals(0,SearchSources.legacy("{\"url\":\"https://example.com\"}").items.length());assertNull(SafeLinks.intent("https://user:secret@example.com"));
 }
}

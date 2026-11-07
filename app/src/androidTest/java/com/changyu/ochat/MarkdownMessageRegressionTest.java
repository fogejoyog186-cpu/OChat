package com.changyu.ochat;

import android.content.Context;
import android.text.Spanned;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.noties.markwon.Markwon;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MarkdownMessageRegressionTest {
 @Test public void commonMarkBlocksExtensionsAndEscapesRender(){
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  Markwon renderer=MarkdownRenderer.create(context);
  Spanned text=renderer.toMarkdown("# Heading\n\n1. first\n2. second\n   - nested\n\n> quote\n\n```java\nint x = 1;\n```\n\n`inline` ~~deleted~~ [safe](https://example.com)\n\n| A | B |\n|---|---|\n| one | two |\n\n\\*literal\\*");
  assertTrue(text.toString().contains("Heading"));assertFalse(text.toString().contains("# Heading"));
  assertTrue(text.toString().contains("java"));assertTrue(text.toString().contains("int x = 1;"));assertTrue(text.toString().contains("*literal*"));
  StringBuilder classes=new StringBuilder();for(Object span:text.getSpans(0,text.length(),Object.class))classes.append(span.getClass().getSimpleName()).append(' ');
  for(String span:new String[]{"HeadingSpan","OrderedListItemSpan","BulletListItemSpan","BlockQuoteSpan","CodeBlockSpan","CodeSpan","StrikethroughSpan","LinkSpan","TableRowSpan"})assertTrue(span+": "+classes,classes.toString().contains(span));
 }
 @Test public void literalSvgCodeHasOutlineWithoutLosingMarkup(){
  Markwon renderer=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());
  String raw="<svg viewBox=\"0 0 20 20\"><title>goose</title><path d=\"M0 0\"/></svg>";
  Spanned text=renderer.toMarkdown("```svg\n"+raw+"\n```");
  assertTrue(text.toString().contains(raw));
  CodeBlockBorderSpan[] borders=text.getSpans(0,text.length(),CodeBlockBorderSpan.class);
  assertEquals(1,borders.length);
  assertTrue(text.getSpanStart(borders[0])<=text.toString().indexOf("<svg"));
  assertTrue(text.getSpanEnd(borders[0])>=text.toString().indexOf("</svg>")+"</svg>".length());
 }
 @Test public void streamingReplacesSpansAndSafeLinksOnly(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Markwon renderer=MarkdownRenderer.create(context);TextView view=new TextView(context);
   renderer.setMarkdown(view,"```java\nint");renderer.setMarkdown(view,"# Complete\n\n```java\nint n;\n```\n\n**done**");assertTrue(view.getText().toString().contains("int n;"));assertFalse(view.getText().toString().contains("```"));
   renderer.setMarkdown(view,"plain");assertEquals("plain",view.getText().toString());for(Object span:((Spanned)view.getText()).getSpans(0,5,Object.class))assertFalse(span.getClass().getName(),span.getClass().getName().startsWith("io.noties.markwon.core.spans")&&!span.getClass().getSimpleName().equals("TextViewSpan"));
  });
  assertTrue(MarkdownRenderer.isSafeLink("https://example.com/path"));assertTrue(MarkdownRenderer.isSafeLink("HTTP://example.com"));
  for(String link:new String[]{"javascript:alert(1)","file:///x","content://x","intent://x","//example.com","https://","https://user:pass@example.com","https://example.com\n"})assertFalse(link,MarkdownRenderer.isSafeLink(link));
 }
 @Test public void exactMessageEditsPreserveAttachmentsMetadataNamesAndOtherSessions()throws Exception{
  JSONObject attachment=new JSONObject().put("uri","content://example/a");
  JSONObject user=new JSONObject().put("user",true).put("text","original").put("attachments",new JSONArray().put(attachment));
  JSONObject assistant=new JSONObject().put("user",false).put("text","reply").put("timestamp",123).put("modelId","model");
  JSONObject session=new JSONObject().put("id","a").put("title","manual").put("titleManual",true).put("messages",new JSONArray().put(user).put(assistant).put(new JSONObject().put("user",true).put("text","later")));
  JSONObject other=new JSONObject().put("id","b").put("messages",new JSONArray());JSONArray sessions=new JSONArray().put(session).put(other);
  assertTrue(MessageEdits.ensureIds(sessions));String userId=user.getString("id"),assistantId=assistant.getString("id");assertFalse(MessageEdits.ensureIds(sessions));
  MessageEdits.apply(sessions,"a",userId,"edited",false);assertEquals(3,session.getJSONArray("messages").length());assertEquals("edited",user.getString("text"));assertSame(attachment,user.getJSONArray("attachments").get(0));
  MessageEdits.apply(sessions,"a",assistantId,"local edit",false);assertEquals(123,assistant.getLong("timestamp"));assertEquals("model",assistant.getString("modelId"));
  assertNull(MessageEdits.apply(sessions,"b",userId,"wrong",true));
  MessageEdits.apply(sessions,"a",assistantId,null,true);assertEquals(1,session.getJSONArray("messages").length());assertEquals(userId,session.getJSONArray("messages").getJSONObject(0).getString("id"));
  MessageEdits.apply(sessions,"a",userId,"retry edit",true);assertEquals(1,session.getJSONArray("messages").length());assertEquals("retry edit",user.getString("text"));assertEquals("manual",session.getString("title"));assertTrue(session.getBoolean("titleManual"));assertEquals(0,other.getJSONArray("messages").length());
 }
}

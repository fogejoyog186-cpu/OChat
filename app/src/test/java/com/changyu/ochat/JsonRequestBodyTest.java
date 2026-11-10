package com.changyu.ochat;

import java.io.ByteArrayOutputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonRequestBodyTest {
 private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
 private static String encode(RequestBody body)throws Exception{
  Buffer sink=new Buffer();body.writeTo(sink);
  assertEquals("Content-Length must count UTF-8 bytes, not Java characters",body.contentLength(),sink.size());
  return sink.readUtf8();
 }
 @Test public void escapesControlsUnicodeAndUnpairedSurrogatesWithoutLosingText()throws Exception{
  StringBuilder controls=new StringBuilder();for(int i=0;i<32;i++)controls.append((char)i);
  String text="中文 Ελληνικά é 🚲 \" \\\n"+controls+"\u2028\u2029\uD800X\uDC00";
  JSONObject source=new JSONObject().put("键\"\\\n🚲",text);
  String encoded=encode(JsonRequestBody.create(source,JSON));
  assertEquals(text,new JSONObject(encoded).getString("键\"\\\n🚲"));
  assertTrue(encoded.contains("\\ud800X\\udc00"));
  assertTrue(encoded.contains("\\u2028\\u2029"));
  assertFalse(encoded.contains("\u0000"));
 }
 @Test public void unicodeCrossesOutputBufferBoundariesCorrectly()throws Exception{
  StringBuilder text=new StringBuilder();for(int i=0;i<8190;i++)text.append('x');
  text.append("🚲汉字é");for(int i=0;i<9000;i++)text.append("🧪\n");
  String expected=text.toString();
  assertEquals(expected,new JSONObject(encode(JsonRequestBody.create(new JSONObject().put("text",expected),JSON))).getString("text"));
 }
 @Test public void retainsLargePdfBase64ExactlyAndIsRepeatable()throws Exception{
  byte[] raw=new byte[3*1024*1024+37];for(int i=0;i<raw.length;i++)raw[i]=(byte)(i*31);
  String expected="data:application/pdf;base64,"+Base64.getEncoder().encodeToString(raw);
  JSONObject source=new JSONObject().put("file",new JSONObject().put("filename","扫描件.pdf").put("file_data",expected));
  RequestBody body=JsonRequestBody.create(source,JSON);
  assertEquals(JSON,body.contentType());
  String first=encode(body),second=encode(body);
  assertEquals(first,second);
  String actual=new JSONObject(first).getJSONObject("file").getString("file_data");
  assertEquals(expected,actual);
  assertArrayEquals(raw,Base64.getDecoder().decode(actual.substring(actual.indexOf(',')+1)));
 }
 @Test public void snapshotsNestedContainersBeforeCallerChangesThem()throws Exception{
  StringBuilder custom=new StringBuilder("original");
  JSONObject nested=new JSONObject().put("value","before");
  JSONArray array=new JSONArray().put(nested).put(JSONObject.NULL).put(true).put(new BigDecimal("1.25"));
  JSONObject source=new JSONObject().put("array",array).put("custom",custom).put("zero",0);
  RequestBody body=JsonRequestBody.create(source,JSON);long size=body.contentLength();
  nested.put("value","after");array.put("extra");source.remove("zero");source.put("newKey","new");custom.append("-changed");
  JSONObject actual=new JSONObject(encode(body));
  assertEquals(size,body.contentLength());
  assertEquals("before",actual.getJSONArray("array").getJSONObject(0).getString("value"));
  assertEquals(4,actual.getJSONArray("array").length());
  assertTrue(actual.getJSONArray("array").isNull(1));assertTrue(actual.getJSONArray("array").getBoolean(2));
  assertEquals(1.25,actual.getJSONArray("array").getDouble(3),0);
  assertEquals("original",actual.getString("custom"));assertEquals(0,actual.getInt("zero"));assertFalse(actual.has("newKey"));
 }
 @Test public void doesNotCloseOrFlushTheCallersSink()throws Exception{
  final boolean[] state=new boolean[2];ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  OutputStream output=new OutputStream(){
   @Override public void write(int value){bytes.write(value);}
   @Override public void write(byte[] data,int offset,int count){bytes.write(data,offset,count);}
   @Override public void flush(){state[0]=true;}
   @Override public void close(){state[1]=true;}
  };
  BufferedSink sink=Okio.buffer(Okio.sink(output));
  RequestBody body=JsonRequestBody.create(new JSONObject().put("ok",true),JSON);
  body.writeTo(sink);
  assertFalse(state[0]);assertFalse(state[1]);
  sink.flush();assertTrue(state[0]);assertFalse(state[1]);
  assertEquals(body.contentLength(),bytes.size());
  assertTrue(new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getBoolean("ok"));
  sink.close();assertTrue(state[1]);
 }
 @Test public void cancellationIsPreservedDuringPreparationAndBeforeWriting()throws Exception{
  JSONObject source=new JSONObject().put("text","cancel");RequestBody body=JsonRequestBody.create(source,JSON);
  Thread.currentThread().interrupt();
  try{
   try{JsonRequestBody.create(source,JSON);fail("cancelled preparation must fail");}
   catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("cancelled"));}
   assertTrue(Thread.currentThread().isInterrupted());
   try{body.writeTo(new Buffer());fail("cancelled upload must fail");}
   catch(InterruptedIOException expected){assertTrue(expected.getMessage().contains("cancelled"));}
   assertTrue(Thread.currentThread().isInterrupted());
  }finally{Thread.interrupted();}
  assertEquals("cancel",new JSONObject(encode(body)).getString("text"));
 }
 @Test public void cancellationDuringLargeUploadStopsBeforeSendingAllBytes()throws Exception{
  char[] chars=new char[128*1024];Arrays.fill(chars,'x');
  RequestBody body=JsonRequestBody.create(new JSONObject().put("file",new String(chars)),JSON);
  final long[] sent={0};
  OutputStream output=new OutputStream(){
   @Override public void write(int value){sent[0]++;Thread.currentThread().interrupt();}
   @Override public void write(byte[] data,int offset,int count){sent[0]+=count;Thread.currentThread().interrupt();}
  };
  try{
   try{body.writeTo(Okio.buffer(Okio.sink(output)));fail("interrupted upload must stop");}
   catch(InterruptedIOException expected){assertTrue(Thread.currentThread().isInterrupted());}
   assertTrue(sent[0]>0);assertTrue(sent[0]<body.contentLength());
  }finally{Thread.interrupted();}
 }
 @Test public void rejectsUnsupportedEncodingAndExcessiveNesting()throws Exception{
  try{JsonRequestBody.create(new JSONObject(),MediaType.get("application/json; charset=iso-8859-1"));fail();}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("UTF-8"));}
  JSONObject root=new JSONObject(),node=root;
  for(int i=0;i<70;i++){JSONObject next=new JSONObject();node.put("child",next);node=next;}
  try{JsonRequestBody.create(root,JSON);fail();}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("64"));}
 }
}

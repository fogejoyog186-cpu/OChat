package com.changyu.ochat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.json.*;

/** Writes a request tree without allocating a second, entire base64/JSON request string. */
final class JsonRequestBody extends RequestBody {
 private static final int MAX_DEPTH=64;
 private final Object value;private final MediaType media;private final long length;
 static RequestBody create(JSONObject body,MediaType media){
  if(body==null)throw new IllegalArgumentException("JSON request body is missing");
  if(media!=null&&!StandardCharsets.UTF_8.equals(media.charset(StandardCharsets.UTF_8)))throw new IllegalArgumentException("JSON request encoding must be UTF-8");
  return new JsonRequestBody(body,media);
 }
 private JsonRequestBody(JSONObject body,MediaType media){
  this.media=media;value=freeze(body,0);
  CountingOutputStream counter=new CountingOutputStream();
  try{Writer writer=new Writer(counter);writer.value(value,0);writer.finish();length=counter.count;}
  catch(IOException error){throw new IllegalArgumentException("Cannot prepare JSON request: "+error.getMessage(),error);}
 }
 @Override public MediaType contentType(){return media;}
 @Override public long contentLength(){return length;}
 @Override public void writeTo(BufferedSink sink)throws IOException{
  interrupted();Writer writer=new Writer(sink.outputStream());writer.value(value,0);writer.finish();
 }
 /** Only container structure is copied; immutable file-data strings are shared unchanged. */
 private static Object freeze(Object value,int depth){
  if(depth>MAX_DEPTH)throw new IllegalArgumentException("JSON request nesting exceeds 64 levels");
  if(Thread.currentThread().isInterrupted())throw new IllegalArgumentException("JSON request preparation cancelled");
  if(value==null||value==JSONObject.NULL)return null;
  if(value instanceof String||value instanceof Boolean)return value;
  if(value instanceof JSONObject){
   Map<String,Object> object=new LinkedHashMap<>();Iterator<String> keys=((JSONObject)value).keys();
   while(keys.hasNext()){String key=keys.next();object.put(key,freeze(((JSONObject)value).opt(key),depth+1));}
   return object;
  }
  if(value instanceof JSONArray){List<Object> array=new ArrayList<>();for(int i=0;i<((JSONArray)value).length();i++)array.add(freeze(((JSONArray)value).opt(i),depth+1));return array;}
  if(value instanceof Number){
   try{String number=JSONObject.numberToString((Number)value);if(!number.matches("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"))throw new IllegalArgumentException("JSON request contains an invalid number");return new Numeric(number);}
   catch(JSONException error){throw new IllegalArgumentException("JSON request contains a non-finite number",error);}
  }
  // JSONObject serializes ordinary custom values through their string representation.
  return String.valueOf(value);
 }
 private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("JSON request write cancelled");}
 private static final class Numeric{final String text;Numeric(String text){this.text=text;}}
 private static final class CountingOutputStream extends OutputStream{
  long count;
  @Override public void write(int value)throws IOException{add(1);}
  @Override public void write(byte[] bytes,int offset,int size)throws IOException{add(size);}
  private void add(int size)throws IOException{interrupted();if(size>Long.MAX_VALUE-count)throw new IOException("JSON request is too large");count+=size;}
 }
 private static final class Writer{
  private static final char[] HEX="0123456789abcdef".toCharArray();
  final OutputStream output;final byte[] buffer=new byte[8192];int used;
  Writer(OutputStream output){this.output=output;}
  void finish()throws IOException{interrupted();if(used>0){output.write(buffer,0,used);used=0;}}
  void put(int b)throws IOException{if(used==buffer.length)finish();buffer[used++]=(byte)b;}
  void ascii(String text)throws IOException{for(int i=0;i<text.length();i++)put(text.charAt(i));}
  void value(Object value,int depth)throws IOException{
   interrupted();if(depth>MAX_DEPTH)throw new IOException("JSON request nesting exceeds 64 levels");
   if(value==null)ascii("null");
   else if(value instanceof String)string((String)value);
   else if(value instanceof Boolean)ascii((Boolean)value?"true":"false");
   else if(value instanceof Numeric)ascii(((Numeric)value).text);
   else if(value instanceof Map){
    put('{');boolean first=true;
    for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()){if(!first)put(',');first=false;string((String)entry.getKey());put(':');value(entry.getValue(),depth+1);}put('}');
   }else if(value instanceof List){put('[');boolean first=true;for(Object item:(List<?>)value){if(!first)put(',');first=false;value(item,depth+1);}put(']');}
   else throw new IOException("Unsupported JSON request value");
  }
  void unicode(char c)throws IOException{ascii("\\u");put(HEX[(c>>>12)&15]);put(HEX[(c>>>8)&15]);put(HEX[(c>>>4)&15]);put(HEX[c&15]);}
  void string(String text)throws IOException{
   put('"');
   for(int i=0;i<text.length();i++){
    char c=text.charAt(i);
    switch(c){
     case '"':ascii("\\\"");break;case '\\':ascii("\\\\");break;case '\b':ascii("\\b");break;
     case '\f':ascii("\\f");break;case '\n':ascii("\\n");break;case '\r':ascii("\\r");break;case '\t':ascii("\\t");break;
     default:
      if(c<0x20||c==0x2028||c==0x2029){unicode(c);}
      else if(c<0x80)put(c);
      else if(c<0x800){put(0xc0|(c>>6));put(0x80|(c&63));}
      else if(Character.isHighSurrogate(c)){
       if(i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){int point=Character.toCodePoint(c,text.charAt(++i));put(0xf0|(point>>18));put(0x80|((point>>12)&63));put(0x80|((point>>6)&63));put(0x80|(point&63));}else unicode(c);
      }else if(Character.isLowSurrogate(c))unicode(c);
      else{put(0xe0|(c>>12));put(0x80|((c>>6)&63));put(0x80|(c&63));}
    }
   }put('"');
  }
 }
}

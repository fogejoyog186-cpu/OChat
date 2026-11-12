package com.changyu.ochat;

import okhttp3.*;
import okio.*;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class FileAttachmentDataTest {
 private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
 @Test public void fileBytesSurviveChunkBoundariesAndBothDataForms()throws Exception{
  for(int size:new int[]{0,1,2,3,12287,12288,12289,25001}){
   byte[] bytes=new byte[size];new Random(3).nextBytes(bytes);File file=File.createTempFile("wire-",".pdf");
   try{
    try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
    for(boolean uri:new boolean[]{false,true}){
     FileAttachmentData data=new FileAttachmentData(file,"application/pdf",uri);
     RequestBody body=JsonRequestBody.create(new JSONObject().put("file",data),JSON);Buffer sink=new Buffer();body.writeTo(sink);
     assertEquals(body.contentLength(),sink.size());String encoded=new JSONObject(sink.readUtf8()).getString("file");
     assertEquals((uri?"data:application/pdf;base64,":"")+java.util.Base64.getEncoder().encodeToString(bytes),encoded);
    }
   }finally{file.delete();}
  }
 }
 @Test public void pdfLargerThanOldLimitStreamsWithoutMaterializingTheBody()throws Exception{
  File file=File.createTempFile("large-wire-",".pdf");
  try{
   try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));out.setLength(50_000_001L);}
   RequestBody body=JsonRequestBody.create(new JSONObject().put("data",new FileAttachmentData(file,"application/pdf",true)),JSON);
   final long[] written={0};
   BufferedSink sink=Okio.buffer(Okio.sink(new OutputStream(){public void write(int b){written[0]++;}public void write(byte[] b,int o,int n){written[0]+=n;}}));
   body.writeTo(sink);sink.flush();assertEquals(body.contentLength(),written[0]);assertTrue(written[0]>50_000_001L);
  }finally{file.delete();}
 }
 @Test public void changedOrCancelledFileNeverProducesAPartialSuccess()throws Exception{
  File file=File.createTempFile("changed-wire-",".pdf");
  try{
   try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}
   RequestBody body=JsonRequestBody.create(new JSONObject().put("data",new FileAttachmentData(file,"application/pdf",true)),JSON);
   try(OutputStream out=new FileOutputStream(file,true)){out.write(4);}
   try{body.writeTo(new Buffer());fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("变化"));}
   Thread.currentThread().interrupt();try{body.writeTo(new Buffer());fail();}catch(InterruptedIOException expected){}finally{Thread.interrupted();}
  }finally{file.delete();}
 }
 @Test public void allProtocolsKeepDiskDataUntilTheSerializerWritesIt()throws Exception{
  File file=File.createTempFile("protocol-file-",".pdf");byte[] bytes="%PDF-1.4\nfixture".getBytes(StandardCharsets.US_ASCII);
  try{
   try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
   for(String protocol:new String[]{ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES,ChatApiClient.GEMINI,ChatApiClient.ANTHROPIC}){
    JSONArray content=new JSONArray().put(new JSONObject().put("type","file").put("file",new JSONObject().put("filename","scan.pdf").put("file_data",new FileAttachmentData(file,"application/pdf",true))));
    JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",content));
    JSONObject body=new ChatApiClient().requestBody("fixture",history,.7,512,1,"default",protocol,SearchOptions.OFF);
    Buffer sink=new Buffer();JsonRequestBody.create(body,JSON).writeTo(sink);String wire=sink.readUtf8();
    assertTrue(wire.contains(java.util.Base64.getEncoder().encodeToString(bytes)));assertFalse(wire.contains(file.getAbsolutePath()));assertFalse(wire.contains("本地附件数据"));
   }
  }finally{file.delete();}
 }
}

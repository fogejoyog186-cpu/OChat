package com.changyu.ochat;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real image-bearing PDFs cross the actual HTTP client without paid model requests. */
@RunWith(AndroidJUnit4.class)
public class PdfProtocolWireRegressionTest {
 private static final String[] PROTOCOLS={ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC,ChatApiClient.GEMINI};
 private byte[] imagePdf()throws Exception{
  Bitmap image=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.CYAN);Paint ink=new Paint();ink.setColor(Color.MAGENTA);canvas.drawCircle(100,80,50,ink);
  PdfDocument document=new PdfDocument();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try{PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(400,500,1).create());page.getCanvas().drawBitmap(image,40,80,null);document.finishPage(page);document.writeTo(bytes);}finally{document.close();image.recycle();}
  byte[] pdf=bytes.toByteArray();assertTrue(pdf.length>100);assertTrue("fixture must reproduce files below the old 3 MiB limit",pdf.length<3*1024*1024);return pdf;
 }
 @Test public void imagePdfReachesAllFourNonStreamingHttpBodies()throws Exception{byte[] pdf=imagePdf();for(String protocol:PROTOCOLS)verify(protocol,pdf,false);}
 @Test public void imagePdfReachesAllFourStreamingHttpBodies()throws Exception{byte[] pdf=imagePdf();for(String protocol:PROTOCOLS)verify(protocol,pdf,true);}
 private void verify(String protocol,byte[] pdf,boolean streaming)throws Exception{
  String data="data:application/pdf;base64,"+Base64.encodeToString(pdf,Base64.NO_WRAP);
  JSONObject descriptor=new JSONObject().put("mime","application/pdf").put("displayName","扫描图片.pdf");
  // Intentionally start with the opposite OpenAI shape to exercise model/protocol changes.
  String shapeProtocol=ChatApiClient.CHAT_COMPLETIONS.equals(protocol)?ChatApiClient.RESPONSES:ChatApiClient.CHAT_COMPLETIONS;
  JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text","Read the picture on the page.")).put(AttachmentBatch.part(descriptor,data,shapeProtocol));
  JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",content));
  AtomicReference<String> request=new AtomicReference<>(),error=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);ChatApiClient.CancellationHandle cancellation=new ChatApiClient.CancellationHandle();
  try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(12000);Thread worker=new Thread(()->{
    try(Socket socket=server.accept()){
     socket.setSoTimeout(12000);request.set(readRequest(socket));byte[] response=response(protocol,streaming).getBytes(StandardCharsets.UTF_8);
     OutputStream output=socket.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+(streaming?"text/event-stream":"application/json")+"; charset=utf-8\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(response);output.flush();
    }catch(Exception failure){error.compareAndSet(null,"fixture: "+failure);done.countDown();}
   },"pdf-wire-fixture");worker.start();
   try{
    ChatApiClient client=new ChatApiClient(cancellation);String endpoint="http://127.0.0.1:"+server.getLocalPort();
    if(streaming)client.streamChat(endpoint,"fixture-key","fixture",history,.7,512,1,"default",protocol,new ChatApiClient.StreamCallback(){
     public void onDelta(String text){}public void onReasoning(String text){}public void onStatus(String text){}public void onDone(){done.countDown();}public void onError(String message){error.set(message);done.countDown();}
    });
    else client.chat(endpoint,"fixture-key","fixture",history,.7,512,1,"default",protocol,new ChatApiClient.CallbackResult<String>(){
     public void onSuccess(String text){if(!"parsed".equals(text))error.set("Unexpected reply: "+text);done.countDown();}public void onError(String message){error.set(message);done.countDown();}
    });
    assertTrue(protocol+" callback timeout",done.await(15,TimeUnit.SECONDS));assertNull(protocol+": "+error.get(),error.get());assertNotNull(protocol+" never sent an HTTP body",request.get());
    JSONObject body=new JSONObject(request.get());JSONObject part;
    String encoded;
    if(ChatApiClient.GEMINI.equals(protocol)){part=body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(1).getJSONObject("inlineData");assertEquals("application/pdf",part.getString("mimeType"));encoded=part.getString("data");}
    else if(ChatApiClient.ANTHROPIC.equals(protocol)){part=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1);assertEquals("document",part.getString("type"));assertEquals("application/pdf",part.getJSONObject("source").getString("media_type"));encoded=part.getJSONObject("source").getString("data");}
    else if(ChatApiClient.RESPONSES.equals(protocol)){part=body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1);assertEquals("input_file",part.getString("type"));encoded=part.getString("file_data").substring(data.indexOf(',')+1);}
    else{part=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1);assertEquals("file",part.getString("type"));encoded=part.getJSONObject("file").getString("file_data").substring(data.indexOf(',')+1);}
    assertArrayEquals(protocol+" dropped/changed embedded PDF page images",pdf,Base64.decode(encoded,Base64.DEFAULT));
   }finally{cancellation.cancel();server.close();worker.join(13000);}
  }
 }
 private String response(String protocol,boolean stream){
  if(!stream){
   if(ChatApiClient.GEMINI.equals(protocol))return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"parsed\"}]},\"finishReason\":\"STOP\"}]}";
   if(ChatApiClient.ANTHROPIC.equals(protocol))return "{\"content\":[{\"type\":\"text\",\"text\":\"parsed\"}]}";
   if(ChatApiClient.RESPONSES.equals(protocol))return "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"parsed\"}]}]}";
   return "{\"choices\":[{\"message\":{\"content\":\"parsed\"},\"finish_reason\":\"stop\"}]}";
  }
  if(ChatApiClient.GEMINI.equals(protocol))return "data: "+response(protocol,false)+"\n\n";
  if(ChatApiClient.ANTHROPIC.equals(protocol))return "data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"parsed\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n";
  if(ChatApiClient.RESPONSES.equals(protocol))return "data: {\"type\":\"response.output_text.delta\",\"delta\":\"parsed\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n";
  return "data: {\"choices\":[{\"delta\":{\"content\":\"parsed\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
 }
 private String readRequest(Socket socket)throws Exception{
  InputStream input=socket.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int value,last=0;
  while((value=input.read())!=-1){header.write(value);last=(last<<8)|value;if(last==0x0d0a0d0a)break;if(header.size()>16384)throw new IOException("header too large");}
  int length=0;for(String line:header.toString("US-ASCII").split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
  if(length<1||length>8*1024*1024)throw new IOException("invalid fixture body length");byte[] bytes=new byte[length];int offset=0;
  while(offset<length){int count=input.read(bytes,offset,length-offset);if(count<0)throw new EOFException();offset+=count;}return new String(bytes,StandardCharsets.UTF_8);
 }
}

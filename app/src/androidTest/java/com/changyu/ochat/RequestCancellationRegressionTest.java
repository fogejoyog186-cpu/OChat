package com.changyu.ochat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RequestCancellationRegressionTest {
 @Test public void groundingPrecedesFinalUserAndPreservesImageContent()throws Exception{
  JSONArray image=new JSONArray().put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,test")));
  JSONArray history=new JSONArray().put(new JSONObject().put("role","system").put("content","original")).put(new JSONObject().put("role","user").put("content",image));
  JSONArray grounded=MainActivity.groundedMessages(history,"source https://example.org");
  assertEquals(2,grounded.length());assertEquals("system",grounded.getJSONObject(0).getString("role"));assertFalse(grounded.getJSONObject(0).getString("content").contains("https://example.org"));JSONObject user=grounded.getJSONObject(1);assertEquals("user",user.getString("role"));JSONArray parts=user.getJSONArray("content");assertTrue(parts.getJSONObject(0).getString("text").contains("https://example.org"));assertEquals(image.getJSONObject(0).toString(),parts.getJSONObject(1).toString());assertEquals(2,history.length());assertEquals(image.toString(),history.getJSONObject(1).getJSONArray("content").toString());
 }
 @Test public void cancelClosesOnlyScopedHttpAndSuppressesCallbacks()throws Exception{
  ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));CountDownLatch accepted=new CountDownLatch(1),closed=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
  Thread worker=new Thread(()->{try(Socket socket=server.accept()){socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));String line;int length=0;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());for(int i=0;i<length;i++)reader.read();accepted.countDown();if(reader.read()==-1)closed.countDown();}catch(Exception ignored){}});worker.start();
  ChatApiClient.CancellationHandle handle=new ChatApiClient.CancellationHandle();ChatApiClient client=new ChatApiClient(handle);
  try{client.chat("http://127.0.0.1:"+server.getLocalPort(),"local-only","mock",new JSONArray(),.7,32,1,new ChatApiClient.CallbackResult<String>(){public void onSuccess(String s){callbacks.incrementAndGet();}public void onError(String s){callbacks.incrementAndGet();}});assertTrue(accepted.await(5,TimeUnit.SECONDS));handle.cancel();assertTrue(handle.isCancelled());assertTrue(closed.await(5,TimeUnit.SECONDS));Thread.sleep(200);assertEquals(0,callbacks.get());assertFalse(new ChatApiClient.CancellationHandle().isCancelled());}finally{handle.cancel();server.close();worker.join(6000);}
 }
 @Test public void invalidSearchIsLocalAndVisible(){AtomicInteger errors=new AtomicInteger();new ChatApiClient().tavily("","query",5,"basic",new ChatApiClient.CallbackResult<String>(){public void onSuccess(String s){fail();}public void onError(String s){assertTrue(s.contains("Tavily"));errors.incrementAndGet();}});assertEquals(1,errors.get());}
}

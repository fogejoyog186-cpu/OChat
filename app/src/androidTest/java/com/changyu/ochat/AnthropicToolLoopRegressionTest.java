package com.changyu.ochat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AnthropicToolLoopRegressionTest {
 private static final class Server implements AutoCloseable {
  final ServerSocket socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));final List<JSONObject> requests=Collections.synchronizedList(new ArrayList<>());final AtomicReference<Throwable> failure=new AtomicReference<>();final Thread worker;
  Server(boolean streaming,String...responses)throws Exception{
   socket.setSoTimeout(10000);
   worker=new Thread(()->{try{
    for(String response:responses)try(Socket connection=socket.accept()){
     connection.setSoTimeout(10000);requests.add(new JSONObject(read(connection)));byte[] body=response.getBytes(StandardCharsets.UTF_8);
     OutputStream output=connection.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: "+(streaming?"text/event-stream":"application/json")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);output.flush();
    }
   }catch(Throwable error){if(!socket.isClosed())failure.set(error);}},"anthropic-tools-fixture");worker.start();
  }
  String base(){return "http://127.0.0.1:"+socket.getLocalPort();}
  private String read(Socket connection)throws Exception{
   InputStream input=connection.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int value,last=0;
   while((value=input.read())!=-1){header.write(value);last=(last<<8)|value;if(last==0x0d0a0d0a)break;if(header.size()>16384)throw new IOException("fixture header too large");}
   int length=0;for(String line:header.toString("US-ASCII").split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
   if(length<=0||length>2*1024*1024)throw new IOException("fixture request length invalid");byte[] bytes=new byte[length];int offset=0;
   while(offset<length){int n=input.read(bytes,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}return new String(bytes,StandardCharsets.UTF_8);
  }
  @Override public void close()throws Exception{socket.close();worker.join(11000);}
 }
 private JSONObject text(String value)throws Exception{return new JSONObject().put("type","text").put("text",value);}
 private JSONObject result(String url)throws Exception{return new JSONObject().put("type","web_search_tool_result").put("tool_use_id","srv-search").put("content",new JSONArray().put(new JSONObject().put("type","web_search_result").put("url",url).put("title",url).put("encrypted_content","opaque-search-data")));}
 private JSONObject response(boolean pause,String value,int input,int output)throws Exception{
  JSONArray content=new JSONArray().put(new JSONObject().put("type","thinking").put("thinking","thought").put("signature","signed-thought"))
   .put(new JSONObject().put("type","redacted_thinking").put("data","encrypted-thought")).put(text(value)).put(result(pause?"https://example.org/first":"https://example.org/second"));
  if(pause)content.put(new JSONObject().put("type","server_tool_use").put("id","srv-pending").put("name","web_fetch").put("input",new JSONObject().put("url","https://example.org/first")));
  return new JSONObject().put("id","fixture").put("type","message").put("role","assistant").put("content",content).put("container",new JSONObject().put("id","container-fixture"))
   .put("stop_reason",pause?"pause_turn":"end_turn").put("usage",new JSONObject().put("input_tokens",input).put("output_tokens",output).put("cache_read_input_tokens",pause?2:0));
 }
 private String event(JSONObject value){return "data: "+value+"\n\n";}
 private String sse(boolean pause,String value,int input,int output,boolean terminal)throws Exception{
  JSONObject response=response(pause,value,input,output);StringBuilder stream=new StringBuilder();
  stream.append(event(new JSONObject().put("type","message_start").put("message",new JSONObject().put("container",response.getJSONObject("container")).put("usage",new JSONObject().put("input_tokens",input)))));
  JSONArray content=response.getJSONArray("content");
  for(int i=0;i<content.length();i++){
   JSONObject part=content.getJSONObject(i);String type=part.getString("type");JSONObject initial=new JSONObject(part.toString());
   if(type.equals("server_tool_use"))initial.put("input",new JSONObject());
   stream.append(event(new JSONObject().put("type","content_block_start").put("index",i).put("content_block",initial)));
   if(type.equals("server_tool_use"))stream.append(event(new JSONObject().put("type","content_block_delta").put("index",i).put("delta",new JSONObject().put("type","input_json_delta").put("partial_json",part.getJSONObject("input").toString()))));
   stream.append(event(new JSONObject().put("type","content_block_stop").put("index",i)));
  }
  stream.append(event(new JSONObject().put("type","message_delta").put("delta",new JSONObject().put("stop_reason",response.getString("stop_reason"))).put("usage",response.getJSONObject("usage"))));
  if(terminal)stream.append(event(new JSONObject().put("type","message_stop")));return stream.toString();
 }
 private JSONArray history()throws Exception{return new JSONArray().put(new JSONObject().put("role","user").put("content","Search and read the page"));}
 private SearchOptions options(){return new SearchOptions(true,SearchOptions.BUILTIN,ChatApiClient.ANTHROPIC,"claude-fixture","",5,"basic",true,false);}
 private JSONObject block(JSONArray content,String type)throws Exception{for(int i=0;i<content.length();i++)if(content.getJSONObject(i).optString("type").equals(type))return content.getJSONObject(i);throw new AssertionError("missing "+type);}
 @Test public void streamingPauseResumesExactBlocksContainerToolsAndRemainingBudget()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();CountDownLatch done=new CountDownLatch(1);StringBuilder text=new StringBuilder();AtomicReference<String> error=new AtomicReference<>("");AtomicReference<GenerationResult> result=new AtomicReference<>();AtomicInteger finished=new AtomicInteger(),sourceCount=new AtomicInteger();
  try(Server server=new Server(true,sse(true,"before ",4,5,true),sse(false,"after",7,9,true))){
   new ChatApiClient(cancel).streamChat(server.base(),"fixture-key","claude-fixture",history(),.7,512,1,"default",ChatApiClient.ANTHROPIC,options(),new ChatApiClient.StreamCallback(){
    public void onDelta(String value){text.append(value);}public void onReasoning(String value){}public void onStatus(String value){}public void onDone(){fail("use final metadata");}public void onError(String value){error.set(value);done.countDown();}
    public void onFinished(GenerationResult value){result.set(value);finished.incrementAndGet();done.countDown();}public void onSearchSources(SearchSources value,String status){sourceCount.set(value.items.length());}
   });
   assertTrue(done.await(15,TimeUnit.SECONDS));assertEquals("",error.get());assertEquals("before after",text.toString());assertEquals(1,finished.get());assertEquals("complete",result.get().state);assertEquals(11,result.get().usage.getLong("input"));assertEquals(14,result.get().usage.getLong("output"));assertEquals(2,sourceCount.get());assertEquals(2,server.requests.size());
   JSONObject first=server.requests.get(0),second=server.requests.get(1);assertEquals(first.getJSONArray("tools").toString(),second.getJSONArray("tools").toString());assertEquals(507,second.getInt("max_tokens"));assertEquals("container-fixture",second.getString("container"));
   JSONArray messages=second.getJSONArray("messages");assertEquals(2,messages.length());assertEquals("assistant",messages.getJSONObject(1).getString("role"));JSONArray content=messages.getJSONObject(1).getJSONArray("content");
   assertEquals("signed-thought",block(content,"thinking").getString("signature"));assertEquals("encrypted-thought",block(content,"redacted_thinking").getString("data"));assertEquals("https://example.org/first",block(content,"server_tool_use").getJSONObject("input").getString("url"));assertNotNull(block(content,"web_search_tool_result"));assertNull(server.failure.get());
  }finally{cancel.cancel();}
 }
 @Test public void nonStreamingPauseCombinesAnswersWithoutInventingUserToolResults()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();CountDownLatch done=new CountDownLatch(1);AtomicReference<String> answer=new AtomicReference<>(""),error=new AtomicReference<>("");
  try(Server server=new Server(false,response(true,"before ",4,5).toString(),response(false,"after",7,9).toString())){
   new ChatApiClient(cancel).chat(server.base(),"fixture-key","claude-fixture",history(),.7,512,1,"default",ChatApiClient.ANTHROPIC,options(),new ChatApiClient.CallbackResult<String>(){public void onSuccess(String value){answer.set(value);done.countDown();}public void onError(String value){error.set(value);done.countDown();}});
   assertTrue(done.await(15,TimeUnit.SECONDS));assertEquals("",error.get());assertEquals("before after",answer.get());assertEquals(2,server.requests.size());assertEquals("assistant",server.requests.get(1).getJSONArray("messages").getJSONObject(1).getString("role"));assertNull(server.failure.get());
  }finally{cancel.cancel();}
 }
 @Test public void truncatedTransportDoesNotResumeAPartialPauseEvent()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();CountDownLatch done=new CountDownLatch(1);AtomicReference<GenerationResult> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>("");
  try(Server server=new Server(true,sse(true,"partial",4,5,false))){
   new ChatApiClient(cancel).streamChat(server.base(),"fixture","claude-fixture",history(),.7,512,1,"default",ChatApiClient.ANTHROPIC,options(),new ChatApiClient.StreamCallback(){public void onDelta(String value){}public void onReasoning(String value){}public void onStatus(String value){}public void onDone(){}public void onError(String value){error.set(value);done.countDown();}public void onFinished(GenerationResult value){result.set(value);done.countDown();}});
   assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals("",error.get());assertEquals("interrupted",result.get().state);assertEquals(1,server.requests.size());
  }finally{cancel.cancel();}
 }
 @Test public void totalOutputBudgetStopsContinuationBeforeAnotherRequest()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();CountDownLatch done=new CountDownLatch(1);AtomicReference<GenerationResult> result=new AtomicReference<>();
  try(Server server=new Server(true,sse(true,"partial",4,5,true))){
   new ChatApiClient(cancel).streamChat(server.base(),"fixture","claude-fixture",history(),.7,5,1,"default",ChatApiClient.ANTHROPIC,options(),new ChatApiClient.StreamCallback(){public void onDelta(String value){}public void onReasoning(String value){}public void onStatus(String value){}public void onDone(){}public void onError(String value){done.countDown();fail(value);}public void onFinished(GenerationResult value){result.set(value);done.countDown();}});
   assertTrue(done.await(10,TimeUnit.SECONDS));assertNotNull(result.get());assertEquals("truncated",result.get().state);assertEquals(1,server.requests.size());
  }finally{cancel.cancel();}
 }
 @Test public void cancellationAtPauseSuppressesContinuationAndFinalCallbacks()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();CountDownLatch stopped=new CountDownLatch(1);AtomicInteger finalCalls=new AtomicInteger(),errors=new AtomicInteger();
  try(Server server=new Server(true,sse(true,"partial",4,5,true))){
   new ChatApiClient(cancel).streamChat(server.base(),"fixture","claude-fixture",history(),.7,512,1,"default",ChatApiClient.ANTHROPIC,options(),new ChatApiClient.StreamCallback(){public void onDelta(String value){}public void onReasoning(String value){}public void onStatus(String value){if(value.contains("继续生成")){cancel.cancel();stopped.countDown();}}public void onDone(){finalCalls.incrementAndGet();}public void onError(String value){errors.incrementAndGet();stopped.countDown();}});
   assertTrue(stopped.await(10,TimeUnit.SECONDS));server.worker.join(1000);assertTrue(cancel.isCancelled());assertEquals(1,server.requests.size());assertEquals(0,finalCalls.get());assertEquals(0,errors.get());
  }finally{cancel.cancel();}
 }
}

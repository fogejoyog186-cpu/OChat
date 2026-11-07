package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import okhttp3.*;
import static org.junit.Assert.*;

public class ApiFailureTest {
 private Response response(int code,String content,String type){return new Response.Builder().request(new Request.Builder().url("https://example.org/v1/chat/completions").build()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").header("x-request-id","request-123").body(ResponseBody.create(content,MediaType.get(type))).build();}
 @Test public void httpKeepsParameterCodeAndRequestIdWithoutLeakingTheKeyOrPrompt()throws Exception{
  JSONObject body=new JSONObject().put("model","gpt-fixture").put("max_output_tokens",128000).put("reasoning",new JSONObject().put("effort","high")).put("input","PRIVATE PROMPT");
  String raw=new JSONObject().put("error",new JSONObject().put("message","Unsupported reasoning.effort; api_key=secret123").put("param","reasoning.effort").put("code","unsupported_value").put("type","invalid_request_error")).toString();
  try(Response r=response(400,raw,"application/json")){String error=ApiFailure.http(r,"responses",body,"secret123");assertTrue(error,error.contains("400"));assertTrue(error.contains("reasoning.effort"));assertTrue(error.contains("unsupported_value"));assertTrue(error.contains("request-123"));assertTrue(error.contains("max_output_tokens=128000"));assertFalse(error.contains("secret123"));assertFalse(error.contains("PRIVATE PROMPT"));}
 }
 @Test public void nativeFieldViolationsAndNestedResponseAreReadable()throws Exception{
  JSONObject error=new JSONObject("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"message\":\"Bad thinkingConfig\",\"details\":[{\"fieldViolations\":[{\"field\":\"generationConfig.thinkingConfig\",\"description\":\"unsupported thinkingLevel\"}]}]}}");
  String result=ApiFailure.provider(new JSONObject().put("response",error));assertTrue(result.contains("thinkingConfig"));assertTrue(result.contains("unsupported thinkingLevel"));assertTrue(result.contains("INVALID_ARGUMENT"));
 }
 @Test public void htmlAndEmptyErrorsStillKeepStatusAndAreBounded(){
  try(Response r=response(502,"<html><h1>Bad gateway</h1><script>secret script</script></html>","text/html")){String text=ApiFailure.http(r,"gemini",null,"");assertTrue(text.contains("502"));assertTrue(text.contains("Bad gateway"));assertFalse(text.contains("<html>"));assertFalse(text.contains("secret script"));}
  try(Response r=response(401,"","text/plain")){assertTrue(ApiFailure.http(r,"anthropic",null,"").contains("API Key"));}
  assertTrue(ApiFailure.safe(new String(new char[10000]).replace('\0','x'),"").length()<6100);
 }
 @Test public void redactionHandlesBearerAndCredentialFields(){
  String text=ApiFailure.safe("Authorization: Bearer abc123; api_key=\"key987\"; x-api-key: xxx999; sk-verylongsecretvalue","");
  for(String secret:new String[]{"abc123","key987","xxx999","sk-verylongsecretvalue"})assertFalse(text,text.contains(secret));
 }
 @Test public void diagnosticSettingsNeverSerializeMessagesOrBase64()throws Exception{
  JSONObject body=new JSONObject().put("messages",new JSONArray().put("sensitive")).put("system","private").put("thinking",new JSONObject().put("type","enabled").put("budget_tokens",2048)).put("generationConfig",new JSONObject().put("maxOutputTokens",500).put("thinkingConfig",new JSONObject().put("thinkingLevel","low")));
  String text=ApiFailure.settings("gemini",body);assertTrue(text.contains("budget_tokens=2048"));assertTrue(text.contains("thinkingLevel=low"));assertFalse(text.contains("sensitive"));assertFalse(text.contains("private"));
 }
 @Test public void realHttpErrorsReachBothCallbackPathsWithParameters()throws Exception{
  String json="{\"error\":{\"message\":\"Unsupported max_tokens; api_key=local-secret\",\"param\":\"max_tokens\",\"code\":\"unsupported_parameter\"}}";
  for(boolean stream:new boolean[]{false,true}){String[] result=mock(stream,400,json,"application/json");assertTrue(result[0],result[0].contains("400"));assertTrue(result[0].contains("max_tokens"));assertTrue(result[0].contains("unsupported_parameter"));assertTrue(result[0].contains("request-mock"));assertFalse(result[0].contains("local-secret"));}
 }
 @Test public void streamErrorAfterPartialTextKeepsTheActualProviderCause()throws Exception{
  String events="data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\ndata: {\"error\":{\"message\":\"thinking budget invalid\",\"param\":\"thinking.budget_tokens\",\"code\":\"invalid_argument\"}}\n\n";
  String[] result=mock(true,200,events,"text/event-stream");assertEquals("partial",result[1]);assertTrue(result[0],result[0].contains("thinking.budget_tokens"));assertTrue(result[0].contains("invalid_argument"));
  String[] json=mock(true,200,"{\"error\":{\"message\":\"stream unsupported\",\"code\":\"unsupported_stream\"}}","application/json");assertTrue(json[0],json[0].contains("unsupported_stream"));
 }
 @Test public void nonJsonGatewayAndEmptySseAreNotReportedAsGenericParameterErrors()throws Exception{
  String[] gateway=mock(false,502,"<html><h1>Upstream timeout</h1></html>","text/html");assertTrue(gateway[0],gateway[0].contains("502"));assertTrue(gateway[0].contains("Upstream timeout"));
  String[] empty=mock(true,200,": keepalive\n\n","text/event-stream");assertTrue(empty[0],empty[0].contains("empty_stream"));
 }
 private String[] mock(boolean stream,int code,String payload,String type)throws Exception{
  java.net.ServerSocket server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
  java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] result={"",""};java.util.concurrent.atomic.AtomicReference<Throwable> serverError=new java.util.concurrent.atomic.AtomicReference<>();
  Thread worker=new Thread(()->{try(java.net.Socket socket=server.accept()){
   socket.setSoTimeout(5000);java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));String line;int length=0;
   while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
   for(int i=0;i<length;i++)if(reader.read()==-1)throw new java.io.EOFException();
   byte[] bytes=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);socket.getOutputStream().write(("HTTP/1.1 "+code+" Fixture\r\nContent-Type: "+type+"\r\nx-request-id: request-mock\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
  }catch(Throwable e){serverError.set(e);done.countDown();}});worker.start();
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();ChatApiClient client=new ChatApiClient(cancel);
  try{
   String base="http://127.0.0.1:"+server.getLocalPort();JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content","hello"));
   if(stream)client.streamChat(base,"local-secret","fixture",history,.7,32,1,"default",new ChatApiClient.StreamCallback(){
    public void onDelta(String s){result[1]+=s;}public void onReasoning(String s){}public void onStatus(String s){}public void onDone(){result[0]="unexpected completion";done.countDown();}public void onError(String s){result[0]=s;done.countDown();}
   });else client.chat(base,"local-secret","fixture",history,.7,32,1,new ChatApiClient.CallbackResult<String>(){
    public void onSuccess(String s){result[0]="unexpected success";done.countDown();}public void onError(String s){result[0]=s;done.countDown();}
   });
   assertTrue("request did not finish",done.await(8,java.util.concurrent.TimeUnit.SECONDS));if(serverError.get()!=null)throw new AssertionError(serverError.get());return result;
  }finally{cancel.cancel();server.close();worker.join(6000);}
 }

}

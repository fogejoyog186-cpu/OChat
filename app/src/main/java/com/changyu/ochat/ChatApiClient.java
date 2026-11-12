package com.changyu.ochat;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Small OpenAI-compatible client. Protocol is explicit and never inferred from model names. */
public class ChatApiClient {
 private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
 private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(120,TimeUnit.SECONDS).writeTimeout(120,TimeUnit.SECONDS).build();
 public static final String CHAT_COMPLETIONS="chat_completions", RESPONSES="responses", ANTHROPIC="anthropic", GEMINI="gemini";
 private JSONObject configuredModel=new JSONObject();
 ChatApiClient withModel(JSONObject value){configuredModel=value==null?new JSONObject():value;return this;}
 public static final class CancellationHandle {private final Set<Call> calls=new HashSet<>();private volatile boolean cancelled;public boolean isCancelled(){return cancelled;}synchronized void register(Call c){if(cancelled)c.cancel();else calls.add(c);}synchronized void remove(Call c){calls.remove(c);}public synchronized void cancel(){cancelled=true;for(Call c:calls)c.cancel();calls.clear();}}
 private final CancellationHandle cancellation; public ChatApiClient(){this(null);} public ChatApiClient(CancellationHandle c){cancellation=c;} boolean cancelled(){return cancellation!=null&&cancellation.isCancelled();}
 void enqueue(Request r,Callback cb){Call c=HTTP.newCall(r);if(cancellation!=null)cancellation.register(c);c.enqueue(new Callback(){public void onFailure(Call x,IOException e){try{if(!cancelled())cb.onFailure(x,e);}finally{if(cancellation!=null)cancellation.remove(x);}}public void onResponse(Call x,Response v)throws IOException{try{if(cancelled())v.close();else cb.onResponse(x,v);}finally{if(cancellation!=null)cancellation.remove(x);}}});}
 public interface CallbackResult<T>{void onSuccess(T v);void onError(String m);default void onSearchSources(SearchSources sources,String status){} default void onToolEvent(JSONObject event){} } public interface StreamCallback{void onDelta(String s);void onReasoning(String s);default void onReasoningSnapshot(String text){} void onStatus(String s);void onDone();void onError(String m);default void onFinished(GenerationResult result){onDone();} default void onSnapshot(String text){} default void onSearchSources(SearchSources sources,String status){} default void onToolEvent(JSONObject event){} }
 public void chat(String b,String k,String m,JSONArray h,double t,int max,double top,CallbackResult<String> cb){chat(b,k,m,h,t,max,top,"",CHAT_COMPLETIONS,cb);} public void chat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,CallbackResult<String> cb){chat(b,k,m,h,t,max,top,e,CHAT_COMPLETIONS,cb);}
 public void chat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String protocol,CallbackResult<String> cb){chat(b,k,m,h,t,max,top,e,protocol,SearchOptions.OFF,cb);}
 public void chat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String protocol,SearchOptions search,CallbackResult<String> cb){try{DeepSeekInputPolicy.validate(b,m,protocol,h);JSONObject body=requestBody(m,h,t,max,top,e,protocol,search);if(!GEMINI.equals(protocol))body.put("stream",false);if(ANTHROPIC.equals(protocol)){new AnthropicToolRequest(this,b,k,body,search).chat(cb);return;}request("POST",NativeProtocols.nativeProtocol(protocol)?NativeProtocols.endpoint(b,protocol,m,false):endpoint(b,protocol),k,protocol,body,new CallbackResult<JSONObject>(){public void onSuccess(JSONObject x){try{NativeToolState tools=new NativeToolState();tools.complete(protocol,x,NativeToolState.callback(cb));String text;try{text=NativeProtocols.nativeProtocol(protocol)?NativeProtocols.text(x,protocol):ChatResponseParser.parse(x,false);}catch(Exception empty){if(tools.fallback().isEmpty())throw empty;text=tools.fallback();}if(search.grounded()){if(RESPONSES.equals(protocol)){ResponseStream state=new ResponseStream();state.inspectSearch(x);cb.onSearchSources(SearchSources.fromResponse(x),state.searchCalled?state.searchStatus:"本次未调用内置搜索");}else cb.onSearchSources(tools.sources,tools.searchStatus.isEmpty()?(tools.sources.items.length()>0?"已收到模型引用来源":"本次未调用搜索或网页读取"):tools.searchStatus);}cb.onSuccess(text);}catch(Exception z){cb.onError(ApiFailure.safe("无法解析模型回复："+ApiFailure.local(z),k));}}public void onError(String x){cb.onError(search.hasTools()?BuiltinTools.error(0,protocol,search)+"\n"+x:x);}});}catch(Exception x){cb.onError(ApiFailure.safe("请求构建失败："+ApiFailure.local(x)+"\n模型："+m+" · 协议："+protocol,k));}}
 public void streamChat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,StreamCallback cb){streamChat(b,k,m,h,t,max,top,e,CHAT_COMPLETIONS,cb);}
 public void streamChat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String protocol,StreamCallback cb){streamChat(b,k,m,h,t,max,top,e,protocol,SearchOptions.OFF,cb);}
 public void streamChat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String protocol,SearchOptions search,StreamCallback cb){try{DeepSeekInputPolicy.validate(b,m,protocol,h);JSONObject body=requestBody(m,h,t,max,top,e,protocol,search);if(!GEMINI.equals(protocol))body.put("stream",true);if(CHAT_COMPLETIONS.equals(protocol))body.put("stream_options",new JSONObject().put("include_usage",true));if(ANTHROPIC.equals(protocol)){new AnthropicToolRequest(this,b,k,body,search).stream(cb);return;}Request.Builder q=new Request.Builder().url(NativeProtocols.nativeProtocol(protocol)?NativeProtocols.endpoint(b,protocol,m,true):endpoint(b,protocol)).header("Accept","text/event-stream");NativeProtocols.auth(q,k,protocol);enqueue(q.post(JsonRequestBody.create(body,JSON)).build(),new Callback(){public void onFailure(Call c,IOException x){if(!cancelled())cb.onError(ApiFailure.safe("网络请求失败："+ApiFailure.local(x)+"\n"+ApiFailure.settings(protocol,body),k));}public void onResponse(Call c,Response r){try(Response z=r){if(!z.isSuccessful()){String detail=ApiFailure.http(z,protocol,body,k);cb.onError(search.hasTools()?BuiltinTools.error(z.code(),protocol,search)+"\n"+detail:detail);return;}if(z.body()==null){cb.onError("接口没有返回内容");return;}String type=z.header("Content-Type","");if(type!=null&&type.contains("application/json")){JSONObject errorBody=new JSONObject(z.body().string());if(errorBody.has("error"))throw new IOException(ApiFailure.provider(errorBody));throw new IOException("invalid_stream：已请求流式回复，但接口返回 application/json。请检查供应商流式支持和接口协议。");}parseSse(z.body().byteStream(),protocol,cb);}catch(Exception x){if(!cancelled()){String detail=ApiFailure.safe(ApiFailure.local(x)+"\n本次请求："+ApiFailure.settings(protocol,body),k);cb.onError(search.hasTools()?BuiltinTools.error(0,protocol,search)+"\n"+detail:detail);}}}});}catch(Exception x){cb.onError(ApiFailure.safe("请求构建失败："+ApiFailure.local(x)+"\n模型："+m+" · 协议："+protocol,k));}}
 JSONObject parseSse(InputStream input,String protocol,StreamCallback cb)throws Exception {
  BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
  String line,event="",reason="";StringBuilder data=new StringBuilder(),chatText=new StringBuilder();ResponseStream state=new ResponseStream();NativeToolState nativeTools=new NativeToolState();
  ReasoningText.Tags tagged=new ReasoningText.Tags(text->{chatText.append(text);cb.onDelta(text);},cb::onReasoning);JSONObject usage=new JSONObject();boolean eof=false,terminal=false;
  while(!cancelled()&&!eof){
   line=reader.readLine();if(line==null){eof=true;line="";}
   if(!line.isEmpty()){if(line.startsWith("event:"))event=line.substring(6).trim();else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');String value=line.substring(5);data.append(value.startsWith(" ")?value.substring(1):value);}continue;}
   if(data.length()==0)continue;String raw=data.toString();data.setLength(0);
   if("[DONE]".equals(raw.trim())){terminal=true;if(reason.isEmpty())reason="stop";break;}
   JSONObject o;try{o=new JSONObject(raw);}catch(JSONException malformed){throw new IOException("invalid_sse：流事件不是有效 JSON");}
   String type=o.optString("type",event);event="";
   if(o.has("error")&&!o.isNull("error"))throw new IOException(ApiFailure.provider(o));
   nativeTools.observe(protocol,type,o,cb);
   if(RESPONSES.equals(protocol)){
    String beforeReasoning=state.reasoning();
    try{state.accept(type,o);}catch(IOException failure){if(failure.getMessage()!=null&&failure.getMessage().startsWith("refusal")){cb.onSnapshot(state.text());cb.onFinished(GenerationResult.of("refusal",usage));return null;}throw failure;}String afterReasoning=state.reasoning();if(!afterReasoning.equals(beforeReasoning)){if(afterReasoning.startsWith(beforeReasoning))cb.onReasoning(afterReasoning.substring(beforeReasoning.length()));cb.onReasoningSnapshot(afterReasoning);}cb.onSnapshot(state.text());cb.onSearchSources(state.sources,state.searchCalled?state.searchStatus:("response.completed".equals(type)?"本次未调用内置搜索":""));
    JSONObject response=o.optJSONObject("response");if(response!=null){GenerationResult.merge(usage,GenerationResult.usage(response.optJSONObject("usage")));}
    if(type.equals("response.completed")||type.equals("response.incomplete")){
     terminal=true;JSONObject detail=response==null?null:response.optJSONObject("incomplete_details");
     reason=type.equals("response.completed")?"stop":detail==null?"max_output_tokens":detail.optString("reason","max_output_tokens");break;
    }
   }else if(ANTHROPIC.equals(protocol)){
    JSONObject message=o.optJSONObject("message");if(message!=null)GenerationResult.merge(usage,GenerationResult.usage(message.optJSONObject("usage")));
    GenerationResult.merge(usage,GenerationResult.usage(o.optJSONObject("usage")));
    JSONObject delta=o.optJSONObject("delta");
    JSONObject initial=o.optJSONObject("content_block");if(type.equals("content_block_start")&&initial!=null&&"thinking".equals(initial.optString("type")))cb.onReasoning(initial.optString("thinking"));
    if(type.equals("content_block_start")&&initial!=null&&"text".equals(initial.optString("type"))){String text=initial.optString("text");chatText.append(text);if(!text.isEmpty())cb.onDelta(text);}
    if(type.equals("content_block_delta")&&delta!=null){String text=delta.optString("text");if(!text.isEmpty()){chatText.append(text);cb.onDelta(text);}if(delta.has("thinking"))cb.onReasoning(delta.optString("thinking"));}
    if(type.equals("message_delta")&&delta!=null)reason=delta.optString("stop_reason",reason);
    if(type.equals("message_stop")){terminal=true;break;}
   }else if(GEMINI.equals(protocol)){
    GenerationResult.merge(usage,GenerationResult.usage(o.optJSONObject("usageMetadata")));
    JSONArray candidates=o.optJSONArray("candidates");JSONObject first=candidates==null?null:candidates.optJSONObject(0);
    JSONObject feedback=o.optJSONObject("promptFeedback");if(feedback!=null&&feedback.has("blockReason")){reason="blocked";terminal=true;break;}
    if(first!=null){JSONObject content=first.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");
     if(parts!=null)for(int i=0;i<parts.length();i++){JSONObject p=parts.optJSONObject(i);if(p==null)continue;String text=p.optString("text");if(p.optBoolean("thought"))cb.onReasoning(text);else{chatText.append(text);cb.onDelta(text);}}
     if(first.has("finishReason")){reason=first.optString("finishReason");terminal=true;}
    }
   }else{
    GenerationResult.merge(usage,GenerationResult.usage(o.optJSONObject("usage")));
    JSONArray choices=o.optJSONArray("choices");JSONObject choice=choices==null?null:choices.optJSONObject(0),delta=choice==null?null:choice.optJSONObject("delta");
    if(delta!=null){if(ChatResponseParser.hasRefusal(delta)){reason="refusal";terminal=true;String refusal=ChatResponseParser.text(delta.opt("refusal"));if(!refusal.isEmpty()){chatText.append(refusal);cb.onDelta(refusal);}}String rs=ReasoningText.delta(delta);if(!rs.isEmpty())cb.onReasoning(rs);String text=ChatResponseParser.text(delta.opt("content"));if(!text.isEmpty())tagged.append(text);}
    if(choice!=null&&!choice.isNull("finish_reason")&&!choice.optString("finish_reason").isEmpty()){if(!"refusal".equals(reason))reason=choice.optString("finish_reason");terminal=true;}
   }
  }
  if(cancelled())return null;
  if(ANTHROPIC.equals(protocol)&&terminal&&"pause_turn".equals(reason))return nativeTools.paused(usage);
  if(CHAT_COMPLETIONS.equals(protocol))tagged.finish();
  String text=RESPONSES.equals(protocol)?state.text():chatText.toString();
  if(text.isEmpty()&&!nativeTools.fallback().isEmpty()){text=nativeTools.fallback();if(RESPONSES.equals(protocol))cb.onSnapshot(text);else cb.onDelta(text);}
  if(text.isEmpty()&&!terminal)throw new IOException("empty_stream：连接已结束，但未收到有效答案或结束事件。请检查接口是否支持当前协议的 SSE 流式回复。");
  if(text.isEmpty()&&(reason.equals("stop")||reason.equals("end_turn")||reason.equals("STOP")))cb.onError("no_text：模型返回了空内容");
  else cb.onFinished(GenerationResult.of(terminal?reason:"interrupted",usage));
  return null;
 }
 static String searchError(int code){return "web_search_error："+(code>0?"接口错误（"+code+"）":"请求未完成")+"。请检查供应商 / 模型是否支持 Responses 的 web_search 工具及工具权限、计费；可在设置 → 联网搜索选择 Tavily。不自动重发或退回无搜索。";}
 private JSONObject requestBody(String model,JSONArray messages,double t,int max,double top,String effort,String protocol)throws Exception{return requestBody(model,messages,t,max,top,effort,protocol,SearchOptions.OFF);}
 JSONObject requestBody(String model,JSONArray messages,double t,int max,double top,String effort,String protocol,SearchOptions search)throws Exception{if(!search.error.isEmpty())throw new IllegalArgumentException(search.error);if(search.grounded())messages=SearchGrounding.withPolicy(messages);ModelCapabilities caps=new ModelCapabilities(model,protocol,configuredModel);if(NativeProtocols.nativeProtocol(protocol))return BuiltinTools.apply(NativeProtocols.body(model,messages,t,max,top,effort,protocol,caps),protocol,search);if(RESPONSES.equals(protocol)){JSONObject b=new JSONObject().put("model",model).put("input",responsesInput(messages)).put("max_output_tokens",max).put("store",false);if(caps.sampling)b.put("temperature",t).put("top_p",top);String instruction=messages.length()>0&&messages.optJSONObject(0)!=null&&"system".equals(messages.optJSONObject(0).optString("role"))?messages.optJSONObject(0).optString("content",""):"";if(!instruction.isEmpty()){b.put("instructions",instruction);b.getJSONArray("input").remove(0);}ReasoningRequest.responses(b,caps,effort);return BuiltinTools.apply(b,protocol,search);}JSONObject b=new JSONObject().put("model",model).put("messages",chatInput(messages,caps.preserveReasoning())).put(caps.outputTokenKey(),max);if(caps.sampling)b.put("temperature",t).put("top_p",top);ReasoningRequest.chat(b,caps,effort,max);return BuiltinTools.apply(b,protocol,search);}
 private JSONArray chatInput(JSONArray messages,boolean preserveReasoning)throws JSONException{
  JSONArray result=ReasoningRequest.chatMessages(messages,preserveReasoning);
  for(int i=0;i<result.length();i++){
   JSONObject message=result.getJSONObject(i);JSONArray parts=message.optJSONArray("content");if(parts==null)continue;JSONArray content=new JSONArray();
   for(int j=0;j<parts.length();j++){
    JSONObject part=parts.optJSONObject(j);if(part==null)throw new IllegalArgumentException("附件内容块无效");
    String type=part.optString("type");
    if("text".equals(type)||"input_text".equals(type)||"output_text".equals(type))content.put(new JSONObject().put("type","text").put("text",part.optString("text")));
    else if("image_url".equals(type)||"input_image".equals(type)){
     JSONObject image="image_url".equals(type)?part.optJSONObject("image_url"):new JSONObject().put("url",part.optString("image_url"));
     if(image==null||image.optString("url").isEmpty())throw new IllegalArgumentException("图片附件缺少地址");
     content.put(new JSONObject().put("type","image_url").put("image_url",image));
    }else if("file".equals(type)||"input_file".equals(type)){
     JSONObject file=NativeProtocols.fileData(part);
     if(!"application/pdf".equals(NativeProtocols.embeddedMime(file.get("file_data"))))throw new IllegalArgumentException("Chat Completions 原文件输入仅支持 PDF，其他文件请使用 Responses 或提取文字");
     content.put(new JSONObject().put("type","file").put("file",file));
    }else throw new IllegalArgumentException("不支持的附件内容类型："+type);
   }
   message.put("content",content);
  }
  return result;
 }
 private JSONArray responsesInput(JSONArray messages)throws Exception{
  JSONArray out=new JSONArray();
  for(int i=0;i<messages.length();i++){
   JSONObject message=messages.optJSONObject(i);if(message==null)continue;String role=message.optString("role","user");JSONArray content=new JSONArray();Object value=message.opt("content");
   if(value instanceof JSONArray){
    JSONArray parts=(JSONArray)value;
    for(int j=0;j<parts.length();j++){
     JSONObject part=parts.optJSONObject(j);if(part==null)throw new IllegalArgumentException("附件内容块无效");
     String type=part.optString("type");
     if("image_url".equals(type)||"input_image".equals(type)){
      JSONObject image=part.optJSONObject("image_url");Object url="image_url".equals(type)?image==null?null:image.opt("url"):part.opt("image_url");
      if(url==null||url instanceof String&&((String)url).isEmpty())throw new IllegalArgumentException("图片附件缺少地址");
      JSONObject converted=new JSONObject().put("type","input_image").put("image_url",url);
      String detail=image==null?part.optString("detail"):image.optString("detail");if(!detail.isEmpty())converted.put("detail",detail);content.put(converted);
     }else if("input_file".equals(type)||"file".equals(type)){
      JSONObject file=NativeProtocols.fileData(part);NativeProtocols.embeddedMime(file.get("file_data"));
      JSONObject converted=new JSONObject().put("type","input_file").put("file_data",file.get("file_data")).put("filename",file.getString("filename"));
      if(part.has("detail"))converted.put("detail",part.get("detail"));content.put(converted);
     }else if("text".equals(type)||"input_text".equals(type)||"output_text".equals(type))content.put(new JSONObject().put("type","assistant".equals(role)?"output_text":"input_text").put("text",part.optString("text",part.optString("value",""))));
     else throw new IllegalArgumentException("不支持的附件内容类型："+type);
    }
   }else content.put(new JSONObject().put("type","assistant".equals(role)?"output_text":"input_text").put("text",String.valueOf(value==null||value==JSONObject.NULL?"":value)));
   out.put(new JSONObject().put("type","message").put("role","system".equals(role)?"developer":role).put("content",content));
  }
  return out;
 }
 public void topic(String b,String k,String m,String prompt,String first,CallbackResult<String> cb){topic(b,k,m,prompt,first,CHAT_COMPLETIONS,cb);}public void topic(String b,String k,String m,String prompt,String first,String protocol,CallbackResult<String> cb){try{JSONArray h=new JSONArray().put(new JSONObject().put("role","system").put("content",prompt)).put(new JSONObject().put("role","user").put("content",first));chat(b,k,m,h,.2,2048,1,"default",protocol,cb);}catch(Exception e){cb.onError("invalid_config：命名请求参数无效");}}
 public void models(String base,String key,CallbackResult<JSONArray> cb){models(base,key,CHAT_COMPLETIONS,cb);}
 public void models(String base,String key,String protocol,CallbackResult<JSONArray> cb){
  try{String url=NativeProtocols.nativeProtocol(protocol)?NativeProtocols.modelsEndpoint(base,protocol):endpoint(base,"models");request("GET",url,key,protocol,null,new CallbackResult<JSONObject>(){
   public void onSuccess(JSONObject x){JSONArray data=x.optJSONArray(GEMINI.equals(protocol)?"models":"data"),out=new JSONArray();if(data!=null)for(int i=0;i<data.length();i++){JSONObject m=data.optJSONObject(i);String id=m==null?data.optString(i):m.optString(GEMINI.equals(protocol)?"name":"id");if(GEMINI.equals(protocol)){JSONArray supported=m==null?null:m.optJSONArray("supportedGenerationMethods");if(supported!=null&&!supported.toString().contains("generateContent"))continue;if(id.startsWith("models/"))id=id.substring(7);}if(!id.isEmpty())out.put(id);}cb.onSuccess(out);}
   public void onError(String message){cb.onError(message);}
  });}catch(Exception e){cb.onError("API 地址或请求参数无效："+readable(e.getMessage()));}
 }
 private void request(String method,String url,String key,JSONObject json,CallbackResult<JSONObject> cb){request(method,url,key,CHAT_COMPLETIONS,json,cb);}
 private void request(String method,String url,String key,String protocol,JSONObject json,CallbackResult<JSONObject> cb){Request.Builder b=new Request.Builder().url(url).header("Accept","application/json");NativeProtocols.auth(b,key,protocol);if("POST".equals(method))b.post(JsonRequestBody.create(json,JSON));else b.get();enqueue(b.build(),new Callback(){public void onFailure(Call c,IOException e){cb.onError(ApiFailure.safe("网络请求失败："+ApiFailure.local(e),key));}public void onResponse(Call c,Response r){try(Response x=r){if(!x.isSuccessful()){cb.onError(ApiFailure.http(x,protocol,json,key));return;}String raw=x.body()==null?"":x.body().string();JSONObject o;try{o=new JSONObject(raw);}catch(Exception e){throw new IOException("invalid_json：服务端返回的不是有效 JSON，请核对接口协议和 API 路径（HTTP "+x.code()+"，Content-Type: "+x.header("Content-Type","未提供")+"）");}if(o.has("error")&&!o.isNull("error")){cb.onError(ApiFailure.safe(ApiFailure.provider(o),key));return;}cb.onSuccess(o);}catch(Exception e){cb.onError(ApiFailure.safe(ApiFailure.local(e),key));}}});}
 private static void auth(Request.Builder b,String k){if(k!=null&&!k.trim().isEmpty())b.header("Authorization","Bearer "+k.trim());}
 static String endpoint(String base,String suffix){String b=base==null?"":base.trim();while(b.endsWith("/"))b=b.substring(0,b.length()-1);if(!(b.startsWith("http://")||b.startsWith("https://")))throw new IllegalArgumentException("API 地址必须以 http:// 或 https:// 开头");for(String x:new String[]{"/chat/completions","/responses"})if(b.endsWith(x))b=b.substring(0,b.length()-x.length());if("models".equals(suffix))return b+"/models";return b+(RESPONSES.equals(suffix)?"/responses":"/chat/completions");}
 private static String readable(String s){return s==null||s.trim().isEmpty()?"未知网络错误":s;}
 public void tavily(String k,String q,int count,String depth,CallbackResult<String> cb){tavilySources(k,q,count,depth,new CallbackResult<SearchSources>(){public void onSuccess(SearchSources s){cb.onSuccess(s.grounding());}public void onError(String error){cb.onError(error);}});}
 public void tavilySources(String k,String q,int count,String depth,CallbackResult<SearchSources> cb){if(k==null||k.trim().isEmpty()){cb.onError("请在设置 → 联网搜索中填写 Tavily API Key");return;}if(count<1||count>20||!("basic".equals(depth)||"advanced".equals(depth))){cb.onError("搜索参数无效：数量应为 1–20，深度为 basic/advanced");return;}try{JSONObject body=new JSONObject().put("api_key",k).put("query",q).put("max_results",count).put("search_depth",depth);request("POST","https://api.tavily.com/search",null,body,new CallbackResult<JSONObject>(){public void onSuccess(JSONObject v){if(v.has("error")){cb.onError("搜索服务返回错误，请检查配置");return;}cb.onSuccess(SearchSources.parse(v.optJSONArray("results")));}public void onError(String x){cb.onError(x);}});}catch(Exception e){cb.onError("搜索请求无效，请检查配置");}}
}
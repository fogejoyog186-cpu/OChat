package com.changyu.ochat;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import androidx.core.app.NotificationCompat;

import androidx.core.content.ContextCompat;
import org.json.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Owns the request and its persisted result independently of any Activity or view. */
public final class ChatGenerationService extends Service {
 static final String CHANNEL="chat_generation",STOP="com.changyu.ochat.STOP_GENERATION";
 private static final int NOTICE=71;
 private static volatile Run current;
 private static final Map<String,Run> runs=new ConcurrentHashMap<>();
 private static WeakReference<Listener> listener=new WeakReference<>(null);
 interface Listener {void changed(Run run);}
 static final class Run {
  final String id=UUID.randomUUID().toString(),sessionId,modelId,effort;
  final long timestamp;final StringBuffer partial=new StringBuffer(),reasoningText=new StringBuffer();
  final ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();
  ChatApiClient client=new ChatApiClient(cancel),ocrClient;
  String base,key,protocol,branchId="",temperature="0.7",topP="0.9",outputTokens=ContextBudget.DEFAULT_OUTPUT_TEXT,query="",status="正在准备上下文和附件…";
  JSONArray history;JSONObject session;Map<String,String> prepared=Collections.emptyMap();
  final PdfPageFallback.Session pdfPages=new PdfPageFallback.Session();boolean pdfFallbackTried;String pdfOriginalError="";
  SearchOptions search=SearchOptions.OFF;SearchSources sources=new SearchSources();
  volatile boolean finished;private volatile String sourceUpdate="";boolean error,stopped,started;StreamingMarkdown stream,reasoningStream;
  JSONObject configuredModel=new JSONObject(),usage=new JSONObject();String completionState="complete",finishReason="",searchNote="",searchStatus="";final JSONArray toolEvents=new JSONArray();int contextTokens,omittedMessages;long startedAt;
  Run(String sessionId,String modelId,String effort,long timestamp){this.sessionId=sessionId;this.modelId=modelId;this.effort=effort;this.timestamp=timestamp;}
 }
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newFixedThreadPool(2);
 private PowerManager.WakeLock wake;
 private final Runnable checkpoint=new Runnable(){public void run(){
  for(Run run:new ArrayList<>(runs.values()))if(valid(run)){persist(run,false);if(System.currentTimeMillis()-run.startedAt>30*60*1000L)finish(run,"",false,true);}
  if(!runs.isEmpty())main.postDelayed(this,3000);
 }};
 static Run active(){return current;}
 static Run active(String sessionId){return runs.get(sessionId);}
 static int activeCount(){return runs.size();}
 static void attach(Listener target){listener=new WeakReference<>(target);for(Run run:new ArrayList<>(runs.values()))target.changed(run);}
 static void detach(Listener target){if(listener.get()==target)listener.clear();}
 static void launch(Context context,Run run){
  if(runs.containsKey(run.sessionId))throw new IllegalStateException("本对话已有回复正在生成");
  runs.put(run.sessionId,run);current=run;
  try{ContextCompat.startForegroundService(context.getApplicationContext(),new Intent(context,ChatGenerationService.class));}
  catch(RuntimeException failure){runs.remove(run.sessionId);current=runs.isEmpty()?null:runs.values().iterator().next();throw failure;}
 }
 static void stop(Context context,Run run){context.startService(new Intent(context,ChatGenerationService.class).setAction(STOP).putExtra("runId",run.id));}
 private static boolean valid(Run run){return run!=null&&run==runs.get(run.sessionId)&&!run.finished&&!run.cancel.isCancelled();}
 private static void publish(Run run){Listener target=listener.get();if(target!=null)target.changed(run);}
 static JSONArray history(JSONObject session,String prompt)throws JSONException{
  JSONArray result=new JSONArray();if(!prompt.trim().isEmpty())result.put(new JSONObject().put("role","system").put("content",prompt));
  JSONArray messages=session.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&!m.optBoolean("error"))result.put(new JSONObject().put("role",m.optBoolean("user")?"user":"assistant").put("content",m.optString("text")).put("_messageId",m.optString("id")).put("reasoning_content",m.optString("reasoningContent")));}
  return result;
 }
 static void enqueue(Context context,String sessionId,JSONObject queued)throws JSONException{
  SharedPreferences prefs=context.getSharedPreferences("chat",MODE_PRIVATE);JSONObject session=MessageEdits.findSession(SessionRepository.get(prefs).read(),sessionId);if(session==null)throw new JSONException("对话不存在");
  JSONArray queue=session.optJSONArray("outbox");if(queue==null){queue=new JSONArray();session.put("outbox",queue);}if(queue.length()>=10)throw new JSONException("最多排队 10 条消息");
  queue.put(queued);SessionRepository.get(prefs).upsert(session);
 }
 static void resumeQueue(Context context,String sessionId){
  if(active(sessionId)!=null)return;
  SharedPreferences prefs=context.getSharedPreferences("chat",MODE_PRIVATE);JSONObject session=MessageEdits.findSession(SessionRepository.get(prefs).read(),sessionId);if(session==null)return;
  JSONArray queue=session.optJSONArray("outbox");if(queue==null||queue.length()==0)return;JSONObject next=queue.optJSONObject(0);
  try{
   JSONObject config=next.optJSONObject("config");if(config==null)config=SessionConfig.capture(prefs);
   JSONObject temp=new JSONObject().put("config",config);SessionConfig scoped=new SessionConfig(prefs,temp);JSONObject provider=scoped.provider();
   if(provider.optString("api").isEmpty()||provider.optString("apiKey").isEmpty())return;
   JSONObject message=next.getJSONObject("message");session.getJSONArray("messages").put(message);queue.remove(0);ConversationBranches.sync(session);SessionRepository.get(prefs).upsert(session);
   Run run=new Run(sessionId,scoped.getString("modelId",""),scoped.getString("reasoningEffort","default"),System.currentTimeMillis());
   run.base=provider.optString("api");run.key=provider.optString("apiKey");run.protocol=provider.optString("protocol",ChatApiClient.CHAT_COMPLETIONS);run.configuredModel=ModelCapabilities.find(provider,run.modelId);
   run.temperature=scoped.getString("conversationTemperature","0.7");run.topP=scoped.getString("conversationTopP","0.9");run.outputTokens=scoped.getString("outputTokens",run.configuredModel.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT));
   run.search=SearchOptions.capture(scoped,run.protocol);run.session=session;run.branchId=session.optString("activeBranchId");run.history=history(session,scoped.getString("systemPrompt",""));run.query=message.optString("text");
   launch(context,run);
  }catch(Exception failure){android.util.Log.e("ChatGeneration","Unable to resume queue",failure);}
 }
 @Override public void onCreate(){
  super.onCreate();if(Build.VERSION.SDK_INT>=26){
   NotificationChannel channel=new NotificationChannel(CHANNEL,"对话生成",NotificationManager.IMPORTANCE_LOW);
   channel.setDescription("回复生成期间保持连接，可随时停止");getSystemService(NotificationManager.class).createNotificationChannel(channel);
  }
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null&&STOP.equals(intent.getAction())){
   for(Run run:new ArrayList<>(runs.values()))if(run.id.equals(intent.getStringExtra("runId"))){
    JSONObject session=MessageEdits.findSession(SessionRepository.get(getSharedPreferences("chat",MODE_PRIVATE)).read(),run.sessionId);if(session!=null){session.remove("outbox");SessionRepository.get(getSharedPreferences("chat",MODE_PRIVATE)).upsert(session);}
    finish(run,"",false,true);break;
   }return START_NOT_STICKY;
  }
  if(runs.isEmpty()){stopSelf();return START_NOT_STICKY;}
  try{
   Run representative=active();if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification(representative),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTICE,notification(representative));
   if(wake==null){PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":generation");wake.acquire(30*60*1000L);}
  }catch(RuntimeException failure){for(Run run:new ArrayList<>(runs.values()))finish(run,"无法启动后台生成服务，请重试。",true,false);return START_NOT_STICKY;}
  for(Run run:new ArrayList<>(runs.values()))if(!run.started){
   run.started=true;run.startedAt=System.currentTimeMillis();run.client.withModel(run.configuredModel);
   run.stream=new StreamingMarkdown(run.partial,text->{if(valid(run))publish(run);});
   run.reasoningStream=new StreamingMarkdown(run.reasoningText,text->{if(valid(run))publish(run);});publish(run);
   worker.execute(()->{
    try{if(!new ModelCapabilities(run.modelId,run.protocol,run.configuredModel).preserveReasoning()||!ChatApiClient.CHAT_COMPLETIONS.equals(run.protocol))for(int i=0;i<run.history.length();i++)run.history.getJSONObject(i).remove("reasoning_content");int capacity=ModelCapabilities.positive(run.configuredModel,"contextLength",ContextBudget.DEFAULT_CONTEXT),output=Integer.parseInt(run.outputTokens);ContextBudget.Result initial=ContextBudget.trim(run.history,capacity,output);run.history=initial.messages;run.omittedMessages=initial.omitted;prepareAttachments(run);recognizeDocuments(run);DocumentOcr.scrub(run.history);run.history=DeepSeekDocumentInput.prepare(getApplicationContext(),run.base,run.modelId,run.protocol,run.history,run.pdfPages,()->valid(run),text->main.post(()->status(run,text)));ContextBudget.Result budget=ContextBudget.trim(run.history,capacity,output);run.history=budget.messages;run.contextTokens=budget.estimatedTokens;run.omittedMessages+=budget.omitted;main.post(()->{if(valid(run))search(run);});}
    catch(Exception failure){main.post(()->finish(run,failure.getMessage()==null?"附件或上下文准备失败，请检查配置。":failure.getMessage(),true,false));}
    finally{run.session=null;run.prepared=Collections.emptyMap();if(!valid(run))run.pdfPages.close();}
   });
  }
  main.removeCallbacks(checkpoint);main.postDelayed(checkpoint,3000);return START_NOT_STICKY;
 }
 private Notification notification(Run run){
  Intent open=new Intent(this,MainActivity.class).putExtra("resumeGeneration",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ChatGenerationService.class).setAction(STOP).putExtra("runId",run.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_chat_notification)
   .setContentTitle(runs.size()>1?runs.size()+" 个对话正在生成":"正在生成回复").setContentText(run.configuredModel.optString("displayName",run.modelId)+" · "+run.status).setContentIntent(content)
   .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW)
   .setCategory(NotificationCompat.CATEGORY_PROGRESS).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
   .addAction(0,"停止生成",stop).build();
 }
 private void status(Run run,String value){
  if(!valid(run))return;run.status=value;publish(run);
  if(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
   try{getSystemService(NotificationManager.class).notify(NOTICE,notification(run));}catch(SecurityException ignored){}
 }
 private void search(Run run){
  if(!run.search.error.isEmpty()){finish(run,run.search.error,true,false);return;}
  if(run.search.builtin){try{run.history=SearchGrounding.withPolicy(run.history);status(run,"内置搜索可用 · 由模型决定是否搜索");chat(run);}catch(JSONException invalid){finish(run,"搜索上下文构建失败",true,false);}return;}
  if(!run.search.tavily){chat(run);return;}
  if(run.search.key.isEmpty()){run.searchNote="搜索未配置 · 本次未联网";run.search=run.search.withoutSearch();status(run,run.searchNote);chat(run);return;}
  status(run,"正在联网搜索…");
  run.client.tavilySources(run.search.key,run.query,run.search.count,run.search.depth,new ChatApiClient.CallbackResult<SearchSources>(){
   public void onSuccess(SearchSources sources){main.post(()->{if(!valid(run))return;run.sources=sources;int capacity=ModelCapabilities.positive(run.configuredModel,"contextLength",ContextBudget.DEFAULT_CONTEXT);String grounding=sources.grounding(SearchGrounding.characterBudget(run.history,capacity,Integer.parseInt(run.outputTokens)));
    if(grounding.isEmpty()&&sources.items.length()>0)run.searchNote="上下文空间不足 · 未加入搜索资料";
    run.searchStatus=grounding.isEmpty()?(sources.items.length()>0?run.searchNote:"搜索无有效来源 · 直接回答"):"搜索完成 · "+sources.items.length()+" 个来源";status(run,run.searchStatus);
    try{if(!grounding.isEmpty())run.history=MainActivity.groundedMessages(run.history,grounding);chat(run);}catch(JSONException e){finish(run,"搜索上下文构建失败",true,false);}
   });}
   public void onError(String message){main.post(()->{if(valid(run)){run.searchNote="搜索失败 · 本次未联网";run.searchStatus=run.searchNote;run.search=run.search.withoutSearch();status(run,run.searchNote);chat(run);}});}
  });
 }
 private void sources(Run run,SearchSources sources,String status){
  if(!run.search.grounded()||!valid(run))return;
  String signature=sources.items.toString()+"\u0000"+status;
  if(signature.equals(run.sourceUpdate))return;run.sourceUpdate=signature;
  SearchSources copy=SearchSources.parse(sources.items);
  main.post(()->{if(valid(run)){
   if(run.search.tavily){JSONArray combined=new JSONArray();for(int i=0;i<run.sources.items.length();i++)combined.put(run.sources.items.opt(i));for(int i=0;i<copy.items.length();i++)combined.put(copy.items.opt(i));run.sources=SearchSources.parse(combined);}else run.sources=copy;
   if(!status.isEmpty()){run.status=status;run.searchStatus=status;}publish(run);
  }});
 }
 private void chat(Run run){
  if(!valid(run))return;
  try{
   boolean sampling=new ModelCapabilities(run.modelId,run.protocol,run.configuredModel).sampling;double temperature=sampling?ModelStore.range(run.temperature,2):.7,top=sampling?ModelStore.range(run.topP,1):1;int max=Integer.parseInt(run.outputTokens);if(max<=0)throw new IllegalArgumentException();
   ContextBudget.Result budget=ContextBudget.trim(run.history,ModelCapabilities.positive(run.configuredModel,"contextLength",ContextBudget.DEFAULT_CONTEXT),max);run.history=budget.messages;run.contextTokens=budget.estimatedTokens;run.omittedMessages+=budget.omitted;max=budget.outputTokens;
   if(new ModelCapabilities(run.modelId,run.protocol,run.configuredModel).needsBudget()&&!"default".equals(run.effort)&&!"none".equals(run.effort)&&max<=1024)throw new IllegalArgumentException("剩余上下文只能分配 "+max+" 个输出 token，此 Claude 模型开启思考至少需要 1025；请减少输入、增加模型上下文容量，或选择自动 / none");
   status(run,"正在接收回复");
   run.client.streamChat(run.base,run.key,run.modelId,run.history,temperature,max,top,run.effort,run.protocol,run.search,new ChatApiClient.StreamCallback(){
    public void onDelta(String text){if(valid(run))run.stream.append(text);}
    public void onSnapshot(String text){if(valid(run))run.stream.replace(text);}
    public void onReasoning(String text){if(valid(run))run.reasoningStream.append(text);}
    public void onReasoningSnapshot(String text){if(valid(run))run.reasoningStream.replace(text);}
    public void onStatus(String text){main.post(()->status(run,text));}
    public void onSearchSources(SearchSources value,String status){sources(run,value,status);}
    public void onToolEvent(JSONObject event){main.post(()->{if(valid(run))run.toolEvents.put(event);});}
    public void onDone(){main.post(()->finish(run,run.partial.toString(),run.partial.length()==0,false));}
    public void onFinished(GenerationResult result){main.post(()->{run.completionState=result.state;run.finishReason=result.reason;run.usage=result.usage;if("filtered".equals(result.state)&&run.partial.length()==0)run.partial.append("模型未提供可显示的回答，回复因内容限制而终止。");finish(run,run.partial.toString(),false,"interrupted".equals(result.state));});}
    public void onError(String message){main.post(()->{
     if(!valid(run))return;
     if(!run.pdfFallbackTried&&run.partial.length()==0&&run.reasoningText.length()==0&&PdfPageFallback.shouldRetry(message,run.history)){retryPdfAsImages(run,message);return;}
     String detail=run.pdfFallbackTried?run.pdfOriginalError+"\n逐页图像兼容发送仍失败：\n"+message:message;
     run.completionState="interrupted";run.finishReason=detail;
     if(run.partial.length()>0)finish(run,run.partial.toString(),false,true);else finish(run,detail,true,false);
    });}
   });
  }catch(Exception error){finish(run,ApiFailure.safe("请求准备失败："+ApiFailure.local(error)+"\n模型："+run.modelId+" · 协议："+run.protocol+" · 输出上限："+run.outputTokens,run.key),true,false);}
 }
 private void retryPdfAsImages(Run run,String original){
  run.pdfFallbackTried=true;run.pdfOriginalError=original;status(run,"接口拒绝 PDF，正在逐页转换为图像…");
  worker.execute(()->{
   try{
    JSONArray converted=PdfPageFallback.render(getApplicationContext(),run.history,run.pdfPages,()->valid(run),text->main.post(()->status(run,text)));
    main.post(()->{if(valid(run)){run.history=converted;chat(run);}});
   }catch(Exception failure){main.post(()->{if(valid(run))finish(run,original+"\nPDF 图像兼容处理失败："+ApiFailure.local(failure),true,false);});}
   finally{if(!valid(run))run.pdfPages.close();}
  });
 }
 private void finish(Run run,String text,boolean error,boolean stopped){
  if(run==null||run.finished)return;
  if(stopped&&run.partial.length()>0)text=run.partial.toString();
  if(text.isEmpty()&&!stopped)text=run.reasoningText.length()>0?"模型只返回了思考内容，尚未生成最终回答。":"模型返回了空内容";
  if(error&&run.finishReason.isEmpty())run.finishReason=text;
  if(!error&&!stopped&&run.search.grounded()&&run.searchStatus.isEmpty())run.searchStatus="本次未调用搜索或网页读取";
  run.error=error;run.stopped=stopped;run.finished=true;run.cancel.cancel();run.pdfPages.close();
  if(stopped){run.completionState="interrupted";run.status="回复已停止或中断";}else if(error){run.completionState="error";run.status="回复生成失败";}else run.status=new GenerationResult(run.completionState,run.finishReason,run.usage).label();
  if(run.stream!=null)run.stream.seal();if(run.reasoningStream!=null)run.reasoningStream.seal();
  run.partial.setLength(0);run.partial.append(text);
  persist(run,true);
  runs.remove(run.sessionId);if(current==run)current=runs.isEmpty()?null:runs.values().iterator().next();
  publish(run);if(!stopped)resumeQueue(this,run.sessionId);
  if(runs.isEmpty()){main.removeCallbacks(checkpoint);if(wake!=null&&wake.isHeld())wake.release();wake=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
  else {getSystemService(NotificationManager.class).notify(NOTICE,notification(current));main.removeCallbacks(checkpoint);main.postDelayed(checkpoint,3000);}
 }
 private void persist(Run run,boolean complete){
  SharedPreferences prefs=getSharedPreferences("chat",MODE_PRIVATE);
  try{
   JSONArray all=SessionRepository.get(prefs).read();JSONObject session=MessageEdits.findSession(all,run.sessionId);
   if(session==null||!run.branchId.equals(session.optString("activeBranchId")))return;
   JSONObject message=new JSONObject().put("id",run.id).put("text",run.partial.toString()).put("user",false).put("timestamp",run.timestamp)
    .put("modelId",run.modelId).put("reasoningContent",run.reasoningText.toString()).put("reasoningKind",ChatApiClient.RESPONSES.equals(run.protocol)||ChatApiClient.GEMINI.equals(run.protocol)?"summary":"reasoning").put("error",run.error).put("interrupted",run.stopped).put("searchStatus",run.search.grounded()||run.search.tavily?(run.searchStatus.isEmpty()?run.searchNote:run.searchStatus):"").put("toolEvents",run.toolEvents).put("sources",run.sources.items).put("completionState",run.completionState).put("finishReason",run.finishReason).put("usage",run.usage).put("modelName",run.configuredModel.optString("displayName",run.modelId)).put("estimatedInputTokens",run.contextTokens).put("omittedMessages",run.omittedMessages).put("searchNote",run.searchNote).put("pricing",run.configuredModel);
   if(complete){
    session.remove("pendingReply");JSONArray messages=session.optJSONArray("messages");boolean exists=false;
    if(messages!=null){for(int i=0;i<messages.length();i++){JSONObject old=messages.optJSONObject(i);if(old!=null&&run.id.equals(old.optString("id")))exists=true;}
     if(!exists&&(!run.stopped||run.partial.length()>0||run.reasoningText.length()>0))messages.put(message);}
    session.put("updatedAt",System.currentTimeMillis());ConversationBranches.sync(session);
   }else {SessionRepository.get(prefs).checkpoint(run.sessionId,new JSONObject().put("branchId",run.branchId).put("message",message));return;}
   SessionRepository.get(prefs).clearCheckpoint(run.sessionId);SessionRepository.get(prefs).upsert(session);
  }catch(JSONException failure){android.util.Log.e("ChatGeneration","Unable to persist reply",failure);}
 }
 static void recoverInterrupted(Context context){
  if(!runs.isEmpty())return;SharedPreferences prefs=context.getSharedPreferences("chat",MODE_PRIVATE);
  try{JSONArray all=SessionRepository.get(prefs).read();boolean changed=false;
   for(int i=0;i<all.length();i++){JSONObject session=all.optJSONObject(i);JSONObject draft=session==null?null:session.optJSONObject("pendingReply");if(draft==null&&session!=null){String saved=prefs.getString("draft_"+session.optString("id"),"");if(!saved.isEmpty())try{draft=new JSONObject(saved);}catch(JSONException ignored){}}if(draft==null)continue;
    session.remove("pendingReply");SessionRepository.get(prefs).clearCheckpoint(session.optString("id"));changed=true;
    JSONObject message=draft.optJSONObject("message");JSONArray messages=session.optJSONArray("messages");
    if(message==null||messages==null||!draft.optString("branchId").equals(session.optString("activeBranchId")))continue;
    boolean exists=false;for(int j=0;j<messages.length();j++){JSONObject old=messages.optJSONObject(j);if(old!=null&&message.optString("id").equals(old.optString("id")))exists=true;}
    if(!exists){message.put("interrupted",true).put("searchStatus","上次生成已中断 · 可重试");
     if(message.optString("text").isEmpty())message.put("text","上次生成被系统终止，请重试。").put("error",true);
     messages.put(message);ConversationBranches.sync(session);}
   }
   if(changed)SessionRepository.get(prefs).replace(all);
  }catch(JSONException failure){android.util.Log.e("ChatGeneration","Unable to restore interrupted reply",failure);}
 }
 private void prepareAttachments(Run run)throws Exception{
  JSONArray messages=run.session==null?null:run.session.optJSONArray("messages");Map<String,JSONObject> entries=new HashMap<>();
  for(int i=0;i<run.history.length();i++){JSONObject entry=run.history.optJSONObject(i);if(entry!=null&&!entry.optString("_messageId").isEmpty())entries.put(entry.optString("_messageId"),entry);}
  int latestUser=-1;
  if(messages!=null)for(int i=messages.length()-1;i>=0;i--){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optBoolean("user")&&!m.optBoolean("error")){latestUser=i;break;}}
  if(messages!=null)for(int i=messages.length()-1;i>=0;i--){
   JSONObject message=messages.optJSONObject(i);if(message==null||message.optBoolean("error")||!message.optBoolean("user"))continue;
   JSONObject entry=entries.get(message.optString("id"));JSONArray attachments=message.optJSONArray("attachments");if(entry==null||attachments==null)continue;
   JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text",message.optString("text")));
   for(int j=0;j<Math.min(10,attachments.length());j++){
    if(!valid(run)||Thread.currentThread().isInterrupted())return;
    JSONObject attachment=attachments.optJSONObject(j);if(attachment==null)continue;
    try{String uri=attachment.optString("uri"),payload=run.prepared.get(uri);
     if(payload==null)payload=AttachmentSnapshots.read(this,attachment);
     if(payload==null){if(!"content".equals(Uri.parse(uri).getScheme()))throw new java.io.IOException("附件地址不可读取");payload=AttachmentBatch.payload(getApplicationContext(),attachment);}
     if(AttachmentBatch.isFailure(payload))throw new java.io.IOException(AttachmentBatch.failureMessage(payload));
     content.put(AttachmentBatch.part(getApplicationContext(),attachment,payload,run.protocol));
    }catch(Exception unavailable){String reason="附件 "+attachment.optString("displayName","文件")+" 未发送："+ApiFailure.local(unavailable);if(i==latestUser)throw new java.io.IOException(reason);content.put(new JSONObject().put("type","text").put("text",reason));}
   }entry.put("content",content).put("_ocrCache",message.optJSONArray("documentOcr"));
  }
 }
 private void recognizeDocuments(Run run)throws Exception{
  if(!DocumentOcr.enabled(run.configuredModel)||!DocumentOcr.hasMedia(run.history))return;
  DocumentOcr.Target target=DocumentOcr.resolve(ModelStore.read(getSharedPreferences("chat",MODE_PRIVATE)),run.configuredModel,run.base,run.modelId);
  ChatApiClient client=run.ocrClient==null?new ChatApiClient(run.cancel):run.ocrClient;
  run.history=DocumentOcr.prepare(getApplicationContext(),target,run.history,run.pdfPages,()->valid(run),text->main.post(()->status(run,text)),client,(messageId,record)->main.post(()->cacheRecognition(run,messageId,record)));
  main.post(()->{if(valid(run))try{run.toolEvents.put(new JSONObject().put("tool","document_ocr").put("status","completed").put("input",target.provider.optString("name")+" · "+target.id).put("output","附件已识别为文字"));}catch(JSONException ignored){}});
 }
 private void cacheRecognition(Run run,String messageId,JSONObject record){
  if(!valid(run)||messageId.isEmpty())return;
  try{
   SessionRepository repository=SessionRepository.get(getSharedPreferences("chat",MODE_PRIVATE));JSONObject session=MessageEdits.findSession(repository.read(),run.sessionId);
   if(session==null||!run.branchId.equals(session.optString("activeBranchId")))return;
   JSONArray messages=session.optJSONArray("messages");
   for(int i=0;messages!=null&&i<messages.length();i++){
    JSONObject message=messages.optJSONObject(i);if(message==null||!messageId.equals(message.optString("id")))continue;
    JSONArray oldRecords=message.optJSONArray("documentOcr");JSONArray records=oldRecords==null?new JSONArray():new JSONArray(oldRecords.toString());boolean found=false;
    for(int j=0;j<records.length();j++)if(record.optString("key").equals(records.getJSONObject(j).optString("key"))){records.put(j,record);found=true;break;}
    if(!found)records.put(record);message.put("documentOcr",records);ConversationBranches.sync(session);repository.upsert(session);return;
   }
  }catch(JSONException invalid){android.util.Log.e("DocumentOcr","Unable to save recognition result",invalid);}
 }
 @Override public void onTaskRemoved(Intent rootIntent){SessionLaunchPolicy.markTaskRemoved();super.onTaskRemoved(rootIntent);}
 @Override public void onTimeout(int startId,int type){for(Run run:new ArrayList<>(runs.values()))finish(run,"后台运行时间已达系统限制，请重试。",true,true);}
 @Override public void onDestroy(){
  for(Run run:new ArrayList<>(runs.values()))finish(run,run.partial.toString(),run.partial.length()==0,true);
  main.removeCallbacksAndMessages(null);worker.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}

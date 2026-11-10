package com.changyu.ochat;

import android.content.*;import android.app.AlertDialog;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import android.text.Html;import android.text.InputType;import android.view.*;import android.view.inputmethod.InputMethodManager;import android.widget.*;import androidx.annotation.Nullable;import androidx.appcompat.app.AppCompatActivity;import androidx.appcompat.widget.SwitchCompat;import androidx.core.view.ViewCompat;import androidx.core.view.WindowInsetsCompat;import androidx.drawerlayout.widget.DrawerLayout;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.text.SimpleDateFormat;import java.util.*;

public class MainActivity extends AppCompatActivity {
 private int ink=Color.rgb(36,37,43),muted=Color.rgb(119,121,131),purple=Color.rgb(103,80,164),page=Color.rgb(250,248,252),card=Color.WHITE; private static final int PICK_ATTACHMENT=41;
 private AttachmentUi attachmentUi;
 private final ArrayDeque<IncomingShare.Request> attachmentImports=new ArrayDeque<>();private IncomingShare.Request activeAttachmentImport;private boolean sharedIntentConsumed;
 private LinearLayout messageList,attachmentPreview,historyList;private ScrollView conversationScroll;private EditText composer;private TextView modelLabel,modelSelector,conversationTitle;private SwitchCompat webSwitch;private String provider="OpenAI Compatible",model="gpt-4o-mini",reasoning="default";private final List<JSONObject> pendingAttachments=new ArrayList<>();private final Map<String,String> preparedAttachments=new HashMap<>();private boolean attachmentLoading;private volatile int attachmentGeneration;private final java.util.concurrent.ExecutorService attachmentWorker=java.util.concurrent.Executors.newSingleThreadExecutor();private java.util.concurrent.Future<?> attachmentTask;private LinearLayout attachmentChoices;private android.animation.ValueAnimator attachmentAnimation;private boolean choicesOpen;private SharedPreferences prefs;private final ChatApiClient.CancellationHandle topicCancellation=new ChatApiClient.CancellationHandle();private final ChatApiClient apiClient=new ChatApiClient(topicCancellation);private boolean sending; private final Set<String> topicRequestsInFlight=new HashSet<>();
 private final SharedPreferences.OnSharedPreferenceChangeListener modelPreferences=(p,k)->{
  if("provider".equals(k)||"providerId".equals(k)||"model".equals(k)||"modelId".equals(k)||"reasoningEffort".equals(k))runOnUiThread(this::refreshModelLabels);
  if("sessions".equals(k)||"currentSessionId".equals(k))runOnUiThread(this::refreshConversationTitle);
 };
 private SharedPreferences scoped(){JSONObject session=MessageEdits.findSession(readSessions(),prefs.getString("currentSessionId",""));return session==null?prefs:new SessionConfig(prefs,session);}
 private ModelCapabilities capabilities(){JSONObject p=activeProvider();return new ModelCapabilities(model,p.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),ModelCapabilities.find(p,model));}
 private void refreshModelLabels(){
  if(prefs==null)return;SharedPreferences config=scoped();provider=config.getString("provider","");model=config.getString("modelId",config.getString("model",""));reasoning=config.getString("reasoningEffort","default");
  JSONObject selected=ModelCapabilities.find(activeProvider(),model);String display=selected.optString("displayName",model);if(display.trim().isEmpty())display="选择模型";
  if(modelSelector!=null)UiStyle.textIfChanged(modelSelector,display+"  ›");
  if(modelLabel!=null)UiStyle.textIfChanged(modelLabel,ReasoningSheet.label(reasoning)+"  ›");
  if(webSwitch!=null&&webSwitch.isChecked()!=config.getBoolean("webSearchEnabled",false))webSwitch.setChecked(config.getBoolean("webSearchEnabled",false));
 }
 @Override protected void onDestroy(){if(isFinishing()&&!isChangingConfigurations())SessionLaunchPolicy.markTaskRemoved();if(follow!=null)follow.cancel();topicCancellation.cancel();if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(modelPreferences);ChatGenerationService.detach(generationListener);if(activeReply!=null&&generation==null){stopReply();}attachmentGeneration++;if(attachmentUi!=null)attachmentUi.close();if(historyBrowser!=null)historyBrowser.close();attachmentWorker.shutdownNow();if(attachmentAnimation!=null)attachmentAnimation.cancel();super.onDestroy();}
 private ChatGenerationService.Run generation;private MarkdownMessageView generationBody;private final Map<String,Bundle> codeViewStates=new HashMap<>();private final Map<String,Boolean> reasoningExpanded=new LinkedHashMap<>();private final ChatGenerationService.Listener generationListener=this::renderGeneration;
 private ImageButton sendButton;private boolean sendButtonStopped,showingBackgroundRun; private ReplyContext activeReply; private FollowOutput follow; private ImageButton bottomButton; private final Map<String,Integer> viewportPositions=new HashMap<>(); private int restoreGeneration;
 private void launchGeneration(ReplyContext request,String base,String key,JSONArray history,JSONObject session,Map<String,String> prepared,String query){
  ChatGenerationService.Run run=new ChatGenerationService.Run(request.sessionId,request.modelId,request.effort,request.timestamp);
  run.base=base;run.key=key;run.history=history;run.session=session;run.prepared=new HashMap<>(prepared);run.query=query;
  run.protocol=request.protocol;run.search=request.search;run.branchId=request.branchId;
  run.temperature=request.temperature;run.topP=request.topP;run.outputTokens=request.outputTokens;run.configuredModel=ModelCapabilities.find(activeProvider(),request.modelId);
  try{
   if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("askedGenerationNotifications",false)){
    prefs.edit().putBoolean("askedGenerationNotifications",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},87);
   }
   generation=run;generationBody=null;ChatGenerationService.attach(generationListener);ChatGenerationService.launch(this,run);
  }catch(RuntimeException failure){generation=null;finishRequest(request.visibleRow,"无法启动后台生成，请重试。",true);}
 }
 private void renderGeneration(ChatGenerationService.Run run){
  if(isDestroyed()||!run.sessionId.equals(prefs.getString("currentSessionId","")))return;
  generation=run;
  if(run.finished){
   cacheCodeViewStates();generation=null;generationBody=null;activeReply=null;sending=false;showingBackgroundRun=false;updateSendButton();refreshModelLabels();
   JSONObject current=MessageEdits.findSession(readSessions(),run.sessionId);if(current!=null)restoreSession(current);refreshHistory();return;
  }
  sending=true;updateSendButton();
  if(activeReply==null||!activeReply.sessionId.equals(run.sessionId)||generationBody==null){
   if(activeReply!=null&&activeReply.visibleRow!=null)messageList.removeView(activeReply.visibleRow);
   // A queued message has just become the active turn; reload its persisted user row.
   JSONObject session=MessageEdits.findSession(readSessions(),run.sessionId);
   if(session!=null)renderSessionMessages(session,Math.max(0,session.optJSONArray("messages").length()-40));
   activeReply=new ReplyContext(run.sessionId,run.modelId,run.effort,run.timestamp);activeReply.branchId=run.branchId;activeReply.search=run.search;
   generationBody=markdownText("");generationBody.setMessageId(run.id);activeReply.visibleRow=assistantRow(generationBody,run.timestamp,run.modelId);messageList.addView(activeReply.visibleRow);
  }
  String text=run.partial.toString(),thought=run.reasoningText.toString();generationBody.setVisibility(text.isEmpty()&&!thought.isEmpty()?View.GONE:View.VISIBLE);renderMarkdown(generationBody,text.isEmpty()?run.status:text);
  bindReasoning(activeReply.visibleRow,run.id,thought,text.isEmpty(),false,ChatApiClient.RESPONSES.equals(run.protocol)||ChatApiClient.GEMINI.equals(run.protocol));
  if((run.search.builtin||run.search.tavily)&&(!run.status.equals(activeReply.searchStatus)||activeReply.sources!=run.sources)){activeReply.searchStatus=run.status;activeReply.sources=run.sources;refreshSearch(activeReply);}
  scrollBottom();
 }
 @Override protected void onStop(){ChatGenerationService.detach(generationListener);super.onStop();}
 @Override protected void onSaveInstanceState(Bundle out){saveIncomingState(out);cacheCodeViewStates();Bundle states=new Bundle();for(Map.Entry<String,Bundle> item:codeViewStates.entrySet())states.putBundle(item.getKey(),item.getValue());out.putBundle("codeViewStates",states);Bundle reasoningState=new Bundle();for(Map.Entry<String,Boolean> item:reasoningExpanded.entrySet())reasoningState.putBoolean(item.getKey(),item.getValue());out.putBundle("reasoningExpanded",reasoningState);super.onSaveInstanceState(out);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(IncomingShare.accepts(intent)){receiveSharedIntent(intent);return;}setIntent(intent);if(resumingGeneration(intent))openRunningConversation();}
 private boolean resumingGeneration(Intent intent){try{return intent!=null&&!IncomingShare.accepts(intent)&&intent.getBooleanExtra("resumeGeneration",false);}catch(RuntimeException malformed){return false;}}
 private void openRunningConversation(){
  ChatGenerationService.Run run=ChatGenerationService.active();if(run==null)return;
  JSONObject session=MessageEdits.findSession(readSessions(),run.sessionId);if(session==null)return;
  sending=false;restoreSession(session);activeReply=null;generationBody=null;renderGeneration(run);closeHistory();
 }
 private void cacheCodeViewStates(){if(messageList==null)return;for(int i=0;i<messageList.getChildCount();i++){MarkdownMessageView view=messageList.getChildAt(i).findViewWithTag("markdown-message");if(view!=null&&!view.messageId().isEmpty())codeViewStates.put(view.messageId(),view.codeState());}}
 private String protocol(){return activeProvider().optString("protocol",ChatApiClient.CHAT_COMPLETIONS);}
 private static final class ReplyContext {final ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();final ChatApiClient client=new ChatApiClient(cancel);final StringBuffer partial=new StringBuffer();StreamingMarkdown stream;String searchStatus="",searchResults="";SearchSources sources=new SearchSources();LinearLayout visibleRow;final String sessionId,modelId,effort;final long timestamp;SearchOptions search=SearchOptions.OFF;String protocol="chat_completions",branchId="",temperature="0.7",topP="0.9",outputTokens=ContextBudget.DEFAULT_OUTPUT_TEXT;volatile boolean finished;ReplyContext(String sessionId,String modelId,String effort){this(sessionId,modelId,effort,System.currentTimeMillis());}ReplyContext(String sessionId,String modelId,String effort,long timestamp){this.sessionId=sessionId;this.modelId=modelId;this.effort=effort;this.timestamp=timestamp;}}
 @Override protected void onCreate(@Nullable Bundle state){ThemePalette.applyActivityTheme(this);super.onCreate(state);if(state!=null){Bundle savedReasoning=state.getBundle("reasoningExpanded");if(savedReasoning!=null)for(String key:savedReasoning.keySet())reasoningExpanded.put(key,savedReasoning.getBoolean(key));Bundle saved=state.getBundle("codeViewStates");if(saved!=null)for(String key:saved.keySet())codeViewStates.put(key,saved.getBundle(key));}prefs=getSharedPreferences("chat",MODE_PRIVATE);ModelStore.migrate(prefs);applyCustomTheme();getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);getWindow().setStatusBarColor(page);getWindow().setNavigationBarColor(page);getWindow().getDecorView().setSystemUiVisibility(ThemePalette.resolve(this).dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);provider=prefs.getString("provider",provider);model=prefs.getString("model",model);reasoning=prefs.getString("reasoningEffort","default");setContentView(buildScreen());prefs.registerOnSharedPreferenceChangeListener(modelPreferences);refreshModelLabels();ChatGenerationService.recoverInterrupted(this);initializeSessions(SessionLaunchPolicy.claimColdStart()&&!resumingGeneration(getIntent()));refreshConversationTitle();restoreIncomingState(state);SessionRepository.get(prefs).manageSnapshots(this,pendingAttachments);if(!sharedIntentConsumed&&IncomingShare.accepts(getIntent()))receiveSharedIntent(getIntent());resumeAttachmentWork();}
 private androidx.viewpager2.widget.ViewPager2 pager;
 private View buildScreen(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(page);root.addView(buildTopBar(),new LinearLayout.LayoutParams(-1,dp(ScreenInsets.TOP_BAR_DP)));root.addView(buildConversation(),new LinearLayout.LayoutParams(-1,0,1));root.addView(buildComposer(),new LinearLayout.LayoutParams(-1,-2));LinearLayout history=buildHistoryPanel();ScreenInsets.apply(this,root);ScreenInsets.apply(this,history);pager=new androidx.viewpager2.widget.ViewPager2(this);pager.setId(View.generateViewId());pager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);pager.setOffscreenPageLimit(1);View[] pages={history,root};pager.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<PageHolder>(){public int getItemCount(){return 2;}public PageHolder onCreateViewHolder(ViewGroup parent,int type){FrameLayout host=new FrameLayout(MainActivity.this);host.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));return new PageHolder(host);}public void onBindViewHolder(PageHolder h,int position){FrameLayout host=(FrameLayout)h.itemView;host.removeAllViews();View v=pages[position];if(v.getParent()!=null)((ViewGroup)v.getParent()).removeView(v);host.addView(v,new FrameLayout.LayoutParams(-1,-1));}});pager.setCurrentItem(1,false);pager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback(){public void onPageSelected(int position){if(position==0){if(follow!=null)follow.detach();hideKeyboard();refreshHistory();}}});getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){public void handleOnBackPressed(){if(choicesOpen){setAttachmentChoices(false);return;}if(pager.getCurrentItem()==0)pager.setCurrentItem(1,true);else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}}});return pager;}
 private static final class PageHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {PageHolder(View v){super(v);}}
 private void openHistory(){refreshHistory();hideKeyboard();pager.setCurrentItem(0,true);}
 private void closeHistory(){pager.setCurrentItem(1,true);}
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  // Choices are visually below the composer, but the whole screen remains normal input.
  // Close on an outside DOWN without consuming it, so EditText, scrolling, back, and the
  // two choice buttons retain their existing dispatch/focus behaviour.
  if(choicesOpen&&e.getActionMasked()==MotionEvent.ACTION_DOWN){
   View hit=touchTarget(getWindow().getDecorView(),e.getRawX(),e.getRawY());
   boolean insideChoices=false,insideAttach=false;
   for(View v=hit;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null){
    if(v==attachmentChoices)insideChoices=true;
    if("添加附件".equals(v.getContentDescription()))insideAttach=true;
   }
   if(!insideChoices&&!insideAttach)setAttachmentChoices(false);
  }
  if(pager!=null){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){View hit=touchTarget(getWindow().getDecorView(),e.getRawX(),e.getRawY());for(View v=hit;v!=null&&v.getParent() instanceof View;v=(View)v.getParent())if(v==conversationScroll){follow.detach();break;}boolean protectedTouch=hit instanceof EditText||hit instanceof SeekBar||(hit instanceof TextView&&((TextView)hit).hasSelection());for(View v=hit;v!=null&&v.getParent() instanceof View;v=(View)v.getParent())if(v instanceof HorizontalScrollView||v instanceof CodeBlockView)protectedTouch=true;pager.setUserInputEnabled(!protectedTouch&&e.getX()>dp(24)&&e.getX()<pager.getWidth()-dp(24));}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)pager.post(()->pager.setUserInputEnabled(true));}return super.dispatchTouchEvent(e);}
 private View touchTarget(View v,float x,float y){int[] pos=new int[2];v.getLocationOnScreen(pos);if(v.getVisibility()!=View.VISIBLE||x<pos[0]||y<pos[1]||x>=pos[0]+v.getWidth()||y>=pos[1]+v.getHeight())return null;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){View hit=touchTarget(g.getChildAt(i),x,y);if(hit!=null)return hit;}}return v;}
 private View buildTopBar(){
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(8),dp(16),dp(8));
  ImageButton menu=vectorButton(R.drawable.ic_menu,"打开历史记录",ink);menu.setOnClickListener(v->openHistory());
  bar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
  conversationTitle=label("新对话",20,ink,true);conversationTitle.setContentDescription("当前对话名称");
  conversationTitle.setPadding(dp(12),0,dp(12),0);conversationTitle.setSingleLine(true);conversationTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
  bar.addView(conversationTitle,new LinearLayout.LayoutParams(0,-1,1));conversationTitle.setGravity(Gravity.CENTER_VERTICAL);
  ImageButton add=vectorButton(R.drawable.ic_plus,"新建对话",purple);UiStyle.clickable(add,card,16);add.setOnClickListener(v->resetConversation());bar.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));return bar;
 }
 private View buildConversation(){conversationScroll=new ScrollView(this);conversationScroll.setFillViewport(true);conversationScroll.setClipToPadding(false);conversationScroll.setVerticalScrollBarEnabled(false);messageList=new LinearLayout(this);messageList.setOrientation(LinearLayout.VERTICAL);messageList.setPadding(dp(24),dp(16),dp(24),dp(24));LinearLayout w=new LinearLayout(this);w.setOrientation(LinearLayout.VERTICAL);w.setGravity(Gravity.CENTER_HORIZONTAL);w.setPadding(0,dp(52),0,dp(32));if(HomeCopy.isIconMode(prefs)){ImageView welcome=WelcomeImage.create(this,prefs.getString("welcomeImageUri",""),prefs.getString("welcomeImageShape","circle"));if(welcome!=null)w.addView(welcome,new LinearLayout.LayoutParams(dp(76),dp(76)));else w.addView(label(HomeCopy.get(prefs,"welcomeSymbol"),38,purple,true));}else{TextView welcomeTitle=label(HomeCopy.get(prefs,"welcomeTitle"),26,ink,true);welcomeTitle.setGravity(Gravity.CENTER);w.addView(welcomeTitle);TextView welcomeBody=label(HomeCopy.get(prefs,"welcomeBody"),15,muted,false);welcomeBody.setGravity(Gravity.CENTER);welcomeBody.setLineSpacing(dp(4),1.15f);welcomeBody.setPadding(dp(16),dp(16),dp(16),0);w.addView(welcomeBody);}messageList.addView(w,new LinearLayout.LayoutParams(-1,-2));messageList.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->w.setVisibility(messageList.getChildCount()>1?View.GONE:View.VISIBLE));conversationScroll.addView(messageList);conversationScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);FrameLayout host=new FrameLayout(this);host.addView(conversationScroll,new FrameLayout.LayoutParams(-1,-1));bottomButton=vectorButton(R.drawable.ic_arrow_down,"回到底部",muted);bottomButton.setBackground(roundDrawable(card,20));bottomButton.setVisibility(View.INVISIBLE);FrameLayout.LayoutParams buttonParams=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.BOTTOM|Gravity.RIGHT);buttonParams.setMargins(dp(12),dp(12),dp(12),dp(12));host.addView(bottomButton,buttonParams);follow=new FollowOutput(conversationScroll,detached->updateBottomButton());conversationScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->updateBottomButton());conversationScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateBottomButton());messageList.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateBottomButton());bottomButton.setOnClickListener(v->follow.resume());return host;}
 private void updateBottomButton(){if(follow==null||bottomButton==null)return;float density=getResources().getDisplayMetrics().density;int distance=Math.max(0,follow.bottom()-conversationScroll.getScrollY());boolean visible=bottomButton.getVisibility()==View.VISIBLE;boolean show=!follow.following()&&(visible?distance>32*density:distance>Math.max(160*density,conversationScroll.getHeight()*.25f));int visibility=show?View.VISIBLE:View.INVISIBLE;if(bottomButton.getVisibility()!=visibility)bottomButton.setVisibility(visibility);}
 private ImageButton vectorButton(int resource,String description,int tint){ImageButton button=new ImageButton(this);button.setImageResource(resource);button.setScaleType(ImageView.ScaleType.CENTER);button.setPadding(0,0,0,0);button.setImageTintList(android.content.res.ColorStateList.valueOf(tint==muted?ThemePalette.readable(tint,card,page):tint));android.util.TypedValue background=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,background,true);button.setBackgroundResource(background.resourceId);button.setContentDescription(description);return button;}
 private View buildComposer(){
  LinearLayout area=new LinearLayout(this);area.setOrientation(LinearLayout.VERTICAL);area.setPadding(dp(16),dp(6),dp(16),dp(12));
  LinearLayout opts=new LinearLayout(this);opts.setGravity(Gravity.CENTER_VERTICAL);
  opts.addView(label(HomeCopy.get(prefs,"searchLabel"),12,muted,false));
  webSwitch=new SwitchCompat(this);webSwitch.setContentDescription("联网搜索");webSwitch.setChecked(scoped().getBoolean("webSearchEnabled",false));
  webSwitch.setOnCheckedChangeListener((button,checked)->scoped().edit().putBoolean("webSearchEnabled",checked).apply());
  opts.addView(webSwitch,new LinearLayout.LayoutParams(dp(52),dp(48)));
  modelSelector=label("",12,purple,true);modelSelector.setSingleLine(true);modelSelector.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);modelSelector.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);modelSelector.setPadding(dp(12),0,dp(4),0);modelSelector.setContentDescription("选择模型");modelSelector.setOnClickListener(v->showModelSelector());
  opts.addView(modelSelector,new LinearLayout.LayoutParams(0,dp(48),1));area.addView(opts,new LinearLayout.LayoutParams(-1,-2));
  attachmentPreview=new LinearLayout(this);attachmentPreview.setOrientation(LinearLayout.VERTICAL);area.addView(attachmentPreview,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(6),dp(8),dp(8));
  panel.setBackground(UiStyle.surface(this,card,24,true));panel.setElevation(0);panel.setTranslationZ(0);
  composer=new EditText(this);composer.setHint(HomeCopy.get(prefs,"composerHint"));composer.setHintTextColor(muted);composer.setTextColor(ink);composer.setTextSize(16);
  composer.setSingleLine(false);composer.setMinLines(1);composer.setMaxLines(5);composer.setGravity(Gravity.TOP);
  composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  composer.setBackgroundColor(Color.TRANSPARENT);composer.setPadding(dp(12),dp(10),dp(12),dp(8));composer.setContentDescription("消息输入框");
  panel.addView(composer,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
  ImageButton at=vectorButton(R.drawable.ic_attach,"添加附件",muted);at.setOnClickListener(v->toggleAttachmentChoices());tools.addView(at,new LinearLayout.LayoutParams(dp(48),dp(48)));
  ImageButton expand=vectorButton(R.drawable.ic_expand,"展开输入框",muted);expand.setOnClickListener(v->showExpandedComposer());tools.addView(expand,new LinearLayout.LayoutParams(dp(48),dp(48)));
  modelLabel=label(ReasoningSheet.label(reasoning)+"  ›",12,muted,false);modelLabel.setSingleLine(true);modelLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);modelLabel.setGravity(Gravity.CENTER_VERTICAL);modelLabel.setContentDescription("选择思考强度");modelLabel.setOnClickListener(v->showReasoningSelector());
  tools.addView(modelLabel,new LinearLayout.LayoutParams(0,dp(48),1));
  sendButton=vectorButton(R.drawable.ic_send,"发送消息",ThemePalette.resolve(this).onPrimary);UiStyle.clickable(sendButton,purple,18);
  sendButton.setOnClickListener(v->{if(sending&&composer.getText().toString().trim().isEmpty()&&pendingAttachments.isEmpty())stopReply();else sendMessage();});sendButton.setOnLongClickListener(v->{showConversationSettings();return true;});composer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int d){}public void onTextChanged(CharSequence x,int a,int b,int c){updateSendButton();}public void afterTextChanged(android.text.Editable e){}});tools.addView(sendButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
  panel.addView(tools,new LinearLayout.LayoutParams(-1,-2));area.addView(panel,new LinearLayout.LayoutParams(-1,-2));
  attachmentChoices=new LinearLayout(this);attachmentChoices.setTag("attachment-choices");attachmentChoices.setPadding(0,dp(8),0,dp(4));attachmentChoices.setVisibility(View.GONE);
  for(String choice:new String[]{"图片","文件"}){TextView option=label(choice,15,ink,true);option.setGravity(Gravity.CENTER);option.setContentDescription("选择"+choice);UiStyle.clickable(option,card,16);option.setOnClickListener(v->{setAttachmentChoices(false);if("文件".equals(choice))openFileManagers();else openImagePicker();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(48),1);cp.setMargins(dp(4),0,dp(4),0);attachmentChoices.addView(option,cp);}
  area.addView(attachmentChoices,new LinearLayout.LayoutParams(-1,0));return area;
 }
 private boolean isActive(ReplyContext reply){return reply==activeReply&&!reply.finished&&!reply.cancel.isCancelled()&&!isDestroyed();}
 private void updateSendButton(){if(sendButton==null)return;boolean stop=sending&&composer!=null&&composer.getText().toString().trim().isEmpty()&&pendingAttachments.isEmpty();sendButtonStopped=stop;sendButton.setImageResource(stop?R.drawable.ic_stop:R.drawable.ic_send);sendButton.setContentDescription(stop?"停止回复并清空本对话队列":sending?"加入发送队列":"发送消息");}
 private void stopReply(){if(generation!=null&&!generation.finished){ChatGenerationService.stop(this,generation);return;}if(follow!=null)follow.detach();ReplyContext reply=activeReply;if(reply==null)return;if(reply.search.builtin){reply.searchStatus="内置搜索 / 回复已停止";refreshSearch(reply);}String partial=reply.stream==null?reply.partial.toString():reply.stream.seal();if(!partial.isEmpty()){completeReply(reply,partial,false);return;}reply.cancel.cancel();if(reply.stream!=null)reply.stream.finish("");reply.finished=true;activeReply=null;sending=false;updateSendButton();if(reply.visibleRow!=null)messageList.removeView(reply.visibleRow);}
 private AlertDialog expandedComposerDialog;
 private void showExpandedComposer(){
  setAttachmentChoices(false);final String original=composer.getText().toString();
  Context context=EditorDialogs.themedContext(this);EditText editor=EditorDialogs.editor(context);
  editor.setContentDescription("展开的消息编辑器");editor.setHint(HomeCopy.get(prefs,"composerHint"));
  editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  editor.setGravity(Gravity.TOP);editor.setTextSize(17);editor.setLineSpacing(dp(4),1.15f);
  editor.setPadding(dp(16),dp(16),dp(16),dp(16));editor.setBackground(UiStyle.surface(context,page,18,false));
  editor.setText(original);editor.setSelection(editor.length());
  PanelDialog dialog=new PanelDialog(this,"编辑草稿","整理思路，完成后应用到输入框",editor);
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",()->composer.setText(original))
   .action(AlertDialog.BUTTON_NEUTRAL,"收起",()->composer.setText(editor.getText()))
   .action(AlertDialog.BUTTON_POSITIVE,"应用",()->composer.setText(editor.getText()));
  expandedComposerDialog=dialog;dialog.setOnCancelListener(d->composer.setText(original));
  dialog.setOnDismissListener(d->expandedComposerDialog=null);dialog.show();
 }
 private void refreshSearch(ReplyContext reply){if(reply.visibleRow!=null)addSearchCard(reply.visibleRow,reply.searchStatus,reply.sources,"");}
 private void addSearchCard(LinearLayout row,String status,SearchSources sources,String legacy){if(row.getChildCount()==0||!(row.getChildAt(0) instanceof LinearLayout))return;if((status==null||status.isEmpty())&&sources.items.length()==0&&legacy.isEmpty())return;LinearLayout box=(LinearLayout)row.getChildAt(0);View oldHeading=box.findViewWithTag("search-heading"),oldContent=box.findViewWithTag("search-content");boolean expanded=oldContent!=null&&oldContent.getVisibility()==View.VISIBLE;if(oldHeading!=null)box.removeView(oldHeading);if(oldContent!=null)box.removeView(oldContent);TextView heading=label((status==null||status.isEmpty()?"历史搜索来源":status)+" · 点击展开",12,muted,false);heading.setTag("search-heading");heading.setPadding(0,dp(8),0,dp(8));heading.setMinHeight(dp(48));box.addView(heading,0);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setTag("search-content");content.setVisibility(expanded?View.VISIBLE:View.GONE);box.addView(content,1);for(int i=0;i<sources.items.length();i++){JSONObject source=sources.items.optJSONObject(i);String url=source.optString("url"),domain=Uri.parse(url).getHost();String snippet=source.optString("snippet").replaceAll("\\s+"," ").trim();if(snippet.length()>300)snippet=snippet.substring(0,300)+"…";TextView item=label((i+1)+". "+source.optString("title",domain)+"\n"+domain+(snippet.isEmpty()?"":"\n"+snippet),13,ink,false);item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setMaxLines(7);item.setEllipsize(android.text.TextUtils.TruncateAt.END);item.setBackground(roundDrawable(card,12));item.setContentDescription(item.getText()+"，打开来源");item.setOnClickListener(v->SafeLinks.open(this,url));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(6);content.addView(item,lp);}if(sources.items.length()==0&&!legacy.isEmpty()){String clean=legacy.trim();if(clean.startsWith("{")||clean.startsWith("["))clean="历史来源格式不可用";else clean=clean.replaceAll("(?m)^\\s*[-#*>]+\\s*","");if(clean.length()>600)clean=clean.substring(0,600)+"…";TextView fallback=label(clean,13,muted,false);fallback.setTextIsSelectable(true);content.addView(fallback);}heading.setOnClickListener(v->content.setVisibility(content.getVisibility()==View.GONE?View.VISIBLE:View.GONE));}
 static JSONArray groundedMessages(JSONArray history,String results)throws JSONException{return SearchGrounding.messages(history,results);}
 private void showModelSelector(){hideKeyboard();ModelPicker.show(this,scoped(),this::refreshModelLabels,()->startActivity(new Intent(this,ProviderListActivity.class)));}
 private boolean isAnthropic(){JSONObject p=activeProvider();String type=(provider+" "+p.optString("name")+" "+p.optString("api",prefs.getString("api",""))+" "+model).toLowerCase(Locale.ROOT);return type.contains("anthropic")||type.contains("claude");}
 private void setReasoning(String value){reasoning=value;scoped().edit().putString("reasoningEffort",value).apply();refreshModelLabels();}
 private void showReasoningSelector(){hideKeyboard();ModelCapabilities caps=capabilities();if(caps.efforts.length==1){TextView hint=label("此模型由服务端决定思考强度。兼容接口可在模型详情配置支持的档位。",15,muted,false);new PanelDialog(this,"思考强度","当前模型使用自动设置",hint).compact().action(AlertDialog.BUTTON_POSITIVE,"使用自动",()->setReasoning("default")).show();return;}ReasoningSheet.show(this,ModelCapabilities.find(activeProvider(),model).optString("displayName",model),caps,reasoning,this::setReasoning);}
 private void toggleAttachmentChoices(){setAttachmentChoices(!choicesOpen);}
 private void setAttachmentChoices(boolean open){if(attachmentChoices==null||choicesOpen==open)return;choicesOpen=open;if(attachmentAnimation!=null)attachmentAnimation.cancel();attachmentChoices.setVisibility(View.VISIBLE);int start=attachmentChoices.getLayoutParams().height;attachmentAnimation=android.animation.ValueAnimator.ofInt(start,open?dp(60):0);attachmentAnimation.setDuration(160);attachmentAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());attachmentAnimation.addUpdateListener(a->{int height=(Integer)a.getAnimatedValue();ViewGroup.LayoutParams lp=attachmentChoices.getLayoutParams();lp.height=height;attachmentChoices.setLayoutParams(lp);attachmentChoices.setAlpha(height/(float)dp(60));});attachmentAnimation.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){if(!choicesOpen)attachmentChoices.setVisibility(View.GONE);}});attachmentAnimation.start();}
 private void openImagePicker(){
  Intent i;if(Build.VERSION.SDK_INT>=33){i=new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).setType("image/*");i.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(10,android.provider.MediaStore.getPickImagesMaxLimit()));}
  else i=AttachmentSelection.documents(true);
  try{startActivityForResult(i,PICK_ATTACHMENT);}catch(ActivityNotFoundException missing){openFileManagers();}
 }
 private void openFileManagers(){try{startActivityForResult(AttachmentSelection.managers(),PICK_ATTACHMENT);}catch(ActivityNotFoundException missing){Toast.makeText(this,"没有可用的文件管理器，请安装支持文件选择的应用",Toast.LENGTH_LONG).show();}}
 @Override protected void onActivityResult(int r,int result,@Nullable Intent data){
  super.onActivityResult(r,result,data);if(r==81||r==82){handleBackupResult(r,result,data);return;}if(r!=PICK_ATTACHMENT)return;setAttachmentChoices(false);if(result!=RESULT_OK)return;
  List<Uri> selected=AttachmentBatch.collect(data);if(selected.size()>AttachmentBatch.MAX_COUNT){Toast.makeText(this,"最多添加 "+AttachmentBatch.MAX_COUNT+" 个附件",Toast.LENGTH_LONG).show();selected=new ArrayList<>(selected.subList(0,AttachmentBatch.MAX_COUNT));}
  attachmentImports.addLast(new IncomingShare.Request(selected,data==null?0:data.getFlags(),"",false));startNextAttachmentImport();
 }
 private void receiveSharedIntent(Intent intent){
  sharedIntentConsumed=true;
  // Keep the launch filter stable across recreation; discard external extras and URI grants.
  Intent clean=getIntent().cloneFilter().setComponent(new ComponentName(this,MainActivity.class));clean.setSelector(null);clean.setFlags(0);setIntent(clean);
  if(attachmentImports.size()>=10){Toast.makeText(this,"分享任务较多，请稍后重试",Toast.LENGTH_LONG).show();return;}
  try{IncomingShare.Request request=IncomingShare.read(this,intent);if(request!=null){attachmentImports.addLast(request);closeHistory();setAttachmentChoices(false);startNextAttachmentImport();}}
  catch(IOException error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
 }
 private void startNextAttachmentImport(){
  if(isDestroyed()||attachmentLoading||activeAttachmentImport!=null||attachmentImports.isEmpty())return;
  IncomingShare.Request request=attachmentImports.removeFirst();activeAttachmentImport=request;
  if(request.shared&&!request.started){
   request.started=true;request.autoSend=composer.getText().toString().trim().isEmpty()&&pendingAttachments.isEmpty();
   request.sessionId=prefs.getString("currentSessionId","");refreshModelLabels();request.modelId=model;request.providerId=activeProvider().optString("id");
   if(request.autoSend){request.expectedDraft=request.text.trim().isEmpty()?"请阅读并概括分享文件的主要内容。":request.text;composer.setText(request.expectedDraft);}
   else if(!request.text.trim().isEmpty())composer.append((composer.length()==0?"":"\n")+request.text);
  }
  List<Uri> uris=new ArrayList<>();List<String> rejected=new ArrayList<>();
  for(Uri uri:request.uris){
   if(!"content".equals(uri.getScheme())){rejected.add("文件管理器未提供可读取的文件地址");continue;}
   boolean duplicate=false;for(JSONObject d:pendingAttachments)if(uri.toString().equals(d.optString("sourceUri",d.optString("uri"))))duplicate=true;
   if(duplicate)continue;if(pendingAttachments.size()+uris.size()>=AttachmentBatch.MAX_COUNT){rejected.add("最多添加 "+AttachmentBatch.MAX_COUNT+" 个附件");continue;}uris.add(uri);
  }
  if(uris.isEmpty()){if(!rejected.isEmpty())showAttachmentImportErrors(rejected);preparePendingAttachments(()->finishAttachmentImport(request,!rejected.isEmpty()));return;}
  final int generation=++attachmentGeneration;if(attachmentTask!=null)attachmentTask.cancel(true);attachmentLoading=true;renderAttachmentPreview();updateSendButton();
  attachmentTask=attachmentWorker.submit(()->{
   List<JSONObject> imported=new ArrayList<>();List<String> errors=new ArrayList<>(rejected);
   for(Uri uri:uris){if(Thread.currentThread().isInterrupted())break;try{if(request.shared)IncomingShare.validate(getApplicationContext(),uri);imported.add(AttachmentSelection.retain(getApplicationContext(),uri,request.flags));}catch(Exception error){errors.add(ApiFailure.local(error));}}
   runOnUiThread(()->{
    if(isDestroyed()||generation!=attachmentGeneration){for(JSONObject d:imported)AttachmentSelection.discard(getApplicationContext(),d);return;}
    pendingAttachments.addAll(imported);attachmentLoading=false;if(!errors.isEmpty())showAttachmentImportErrors(errors);
    preparePendingAttachments(()->finishAttachmentImport(request,!errors.isEmpty()));
   });
  });
 }
 private void showAttachmentImportErrors(List<String> errors){
  TextView text=label(android.text.TextUtils.join("\n\n",errors),14,ink,false);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(text);
  new PanelDialog(this,"部分附件导入失败","已成功导入的附件可以继续使用",scroll).compact().action(AlertDialog.BUTTON_POSITIVE,"知道了",null).show();
 }
 private void finishAttachmentImport(IncomingShare.Request request,boolean importFailed){
  if(activeAttachmentImport!=request)return;activeAttachmentImport=null;
  if(request.shared){
   refreshModelLabels();boolean ready=!importFailed&&!pendingAttachments.isEmpty();
   for(JSONObject d:pendingAttachments){String payload=preparedAttachments.get(d.optString("uri"));if(payload==null||AttachmentBatch.isFailure(payload))ready=false;}
   boolean unchanged=request.expectedDraft.equals(composer.getText().toString())&&request.sessionId.equals(prefs.getString("currentSessionId",""))&&request.modelId.equals(model)&&request.providerId.equals(activeProvider().optString("id"));
   if(request.autoSend&&ready&&unchanged)sendMessage();
   else if(!pendingAttachments.isEmpty())Toast.makeText(this,"分享文件已添加，草稿已保留，请确认后发送",Toast.LENGTH_LONG).show();
  }
  startNextAttachmentImport();
 }
 private void preparePendingAttachments(){
  if(activeAttachmentImport!=null){activeAttachmentImport.autoSend=false;if(!activeAttachmentImport.uris.isEmpty())attachmentImports.addFirst(activeAttachmentImport);activeAttachmentImport=null;}
  preparePendingAttachments(null);
 }
 private void preparePendingAttachments(Runnable ready){
  final int generation=++attachmentGeneration;if(attachmentTask!=null)attachmentTask.cancel(true);attachmentLoading=!pendingAttachments.isEmpty();preparedAttachments.clear();renderAttachmentPreview();
  final List<JSONObject> captured=new ArrayList<>();try{for(JSONObject d:pendingAttachments)captured.add(new JSONObject(d.toString()));}catch(JSONException error){attachmentLoading=false;return;}
  attachmentTask=attachmentWorker.submit(()->{
   Map<String,String> payloads=new HashMap<>();Map<String,JSONObject> snapshots=new HashMap<>();List<JSONObject> createdSnapshots=new ArrayList<>();int used=0;boolean notice=false;
   for(JSONObject d:captured){
    if(generation!=attachmentGeneration||Thread.currentThread().isInterrupted()){discardPreparedSnapshots(createdSnapshots);return;}
    String p;try{
     p=AttachmentSnapshots.read(getApplicationContext(),d);boolean hadSnapshot=p!=null;if(p==null)p=AttachmentBatch.payload(getContentResolver(),d);
     int bytes=AttachmentBatch.estimatedBytes(d,p);if(bytes>AttachmentBatch.MAX_TOTAL_CHARS-used)throw new IOException("附件超出总大小限制，请减少附件或选择较小文件");used+=bytes;
     if(!hadSnapshot){AttachmentSnapshots.capture(getApplicationContext(),d,p);if(!d.optString("payloadSnapshotFile").isEmpty())createdSnapshots.add(d);}
     snapshots.put(d.optString("uri"),d);
    }catch(Exception error){p=AttachmentBatch.failure(error.getMessage()==null?"附件不可用，请重新选择":error.getMessage());}
    if(AttachmentBatch.isFailure(p))notice=true;payloads.put(d.optString("uri"),p);
   }
   final boolean warn=notice;
   runOnUiThread(()->{
    if(isDestroyed()||generation!=attachmentGeneration){discardPreparedSnapshots(createdSnapshots);return;}
    for(int i=0;i<pendingAttachments.size();i++){JSONObject updated=snapshots.get(pendingAttachments.get(i).optString("uri"));if(updated!=null)pendingAttachments.set(i,updated);}
    preparedAttachments.putAll(payloads);attachmentLoading=false;renderAttachmentPreview();updateSendButton();
    if(warn)Toast.makeText(this,"部分附件无法发送，请查看提示并移除或更换",Toast.LENGTH_LONG).show();
    if(ready!=null)ready.run();else startNextAttachmentImport();
   });
  });
 }
 private void discardPreparedSnapshots(List<JSONObject> snapshots){for(JSONObject d:snapshots)AttachmentSnapshots.discard(getApplicationContext(),d);}
 private void resumeAttachmentWork(){
  if(attachmentLoading||activeAttachmentImport!=null)return;if(!attachmentImports.isEmpty())startNextAttachmentImport();else if(!pendingAttachments.isEmpty()&&preparedAttachments.isEmpty())preparePendingAttachments();
 }
 private void saveIncomingState(Bundle out){
  if(activeAttachmentImport!=null||attachmentLoading){
   ++attachmentGeneration;if(attachmentTask!=null)attachmentTask.cancel(true);attachmentLoading=false;
   if(activeAttachmentImport!=null){attachmentImports.addFirst(activeAttachmentImport);activeAttachmentImport=null;}
  }
  try{
   JSONArray pending=new JSONArray();for(JSONObject d:pendingAttachments){JSONObject copy=new JSONObject(d.toString());copy.remove("payloadSnapshot");pending.put(copy);}
   JSONArray requests=new JSONArray();for(IncomingShare.Request request:attachmentImports)requests.put(request.json());
   out.putString("pendingAttachmentDescriptors",pending.toString());out.putString("attachmentImports",requests.toString());out.putString("composerDraft",composer==null?"":composer.getText().toString());out.putBoolean("sharedIntentConsumed",sharedIntentConsumed);
  }catch(JSONException failure){android.util.Log.w("IncomingShare","Unable to preserve attachment draft");}
 }
 private void restoreIncomingState(Bundle state){
  if(state==null)return;sharedIntentConsumed=state.getBoolean("sharedIntentConsumed",false);composer.setText(state.getString("composerDraft",""));
  try{
   JSONArray pending=new JSONArray(state.getString("pendingAttachmentDescriptors","[]"));for(int i=0;i<Math.min(pending.length(),AttachmentBatch.MAX_COUNT);i++){JSONObject d=pending.getJSONObject(i);if("content".equals(Uri.parse(d.optString("uri")).getScheme()))pendingAttachments.add(d);}
   JSONArray requests=new JSONArray(state.getString("attachmentImports","[]"));for(int i=0;i<requests.length();i++)attachmentImports.addLast(IncomingShare.Request.restore(requests.getJSONObject(i)));
  }catch(JSONException invalid){Toast.makeText(this,"部分待发文件无法恢复，请重新添加",Toast.LENGTH_LONG).show();}
  renderAttachmentPreview();
 }
 private void removePendingAttachment(JSONObject descriptor){
  String source=descriptor.optString("sourceUri",descriptor.optString("uri"));if(activeAttachmentImport!=null)activeAttachmentImport.uris.removeIf(uri->source.equals(uri.toString()));for(IncomingShare.Request request:attachmentImports)request.uris.removeIf(uri->source.equals(uri.toString()));
  pendingAttachments.remove(descriptor);preparePendingAttachments();AttachmentSelection.discard(getApplicationContext(),descriptor);
 }

 private AttachmentUi attachmentUi(){if(attachmentUi==null)attachmentUi=new AttachmentUi(this);return attachmentUi;}
 private void renderAttachmentPreview(){
  attachmentPreview.removeAllViews();if(pendingAttachments.isEmpty())return;
  HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setTag("attachment-tray");scroll.setHorizontalScrollBarEnabled(false);
  scroll.setClipToPadding(false);scroll.setPadding(0,dp(6),0,dp(6));
  LinearLayout items=new LinearLayout(this);items.setGravity(Gravity.CENTER_VERTICAL);scroll.addView(items,new ViewGroup.LayoutParams(-2,dp(96)));
  for(JSONObject descriptor:pendingAttachments){
   View item=attachmentUi().pending(descriptor,attachmentLoading,()->previewAttachment(descriptor),()->removePendingAttachment(descriptor));
   LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)item.getLayoutParams();params.rightMargin=dp(8);items.addView(item,params);
  }
  attachmentPreview.addView(scroll,new LinearLayout.LayoutParams(-1,dp(108)));
 }
 private void saveAttachment(String sessionId,JSONArray descriptors,Map<String,String> payloads){if(descriptors.length()==0)return;try{JSONArray all=readSessions();for(int i=0;i<all.length();i++){JSONObject session=all.optJSONObject(i);if(session!=null&&sessionId.equals(session.optString("id"))){JSONArray messages=session.optJSONArray("messages");if(messages!=null&&messages.length()>0){JSONArray saved=new JSONArray();int total=0;for(int j=0;j<descriptors.length();j++){JSONObject descriptor=descriptors.optJSONObject(j);if(descriptor==null)continue;JSONObject copy=new JSONObject(descriptor.toString());String payload=payloads.get(descriptor.optString("uri"));if(payload==null||AttachmentBatch.isFailure(payload))throw new IOException("附件内容未准备好，不能保存为已发送附件");total+=AttachmentBatch.estimatedBytes(descriptor,payload);if(total>AttachmentBatch.MAX_TOTAL_CHARS)throw new IOException("会话附件总量超过限制");if(!AttachmentSnapshots.has(copy))copy.put("payloadSnapshot",payload);saved.put(copy);}messages.getJSONObject(messages.length()-1).put("attachments",saved);persistSessions(all);}return;}}}catch(Exception e){android.util.Log.w("Attachment","Unable to persist attachment payload snapshot",e);}}
 private void appendAttachment(JSONObject descriptor){
  if(descriptor==null||messageList.getChildCount()==0)return;
  View row=messageList.getChildAt(messageList.getChildCount()-1);if(!(row instanceof LinearLayout))return;
  LinearLayout message=(LinearLayout)row;View first=message.getChildAt(0);LinearLayout box;
  if(first instanceof LinearLayout)box=(LinearLayout)first;else{
   message.removeView(first);box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(first,new LinearLayout.LayoutParams(-1,-2));
   message.addView(box,new LinearLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(92),dp(310)),-2));
  }
  LinearLayout attachments=box.findViewWithTag("message-attachments");
  if(attachments==null){attachments=new LinearLayout(this);attachments.setTag("message-attachments");attachments.setOrientation(LinearLayout.VERTICAL);box.addView(attachments,0,new LinearLayout.LayoutParams(-1,-2));}
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);
  attachments.addView(attachmentView(descriptor),params);
 }
 private View attachmentView(JSONObject descriptor){return attachmentUi().message(descriptor,()->previewAttachment(descriptor));}
 private void previewAttachment(JSONObject descriptor){hideKeyboard();attachmentUi().preview(descriptor);}
 private String queryName(Uri u){String x="附件";try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)x=c.getString(i);}}catch(Exception ignored){}return x;}
 private void applyCustomTheme(){ThemePalette p=ThemePalette.resolve(this);purple=p.primary;page=p.page;card=p.surface;ink=p.ink;muted=p.muted;}
 private boolean preflight(){
  refreshModelLabels();JSONObject p=activeProvider(),m=ModelCapabilities.find(p,model);String problem="";
  try{
   String base=p.optString("api");if(NativeProtocols.nativeProtocol(protocol()))NativeProtocols.endpoint(base,protocol(),model,true);else ChatApiClient.endpoint(base,protocol());
   if(p.optString("apiKey").trim().isEmpty())problem="请填写供应商的 API Key。";else if(model.trim().isEmpty()||!m.has("id"))problem="请选择已配置的模型。";
   int max;try{max=Integer.parseInt(scoped().getString("outputTokens",m.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT)));}catch(Exception invalid){max=-1;}int context=ModelCapabilities.positive(m,"contextLength",ContextBudget.DEFAULT_CONTEXT);
   if(max<1||context<512)problem="输出上限必须为正整数，上下文容量至少为 512 token。";
   if(capabilities().needsBudget()&&!"default".equals(reasoning)&&!"none".equals(reasoning)&&max<=1024)problem="此 Claude 模型开启思考时，输出上限必须大于 1024 token。";
   if(!capabilities().supports(reasoning))problem="此模型不支持当前思考档位，请先选择自动或支持的档位。";
   if(capabilities().sampling){ModelStore.range(scoped().getString("conversationTemperature","0.7"),2);ModelStore.range(scoped().getString("conversationTopP","0.9"),1);}
  }catch(Exception invalid){problem="请求配置无效："+ApiFailure.local(invalid);}
  if(!problem.isEmpty()){TextView text=label(problem+"\n输入和附件已保留。",15,ink,false);new PanelDialog(this,"发送前需要完成配置","",text).compact().action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_NEUTRAL,"思考强度",this::showReasoningSelector).action(AlertDialog.BUTTON_POSITIVE,"选择模型",this::showModelSelector).show();return false;}
  SearchOptions search=SearchOptions.capture(scoped(),protocol());
  if(!search.error.isEmpty()||(search.tavily&&search.key.isEmpty())){
   TextView hint=label("当前联网搜索不可用。可以关闭联网继续，本次回答不会使用搜索结果。",15,ink,false);
   new PanelDialog(this,"联网搜索未就绪","输入和附件会保留",hint).compact().action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_NEUTRAL,"配置搜索",()->startActivity(new Intent(this,SettingsOptionActivity.class).putExtra("mode","search"))).action(AlertDialog.BUTTON_POSITIVE,"关闭联网继续",()->{scoped().edit().putBoolean("webSearchEnabled",false).apply();refreshModelLabels();sendMessage();}).show();return false;
  }
  return true;
 }
 private void clearSentComposer(){composer.setText("");hideKeyboard();attachmentGeneration++;pendingAttachments.clear();preparedAttachments.clear();setAttachmentChoices(false);renderAttachmentPreview();updateSendButton();}
 private void sendMessage(){
  if(attachmentLoading){Toast.makeText(this,"附件正在准备，请稍候",Toast.LENGTH_SHORT).show();return;}
  for(JSONObject attachment:pendingAttachments){String payload=preparedAttachments.get(attachment.optString("uri"));if(payload==null||AttachmentBatch.isFailure(payload)){Toast.makeText(this,payload==null?"附件尚未准备完成":AttachmentBatch.failureMessage(payload),Toast.LENGTH_LONG).show();return;}}
  String text=composer.getText().toString().trim();if(text.isEmpty()&&pendingAttachments.isEmpty())return;if(!preflight())return;
  String sent=text.isEmpty()?"已添加附件":text;JSONArray descriptors=new JSONArray();
  try{for(JSONObject d:pendingAttachments){JSONObject copy=new JSONObject(d.toString());String payload=preparedAttachments.get(d.optString("uri"));if(payload!=null&&!AttachmentSnapshots.has(copy))copy.put("payloadSnapshot",payload);descriptors.put(copy);}}catch(JSONException invalid){return;}
  if(ChatGenerationService.active(prefs.getString("currentSessionId",""))!=null){
   try{JSONObject queued=new JSONObject().put("message",new JSONObject().put("id",UUID.randomUUID().toString()).put("user",true).put("text",sent).put("timestamp",System.currentTimeMillis()).put("attachments",descriptors)).put("config",new JSONObject(((SessionConfig)scoped()).values.toString()));
    ChatGenerationService.enqueue(this,prefs.getString("currentSessionId",""),queued);clearSentComposer();Toast.makeText(this,"已加入队列，将在本次回复结束后发送",Toast.LENGTH_SHORT).show();refreshHistory();
   }catch(Exception failure){Toast.makeText(this,failure.getMessage(),Toast.LENGTH_LONG).show();}return;
  }
  follow.begin();Map<String,String> payloads=new HashMap<>(preparedAttachments);String sessionId=saveCurrentMessage(sent,true,null);saveAttachment(sessionId,descriptors,payloads);clearSentComposer();
  JSONObject session=MessageEdits.findSession(readSessions(),sessionId);if(session!=null)renderSessionMessages(session,Math.max(0,session.optJSONArray("messages").length()-40));
  ReplyContext request=captureRequest(sessionId);LinearLayout row=loadingRow();row.setTag(request);request.visibleRow=row;messageList.addView(row);activeReply=request;sending=true;updateSendButton();scrollBottom();
  JSONObject p=activeProvider();launchGeneration(request,p.optString("api"),p.optString("apiKey"),buildMessages(),session,payloads,text.isEmpty()?sent:text);
 }
 private ReplyContext captureRequest(String sessionId){
  ReplyContext request=new ReplyContext(sessionId,model,reasoning);request.temperature=scoped().getString("conversationTemperature","0.7");request.topP=scoped().getString("conversationTopP","0.9");request.outputTokens=scoped().getString("outputTokens",ModelCapabilities.find(activeProvider(),model).optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT));request.protocol=protocol();request.search=SearchOptions.capture(scoped(),request.protocol);JSONObject session=MessageEdits.findSession(readSessions(),sessionId);request.branchId=session==null?"":session.optString("activeBranchId");return request;
 }
 private void requestTopicTitle(String firstMessage,String sessionId) {
  if(sessionId==null||sessionId.isEmpty())return;
  try {
   JSONObject session=null;JSONArray all=readSessions();
   for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&sessionId.equals(s.optString("id"))){session=s;break;}}
   if(!canAutomaticallyTitle(session))return;
   JSONObject first=firstUserMessage(session);
   if(first==null)return;
   if(!session.optBoolean("titleFallback",false)){
    session.put("title",fallbackTitle(first)).put("titleFallback",true);
    persistSessions(all);refreshHistory();
   }
   if(!prefs.getBoolean("topicEnabled",false)||topicRequestsInFlight.contains(sessionId))return;
   TopicConfig config;
   try{config=new TopicConfig(prefs);}catch(Exception invalid){return;}
   firstMessage=first.optString("text","").trim();
   if(firstMessage.isEmpty()||("已添加附件".equals(firstMessage)&&first.has("attachment")))firstMessage=fallbackTitle(first);
   final String titleSourceId=first.optString("id"),titleSourceText=first.optString("text");
   topicRequestsInFlight.add(sessionId);
   android.util.Log.i("TopicNaming","Request started for session "+sessionId);
   apiClient.topic(config.base,config.key,config.model,config.prompt,firstMessage,config.protocol,new ChatApiClient.CallbackResult<String>() {
    public void onSuccess(String title){runOnUiThread(()->{
     try {
      String clean=ChatResponseParser.cleanTitle(title,false);
      JSONArray latest=readSessions();boolean updated=false;
      for(int i=0;i<latest.length();i++){JSONObject s=latest.optJSONObject(i);if(s!=null&&sessionId.equals(s.optString("id"))&&canAutomaticallyTitle(s)&&firstUserMessage(s)!=null&&titleSourceId.equals(firstUserMessage(s).optString("id"))&&titleSourceText.equals(firstUserMessage(s).optString("text"))){s.put("title",clean).put("titleAutomatic",true).put("titleFallback",false);updated=true;break;}}
      if(updated){
       if(!prefs.edit().putString("sessions",latest.toString()).commit()){topicFailure(sessionId,"persistence_error：无法保存标题");return;}
       refreshHistory();
      } else {Toast.makeText(MainActivity.this,"命名结果未应用：目标对话已删除或已重命名",Toast.LENGTH_SHORT).show();}
      android.util.Log.i("TopicNaming",(updated?"Title saved for ":"Title skipped for ")+sessionId);
     }catch(Exception e){topicFailure(sessionId,"persistence_error：无法清理或保存标题");}finally{topicRequestsInFlight.remove(sessionId);}
    });}
    public void onError(String message){runOnUiThread(()->{topicRequestsInFlight.remove(sessionId);topicFailure(sessionId,message);});}
   });
  }catch(IllegalArgumentException e){android.util.Log.w("TopicNaming","Incomplete configuration for session "+sessionId);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
  catch(Exception e){topicRequestsInFlight.remove(sessionId);topicFailure(sessionId,"configuration_error");}
 }
 private void topicFailure(String id,String code){android.util.Log.w("TopicNaming","session="+id+" failure="+code);Toast.makeText(this,"话题命名失败（"+code+"），请在设置中检查供应商、模型、提示词和网络。",Toast.LENGTH_LONG).show();}
 
 private LinearLayout loadingRow(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,dp(5));TextView t=label(HomeCopy.get(prefs,"thinkingStatus"),15,muted,false);t.setTag("status");t.setPadding(dp(15),dp(11),dp(15),dp(11));t.setBackgroundColor(Color.TRANSPARENT);row.addView(t,new LinearLayout.LayoutParams(-1,-2));return row;}
 private void setLoading(LinearLayout r,String s){runOnUiThread(()->{if(!isActive((ReplyContext)r.getTag()))return;View v=r.findViewWithTag("status");if(v instanceof TextView)((TextView)v).setText(s);});}
 private void completeReply(ReplyContext reply,String text,boolean error){if(reply.finished)return;if(reply.stream!=null)reply.stream.finish(text);reply.finished=true;reply.cancel.cancel();if(activeReply==reply){activeReply=null;sending=false;updateSendButton();}saveAssistantMessage(reply,text,error);if(reply.visibleRow!=null&&reply.visibleRow.getParent()==messageList){JSONObject session=MessageEdits.findSession(readSessions(),reply.sessionId);JSONArray messages=session==null?null:session.optJSONArray("messages");JSONObject saved=messages==null?null:messages.optJSONObject(messages.length()-1);if(saved!=null){bindMessageActions(reply.visibleRow,reply.sessionId,saved);bindVersions(reply.visibleRow,session,saved);}}scrollBottom();}
 private MarkdownMessageView markdownText(String s){if(markwon==null)markwon=MarkdownRenderer.create(this);MarkdownMessageView view=new MarkdownMessageView(this,markwon);view.setMarkdown(s);return view;}private io.noties.markwon.Markwon markwon;
 private void renderMarkdown(MarkdownMessageView view,String value){view.setMarkdown(value);}
 private CharSequence markdown(String s){if(markwon==null)markwon=MarkdownRenderer.create(this);return markwon.toMarkdown(s==null?"":s);}
 private void finishRequest(LinearLayout r,String v,boolean err){runOnUiThread(()->{ReplyContext reply=(ReplyContext)r.getTag();if(!isActive(reply))return;if(r.getParent()!=null)messageList.removeView(r);addMessage(v,false,null,reply.timestamp,reply.modelId);reply.visibleRow=(LinearLayout)messageList.getChildAt(messageList.getChildCount()-1);addSearchCard(reply.visibleRow,reply.searchStatus,reply.sources,"");completeReply(reply,v,err);});}private void scrollBottom(){if(follow!=null)follow.changed();}private int parseInt(String s,int f){try{return Integer.parseInt(s);}catch(Exception e){return f;}}
 private JSONArray buildMessages(){try{return ChatGenerationService.history(ensureCurrentSession(readSessions()),scoped().getString("systemPrompt",""));}catch(Exception ignored){return new JSONArray();}}
 private JSONObject activeProvider(){try{SharedPreferences config=scoped();JSONArray a=ModelStore.read(prefs);String id=config.getString("providerId","");for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&id.equals(p.optString("id"))){JSONObject copy=new JSONObject(p.toString());copy.put("selectedModelId",config.getString("modelId",""));return copy;}}}catch(Exception ignored){}return new JSONObject();}
 private void addMessage(String t,boolean user,String att){addMessage(t,user,att,0,"");}
 private LinearLayout assistantRow(MarkdownMessageView body,long timestamp,String modelId){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.LEFT);row.setPadding(0,dp(12),0,dp(16));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(11),0,dp(11));body.setTextSize(16);body.setLineSpacing(dp(3),1.18f);box.addView(body,new LinearLayout.LayoutParams(-1,-2));TextView metadata=label("",12,muted,false);metadata.setTag("message-metadata");metadata.setVisibility(View.GONE);box.addView(metadata,new LinearLayout.LayoutParams(-1,-2));box.addView(messageControlRow(false),new LinearLayout.LayoutParams(-1,dp(44)));row.addView(box,new LinearLayout.LayoutParams(-1,-2));return row;}
 private void addMessage(String t,boolean user,String att,long timestamp,String modelId){if(!user){messageList.addView(assistantRow(markdownText(t),timestamp,modelId));return;}LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.RIGHT);r.setPadding(0,dp(12),0,dp(12));TextView b=label(t+(att==null?"":"\n📎 "+att),16,ink,false);b.setTextIsSelectable(true);b.setLineSpacing(dp(2),1.16f);b.setPadding(dp(18),dp(14),dp(18),dp(14));b.setBackground(UiStyle.surface(this,androidx.core.graphics.ColorUtils.blendARGB(card,purple,.08f),22,true));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(b,new LinearLayout.LayoutParams(-1,-2));box.addView(messageControlRow(true),new LinearLayout.LayoutParams(-1,dp(44)));r.addView(box,new LinearLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(92),dp(310)),-2));messageList.addView(r);}
 private LinearLayout messageControlRow(boolean user){LinearLayout row=new LinearLayout(this);row.setTag("message-control-row");row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);LinearLayout slot=new LinearLayout(this);slot.setTag("revision-slot");slot.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(slot,new LinearLayout.LayoutParams(0,dp(44),1));row.addView(messageActions(user),new LinearLayout.LayoutParams(3*dp(44),dp(44)));return row;}
 private LinearLayout messageActions(boolean user){
  LinearLayout actions=new LinearLayout(this);actions.setTag("message-actions");actions.setGravity((user?Gravity.RIGHT:Gravity.LEFT)|Gravity.CENTER_VERTICAL);actions.setBaselineAligned(false);
  String[] descriptions=user?new String[]{"仅编辑用户消息","重试用户消息","复制用户消息"}:new String[]{"编辑模型消息","重试模型回复","复制模型消息"};
  int[] icons={R.drawable.ic_edit_message,R.drawable.ic_retry_message,R.drawable.ic_copy_message};
  for(int i=0;i<3;i++){ImageButton button=new ImageButton(this);button.setTag("message-action-"+i);button.setImageResource(icons[i]);button.setScaleType(ImageView.ScaleType.CENTER);button.setPadding(0,0,0,0);button.setImageTintList(android.content.res.ColorStateList.valueOf(muted));android.util.TypedValue background=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,background,true);button.setBackgroundResource(background.resourceId);button.setContentDescription(descriptions[i]);button.setEnabled(false);button.setAlpha(.45f);actions.addView(button,new LinearLayout.LayoutParams(dp(44),dp(44)));}
  return actions;
 }
 private void bindMessageActions(View row,String sessionId,JSONObject message){
  if(message==null)return;final String messageId=message.optString("id");
  MarkdownMessageView content=row.findViewWithTag("markdown-message");if(content!=null){content.setMessageId(messageId);content.restoreCodeState(codeViewStates.get(messageId));}
  TextView metadata=row.findViewWithTag("message-metadata");if(metadata!=null&&message.optBoolean("interrupted")&&!metadata.getText().toString().endsWith(" · 已中断"))metadata.append(" · 已中断");
  LinearLayout actions=row.findViewWithTag("message-actions");if(actions!=null)for(int i=0;i<3;i++){final int which=i;View button=actions.findViewWithTag("message-action-"+i);if(button==null)continue;button.setEnabled(!messageId.isEmpty());button.setAlpha(messageId.isEmpty()?.45f:1f);button.setOnClickListener(v->performMessageAction(sessionId,messageId,which));}
  View.OnLongClickListener listener=v->{showMessageActions(sessionId,messageId);return true;};
  row.setOnLongClickListener(listener);
  if(row instanceof ViewGroup){ViewGroup group=(ViewGroup)row;for(int i=0;i<group.getChildCount();i++)bindLongClick(group.getChildAt(i),listener);}
 }
 private void bindVersions(View row,JSONObject session,JSONObject message){LinearLayout slot=row.findViewWithTag("revision-slot");if(slot==null)return;slot.removeAllViews();JSONArray branches=session.optJSONArray("branches");if(branches==null||branches.length()<2||!message.optString("id").equals(ConversationBranches.anchor(session)))return;LinearLayout actions=row.findViewWithTag("message-actions");if(actions==null||!(actions.getParent() instanceof LinearLayout))return;int selected=ConversationBranches.selected(session);LinearLayout nav=new LinearLayout(this);nav.setTag("message-versions");nav.setGravity(Gravity.CENTER_VERTICAL);nav.setBaselineAligned(false);TextView prev=iconButton("‹"),next=iconButton("›"),count=label((selected+1)+" / "+branches.length(),13,muted,false);prev.setContentDescription("上一个消息版本");next.setContentDescription("下一个消息版本");prev.setEnabled(selected>0&&!sending);next.setEnabled(selected+1<branches.length()&&!sending);prev.setAlpha(prev.isEnabled()?1:.4f);next.setAlpha(next.isEnabled()?1:.4f);prev.setOnClickListener(v->switchVersion(session.optString("id"),selected-1));next.setOnClickListener(v->switchVersion(session.optString("id"),selected+1));count.setGravity(Gravity.CENTER);count.setSingleLine(true);nav.addView(prev,new LinearLayout.LayoutParams(dp(32),dp(44)));nav.addView(count,new LinearLayout.LayoutParams(dp(40),dp(44)));nav.addView(next,new LinearLayout.LayoutParams(dp(32),dp(44)));slot.addView(nav,new LinearLayout.LayoutParams(dp(104),dp(44)));}
 private void switchVersion(String sessionId,int index){if(sending||!sessionId.equals(prefs.getString("currentSessionId","")))return;try{JSONArray all=readSessions();JSONObject s=MessageEdits.findSession(all,sessionId);if(s!=null&&ConversationBranches.select(s,index)){persistSessions(all);restoreSession(s);refreshHistory();}}catch(Exception e){Toast.makeText(this,"无法切换版本",Toast.LENGTH_SHORT).show();}}
 private void bindLongClick(View view,View.OnLongClickListener listener){if(view instanceof TextView&&((TextView)view).isTextSelectable())return;view.setOnLongClickListener(listener);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bindLongClick(group.getChildAt(i),listener);}}
 private JSONObject findMessage(String sessionId,String messageId){JSONObject session=MessageEdits.findSession(readSessions(),sessionId);JSONArray messages=session==null?null:session.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&messageId.equals(m.optString("id")))return m;}return null;}
 private void showMessageActions(String sessionId,String messageId){
  if(sending){Toast.makeText(this,"请等待当前回复完成",Toast.LENGTH_SHORT).show();return;}
  JSONObject message=findMessage(sessionId,messageId);if(message==null)return;
  boolean user=message.optBoolean("user");String[] actions=user?new String[]{"编辑","重试","复制","消息详情"}:new String[]{"编辑","重试","复制","消息详情","继续回答"};
  ChoiceDialogs.single(this,user?"用户消息操作":"模型消息操作",actions,-1,which->{if(which==3)showMessageDetails(message);else if(which==4)retryConversation(sessionId);else performMessageAction(sessionId,messageId,which);});
 }
 private void performMessageAction(String sessionId,String messageId,int which){
  if(sending){Toast.makeText(this,"请等待当前回复完成",Toast.LENGTH_SHORT).show();return;}
  JSONObject message=findMessage(sessionId,messageId);if(message==null)return;boolean user=message.optBoolean("user");
   if(which==2){try{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("消息",message.optString("text")));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();}catch(Exception unavailable){Toast.makeText(this,"复制失败：剪贴板不可用",Toast.LENGTH_SHORT).show();}return;}
   if(which==1){applyMessageEdit(sessionId,messageId,null,true);return;}
   if(which!=0||!sessionId.equals(prefs.getString("currentSessionId","")))return;
   Context dialogContext=EditorDialogs.themedContext(this);EditText input=EditorDialogs.editor(dialogContext);
   input.setText(message.optString("text"));input.setTextSize(17);input.setGravity(Gravity.TOP);input.setLineSpacing(dp(4),1.15f);
   input.setPadding(dp(16),dp(16),dp(16),dp(16));input.setBackground(UiStyle.surface(this,page,18,false));
   input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
   PanelDialog dialog=new PanelDialog(this,"编辑消息",user?"原消息和后续历史将保留在旧版本中":"编辑并重试将保留修改后的回复，并继续回答",input);
   dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_NEUTRAL,"仅编辑",null).action(AlertDialog.BUTTON_POSITIVE,"编辑并重试",null);
   dialog.show();
   for(int buttonId:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE})dialog.getButton(buttonId).setOnClickListener(v->{
    String value=input.getText().toString();if(value.trim().isEmpty()){input.setError("消息不能为空");return;}
    applyMessageEdit(sessionId,messageId,value,buttonId==AlertDialog.BUTTON_POSITIVE);dialog.dismiss();
   });
 }
 private void applyMessageEdit(String sessionId,String messageId,String value,boolean retry){
  if(sending||!sessionId.equals(prefs.getString("currentSessionId","")))return;
  try{JSONArray sessions=readSessions();JSONObject session=ConversationBranches.edit(sessions,sessionId,messageId,value,retry);if(session==null)return;persistSessions(sessions);restoreSession(session);refreshHistory();if(retry)retryConversation(sessionId);}catch(JSONException e){Toast.makeText(this,"无法保存消息",Toast.LENGTH_SHORT).show();}
 }
 private void retryConversation(String sessionId){
  if(sending||!sessionId.equals(prefs.getString("currentSessionId",""))||!preflight())return;
  follow.begin();ReplyContext request=captureRequest(sessionId);LinearLayout row=loadingRow();row.setTag(request);request.visibleRow=row;messageList.addView(row);activeReply=request;sending=true;updateSendButton();
  JSONObject session=MessageEdits.findSession(readSessions(),sessionId);JSONArray history=buildMessages();JSONArray retained=session==null?null:session.optJSONArray("messages");JSONObject last=retained==null?null:retained.optJSONObject(retained.length()-1);
  if(last!=null&&!last.optBoolean("user"))try{history.put(new JSONObject().put("role","user").put("content","请接着最后一条回复继续回答，不要重复已有内容。"));}catch(JSONException ignored){}
  String query="";if(retained!=null)for(int i=retained.length()-1;i>=0;i--){JSONObject m=retained.optJSONObject(i);if(m!=null&&m.optBoolean("user")){query=m.optString("text");break;}}
  JSONObject p=activeProvider();launchGeneration(request,p.optString("api"),p.optString("apiKey"),history,session,Collections.emptyMap(),query);
 }
 private void restoreHistoryAttachments(JSONObject session,JSONArray history){restoreHistoryAttachments(session,history,null,Collections.emptyMap());}
 private void restoreHistoryAttachments(JSONObject session,JSONArray history,ReplyContext request,Map<String,String> prepared){int used=0;
  JSONArray messages=session==null?null:session.optJSONArray("messages");if(messages==null)return;int index=history.length()>0&&history.optJSONObject(0)!=null&&"system".equals(history.optJSONObject(0).optString("role"))?1:0;
  for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);if(message==null||message.optBoolean("error",false))continue;JSONObject entry=history.optJSONObject(index++);JSONArray attachments=message.optJSONArray("attachments");if(entry==null||!message.optBoolean("user")||attachments==null)continue;
   try{JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text",message.optString("text")));
    for(int j=0;j<Math.min(10,attachments.length());j++){if(isDestroyed()||Thread.currentThread().isInterrupted()||(request!=null&&request.cancel.isCancelled()))return;JSONObject attachment=attachments.optJSONObject(j);if(attachment==null)continue;Uri uri=Uri.parse(attachment.optString("uri"));if(!"content".equals(uri.getScheme()))continue;String mime=attachment.optString("mime");try{
     String payload=AttachmentSnapshots.read(getApplicationContext(),attachment);if(payload==null||payload.isEmpty())payload=prepared.get(attachment.optString("uri"));if(payload==null)payload=AttachmentBatch.payload(getContentResolver(),attachment);if(AttachmentBatch.isFailure(payload))throw new IOException(AttachmentBatch.failureMessage(payload));int bytes=AttachmentBatch.estimatedBytes(attachment,payload);if(bytes>AttachmentBatch.MAX_TOTAL_CHARS-used)throw new IOException("附件总大小超过限制");used+=bytes;content.put(AttachmentBatch.part(attachment,payload,request==null?ChatApiClient.RESPONSES:request.protocol));
    }catch(Exception unavailable){content.put(new JSONObject().put("type","text").put("text","附件未发送：格式不支持或文件授权已失效，请重新选择。"));}}
    entry.put("content",content);
   }catch(JSONException ignored){}
  }
 }
 private EditText historySearch;
 private LinearLayout buildHistoryPanel(){
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(dp(16),dp(12),dp(16),dp(16));panel.setBackgroundColor(page);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,0,dp(12));
  header.addView(label(HomeCopy.get(prefs,"historyTitle"),22,ink,true),new LinearLayout.LayoutParams(0,-2,1));
  ImageButton add=vectorButton(R.drawable.ic_plus,"新建对话",purple);add.setOnClickListener(v->{closeHistory();resetConversation();});
  header.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(header);
  historySearch=new EditText(this);historySearch.setTextSize(15);historySearch.setTextColor(ink);historySearch.setHintTextColor(muted);
  historySearch.setSingleLine(true);historySearch.setHint("搜索对话…");historySearch.setContentDescription("搜索对话");
  historySearch.setPadding(dp(18),0,dp(18),0);historySearch.setBackground(UiStyle.surface(this,card,24,false));
  panel.addView(historySearch,new LinearLayout.LayoutParams(-1,dp(48)));
  historySearch.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(historyBrowser!=null)historyBrowser.filter(s.toString(),showingArchived);}public void afterTextChanged(android.text.Editable e){}});
  historyBrowser=new HistoryBrowser(this,this::openSearchResult,id->{JSONObject session=MessageEdits.findSession(readSessions(),id);if(session!=null)showSessionActions(session);});
  panel.addView(historyBrowser.view(),new LinearLayout.LayoutParams(-1,0,1));
  TextView manage=label("历史管理与备份  ›",13,muted,false);manage.setGravity(Gravity.CENTER_VERTICAL);manage.setPadding(dp(16),0,dp(16),0);manage.setOnClickListener(v->showHistoryManagement());panel.addView(manage,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView settings=label("设置  ›",15,ink,true);settings.setContentDescription("打开设置");settings.setGravity(Gravity.CENTER_VERTICAL);
  settings.setPadding(dp(16),0,dp(16),0);settings.setOnClickListener(v->{closeHistory();startActivity(new Intent(this,SettingsHomeActivity.class));});
  UiStyle.clickable(settings,card,18);panel.addView(settings,new LinearLayout.LayoutParams(-1,dp(52)));return panel;
 }
 private long sessionTimestamp(JSONObject session,long now){
  long timestamp=session.optLong("updatedAt",session.optLong("createdAt",0));
  if(timestamp>0)return timestamp;
  timestamp=HistoryDates.parseLegacy(session.optString("time",""),now);
  JSONArray messages=session.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){
   JSONObject message=messages.optJSONObject(i);if(message!=null)timestamp=Math.max(timestamp,message.optLong("timestamp",0));
  }
  if(timestamp==0)try{long id=Long.parseLong(session.optString("id",""));if(id>100000000000L)timestamp=id;}catch(NumberFormatException ignored){}
  return timestamp;
 }
 private HistoryBrowser historyBrowser;private boolean showingArchived;
 private void refreshHistory(){if(historyBrowser!=null)historyBrowser.refresh(readSessions(),prefs.getString("currentSessionId",""));}
 private void showSessionActions(JSONObject session){
  String time=new SimpleDateFormat("yyyy年M月d日 HH:mm",Locale.getDefault()).format(new Date(sessionTimestamp(session,System.currentTimeMillis())));
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);PanelDialog dialog=new PanelDialog(this,session.optString("title","对话操作"),"对话时间 · "+time,actions).compact();
  String[] titles={"重命名",session.optBoolean("pinned")?"取消置顶":"置顶",session.optBoolean("archived")?"取消归档":"归档","对话设置","用量与详情","删除对话","清空排队消息"};
  for(int i=0;i<titles.length;i++){final int action=i;TextView item=label(titles[i],16,ink,false);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(16),0,dp(16),0);UiStyle.clickable(item,card,14);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.bottomMargin=dp(6);actions.addView(item,params);
   item.setOnClickListener(v->{dialog.dismiss();if(action==0)renameSession(session);else if(action==1||action==2){try{session.put(action==1?"pinned":"archived",!session.optBoolean(action==1?"pinned":"archived"));SessionRepository.get(prefs).upsert(session);refreshHistory();}catch(JSONException ignored){}}
    else if(action==3){restoreSession(session);closeHistory();showConversationSettings();}else if(action==4)showSessionDetails(session);else if(action==5)confirmDeleteSession(session);else {session.remove("outbox");SessionRepository.get(prefs).upsert(session);Toast.makeText(this,"已清空队列",Toast.LENGTH_SHORT).show();}});
  }dialog.action(AlertDialog.BUTTON_NEGATIVE,"关闭",null).show();
 }
 private void renameSession(JSONObject session){
  EditText input=EditorDialogs.editor(EditorDialogs.themedContext(this));input.setSingleLine(true);input.setTextSize(16);
  input.setText(session.optString("title",""));input.setSelectAllOnFocus(true);input.setMinHeight(dp(56));
  input.setBackground(UiStyle.surface(this,page,16,false));
  PanelDialog dialog=new PanelDialog(this,"重命名对话","为这段对话取一个容易找到的名字",input).compact();
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"保存",null);dialog.show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String title=input.getText().toString().trim();
   if(title.isEmpty()){input.setError("名称不能为空");return;}updateSession(session.optString("id"),title);dialog.dismiss();
  });
 }
 private void confirmDeleteSession(JSONObject session){
  TextView message=label("删除后无法恢复这段对话及其消息。",15,muted,false);message.setPadding(dp(8),dp(8),dp(8),dp(16));
  PanelDialog dialog=new PanelDialog(this,"删除对话？",session.optString("title",""),message).compact();
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"删除",()->deleteSession(session.optString("id")));dialog.show();
 }
 private void updateSession(String id,String title){JSONArray sessions=readSessions();for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null&&id.equals(s.optString("id")))try{s.put("title",title).put("titleManual",true).put("titleFallback",false);}catch(Exception ignored){}}persistSessions(sessions);refreshHistory();}
 private void deleteSession(String id){
  if(ChatGenerationService.active(id)!=null){Toast.makeText(this,"请先停止这段对话的回复",Toast.LENGTH_SHORT).show();return;}
  JSONArray old=readSessions(),updated=new JSONArray();for(int i=0;i<old.length();i++){JSONObject session=old.optJSONObject(i);if(session!=null&&!id.equals(session.optString("id")))updated.put(session);}
  try{if(updated.length()==0)updated.put(newSession());}catch(JSONException ignored){}SessionRepository.get(prefs).replace(updated);
  if(id.equals(prefs.getString("currentSessionId",""))&&updated.length()>0)restoreSession(updated.optJSONObject(updated.length()-1));refreshHistory();
 }
 private int visibleStart;private String visibleSession="";
 private void restoreSession(JSONObject session){
  if(messageList==null)return;cacheCodeViewStates();String previous=prefs.getString("currentSessionId","");viewportPositions.put(previous,conversationScroll.getScrollY());prefs.edit().putString("currentSessionId",session.optString("id")).apply();
  generation=ChatGenerationService.active(session.optString("id"));sending=generation!=null;activeReply=null;generationBody=null;showingBackgroundRun=false;updateSendButton();follow.detach();refreshModelLabels();
  JSONArray messages=session.optJSONArray("messages");renderSessionMessages(session,Math.max(0,(messages==null?0:messages.length())-40));
  int ticket=++restoreGeneration;int y=viewportPositions.getOrDefault(session.optString("id"),-1);conversationScroll.post(()->{if(ticket==restoreGeneration){if(y<0)conversationScroll.fullScroll(View.FOCUS_DOWN);else conversationScroll.scrollTo(0,y);}});
  if(generation!=null)renderGeneration(generation);refreshConversationTitle();
 }
 private void renderSessionMessages(JSONObject session,int start){
  cacheCodeViewStates();if(messageList.getChildCount()>1)messageList.removeViews(1,messageList.getChildCount()-1);JSONArray messages=session.optJSONArray("messages");if(messages==null)return;
  visibleSession=session.optString("id");visibleStart=Math.max(0,start);int end=Math.min(messages.length(),visibleStart+40);
  if(visibleStart>0)addPageButton("查看更早的消息",()->{renderSessionMessages(session,Math.max(0,visibleStart-40));conversationScroll.post(()->conversationScroll.scrollTo(0,0));});
  for(int i=visibleStart;i<end;i++){JSONObject m=messages.optJSONObject(i);if(m==null)continue;addMessage(m.optString("text"),m.optBoolean("user"),m.optString("attachment",null),m.optLong("timestamp"),m.optString("modelId",m.optString("model","")));
   View row=messageList.getChildAt(messageList.getChildCount()-1);row.setTag("message:"+m.optString("id"));if(!m.optBoolean("user")){bindReasoning((LinearLayout)row,m.optString("id"),m.optString("reasoningContent"),false,m.optBoolean("interrupted")||m.optBoolean("error"),"summary".equals(m.optString("reasoningKind")));MarkdownMessageView answer=row.findViewWithTag("markdown-message");if(answer!=null)answer.setVisibility(m.optString("text").isEmpty()&&!m.optString("reasoningContent").isEmpty()?View.GONE:View.VISIBLE);}bindMessageActions(row,session.optString("id"),m);bindVersions(row,session,m);
   if(!m.optBoolean("user")){addSearchCard((LinearLayout)row,m.optString("searchStatus"),m.has("sources")?SearchSources.parse(m.optJSONArray("sources")):SearchSources.legacy(m.optString("searchResults",m.optString("searchText"))),m.optString("searchResults",m.optString("searchText")));bindCompletion((LinearLayout)row,session.optString("id"),m);}
   JSONArray attachments=m.optJSONArray("attachments");if(m.optBoolean("user")&&attachments!=null)for(int j=0;j<attachments.length();j++)appendAttachment(attachments.optJSONObject(j));
  }
  if(end<messages.length())addPageButton("查看后续消息",()->{renderSessionMessages(session,end);conversationScroll.post(()->conversationScroll.scrollTo(0,0));});
  JSONArray queue=session.optJSONArray("outbox");if(queue!=null&&queue.length()>0)addPageButton("有 "+queue.length()+" 条排队消息"+(sending?"":" · 点击继续发送"),()->{if(!sending)ChatGenerationService.resumeQueue(this,session.optString("id"));});
  if(codeViewStates.size()>160){Iterator<String> keys=codeViewStates.keySet().iterator();while(codeViewStates.size()>120&&keys.hasNext()){keys.next();keys.remove();}}
 }
 private void addPageButton(String text,Runnable action){TextView view=label(text,13,purple,false);view.setGravity(Gravity.CENTER);view.setMinHeight(dp(48));view.setOnClickListener(v->action.run());messageList.addView(view,new LinearLayout.LayoutParams(-1,-2));}
 private void bindReasoning(LinearLayout row,String messageId,String text,boolean thinking,boolean interrupted,boolean summary){
  ReasoningView view=row.findViewWithTag("reasoning-view");if(view==null&&(text==null||text.isEmpty()))return;
  if(view==null){if(markwon==null)markwon=MarkdownRenderer.create(this);view=new ReasoningView(this,markwon,expanded->{reasoningExpanded.put(messageId,expanded);if(reasoningExpanded.size()>160){Iterator<String> keys=reasoningExpanded.keySet().iterator();if(keys.hasNext()){keys.next();keys.remove();}}if(follow!=null)follow.detach();});((LinearLayout)row.getChildAt(0)).addView(view,0,new LinearLayout.LayoutParams(-1,-2));}
  view.bind(text,thinking,interrupted,summary,Boolean.TRUE.equals(reasoningExpanded.get(messageId)));
 }
 private void bindCompletion(LinearLayout row,String sessionId,JSONObject message){
  TextView metadata=row.findViewWithTag("message-metadata");if(metadata==null)return;String state=message.optString("completionState",message.optBoolean("interrupted")?"interrupted":"complete"),note=message.optString("searchNote");
  boolean omitted=message.optInt("omittedMessages")>0;metadata.setVisibility(state.equals("complete")&&note.isEmpty()&&!omitted?View.GONE:View.VISIBLE);metadata.setText((state.equals("complete")?"":new GenerationResult(state,message.optString("finishReason"),message.optJSONObject("usage")).label())+(note.isEmpty()?"":" · "+note));metadata.setTextColor(muted);if(state.equals("complete")&&omitted){metadata.setText("本次已省略较早的上下文 · 查看详情  ›");metadata.setMinHeight(dp(44));metadata.setOnClickListener(v->showMessageDetails(message));}
  if(state.equals("error")&&message.optString("finishReason").startsWith("web_search_error")){metadata.setVisibility(View.VISIBLE);metadata.setText("联网请求失败 · 关闭联网重试  ›");metadata.setMinHeight(dp(44));metadata.setOnClickListener(v->{scoped().edit().putBoolean("webSearchEnabled",false).apply();refreshModelLabels();retryConversation(sessionId);});}
  else if(state.equals("error")||state.equals("truncated")||state.equals("interrupted")||state.equals("incomplete")){metadata.setMinHeight(dp(44));metadata.setText(metadata.getText()+"  ›");metadata.setOnClickListener(v->retryConversation(sessionId));}
 }
 private void openSearchResult(String id,String messageId,String query){
  JSONObject session=MessageEdits.findSession(readSessions(),id);if(session==null)return;restoreSession(session);closeHistory();if(messageId.isEmpty())return;
  JSONArray messages=session.optJSONArray("messages");for(int i=0;messages!=null&&i<messages.length();i++)if(messageId.equals(messages.optJSONObject(i).optString("id"))){renderSessionMessages(session,Math.max(0,i-3));break;}
  View target=messageList.findViewWithTag("message:"+messageId);if(target!=null){target.setBackground(UiStyle.surface(this,androidx.core.graphics.ColorUtils.blendARGB(page,purple,.07f),12,false));conversationScroll.post(()->conversationScroll.smoothScrollTo(0,target.getTop()));}
 }
 private JSONArray readSessions(){if(prefs==null)return new JSONArray();JSONArray sessions=SessionRepository.get(prefs).read();return sessions;}
 private void persistSessions(JSONArray all){try{for(int i=0;i<all.length();i++){JSONObject session=all.optJSONObject(i);if(session!=null)ConversationBranches.sync(session);}}catch(JSONException failure){throw new IllegalStateException("无法保存版本",failure);}SessionRepository.get(prefs).replace(all);}
 private void activateSession(JSONArray sessions,JSONObject session){SessionRepository.get(prefs).replace(sessions);prefs.edit().putString("currentSessionId",session.optString("id")).apply();}
 private JSONObject newSession()throws JSONException{return new JSONObject().put("id",UUID.randomUUID().toString()).put("title","新对话").put("createdAt",System.currentTimeMillis()).put("updatedAt",System.currentTimeMillis()).put("time",new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date())).put("messages",new JSONArray()).put("config",SessionConfig.capture(prefs));}
 private boolean reusableEmpty(JSONObject session){JSONArray messages=session==null?null:session.optJSONArray("messages");return messages!=null&&messages.length()==0&&"新对话".equals(session.optString("title","新对话"))&&!session.optBoolean("titleManual",false)&&!session.optBoolean("titleAutomatic",false);}
 private JSONObject ensureCurrentSession(JSONArray sessions)throws JSONException{
  String id=prefs.getString("currentSessionId","");JSONObject empty=null;
  for(int i=0;i<sessions.length();i++){JSONObject session=sessions.optJSONObject(i);if(session==null)continue;if(id.equals(session.optString("id"))){if(session.optJSONArray("messages")==null)session.put("messages",new JSONArray());return session;}if(empty==null&&reusableEmpty(session))empty=session;}
  if(empty==null){empty=newSession();sessions.put(empty);}activateSession(sessions,empty);return empty;
 }
 /** Migrate only unnamed empty placeholders; never discard manually named or populated sessions. */
 private JSONArray collapseEmptySessions(JSONArray sessions,JSONObject current){JSONObject keep=reusableEmpty(current)?current:null;JSONArray cleaned=new JSONArray();
  if(keep==null)for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(reusableEmpty(s)){keep=s;break;}}
  for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s!=null&&(!reusableEmpty(s)||s==keep))cleaned.put(s);}return cleaned;
 }
 private void initializeSessions(){initializeSessions(false);}
 private void initializeSessions(boolean coldStart){
  try{
   JSONArray all=readSessions();JSONObject current=null;ChatGenerationService.Run running=ChatGenerationService.active();
   if(running!=null&&resumingGeneration(getIntent()))current=MessageEdits.findSession(all,running.sessionId);
   if(current==null&&coldStart){for(int i=0;i<all.length();i++){JSONObject candidate=all.optJSONObject(i);if(reusableEmpty(candidate)){current=candidate;break;}}
    if(current==null){current=newSession();all.put(current);}current.put("updatedAt",System.currentTimeMillis()).put("time",new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date()));
   }
   if(current==null)current=ensureCurrentSession(all);
   all=collapseEmptySessions(all,current);activateSession(all,current);restoreSession(current);refreshHistory();
  }catch(JSONException error){android.util.Log.e("Sessions","Unable to initialize sessions",error);}
 }
 private void refreshConversationTitle(){
  if(conversationTitle==null||prefs==null)return;String title="新对话";
  try{JSONObject session=MessageEdits.findSession(readSessions(),prefs.getString("currentSessionId",""));if(session!=null&&!session.optString("title","").trim().isEmpty())title=session.optString("title");}catch(Exception ignored){}
  UiStyle.textIfChanged(conversationTitle,title);
 }
 private String saveCurrentMessage(String t,boolean user,String att){try{JSONArray a=readSessions();JSONObject s=ensureCurrentSession(a);s.put("updatedAt",System.currentTimeMillis()).put("time",new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date()));JSONObject m=new JSONObject().put("id",UUID.randomUUID().toString()).put("text",t).put("user",user).put("timestamp",System.currentTimeMillis());if(att!=null)m.put("attachment",att);s.optJSONArray("messages").put(m);persistSessions(a);refreshHistory();if(user){JSONArray messages=s.optJSONArray("messages");for(int i=0;i<messages.length();i++){JSONObject first=messages.optJSONObject(i);if(first!=null&&first.optBoolean("user")){requestTopicTitle(first.optString("text",""),s.optString("id",""));break;}}}return s.optString("id","");}catch(Exception ignored){return "";}}private void saveAssistantMessage(ReplyContext reply,String t,boolean error){if(reply.sessionId==null||reply.sessionId.isEmpty())return;try{JSONArray a=readSessions();for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s!=null&&reply.sessionId.equals(s.optString("id"))){if(!reply.branchId.equals(s.optString("activeBranchId")))return;s.put("updatedAt",System.currentTimeMillis());JSONArray messages=s.optJSONArray("messages");if(messages!=null)messages.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("text",t).put("user",false).put("timestamp",reply.timestamp).put("modelId",reply.modelId).put("error",error).put("searchStatus",reply.searchStatus).put("sources",reply.sources.items));persistSessions(a);refreshHistory();return;}}}catch(Exception e){android.util.Log.e("Sessions","Unable to save assistant reply",e);}}
 private void resetConversation(){try{JSONArray all=readSessions();JSONObject current=ensureCurrentSession(all),target=reusableEmpty(current)?current:null;all=collapseEmptySessions(all,current);if(target==null)for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(reusableEmpty(s)){target=s;break;}}if(target==null){target=newSession();all.put(target);}target.put("updatedAt",System.currentTimeMillis()).put("time",new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date()));activateSession(all,target);restoreSession(target);refreshHistory();}catch(JSONException e){android.util.Log.e("Sessions","Unable to activate new session",e);}}
 /** Only placeholders and our persisted provisional titles may be automatically replaced. */
 static boolean canAutomaticallyTitle(JSONObject session){return session!=null&&!session.optBoolean("titleManual",false)&&!session.optBoolean("titleAutomatic",false)&&(session.optBoolean("titleFallback",false)||session.optString("title","").trim().isEmpty()||"新对话".equals(session.optString("title")));}
 private static JSONObject firstUserMessage(JSONObject session){JSONArray messages=session.optJSONArray("messages");if(messages!=null)for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m!=null&&m.optBoolean("user"))return m;}return null;}
 static String fallbackTitle(JSONObject message){
  String text=message.optString("text","").trim();
  JSONArray attachments=message.optJSONArray("attachments");JSONObject attachment=attachments==null?null:attachments.optJSONObject(0);
  boolean hasAttachment=message.has("attachment")||attachment!=null;
  if(text.isEmpty()||(hasAttachment&&"已添加附件".equals(text))){
   String name=message.optString("attachment","").trim();
   if(name.isEmpty()&&attachment!=null)name=attachment.optString("displayName","").trim();
   text=name.isEmpty()?"附件对话":"附件："+name;
  }
  return text.substring(0,text.offsetByCodePoints(0,Math.min(20,text.codePointCount(0,text.length()))));
 }
 @Override protected void onResume(){super.onResume();ChatGenerationService.attach(generationListener);if(generation!=null&&generation.finished)renderGeneration(generation);if(prefs!=null){refreshModelLabels();String id=prefs.getString("currentSessionId","");JSONArray all=readSessions();for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&id.equals(s.optString("id"))){JSONObject first=firstUserMessage(s);if(first!=null)requestTopicTitle(first.optString("text",""),id);break;}}}resumeAttachmentWork();}
 private void showConversationSettings(){
  SharedPreferences config=scoped();LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);ThemePalette palette=ThemePalette.resolve(this);
  TextView hint=label("仅应用于本对话。新对话使用设置中的默认值。",13,muted,false);hint.setPadding(0,0,0,dp(12));body.addView(hint);
  EditText prompt=EditorDialogs.editor(EditorDialogs.themedContext(this));prompt.setText(config.getString("systemPrompt",""));prompt.setHint("系统提示词");prompt.setMinLines(3);prompt.setTextSize(15);prompt.setBackground(UiStyle.surface(this,page,14,true));body.addView(prompt,new LinearLayout.LayoutParams(-1,-2));
  EditText output=EditorDialogs.editor(EditorDialogs.themedContext(this));output.setSingleLine(true);output.setText(config.getString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT));output.setInputType(InputType.TYPE_CLASS_NUMBER);body.addView(label("本对话输出上限 · token（按剩余上下文限制）",12,muted,false));body.addView(output,new LinearLayout.LayoutParams(-1,dp(52)));
  EditText temperature=EditorDialogs.editor(EditorDialogs.themedContext(this)),top=EditorDialogs.editor(EditorDialogs.themedContext(this));temperature.setSingleLine(true);top.setSingleLine(true);
  temperature.setText(config.getString("conversationTemperature","0.7"));top.setText(config.getString("conversationTopP","0.9"));
  boolean sampling=capabilities().sampling;temperature.setEnabled(sampling);top.setEnabled(sampling);
  for(int i=0;i<2;i++){TextView label=label(i==0?"温度 · 0–2":"Top P · 0–1",12,muted,false);label.setPadding(0,dp(14),0,dp(6));body.addView(label);EditText field=i==0?temperature:top;field.setTextSize(16);field.setBackground(UiStyle.surface(this,page,14,true));body.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));}
  if(!sampling){TextView note=label("此模型不支持采样参数，本对话不会发送温度和 Top P。",13,muted,false);body.addView(note);}
  ScrollView scroll=new ScrollView(this);scroll.addView(body);PanelDialog dialog=new PanelDialog(this,"对话设置","模型和思考强度也会随对话保存",scroll).compact();dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"保存",null).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int max=Integer.parseInt(output.getText().toString());int capacity=ModelCapabilities.positive(ModelCapabilities.find(activeProvider(),model),"contextLength",ContextBudget.DEFAULT_CONTEXT);if(max<=0||capacity<512){output.setError("请输入正整数；上下文容量至少 512，实际输出按剩余窗口分配");return;}if(sampling){ModelStore.range(temperature.getText().toString(),2);ModelStore.range(top.getText().toString(),1);}config.edit().putString("outputTokens",output.getText().toString()).putString("systemPrompt",prompt.getText().toString()).putString("conversationTemperature",temperature.getText().toString()).putString("conversationTopP",top.getText().toString()).apply();dialog.dismiss();}catch(Exception invalid){temperature.setError("请检查温度和 Top P 的范围");}});
 }
 private String usageText(JSONObject message){
  JSONObject usage=message.optJSONObject("usage");StringBuilder text=new StringBuilder();
  if(usage!=null&&usage.has("input")&&usage.has("output")){text.append("服务端用量：输入 ").append(usage.optLong("input")).append(" / 输出 ").append(usage.optLong("output")).append(" token");
   if(usage.has("cached"))text.append("\n其中缓存读取 ").append(usage.optLong("cached"));if(usage.has("reasoning"))text.append("\n其中思考 ").append(usage.optLong("reasoning"));
   JSONObject prices=message.optJSONObject("pricing");if(prices!=null)try{String in=prices.optString("inputPrice"),out=prices.optString("outputPrice");if(!in.isEmpty()&&!out.isEmpty()){double cost=(usage.optLong("input")*Double.parseDouble(in)+usage.optLong("output")*Double.parseDouble(out))/1000000d;text.append("\n按配置单价估算：").append(String.format(Locale.ROOT,"%.6f",cost)).append(" ").append(prices.optString("currency","USD")).append("\n未计工具费用、缓存折扣等，以供应商账单为准。");}}catch(Exception ignored){}
  }else text.append("供应商未返回完整用量");
  if(message.has("estimatedInputTokens"))text.append("\n发送上下文估算：").append(message.optInt("estimatedInputTokens")).append(" token（非精确分词）");
  if(message.optInt("omittedMessages")>0)text.append("\n本次省略较早的 ").append(message.optInt("omittedMessages")).append(" 条消息，原历史仍保留。");return text.toString();
 }
 private void showMessageDetails(JSONObject message){
  String time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(message.optLong("timestamp")));
  String state=message.optString("completionState",message.optBoolean("interrupted")?"interrupted":"complete");
  TextView details=label("时间："+time+(message.optBoolean("user")?"":"\n模型："+message.optString("modelName",message.optString("modelId"))+"\n状态："+new GenerationResult(state,message.optString("finishReason"),message.optJSONObject("usage")).label()+"\n\n"+usageText(message)+((message.optBoolean("error")||message.optBoolean("interrupted"))&&!message.optString("finishReason").isEmpty()?"\n\n错误 / 中断详情：\n"+message.optString("finishReason"):"")),14,ink,false);details.setTextIsSelectable(true);details.setPadding(dp(8),dp(8),dp(8),dp(16));ScrollView scroll=new ScrollView(this);scroll.addView(details);
  new PanelDialog(this,"消息详情","",scroll).compact().action(AlertDialog.BUTTON_POSITIVE,"完成",null).show();
 }
 private void showSessionDetails(JSONObject session){
  JSONArray messages=session.optJSONArray("messages");long input=0,output=0;int measured=0,answers=0;
  for(int i=0;messages!=null&&i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m==null||m.optBoolean("user"))continue;answers++;JSONObject u=m.optJSONObject("usage");if(u!=null&&u.has("input")&&u.has("output")){input+=u.optLong("input");output+=u.optLong("output");measured++;}}
  TextView text=label("共 "+(messages==null?0:messages.length())+" 条消息\n已有用量的回复："+measured+" / "+answers+"\n服务端输入累计："+input+" token\n服务端输出累计："+output+" token\n\n费用可在消息详情按模型单价估算，未返回用量的回复不计入。",14,ink,false);
  new PanelDialog(this,"对话用量",session.optString("title"),text).compact().action(AlertDialog.BUTTON_POSITIVE,"完成",null).show();
 }
 private void showHistoryManagement(){
  String[] options={showingArchived?"查看普通对话":"查看归档对话","批量归档","批量删除","导出完整备份","导入备份"};
  ChoiceDialogs.single(this,"历史管理",options,-1,which->{if(which==0){showingArchived=!showingArchived;historyBrowser.filter(historySearch.getText().toString(),showingArchived);}else if(which==1||which==2)bulkSessions(which==2);else if(which==3){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"OChat-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date())+".zip");startActivityForResult(intent,81);}else {Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");startActivityForResult(intent,82);}});
 }
 private void bulkSessions(boolean delete){
  JSONArray all=readSessions();List<String> ids=new ArrayList<>(),titles=new ArrayList<>();for(int i=0;i<all.length();i++){JSONObject session=all.optJSONObject(i);if(session!=null&&session.optBoolean("archived")==showingArchived&&ChatGenerationService.active(session.optString("id"))==null){ids.add(session.optString("id"));titles.add(session.optString("title","新对话"));}}
  if(ids.isEmpty()){Toast.makeText(this,"没有可管理的对话（生成中的对话暂不参与）",Toast.LENGTH_SHORT).show();return;}
  ChoiceDialogs.multiple(this,delete?"选择要删除的对话":"选择要归档的对话",titles.toArray(new String[0]),new boolean[ids.size()],checked->{
   Set<String> chosen=new HashSet<>();for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(ids.get(i));if(chosen.isEmpty())return;
   TextView hint=label((delete?"将永久删除":"将归档")+" "+chosen.size()+" 段对话。",15,ink,false);new PanelDialog(this,delete?"确认批量删除":"确认批量归档","",hint).compact().action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"确认",()->{
    JSONArray next=new JSONArray(),latest=readSessions();for(int i=0;i<latest.length();i++){JSONObject session=latest.optJSONObject(i);if(session==null)continue;boolean selected=chosen.contains(session.optString("id"))&&ChatGenerationService.active(session.optString("id"))==null;if(selected&&delete)continue;if(selected)try{session.put("archived",true);}catch(JSONException ignored){}next.put(session);}
    try{if(next.length()==0)next.put(newSession());}catch(JSONException ignored){}SessionRepository.get(prefs).replace(next);if(MessageEdits.findSession(next,prefs.getString("currentSessionId",""))==null)restoreSession(next.optJSONObject(0));refreshHistory();
   }).show();
  });
 }
 private void handleBackupResult(int request,int result,Intent data){
  if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();String snapshot=request==81?readSessions().toString():"";Toast.makeText(this,request==81?"正在导出对话和附件…":"正在读取备份…",Toast.LENGTH_SHORT).show();
  attachmentWorker.execute(()->{try{if(request==81){int missing=ConversationBackup.exportTo(this,uri,snapshot);runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,missing==0?"备份已导出，不包含 API Key；请妥善保管对话内容":"备份已导出；有 "+missing+" 项附件原文件不可用，已保留可用的发送快照",Toast.LENGTH_LONG).show();});}
   else {JSONArray imported=ConversationBackup.importFrom(this,uri);runOnUiThread(()->{if(isDestroyed())return;TextView hint=label("读取到 "+imported.length()+" 段对话及备份中的附件。将作为独立对话添加，已有对话会保留。供应商凭据需要在本机配置。",15,ink,false);new PanelDialog(this,"导入备份","",hint).compact().action(AlertDialog.BUTTON_NEGATIVE,"取消",()->attachmentWorker.execute(()->ConversationBackup.discard(this,imported))).action(AlertDialog.BUTTON_POSITIVE,"导入",()->{JSONArray all=readSessions();for(int i=0;i<imported.length();i++)all.put(imported.optJSONObject(i));SessionRepository.get(prefs).replace(all);refreshHistory();Toast.makeText(this,"已导入 "+imported.length()+" 段对话",Toast.LENGTH_SHORT).show();}).show();});}
  }catch(Exception failure){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"备份操作失败："+failure.getMessage(),Toast.LENGTH_LONG).show();});}});
 }
 private TextView iconButton(String t){TextView v=label(t,24,ink,false);v.setGravity(Gravity.CENTER);return v;}private TextView label(String t,float z,int c,boolean b){TextView v=new TextView(this);v.setText(t);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.DEFAULT,b?Typeface.BOLD:Typeface.NORMAL);return v;}private GradientDrawable roundDrawable(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}private void hideKeyboard(){((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(),0);}
}

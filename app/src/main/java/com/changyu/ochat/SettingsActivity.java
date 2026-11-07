package com.changyu.ochat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;

public class SettingsActivity extends AppCompatActivity {
 private SharedPreferences prefs;private JSONObject item;private EditText provider,api,key;private LinearLayout models;private ThemePalette palette;private boolean isNew;
 private final ChatApiClient.CancellationHandle cancellation=new ChatApiClient.CancellationHandle();
 private final ChatApiClient client=new ChatApiClient(cancellation);
 @Override protected void onCreate(Bundle state){
  ThemePalette.applyActivityTheme(this);super.onCreate(state);prefs=getSharedPreferences("chat",MODE_PRIVATE);ModelStore.migrate(prefs);palette=ThemePalette.resolve(this);
  getWindow().setStatusBarColor(palette.page);getWindow().setNavigationBarColor(palette.page);getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  String id=getIntent().getStringExtra("providerId");isNew=id==null;item=new JSONObject();
  try{
   JSONArray all=ModelStore.read(prefs);for(int i=0;i<all.length();i++){JSONObject existing=all.optJSONObject(i);if(existing!=null&&existing.optString("id").equals(id))item=new JSONObject(existing.toString());}
   if(isNew)item.put("id",java.util.UUID.randomUUID().toString()).put("models",new JSONArray()).put("selectedModelId","");
   else if(!item.has("id")){finish();return;}
  }catch(JSONException failure){finish();return;}
  setContentView(build());
 }
 @Override protected void onDestroy(){cancellation.cancel();super.onDestroy();}
 private View build(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.page);
  TextView back=text("‹  "+(isNew?"新建供应商":"编辑供应商"),21);back.setPadding(dp(18),dp(16),dp(18),dp(16));back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(ScreenInsets.TOP_BAR_DP)));
  ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),0,dp(18),dp(28));
  provider=field(item.optString("name",""));api=field(item.optString("api","https://api.openai.com/v1"));key=field(item.optString("apiKey",""));key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  labelled(body,"供应商名称",provider);labelled(body,"API 地址",api);labelled(body,"API Key",key);protocolControls(body);
  TextView heading=text("已配置模型",18);heading.setPadding(dp(4),dp(24),0,dp(4));body.addView(heading);
  TextView hint=text("点击模型查看详情或重命名",12);hint.setTextColor(palette.muted);body.addView(hint);
  models=new LinearLayout(this);models.setOrientation(LinearLayout.VERTICAL);body.addView(models);renderModels();
  button(body,"手动添加模型 ID",()->editModel(null));button(body,"获取模型 / 测试连接",this::fetchModels);
  LinearLayout footer=new LinearLayout(this);footer.setPadding(0,dp(20),0,0);
  TextView cancel=text("取消",16),save=text("保存供应商",16);cancel.setGravity(Gravity.CENTER);save.setGravity(Gravity.CENTER);
  UiStyle.clickable(cancel,palette.surface,16);UiStyle.clickable(save,palette.primary,16);save.setTextColor(palette.onPrimary);
  cancel.setOnClickListener(v->finish());save.setOnClickListener(v->saveProvider());
  LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(52),1);left.rightMargin=dp(10);footer.addView(cancel,left);footer.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(footer);
  if(!isNew)button(body,"删除供应商",()->ProviderDialogs.confirmDelete(this,item.optString("id"),this::finish));
  scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));ScreenInsets.apply(this,root);return root;
 }
 private void saveProvider(){
  if(provider.getText().toString().trim().isEmpty()){provider.setError("请输入供应商名称");return;}
  try{item.put("name",provider.getText().toString().trim()).put("api",api.getText().toString().trim()).put("apiKey",key.getText().toString());ModelStore.saveProvider(prefs,item);finish();}
  catch(JSONException failure){Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_SHORT).show();}
 }
 private void protocolControls(LinearLayout body){
  TextView label=text("接口协议",13);label.setTextColor(palette.muted);label.setPadding(dp(4),dp(16),0,dp(8));body.addView(label);
  ChoiceSpinner protocol=new ChoiceSpinner(this,"接口协议");
  protocol.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"OpenAI · Chat Completions","OpenAI · Responses","Anthropic · Messages","Google · Gemini"}));
  String[] protocols={ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC,ChatApiClient.GEMINI};int selected=java.util.Arrays.asList(protocols).indexOf(item.optString("protocol"));protocol.setSelection(Math.max(0,selected));
  protocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int index,long id){try{String previous=item.optString("protocol",ChatApiClient.CHAT_COMPLETIONS);item.put("protocol",protocols[index]);if(!previous.equals(protocols[index])){String current=api.getText().toString().trim();if(current.equals("https://api.openai.com/v1")||current.equals("https://api.anthropic.com/v1")||current.equals("https://generativelanguage.googleapis.com/v1beta"))api.setText(index==2?"https://api.anthropic.com/v1":index==3?"https://generativelanguage.googleapis.com/v1beta":"https://api.openai.com/v1");}}catch(JSONException ignored){}}
  });
  body.addView(protocol,new LinearLayout.LayoutParams(-1,dp(56)));
 }
 private void renderModels(){
  models.removeAllViews();JSONArray all=item.optJSONArray("models");
  if(all==null||all.length()==0){TextView empty=text("还没有模型，可手动添加或从接口获取",14);empty.setTextColor(palette.muted);empty.setPadding(dp(16),dp(18),dp(16),dp(18));empty.setBackground(UiStyle.surface(this,palette.surface,16,true));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);models.addView(empty,params);return;}
  for(int i=0;i<all.length();i++){
   JSONObject model=all.optJSONObject(i);if(model==null)continue;String id=model.optString("id");
   String name=model.optString("displayName",id);if(name.trim().isEmpty())name=id;
   boolean selected=id.equals(item.optString("selectedModelId"));
   View row=UiStyle.preference(this,name,(name.equals(id)?"":id+" · ")+"输出上限 "+model.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT)+(selected?" · 当前模型":""),selected,v->editModel(model));
   row.setContentDescription("模型详情 "+name);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);models.addView(row,params);
  }
 }
 private void editModel(JSONObject existing){
  JSONObject model=existing==null?ModelStore.model(""):existing;
  ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(4),0,dp(4),dp(12));
  EditText id=field(model.optString("id")),name=field(model.optString("displayName")),tokens=field(model.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT));tokens.setInputType(InputType.TYPE_CLASS_NUMBER);
  id.setContentDescription("模型 ID");name.setContentDescription("模型显示名称");tokens.setContentDescription("最大输出 token");
  labelled(body,"模型 ID",id);labelled(body,"显示名称",name);labelled(body,"最大输出 token（正整数，发送时按剩余上下文限制）",tokens);
  EditText context=field(model.optString("contextLength",ContextBudget.DEFAULT_CONTEXT_TEXT));context.setInputType(InputType.TYPE_CLASS_NUMBER);labelled(body,"上下文容量 token（按供应商文档填写）",context);
  ModelCapabilities caps=new ModelCapabilities(model.optString("id"),item.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),model);
  androidx.appcompat.widget.SwitchCompat automatic=new androidx.appcompat.widget.SwitchCompat(this);automatic.setText("按模型 ID 自动识别能力");automatic.setTextColor(palette.ink);automatic.setChecked(!caps.custom);body.addView(automatic,new LinearLayout.LayoutParams(-1,dp(52)));
  androidx.appcompat.widget.SwitchCompat sampling=new androidx.appcompat.widget.SwitchCompat(this);sampling.setText("支持温度和 Top P");sampling.setTextColor(palette.ink);sampling.setChecked(caps.sampling);body.addView(sampling,new LinearLayout.LayoutParams(-1,dp(52)));
  EditText levels=field(String.join(",",java.util.Arrays.copyOfRange(caps.efforts,1,caps.efforts.length)));labelled(body,"支持的思考档位（英文逗号分隔，留空仅自动）",levels);
  Runnable updateCapabilities=()->{sampling.setEnabled(!automatic.isChecked());levels.setEnabled(!automatic.isChecked());if(automatic.isChecked()){ModelCapabilities detected=new ModelCapabilities(id.getText().toString(),item.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),new JSONObject());sampling.setChecked(detected.sampling);levels.setText(String.join(",",java.util.Arrays.copyOfRange(detected.efforts,1,detected.efforts.length)));}};automatic.setOnCheckedChangeListener((button,checked)->updateCapabilities.run());id.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){updateCapabilities.run();}public void afterTextChanged(android.text.Editable e){}});updateCapabilities.run();
  TextView capabilityHint=text("自动根据模型 ID 识别档位。兼容网关需要自定义时关闭自动识别，可填写 none、enabled、minimal、low、medium、high、xhigh、max、ultra；enabled 仅用于思考开关。",12);capabilityHint.setTextColor(palette.muted);body.addView(capabilityHint);
  EditText inputPrice=field(model.optString("inputPrice","")),outputPrice=field(model.optString("outputPrice","")),currency=field(model.optString("currency","USD"));
  inputPrice.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);outputPrice.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
  labelled(body,"每百万输入 token 单价（选填）",inputPrice);labelled(body,"每百万输出 token 单价（选填）",outputPrice);labelled(body,"币种",currency);scroll.addView(body);
  PanelDialog dialog=new PanelDialog(this,existing==null?"添加模型":"模型详情 / 重命名","修改将在保存供应商后生效",scroll);
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null);
  if(existing!=null)dialog.action(AlertDialog.BUTTON_NEUTRAL,"删除模型",null);
  dialog.action(AlertDialog.BUTTON_POSITIVE,"保存",null);dialog.show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String value=id.getText().toString().trim();if(value.isEmpty()){id.setError("不能为空");return;}
   try{int output=Integer.parseInt(tokens.getText().toString().trim()),capacity=Integer.parseInt(context.getText().toString().trim());if(output<=0||capacity<512)throw new NumberFormatException();}catch(NumberFormatException failure){context.setError("上下文容量至少 512，输出上限须为正整数；实际输出按剩余窗口分配");return;}
   for(EditText price:new EditText[]{inputPrice,outputPrice})if(!price.getText().toString().trim().isEmpty())try{double amount=Double.parseDouble(price.getText().toString());if(!Double.isFinite(amount)||amount<0)throw new NumberFormatException();}catch(NumberFormatException invalid){price.setError("请输入非负单价");return;}
   String requested=levels.getText().toString().trim();for(String level:requested.split(","))if(!level.trim().isEmpty()&&!java.util.Arrays.asList("none","enabled","minimal","low","medium","high","xhigh","max","ultra").contains(level.trim())){levels.setError("包含未知档位，请使用说明中的英文名称");return;}
   try{
    JSONArray all=item.getJSONArray("models");for(int i=0;i<all.length();i++){JSONObject other=all.optJSONObject(i);if(other!=null&&other!=existing&&value.equals(other.optString("id"))){id.setError("ID 已存在");return;}}
    String old=model.optString("id");model.put("id",value).put("displayName",name.getText().toString().trim().isEmpty()?value:name.getText().toString().trim()).put("outputTokens",tokens.getText().toString().trim()).put("contextLength",context.getText().toString().trim()).put("capabilityMode",automatic.isChecked()?"auto":"custom").put("supportsSampling",sampling.isChecked()).put("reasoningLevels",requested).put("inputPrice",inputPrice.getText().toString().trim()).put("outputPrice",outputPrice.getText().toString().trim()).put("currency",currency.getText().toString().trim());
    if(existing==null)all.put(model);if(old.equals(item.optString("selectedModelId")))item.put("selectedModelId",value);
    ModelStore.fallback(item);renderModels();dialog.dismiss();
   }catch(JSONException failure){Toast.makeText(this,"模型保存失败",Toast.LENGTH_SHORT).show();}
  });
  if(existing!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->confirmDeleteModel(existing,dialog));
 }
 private void confirmDeleteModel(JSONObject model,PanelDialog editor){
  TextView message=text("删除此模型配置？",15);message.setPadding(dp(8),dp(12),dp(8),dp(12));
  PanelDialog confirm=new PanelDialog(this,"删除模型",model.optString("displayName",model.optString("id")),message).compact();
  confirm.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"删除",()->{
   try{JSONArray all=item.getJSONArray("models"),next=new JSONArray();for(int i=0;i<all.length();i++)if(all.optJSONObject(i)!=model)next.put(all.optJSONObject(i));item.put("models",next);ModelStore.fallback(item);renderModels();editor.dismiss();}
   catch(JSONException failure){Toast.makeText(this,"删除失败",Toast.LENGTH_SHORT).show();}
  });confirm.show();
 }
 private void fetchModels(){
  String base=api.getText().toString().trim(),secret=key.getText().toString().trim();
  if(!(base.startsWith("http://")||base.startsWith("https://"))){api.setError("请输入 http/https 地址");return;}
  if(secret.isEmpty()){key.setError("请填写 API Key");return;}
  client.models(base,secret,item.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),new ChatApiClient.CallbackResult<JSONArray>(){
   public void onSuccess(JSONArray data){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())showModels(data);});}
   public void onError(String message){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(SettingsActivity.this,message,Toast.LENGTH_LONG).show();});}
  });
 }
 private void showModels(JSONArray data){
  String[] ids=ModelStore.sortedFetchedIds(data);boolean[] checked=new boolean[ids.length];
  prefs.edit().putString("models_"+item.optString("id"),new JSONArray(java.util.Arrays.asList(ids)).toString()).apply();
  ChoiceDialogs.multiple(this,"添加接口中的模型",ids,checked,selected->{try{ModelStore.addChecked(item,ids,selected);renderModels();}catch(JSONException failure){Toast.makeText(this,"添加失败",Toast.LENGTH_SHORT).show();}});
 }
 private EditText field(String value){EditText input=new EditText(this);input.setText(value);input.setTextColor(palette.ink);input.setHintTextColor(palette.muted);input.setTextSize(16);input.setPadding(dp(14),0,dp(14),0);input.setBackground(UiStyle.surface(this,palette.surface,16,true));input.setSingleLine(true);return input;}
 private TextView text(String value,int size){return UiStyle.text(this,value,size,palette.ink,false);}
 private void labelled(LinearLayout body,String name,EditText input){TextView label=text(name,13);label.setTextColor(palette.muted);label.setPadding(dp(4),dp(16),0,dp(8));body.addView(label);body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));}
 private void button(LinearLayout body,String title,Runnable action){TextView view=text(title,16);view.setTextColor(palette.primary);view.setGravity(Gravity.CENTER);view.setPadding(dp(16),dp(16),dp(16),dp(16));UiStyle.clickable(view,palette.surface,16);view.setOnClickListener(v->action.run());LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);body.addView(view,params);}
 private int dp(int value){return UiStyle.dp(this,value);}
}

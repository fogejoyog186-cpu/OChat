package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import org.json.*;
import java.util.*;

/** Searchable configured-model list, grouped by provider inside the shared large panel. */
final class ModelPicker {
 static PanelDialog show(Context context,SharedPreferences prefs,Runnable selected,Runnable manage){
  ModelStore.migrate(prefs);JSONArray all=ModelStore.read(prefs);ThemePalette palette=ThemePalette.resolve(context);
  LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
  EditText search=new EditText(context);search.setSingleLine(true);search.setTextSize(15);
  search.setTextColor(palette.ink);search.setHintTextColor(palette.muted);
  search.setHint("搜索模型或供应商");search.setContentDescription("搜索模型或供应商");
  search.setPadding(dp(context,16),0,dp(context,16),0);search.setBackground(UiStyle.surface(context,palette.page,16,true));
  LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,dp(context,48));searchParams.bottomMargin=dp(context,8);body.addView(search,searchParams);
  FrameLayout results=new FrameLayout(context);ListView list=new ListView(context);
  list.setDivider(null);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
  list.setPadding(0,0,0,dp(context,8));list.setClipToPadding(false);list.setDrawSelectorOnTop(false);
  results.addView(list,new FrameLayout.LayoutParams(-1,-1));
  TextView empty=UiStyle.text(context,"还没有配置模型\n点击下方「管理模型」添加",15,palette.muted,false);
  empty.setGravity(Gravity.CENTER);empty.setPadding(dp(context,20),dp(context,24),dp(context,20),dp(context,24));
  results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
  body.addView(results,new LinearLayout.LayoutParams(-1,0,1));
  String[] chosen={prefs.getString("providerId",""),prefs.getString("modelId","")};
  List<JSONObject[]> entries=new ArrayList<>();
  BaseAdapter adapter=new BaseAdapter(){
   @Override public int getCount(){return entries.size();}
   @Override public Object getItem(int position){return entries.get(position);}
   @Override public long getItemId(int position){return position;}
   @Override public int getViewTypeCount(){return 2;}
   @Override public int getItemViewType(int position){return entries.get(position)[1]==null?0:1;}
   @Override public boolean areAllItemsEnabled(){return false;}
   @Override public boolean isEnabled(int position){return entries.get(position)[1]!=null;}
   @Override public View getView(int position,View convert,ViewGroup parent){
    JSONObject[] item=entries.get(position);JSONObject provider=item[0],model=item[1];
    if(model==null){
     TextView heading=convert instanceof TextView?(TextView)convert:UiStyle.text(context,"",12,palette.muted,true);
     heading.setText(provider.optString("name","供应商"));heading.setPadding(dp(context,12),dp(context,16),dp(context,12),dp(context,8));
     androidx.core.view.ViewCompat.setAccessibilityHeading(heading,true);
     return heading;
    }
    LinearLayout row;
    if(convert instanceof LinearLayout)row=(LinearLayout)convert;else{
     row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(context,14),dp(context,12),dp(context,12),dp(context,12));row.setMinimumHeight(dp(context,60));
     LinearLayout copy=new LinearLayout(context);copy.setOrientation(LinearLayout.VERTICAL);
     TextView title=UiStyle.text(context,"",16,palette.ink,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);
     TextView id=UiStyle.text(context,"",12,palette.muted,false);id.setSingleLine(true);id.setEllipsize(TextUtils.TruncateAt.MIDDLE);id.setPadding(0,dp(context,4),0,0);copy.addView(id);
     row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
     TextView check=UiStyle.text(context,"✓",18,palette.primary,true);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(context,36),dp(context,36)));
    }
    boolean active=provider.optString("id").equals(chosen[0])&&model.optString("id").equals(chosen[1]);
    String id=model.optString("id"),name=model.optString("displayName",id);if(name.trim().isEmpty())name=id;
    LinearLayout copy=(LinearLayout)row.getChildAt(0);TextView title=(TextView)copy.getChildAt(0),detail=(TextView)copy.getChildAt(1);
    UiStyle.textIfChanged(title,name);title.setTextColor(active?palette.primary:palette.ink);UiStyle.textIfChanged(detail,id);detail.setVisibility(name.equals(id)?View.GONE:View.VISIBLE);
    row.getChildAt(1).setVisibility(active?View.VISIBLE:View.INVISIBLE);
    row.setSelected(false);row.setActivated(active);row.setContentDescription(name+(name.equals(id)?"":"，"+id)+(active?"，当前模型":""));
    UiStyle.clickable(row,active?ColorUtils.blendARGB(palette.surface,palette.primary,.09f):palette.surface,16,false);
    return row;
   }
  };
  Runnable filter=()->{
   String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);entries.clear();
   for(int i=0;i<all.length();i++){
    JSONObject provider=all.optJSONObject(i);JSONArray models=provider==null?null:provider.optJSONArray("models");if(models==null)continue;
    boolean headingAdded=false;
    for(int j=0;j<models.length();j++){
     JSONObject model=models.optJSONObject(j);if(model==null||model.optString("id").trim().isEmpty())continue;
     String terms=provider.optString("name")+" "+model.optString("displayName")+" "+model.optString("id");
     if(!query.isEmpty()&&!terms.toLowerCase(Locale.ROOT).contains(query))continue;
     if(!headingAdded){entries.add(new JSONObject[]{provider,null});headingAdded=true;}
     entries.add(new JSONObject[]{provider,model});
    }
   }
   empty.setText(query.isEmpty()?"还没有配置模型\n点击下方「管理模型」添加":"没有找到匹配的模型");
   adapter.notifyDataSetChanged();list.setSelection(0);
  };
  list.setAdapter(adapter);filter.run();
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});
  PanelDialog dialog=new PanelDialog(context,"选择模型","按供应商分组 · 确认后切换模型",body);
  dialog.action(android.app.AlertDialog.BUTTON_NEGATIVE,"取消",null).action(android.app.AlertDialog.BUTTON_NEUTRAL,"管理模型",manage).action(android.app.AlertDialog.BUTTON_POSITIVE,"确认",null);
  list.setOnItemClickListener((parent,view,position,id)->{
   JSONObject[] item=entries.get(position);if(item[1]==null)return;
   chosen[0]=item[0].optString("id");chosen[1]=item[1].optString("id");adapter.notifyDataSetChanged();dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
  });
  body.setFocusableInTouchMode(true);body.requestFocus();
  dialog.show();
  boolean configured=false;for(int i=0;i<all.length();i++){JSONObject provider=all.optJSONObject(i);if(provider!=null&&chosen[0].equals(provider.optString("id"))){JSONArray models=provider.optJSONArray("models");if(models!=null)for(int j=0;j<models.length();j++){JSONObject model=models.optJSONObject(j);if(model!=null&&chosen[1].equals(model.optString("id")))configured=true;}}}
  dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(configured);
  dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try{JSONArray latest=ModelStore.read(prefs);for(int i=0;i<latest.length();i++){JSONObject provider=latest.optJSONObject(i);if(provider==null||!chosen[0].equals(provider.optString("id")))continue;JSONArray models=provider.optJSONArray("models");for(int j=0;models!=null&&j<models.length();j++){JSONObject model=models.optJSONObject(j);if(model!=null&&chosen[1].equals(model.optString("id"))){provider.put("selectedModelId",chosen[1]);ModelStore.activate(prefs,latest,provider);selected.run();dialog.dismiss();return;}}}
    Toast.makeText(context,"模型配置已变化，请重新选择",Toast.LENGTH_SHORT).show();
   }catch(JSONException failure){Toast.makeText(context,"模型切换失败，请重试",Toast.LENGTH_SHORT).show();}
  });
  for(int i=0;i<entries.size();i++){JSONObject[] item=entries.get(i);if(item[1]!=null&&item[0].optString("id").equals(prefs.getString("providerId",""))&&item[1].optString("id").equals(prefs.getString("modelId",""))){list.setSelection(Math.max(0,i-1));break;}}
  return dialog;
 }
 private static int dp(Context context,int value){return UiStyle.dp(context,value);}
 private ModelPicker(){}
}

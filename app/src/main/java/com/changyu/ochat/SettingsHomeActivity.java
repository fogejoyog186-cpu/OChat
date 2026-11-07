package com.changyu.ochat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsHomeActivity extends AppCompatActivity {
    private int ink=Color.rgb(36,37,43), muted=Color.rgb(119,121,131), purple=Color.rgb(103,80,164), page=Color.rgb(250,248,252);
    private SharedPreferences prefs;
    private boolean resumedOnce;
    @Override protected void onCreate(Bundle state) { ThemePalette.applyActivityTheme(this); super.onCreate(state); ThemePalette p=ThemePalette.resolve(this); ink=p.ink; muted=p.muted; purple=p.primary; page=p.page; getWindow().setStatusBarColor(p.page); getWindow().setNavigationBarColor(p.page); getWindow().getDecorView().setSystemUiVisibility(p.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); prefs=getSharedPreferences("chat",MODE_PRIVATE); setContentView(build()); }
    @Override protected void onResume() { super.onResume(); if (resumedOnce && prefs != null) { ThemePalette p=ThemePalette.resolve(this); ink=p.ink; muted=p.muted; purple=p.primary; page=p.page; setContentView(build()); } resumedOnce=true; }
    private View build() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(page);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(12),dp(10),dp(16),dp(8)); TextView back=icon("‹",32); back.setContentDescription("返回"); back.setOnClickListener(v->finish()); bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44))); bar.addView(text("设置",21,ink,true),new LinearLayout.LayoutParams(0,-2,1)); root.addView(bar,new LinearLayout.LayoutParams(-1,dp(ScreenInsets.TOP_BAR_DP)));
        ScrollView scroll=new ScrollView(this); LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18),dp(8),dp(18),dp(28)); body.addView(text("偏好与功能",13,muted,true));
        addCard(body,"模型与供应商","管理已创建的供应商和模型",v->startActivity(new Intent(this,ProviderListActivity.class)));
        addCard(body,"对话设置","全局对话默认参数与初始系统提示词",v->open("conversation"));
        String searchMode=prefs.getString(SearchOptions.PREF,SearchOptions.AUTO);String searchSummary=SearchOptions.RESPONSES.equals(searchMode)?"Responses内置搜索 · 无需 Tavily Key":SearchOptions.TAVILY.equals(searchMode)?(prefs.getString("tavilyKey","").trim().isEmpty()?"Tavily · Key 未配置":"Tavily · Key 已配置"):"自动 · 按供应商协议选择";
        addCard(body,"联网搜索",searchSummary,v->open("search"));
        addCard(body,"主题设置",themeSummary(),v->open("theme"));
        addCard(body,"话题命名模型",prefs.getBoolean("topicEnabled",false)?topicSummary():"未启用",v->open("topic"));
        body.addView(text("设置会保存在本机，重启应用后仍然有效。",12,muted,false)); TextView surprise=text("千万别点",16,ink,true); surprise.setGravity(Gravity.CENTER_VERTICAL); surprise.setPadding(dp(16),dp(14),dp(12),dp(14)); surprise.setBackground(round(ThemePalette.resolve(this).surface,16)); surprise.setOnClickListener(v->randomizeTheme()); LinearLayout.LayoutParams surpriseParams=new LinearLayout.LayoutParams(-1,dp(56)); surpriseParams.setMargins(0,dp(16),0,0); body.addView(surprise,surpriseParams); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); ScreenInsets.apply(this,root); return root;
    }
    private String topicSummary(){String id=prefs.getString("topicProviderId","");if("__current__".equals(id))return "当前聊天供应商 · "+prefs.getString("topicModel","");try{org.json.JSONArray providers=new org.json.JSONArray(prefs.getString("providers","[]"));for(int i=0;i<providers.length();i++){org.json.JSONObject item=providers.optJSONObject(i);if(item!=null&&(id.equals(item.optString("id"))||(!id.isEmpty()&&id.equals(item.optString("name")))))return item.optString("name","供应商")+" · "+prefs.getString("topicModel","");}}catch(Exception ignored){}return prefs.getString("topicProvider","供应商");}
    private void open(String mode){ if ("models".equals(mode)) { startActivity(new Intent(this,SettingsActivity.class)); } else { Intent i=new Intent(this,SettingsOptionActivity.class); i.putExtra("mode",mode); startActivity(i); } }
    private void randomizeTheme() {
        Random random=new Random();
        SharedPreferences.Editor editor=prefs.edit();
        for (String key:new String[]{"themePrimary","themeBackground","themeSurface","themeText"}) {
            editor.putString(key,String.format(Locale.ROOT,"#%06X",random.nextInt(0x1000000)));
        }
        editor.apply();
        Toast.makeText(this,"随机配色已开启，可在主题设置恢复默认",Toast.LENGTH_SHORT).show();
        Intent intent=new Intent(this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
    private String themeSummary(){String v=prefs.getString("themeMode","system"); return "light".equals(v)?"浅色":"dark".equals(v)?"深色":"跟随系统";}
    private void addCard(LinearLayout body,String title,String summary,View.OnClickListener listener){
        View row=UiStyle.preference(this,title,summary,false,listener);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,dp(12),0,0);body.addView(row,params);
    }
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.DEFAULT,b?Typeface.BOLD:Typeface.NORMAL);return v;}
    private TextView icon(String s,int z){TextView v=text(s,z,ink,false);v.setGravity(Gravity.CENTER);return v;}
    private GradientDrawable round(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}

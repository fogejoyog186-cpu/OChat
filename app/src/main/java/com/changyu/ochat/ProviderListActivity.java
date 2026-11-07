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

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProviderListActivity extends AppCompatActivity {
    private int ink = Color.rgb(36,37,43), muted = Color.rgb(119,121,131), purple = Color.rgb(103,80,164), page = Color.rgb(250,248,252);
    private SharedPreferences prefs;
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        ThemePalette.applyActivityTheme(this);
        super.onCreate(state);
        prefs = getSharedPreferences("chat", MODE_PRIVATE);
        ModelStore.migrate(prefs);
        ThemePalette palette=ThemePalette.resolve(this); ink=palette.ink; muted=palette.muted; purple=palette.primary; page=palette.page; getWindow().setStatusBarColor(palette.page); getWindow().setNavigationBarColor(palette.page); getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(build());
    }

    @Override protected void onResume() { super.onResume(); if (list != null) renderProviders(); }

    private View build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(page);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(12),dp(10),dp(16),dp(8));
        TextView back = icon("‹",32); back.setContentDescription("返回"); back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44),dp(44)));
        bar.addView(text("模型与供应商",21,ink,true), new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar, new LinearLayout.LayoutParams(-1,dp(ScreenInsets.TOP_BAR_DP)));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18),dp(8),dp(18),dp(28));
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        renderProviders();
        ScreenInsets.apply(this,root);
        return root;
    }

    private void renderProviders() {
        list.removeAllViews();
        list.addView(text("已创建的供应商",13,muted,true));
        JSONArray providers = readProviders();
        String active = prefs.getString("providerId", "");
        for (int i=0; i<providers.length(); i++) {
            JSONObject item = providers.optJSONObject(i); if (item == null) continue;
            String id = item.optString("id");
            String title = item.optString("name", "未命名供应商");
            String summary = (id.equals(active) ? "当前使用 · " : "") + item.optString("api", "");
            View card=UiStyle.preference(this,title,summary,id.equals(active),v->openEditor(id));
            card.setOnLongClickListener(v->{ChoiceDialogs.single(this,"供应商操作",new String[]{"编辑供应商","删除供应商"},-1,which->{if(which==0)openEditor(id);else ProviderDialogs.confirmDelete(this,id,this::renderProviders);});return true;});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);list.addView(card,p);
        }
        TextView add = text("＋ 新建供应商",15,purple,true); add.setGravity(Gravity.CENTER); add.setBackground(round(ThemePalette.resolve(this).surface,18));
        add.setOnClickListener(v -> openEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1,dp(52)); addParams.setMargins(0,dp(18),0,0); list.addView(add,addParams);
    }

    private void openEditor(String id) { Intent intent = new Intent(this, SettingsActivity.class); if (id != null) intent.putExtra("providerId",id); else intent.putExtra("newProvider",true); startActivity(intent); }

    private JSONArray readProviders() { try { return new JSONArray(prefs.getString("providers","[]")); } catch (Exception e) { return new JSONArray(); } }
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.DEFAULT,b?Typeface.BOLD:Typeface.NORMAL);return v;}
    private TextView icon(String s,int z){TextView v=text(s,z,ink,false);v.setGravity(Gravity.CENTER);return v;}
    private GradientDrawable round(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}

package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;

/** Large artwork canvas with a separate, literal source inspector. */
final class CodePreview {
 static PanelDialog show(Context context,String raw,String language){
  ThemePalette p=ThemePalette.resolve(context);boolean svg="svg".equalsIgnoreCase(language);
  LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
  LinearLayout toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(0,0,0,dp(context,12));
  LinearLayout tabs=new LinearLayout(context);tabs.setPadding(dp(context,4),dp(context,4),dp(context,4),dp(context,4));
  tabs.setBackground(UiStyle.surface(context,p.page,16,false));
  TextView preview=tab(context,"图形预览",p),source=tab(context,"源码",p);
  tabs.addView(preview,new LinearLayout.LayoutParams(dp(context,100),dp(context,40)));
  tabs.addView(source,new LinearLayout.LayoutParams(dp(context,72),dp(context,40)));toolbar.addView(tabs);
  toolbar.addView(new Space(context),new LinearLayout.LayoutParams(0,1,1));
  TextView reset=tab(context,"重置",p);reset.setContentDescription("重置预览缩放");toolbar.addView(reset,new LinearLayout.LayoutParams(dp(context,56),dp(context,48)));
  body.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
  FrameLayout stage=new FrameLayout(context);stage.setBackground(UiStyle.surface(context,p.page,18,true));stage.setClipToOutline(true);
  WebView web=new WebView(context);web.setContentDescription((svg?"SVG":"HTML")+" 预览内容");
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(false);settings.setDomStorageEnabled(false);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setSupportZoom(true);
  settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setLoadWithOverviewMode(true);settings.setUseWideViewPort(true);
  web.setBackgroundColor(Color.WHITE);
  TextView loading=UiStyle.text(context,"正在载入静态预览…",13,p.muted,false);loading.setGravity(Gravity.CENTER);stage.addView(loading,new FrameLayout.LayoutParams(-1,-1));
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView view,String url){view.postVisualStateCallback(1,new WebView.VisualStateCallback(){@Override public void onComplete(long id){loading.setVisibility(View.GONE);view.setTag("preview-ready");}});}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())loading.setText("预览载入失败，请检查源码或重置预览");}
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    String scheme=request.getUrl().getScheme();return !("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)||"about".equalsIgnoreCase(scheme)||"data".equalsIgnoreCase(scheme)||"blob".equalsIgnoreCase(scheme));
   }
  });
  String document=MarkdownRenderer.sanitizePreviewDocument(raw);
  Runnable load=()->{loading.setVisibility(View.VISIBLE);web.setTag(null);web.setInitialScale(0);web.loadDataWithBaseURL("https://preview.invalid/",document,"text/html","UTF-8",null);};
  stage.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
  ScrollView vertical=new ScrollView(context);vertical.setFillViewport(true);vertical.setBackgroundColor(0xff18242e);
  HorizontalScrollView horizontal=new HorizontalScrollView(context);
  TextView code=UiStyle.text(context,"",12,0xffdce6ed,false);code.setText(CodeBlockView.highlight(raw));code.setTypeface(Typeface.MONOSPACE);
  code.setTextIsSelectable(true);code.setHorizontallyScrolling(true);code.setLineSpacing(dp(context,3),1);
  code.setPadding(dp(context,18),dp(context,18),dp(context,18),dp(context,18));code.setContentDescription("完整源码");
  horizontal.addView(code,new HorizontalScrollView.LayoutParams(-2,-2));
  vertical.addView(horizontal,new ScrollView.LayoutParams(-1,-2));stage.addView(vertical,new FrameLayout.LayoutParams(-1,-1));
  vertical.setVisibility(View.GONE);body.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
  java.util.function.Consumer<Boolean> select=showSource->{
   vertical.setVisibility(showSource?View.VISIBLE:View.GONE);web.setVisibility(showSource?View.GONE:View.VISIBLE);
   reset.setVisibility(showSource?View.INVISIBLE:View.VISIBLE);
   for(TextView tab:new TextView[]{preview,source}){
    boolean selected=tab==(showSource?source:preview);tab.setSelected(selected);
    tab.setTextColor(selected?p.primary:p.muted);
    UiStyle.clickable(tab,selected?p.surface:p.page,12);
   }
  };
  preview.setOnClickListener(v->select.accept(false));source.setOnClickListener(v->select.accept(true));reset.setOnClickListener(v->load.run());
  select.accept(false);load.run();
  PanelDialog dialog=new PanelDialog(context,svg?"SVG 预览":"网页预览",svg?"静态 SVG · 双指缩放查看细节":"静态 HTML · 不执行 JavaScript，交互功能不可用",body);
  dialog.action(AlertDialog.BUTTON_NEUTRAL,"复制源码",null).action(AlertDialog.BUTTON_POSITIVE,"完成",null);
  dialog.setOnDismissListener(d->{web.stopLoading();web.destroy();});dialog.show();
  dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
   ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
   if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("代码",raw));Toast.makeText(context,"源码已复制",Toast.LENGTH_SHORT).show();}
  });
  return dialog;
 }
 private static TextView tab(Context context,String text,ThemePalette p){TextView view=UiStyle.text(context,text,13,p.primary,true);view.setGravity(Gravity.CENTER);return view;}
 private static int dp(Context context,int value){return UiStyle.dp(context,value);}
 private CodePreview(){}
}

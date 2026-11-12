package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.webkit.*;
import android.widget.LinearLayout;

/** Display the provider's Search entry point without scripts, local files, or network subresources. */
final class GoogleSearchSuggestions {
 static PanelDialog show(Context context,String html){
  WebView web=new WebView(context);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setBlockNetworkLoads(true);
  web.setLayoutParams(new LinearLayout.LayoutParams(-1,UiStyle.dp(context,180)));
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){SafeLinks.open(context,request.getUrl().toString());return true;}
   @Override public boolean shouldOverrideUrlLoading(WebView view,String url){SafeLinks.open(context,url);return true;}
  });
  PanelDialog dialog=new PanelDialog(context,"Google 搜索建议",null,web).compact().action(AlertDialog.BUTTON_POSITIVE,"完成",null);
  dialog.setOnDismissListener(d->{web.stopLoading();web.loadUrl("about:blank");web.destroy();});dialog.show();web.loadDataWithBaseURL("https://www.google.com/",html,"text/html","UTF-8",null);return dialog;
 }
 private GoogleSearchSuggestions(){}
}

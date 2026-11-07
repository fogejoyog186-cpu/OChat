package com.changyu.ochat;

import android.content.*;
import android.net.Uri;
import android.widget.Toast;

final class SafeLinks {
 static Intent intent(String value){if(!MarkdownRenderer.isSafeLink(value))return null;return new Intent(Intent.ACTION_VIEW,Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE);}
 static void open(Context context,String value){Intent i=intent(value);if(i==null){Toast.makeText(context,"链接地址不安全或无效",Toast.LENGTH_SHORT).show();return;}try{context.startActivity(i);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(context,"没有可打开链接的应用",Toast.LENGTH_SHORT).show();}}
}

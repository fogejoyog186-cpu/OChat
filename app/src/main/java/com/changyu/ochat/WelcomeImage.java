package com.changyu.ochat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import java.io.InputStream;

/** Shared, bounded image loading with a safe fallback for revoked document permissions. */
final class WelcomeImage {
 static ImageView create(Context context, String uri, String shape) {
  if(uri==null||uri.isEmpty())return null;
  try {
   Uri document=Uri.parse(uri);
   BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
   try(InputStream stream=context.getContentResolver().openInputStream(document)){BitmapFactory.decodeStream(stream,null,options);}
   if(options.outWidth<=0||options.outHeight<=0)return null;
   options.inSampleSize=1;
   while(options.outWidth/options.inSampleSize>512||options.outHeight/options.inSampleSize>512)options.inSampleSize*=2;
   options.inJustDecodeBounds=false;
   Bitmap bitmap;
   try(InputStream stream=context.getContentResolver().openInputStream(document)){bitmap=BitmapFactory.decodeStream(stream,null,options);}
   if(bitmap==null)return null;
   ImageView image=new ImageView(context);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
   image.setContentDescription("自定义欢迎图标");
   image.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){
    float radius="square".equals(shape)?8*context.getResources().getDisplayMetrics().density:Math.min(view.getWidth(),view.getHeight())/2f;
    outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),radius);
   }});image.setClipToOutline(true);return image;
  }catch(Exception e){return null;}
 }
 private WelcomeImage(){}
}

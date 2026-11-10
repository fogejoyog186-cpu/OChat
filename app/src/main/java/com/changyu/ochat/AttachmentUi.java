package com.changyu.ochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** One attachment presentation for the composer, conversation and preview panels. */
final class AttachmentUi implements AutoCloseable {
 private final Activity owner;private final ThemePalette palette;
 private final ExecutorService thumbnails=Executors.newFixedThreadPool(2),previews=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){
  @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
 };
 private volatile boolean closed;private PanelDialog activePreview;private int previewVersion;private Future<?> markupWork;

 AttachmentUi(Activity owner){this.owner=owner;palette=ThemePalette.resolve(owner);}
 static boolean isImage(JSONObject file){
  String mime=file.optString("mime"),name=file.optString("displayName").toLowerCase(Locale.ROOT);
  if(mime.contains("svg")||name.endsWith(".svg"))return false;
  return mime.startsWith("image/")||"image".equals(file.optString("type"))||name.matches(".*\\.(png|jpe?g|webp|gif|bmp|avif|heic|heif)$");
 }
 View message(JSONObject file,Runnable open){
  if(isImage(file)){Photo photo=new Photo(false,false);photo.setOnClickListener(v->open.run());load(photo,file,1024);return photo;}
  return fileCard(file,false,open);
 }
 View pending(JSONObject file,boolean preparing,Runnable open,Runnable remove){
  boolean image=isImage(file);FrameLayout tile=new FrameLayout(owner);tile.setTag("pending-attachment");
  tile.setLayoutParams(new LinearLayout.LayoutParams(dp(image?96:248),dp(96)));
  View content;
  if(image){Photo photo=new Photo(true,preparing);photo.setOnClickListener(v->open.run());load(photo,file,256);content=photo;}
  else{content=fileCard(file,true,open);if(preparing){ProgressBar busy=spinner(16);((ViewGroup)content).addView(busy,new LinearLayout.LayoutParams(dp(16),dp(16)));}}
  FrameLayout.LayoutParams contentParams=new FrameLayout.LayoutParams(image?dp(88):-1,dp(88),Gravity.CENTER_VERTICAL|Gravity.START);
  if(!image)contentParams.rightMargin=dp(4);tile.addView(content,contentParams);
  ImageButton close=new ImageButton(owner);close.setImageDrawable(new Icon(image?Color.WHITE:palette.muted,true,dp(20)));close.setScaleType(ImageView.ScaleType.CENTER);
  close.setPadding(dp(12),dp(12),dp(12),dp(12));close.setContentDescription("移除附件 "+file.optString("displayName","附件"));
  UiStyle.clickable(close,image?0xB3243237:palette.surface,12,false);
  close.setBackground(new android.graphics.drawable.InsetDrawable(close.getBackground(),dp(10)));
  close.setOnClickListener(v->remove.run());
  tile.addView(close,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END));return tile;
 }
 private View fileCard(JSONObject file,boolean compact,Runnable open){
  LinearLayout card=new LinearLayout(owner);card.setTag("attachment-file");card.setGravity(Gravity.CENTER_VERTICAL);
  card.setPadding(dp(12),dp(12),dp(compact?32:12),dp(12));card.setMinimumHeight(dp(72));
  UiStyle.clickable(card,palette.surface,16);card.setOnClickListener(v->open.run());
  ImageView icon=new AppCompatImageView(owner);icon.setImageDrawable(new Icon(palette.primary,false,dp(24)));icon.setScaleType(ImageView.ScaleType.CENTER);
  icon.setBackground(UiStyle.surface(owner,ColorUtils.blendARGB(palette.surface,palette.primary,.08f),10,false));
  card.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(40)));
  LinearLayout labels=new LinearLayout(owner);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,0,0);
  String name=file.optString("displayName","文件");
  TextView title=UiStyle.text(owner,name,14,palette.ink,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
  labels.addView(title,new LinearLayout.LayoutParams(-1,-2));
  TextView type=UiStyle.text(owner,fileType(file),11,palette.muted,false);type.setPadding(0,dp(3),0,0);labels.addView(type);
  card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.setContentDescription(name);return card;
 }
 private static String fileType(JSONObject file){
  String name=file.optString("displayName"),mime=file.optString("mime");int dot=name.lastIndexOf('.');
  if(dot>=0&&name.length()-dot<=9)return name.substring(dot+1).toUpperCase(Locale.ROOT);
  if(mime.startsWith("text/"))return "文本文档";return "文件";
 }
 private void load(Photo photo,JSONObject file,int size){
  String key=file.optString("uri")+":"+size;photo.key=key;Bitmap known=cache.get(key);
  if(known!=null){photo.picture(known);return;}
  WeakReference<Photo> target=new WeakReference<>(photo);Uri uri=Uri.parse(file.optString("uri"));
  photo.work=thumbnails.submit(()->{
   try{
    Bitmap bitmap=AttachmentBatch.decode(owner.getContentResolver(),uri,size);
    if(closed||Thread.currentThread().isInterrupted())return;cache.put(key,bitmap);
    main.post(()->{Photo view=target.get();if(!closed&&view!=null&&key.equals(view.key))view.picture(bitmap);});
   }catch(Exception error){main.post(()->{Photo view=target.get();if(!closed&&view!=null&&key.equals(view.key))view.failed();});}
  });
 }
 PanelDialog preview(JSONObject file){
  if(file==null){toast("附件不可用");return null;}
  Uri uri=Uri.parse(file.optString("uri"));if(!"content".equals(uri.getScheme())){toast("附件地址不可用，请重新选择");return null;}
  int version=++previewVersion;if(markupWork!=null)markupWork.cancel(true);
  if(activePreview!=null)activePreview.dismiss();
  String mime=file.optString("mime"),name=file.optString("displayName","文件"),lower=name.toLowerCase(Locale.ROOT);
  if(mime.contains("svg")||lower.endsWith(".svg")||mime.contains("html")||lower.endsWith(".html")||lower.endsWith(".htm")){
   markupWork=previews.submit(()->{try{String text=readText(uri,512*1024+1);if(text.length()>512*1024)throw new IOException();
    main.post(()->{if(alive()&&version==previewVersion)MarkdownRenderer.showPreview(owner,text,lower.endsWith(".svg")||mime.contains("svg")?"svg":"html");});
   }catch(Exception e){main.post(()->toast("无法读取附件，请重新选择"));}});return null;
  }
  if(isImage(file))return previewImage(uri);
  if(AttachmentBatch.isPdf(mime,name))return previewPdf(file,uri);
  boolean office=lower.matches(".*\\.(docx|pptx|xlsx|rtf)$");
  if(office||mime.startsWith("text/")||lower.matches(".*\\.(txt|md|csv|log|json|xml|java|kt|py|js|ts|css|yaml|yml)$")||mime.contains("json")||mime.contains("xml"))return previewText(file,uri,office);
  openExternal(file,uri);return null;
 }
 private FrameLayout stage(){FrameLayout stage=new FrameLayout(owner);stage.setBackgroundColor(palette.page);return stage;}
 private ProgressBar spinner(int size){
  ProgressBar spinner=new ProgressBar(owner);spinner.setIndeterminateTintList(ColorStateList.valueOf(palette.primary));
  spinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);spinner.setMinimumHeight(dp(size));spinner.setMinimumWidth(dp(size));return spinner;
 }
 private PanelDialog previewImage(Uri uri){
  FrameLayout stage=stage();AttachmentImageView picture=new AttachmentImageView(owner);stage.addView(picture,new FrameLayout.LayoutParams(-1,-1));
  ProgressBar busy=spinner(28);stage.addView(busy,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));
  PanelDialog dialog=new PanelDialog(owner,"图片",null,stage);Future<?>[] work={null};show(dialog,()->{if(work[0]!=null)work[0].cancel(true);picture.setImageDrawable(null);});
  work[0]=previews.submit(()->{try{Bitmap bitmap=AttachmentBatch.decode(owner.getContentResolver(),uri,1600);
   main.post(()->{if(alive()&&dialog.isShowing()){picture.setPicture(bitmap);busy.setVisibility(View.GONE);}});
  }catch(Exception e){main.post(()->{if(alive()&&dialog.isShowing()){busy.setVisibility(View.GONE);error(stage,"图片不可用，请重新选择");}});}});return dialog;
 }
 private PanelDialog previewText(JSONObject file,Uri uri,boolean office){
  FrameLayout stage=stage();ProgressBar busy=spinner(28);stage.addView(busy,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));
  PanelDialog dialog=new PanelDialog(owner,file.optString("displayName","文件"),fileType(file),stage);
  dialog.action(AlertDialog.BUTTON_NEUTRAL,"其他应用打开",()->openExternal(file,uri));
  Future<?>[] work={null};show(dialog,()->{if(work[0]!=null)work[0].cancel(true);});
  work[0]=previews.submit(()->{try{
   String text;
   if(office){try(InputStream input=owner.getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("文件不可读取");text=AttachmentBatch.extractDocumentText(input,file.optString("mime"),file.optString("displayName"));}}
   else text=readText(uri,64001);
   boolean truncated=text.length()>64000;String content=truncated?text.substring(0,64000)+"\n\n… 已显示前 64,000 字符":text;
   main.post(()->{if(!alive()||!dialog.isShowing())return;stage.removeAllViews();
    TextView body=UiStyle.text(owner,content,14,palette.ink,false);body.setTextIsSelectable(true);body.setLineSpacing(dp(3),1.12f);body.setPadding(dp(16),dp(16),dp(16),dp(24));
    ScrollView scroll=new ScrollView(owner);scroll.addView(body);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
   });
  }catch(Exception e){main.post(()->{if(alive()&&dialog.isShowing()){busy.setVisibility(View.GONE);error(stage,"无法读取此文件，可使用其他应用打开");}});}});return dialog;
 }
 private PanelDialog previewPdf(JSONObject file,Uri uri){
  LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
  FrameLayout stage=stage();AttachmentImageView picture=new AttachmentImageView(owner);picture.setContentDescription("PDF 页面，可双指缩放");
  stage.addView(picture,new FrameLayout.LayoutParams(-1,-1));ProgressBar busy=spinner(28);stage.addView(busy,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));body.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout controls=new LinearLayout(owner);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(dp(4),dp(6),dp(4),dp(6));
  Button previous=pageButton("‹","上一页"),next=pageButton("›","下一页");TextView count=UiStyle.text(owner,"",13,palette.muted,false);count.setGravity(Gravity.CENTER);count.setTag("pdf-page-count");
  controls.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));controls.addView(count,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(controls);
  PanelDialog dialog=new PanelDialog(owner,file.optString("displayName","文件"),"PDF",body);dialog.action(AlertDialog.BUTTON_NEUTRAL,"其他应用打开",()->openExternal(file,uri));
  Future<?>[] work={null};int[] page={0},pages={0},generation={0};
  java.util.function.IntConsumer render=index->{
   if(index<0||(pages[0]>0&&index>=pages[0]))return;int token=++generation[0];if(work[0]!=null)work[0].cancel(true);
   previous.setEnabled(false);next.setEnabled(false);busy.setVisibility(View.VISIBLE);
   work[0]=previews.submit(()->{
    try(ParcelFileDescriptor descriptor=owner.getContentResolver().openFileDescriptor(uri,"r")){
     if(descriptor==null)throw new IOException();try(PdfRenderer renderer=new PdfRenderer(descriptor);PdfRenderer.Page pdf=renderer.openPage(index)){
      int total=renderer.getPageCount();float scale=Math.min(1400f/pdf.getWidth(),1800f/pdf.getHeight());
      Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(pdf.getWidth()*scale)),Math.max(1,Math.round(pdf.getHeight()*scale)),Bitmap.Config.ARGB_8888);
      bitmap.eraseColor(Color.WHITE);pdf.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      main.post(()->{if(!alive()||!dialog.isShowing()||token!=generation[0])return;page[0]=index;pages[0]=total;picture.setPicture(bitmap);busy.setVisibility(View.GONE);count.setText(String.format(Locale.getDefault(),"%d / %d",index+1,total));previous.setEnabled(index>0);next.setEnabled(index+1<total);previous.setAlpha(index>0?1:.35f);next.setAlpha(index+1<total?1:.35f);});
     }
    }catch(Exception e){main.post(()->{if(alive()&&dialog.isShowing()&&token==generation[0]){busy.setVisibility(View.GONE);error(stage,"无法预览此 PDF，可使用其他应用打开");}});}
   });
  };
  previous.setOnClickListener(v->render.accept(page[0]-1));next.setOnClickListener(v->render.accept(page[0]+1));
  show(dialog,()->{generation[0]++;if(work[0]!=null)work[0].cancel(true);picture.setImageDrawable(null);});render.accept(0);return dialog;
 }
 private Button pageButton(String label,String description){
  Button button=new Button(owner);button.setText(label);button.setTextSize(24);button.setTextColor(palette.primary);button.setContentDescription(description);button.setPadding(0,0,0,0);button.setMinWidth(0);button.setMinimumWidth(0);button.setBackgroundTintList(null);UiStyle.clickable(button,palette.surface,16,false);return button;
 }
 private void show(PanelDialog dialog,Runnable cleanup){
  activePreview=dialog;dialog.setOnDismissListener(d->{cleanup.run();if(activePreview==dialog)activePreview=null;});dialog.show();
 }
 private boolean alive(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed();}
 private void error(FrameLayout stage,String message){TextView error=UiStyle.text(owner,message,14,palette.muted,false);error.setGravity(Gravity.CENTER);error.setPadding(dp(24),dp(24),dp(24),dp(24));stage.addView(error,new FrameLayout.LayoutParams(-1,-1));}
 private void openExternal(JSONObject file,Uri uri){
  try{Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,file.optString("mime","application/octet-stream")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("attachment",uri));owner.startActivity(intent);}
  catch(ActivityNotFoundException e){toast("没有可以打开此文件的应用");}catch(Exception e){toast("文件或授权不可用，请重新选择");}
 }
 private String readText(Uri uri,int limit)throws IOException{
  try(InputStream input=owner.getContentResolver().openInputStream(uri)){
   if(input==null)throw new IOException();try(Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
    StringBuilder out=new StringBuilder();char[] buffer=new char[4096];int count;
    while(out.length()<limit&&(count=reader.read(buffer,0,Math.min(buffer.length,limit-out.length())))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.append(buffer,0,count);}return out.toString();
   }
  }
 }
 private byte[] readBytes(Uri uri,int max)throws IOException{
  try(InputStream input=owner.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(input==null)throw new IOException();byte[] buffer=new byte[8192];int count,total=0;
   while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=count;if(total>max)throw new IOException();out.write(buffer,0,count);}return out.toByteArray();
  }
 }
 private void toast(String message){if(alive())Toast.makeText(owner,message,Toast.LENGTH_LONG).show();}
 private int dp(int value){return UiStyle.dp(owner,value);}
 @Override public void close(){closed=true;previewVersion++;if(markupWork!=null)markupWork.cancel(true);if(activePreview!=null)activePreview.dismiss();thumbnails.shutdownNow();previews.shutdownNow();cache.evictAll();}

 private final class Photo extends FrameLayout {
  final AppCompatImageView image;final ProgressBar busy;final TextView unavailable;
  final boolean compact,preparing;String key;Future<?> work;Bitmap bitmap;
  Photo(boolean compact,boolean preparing){
   super(owner);this.compact=compact;this.preparing=preparing;setTag("attachment-image");setContentDescription("图片附件");
   setClipToOutline(true);setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(14));}});
   image=new AppCompatImageView(owner);image.setScaleType(compact?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(image,new LayoutParams(-1,-1));
   busy=spinner(20);addView(busy,new LayoutParams(dp(20),dp(20),Gravity.CENTER));
   unavailable=UiStyle.text(owner,"图片不可用",12,palette.muted,false);unavailable.setGravity(Gravity.CENTER);unavailable.setVisibility(GONE);addView(unavailable,new LayoutParams(-1,-1));
  }
  void picture(Bitmap bitmap){this.bitmap=bitmap;image.setImageBitmap(bitmap);busy.setVisibility(preparing?VISIBLE:GONE);unavailable.setVisibility(GONE);requestLayout();}
  void failed(){busy.setVisibility(GONE);unavailable.setVisibility(VISIBLE);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
   if(!compact){int width=MeasureSpec.getSize(widthSpec);int height=bitmap==null?dp(180):Math.round(width*bitmap.getHeight()/(float)bitmap.getWidth());height=Math.max(dp(72),Math.min(dp(320),height));if(MeasureSpec.getMode(heightSpec)==MeasureSpec.AT_MOST)height=Math.min(height,MeasureSpec.getSize(heightSpec));heightSpec=MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY);}
   super.onMeasure(widthSpec,heightSpec);
  }
  @Override protected void onDetachedFromWindow(){if(work!=null)work.cancel(true);super.onDetachedFromWindow();}
 }
 private static final class Icon extends Drawable {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean close;final int color,size;int alpha=255;
  Icon(int color,boolean close,int size){this.color=color;this.close=close;this.size=size;}
  @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
  @Override public void draw(Canvas canvas){
   Rect bounds=getBounds();int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);
   paint.setColor(ColorUtils.setAlphaComponent(color,Color.alpha(color)*alpha/255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
   if(close){canvas.drawLine(6,6,18,18,paint);canvas.drawLine(18,6,6,18,paint);}
   else{canvas.drawRoundRect(5,3,19,21,2,2,paint);canvas.drawLine(9,9,15,9,paint);canvas.drawLine(9,13,15,13,paint);canvas.drawLine(9,17,13,17,paint);}canvas.restoreToCount(save);
  }
  @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}

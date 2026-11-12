package com.changyu.ochat;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Base64InputStream;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One visual compatibility retry for services that reject a valid original PDF. No page is omitted. */
final class PdfPageFallback {
 static boolean shouldRetry(String message,JSONArray history){
  String m=message==null?"":message.toLowerCase(Locale.ROOT).replace('_',' ');
  java.util.regex.Matcher code=java.util.regex.Pattern.compile("接口错误[（(](\\d{3})[）)]").matcher(m);
  if(code.find()&&!Arrays.asList("400","413","415","422").contains(code.group(1)))return false;
  boolean rejected=m.contains("unsupported document")||m.contains("unsupport document")||m.contains("unsupported file")||m.contains("unsupported mime")
   ||m.contains("failed to parse")||m.contains("no pages")||m.contains("document has no")||m.contains("413")&&m.contains("接口错误")
   ||m.contains("pdf")&&(m.contains("not supported")||m.contains("unsupported"))
   ||m.contains("payload")&&(m.contains("too large")||m.contains("size")&&m.contains("exceed"))
   ||(m.contains("file")||m.contains("document"))&&m.contains("size")&&m.contains("exceed");
  return rejected&&hasPdf(history);
 }
 static boolean hasPdf(JSONArray history){
  if(history==null)return false;
  for(int i=0;i<history.length();i++){JSONArray parts=history.optJSONObject(i)==null?null:history.optJSONObject(i).optJSONArray("content");
   for(int j=0;parts!=null&&j<parts.length();j++)if(pdfData(parts.optJSONObject(j))!=null)return true;
  }return false;
 }
 static Object pdfData(JSONObject part){
  if(part==null)return null;String type=part.optString("type");if(!type.equals("file")&&!type.equals("input_file"))return null;
  JSONObject file=type.equals("file")?part.optJSONObject("file"):part;if(file==null)return null;Object data=file.opt("file_data");
  return data instanceof FileAttachmentData&&"application/pdf".equals(((FileAttachmentData)data).mime)
   ||data instanceof String&&((String)data).startsWith("data:application/pdf;base64,")?data:null;
 }
 static final class Session implements AutoCloseable {
  private final List<File> files=new ArrayList<>();private boolean closed;
  synchronized File create(Context context,String suffix)throws IOException{
   if(closed)throw new InterruptedIOException("PDF 处理已取消");
   File file=File.createTempFile("ochat-pdf-page-",suffix,context.getCacheDir());files.add(file);return file;
  }
  // Keep the names until the worker exits so a concurrent renderer can be cleaned a second time.
  public synchronized void close(){closed=true;for(File file:files)file.delete();}
 }
 static JSONArray render(Context context,JSONArray history,Session session,BooleanSupplier active,Consumer<String> status)throws Exception{
  JSONArray output=new JSONArray();
  for(int i=0;i<history.length();i++){
   check(active);JSONObject message=history.getJSONObject(i),copy=new JSONObject();Iterator<String> keys=message.keys();
   while(keys.hasNext()){String key=keys.next();copy.put(key,message.get(key));}
   JSONArray parts=message.optJSONArray("content");if(parts==null){output.put(copy);continue;}JSONArray converted=new JSONArray();
   for(int j=0;j<parts.length();j++){
    JSONObject part=parts.getJSONObject(j);Object data=pdfData(part);
    if(data==null){converted.put(part);continue;}
    JSONObject file=NativeProtocols.fileData(part);String name=file.optString("filename","PDF");
    File source=source(context,data,session,active);
    try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
     int pages=renderer.getPageCount();if(pages==0)throw new IOException("PDF 没有可读取的页面");
     for(int page=0;page<pages;page++){
      check(active);File image=session.create(context,".jpg");
      try(PdfRenderer.Page current=renderer.openPage(page)){renderPage(current,image);}
      check(active);
      converted.put(new JSONObject().put("type","text").put("text",name+" · 第 "+(page+1)+" / "+pages+" 页"));
      converted.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",new FileAttachmentData(image,"image/jpeg",true))));
      if(page==0||(page+1)%10==0||page+1==pages)status.accept("PDF 图像兼容模式 · "+(page+1)+" / "+pages+" 页");
     }
    }
   }copy.put("content",converted);output.put(copy);
  }return output;
 }
 static File source(Context context,Object data,Session session,BooleanSupplier active)throws IOException{
  check(active);
  if(data instanceof FileAttachmentData){((FileAttachmentData)data).validate();return ((FileAttachmentData)data).file;}
  if(!(data instanceof String))throw new IOException("附件没有可读取的原始数据");
  String encoded=(String)data;NativeProtocols.embeddedMime(encoded);File source=session.create(context,".pdf");
  try(InputStream input=new Base64InputStream(new AsciiInput(encoded,encoded.indexOf(',')+1),Base64.DEFAULT);OutputStream out=new FileOutputStream(source)){
   byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){check(active);out.write(buffer,0,n);}
  }
  check(active);return source;
 }
 private static void renderPage(PdfRenderer.Page page,File output)throws IOException{
  if(page.getWidth()<=0||page.getHeight()<=0)throw new IOException("PDF 页面尺寸无效");
  for(int side=2048;side>=256;side/=2){
   Bitmap bitmap=null;
   try{
    double scale=(double)side/Math.max(page.getWidth(),page.getHeight());int width=Math.max(1,(int)Math.round(page.getWidth()*scale)),height=Math.max(1,(int)Math.round(page.getHeight()*scale));
    bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    try(OutputStream stream=new FileOutputStream(output)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream))throw new IOException("PDF 页面无法编码");}
    return;
   }catch(OutOfMemoryError lowMemory){if(side==256)throw new IOException("内存不足，无法渲染此 PDF 页面",lowMemory);}
   finally{if(bitmap!=null)bitmap.recycle();}
  }
 }
 private static void check(BooleanSupplier active)throws IOException{FileAttachmentData.checkCancelled();if(!active.getAsBoolean())throw new InterruptedIOException("PDF 处理已取消");}
 private static final class AsciiInput extends InputStream {
  final String data;int position;
  AsciiInput(String data,int start){this.data=data;position=start;}
  public int read(){return position<data.length()?data.charAt(position++):-1;}
  public int read(byte[] buffer,int offset,int length){if(length==0)return 0;if(position>=data.length())return -1;int n=Math.min(length,data.length()-position);for(int i=0;i<n;i++)buffer[offset+i]=(byte)data.charAt(position++);return n;}
 }
 private PdfPageFallback(){}
}

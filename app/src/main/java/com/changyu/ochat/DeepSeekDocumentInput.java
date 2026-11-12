package com.changyu.ochat;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.Iterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Prepare unsupported file parts before the official service can silently ignore them. */
final class DeepSeekDocumentInput {
 static JSONArray prepare(Context context,String base,String model,String protocol,JSONArray history,PdfPageFallback.Session files,BooleanSupplier active,Consumer<String> status)throws Exception{
  if(!DeepSeekInputPolicy.applies(base,protocol))return history;
  boolean vision=DeepSeekInputPolicy.images(model);int latestUser=-1;
  for(int i=0;i<history.length();i++)if("user".equals(history.getJSONObject(i).optString("role")))latestUser=i;
  JSONArray result=new JSONArray();
  for(int i=0;i<history.length();i++){
   check(active);JSONObject message=history.getJSONObject(i),copy=new JSONObject();Iterator<String> keys=message.keys();
   while(keys.hasNext()){String key=keys.next();copy.put(key,message.get(key));}
   JSONArray parts=message.optJSONArray("content");if(parts==null){result.put(copy);continue;}JSONArray prepared=new JSONArray();
   for(int j=0;j<parts.length();j++){
    check(active);JSONObject part=parts.getJSONObject(j);String type=part.optString("type");
    try{
     if(type.equals("file")||type.equals("input_file")){
      JSONObject file=NativeProtocols.fileData(part);Object data=file.get("file_data");String name=file.optString("filename","附件");
      JSONArray converted;
      if(PdfPageFallback.pdfData(part)!=null&&vision){
       status.accept("DeepSeek 不支持原 PDF，正在逐页转换为图像…");
       JSONObject single=new JSONObject().put("role",message.optString("role","user")).put("content",new JSONArray().put(part));
       converted=PdfPageFallback.render(context,new JSONArray().put(single),files,active,status).getJSONObject(0).getJSONArray("content");
      }else{
       String mime=NativeProtocols.embeddedMime(data);
       if(mime.startsWith("image/")){
        if(!vision)throw new IOException(DeepSeekInputPolicy.imageError(model));
        converted=new JSONArray().put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",data)));
       }else{
        File source=PdfPageFallback.source(context,data,files,active);
        if(PdfPageFallback.pdfData(part)!=null){
         status.accept("当前模型需要文档识别");
         converted=PdfTextInput.read(context,source,name,model,active,status);
        }else{
         if(!AttachmentBatch.isOfficeOrTextDocument(mime,name)&&!mime.contains("csv"))throw new IOException("DeepSeek 不支持此原文件类型，请转换为文本或文档："+name);
         String value;try(InputStream input=new FileInputStream(source)){value=AttachmentBatch.extractDocumentText(input,mime,name);}
         if(value.trim().isEmpty())throw new IOException("文件中没有可读取的文字："+name);
         converted=new JSONArray().put(text("附件："+name+"\n"+value));
        }
       }
      }
      for(int k=0;k<converted.length();k++)prepared.put(converted.get(k));
     }else{
      if((type.equals("image_url")||type.equals("input_image"))&&!vision)throw new IOException(DeepSeekInputPolicy.imageError(model));
      prepared.put(part);
     }
    }catch(InterruptedIOException cancelled){throw cancelled;}
    catch(Exception unreadable){
     if(i==latestUser||!"user".equals(message.optString("role")))throw unreadable;
     prepared.put(text("历史附件未发送给当前 DeepSeek 模型："+ApiFailure.local(unreadable)));
    }
   }
   copy.put("content",prepared);result.put(copy);
  }
  DeepSeekInputPolicy.validate(base,model,protocol,result);return result;
 }
 private static JSONObject text(String value)throws JSONException{return new JSONObject().put("type","text").put("text",value);}
 private static void check(BooleanSupplier active)throws IOException{FileAttachmentData.checkCancelled();if(!active.getAsBoolean())throw new InterruptedIOException("附件处理已取消");}
 private DeepSeekDocumentInput(){}
}

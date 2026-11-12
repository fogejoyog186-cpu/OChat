package com.changyu.ochat;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;

/** A seekable preview source. Read on the preview worker; close safely from the UI thread. */
final class PdfPreviewSource implements AutoCloseable {
 private final Context context;private final JSONObject descriptor;private final Uri uri;
 private File source,temporary;private volatile boolean closed;
 PdfPreviewSource(Context context,JSONObject descriptor,Uri uri){this.context=context.getApplicationContext();this.descriptor=descriptor;this.uri=uri;}
 File file()throws IOException{
  synchronized(this){checkOpen();if(source!=null)return source;}
  if(descriptor.optString("payloadSnapshotFile").endsWith(".pdf")){
   try{
    File saved=AttachmentSnapshots.pdfData(context,AttachmentSnapshots.read(context,descriptor)).file;
    synchronized(this){checkOpen();source=saved;return source;}
   }catch(InterruptedIOException cancelled){throw cancelled;}catch(IOException unavailable){/* Try the original provider. */}
  }
  File copy=File.createTempFile("ochat-pdf-preview-",".pdf",context.getCacheDir());
  try{
   // Open before registering: closing the panel must not let a later open recreate a deleted file.
   try(InputStream input=context.getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(copy)){
    if(input==null)throw new IOException("PDF 无法读取");
    synchronized(this){checkOpen();temporary=copy;}
    byte[] buffer=new byte[16384];int count;
    while((count=input.read(buffer))!=-1){checkOpen();output.write(buffer,0,count);}
   }
   synchronized(this){checkOpen();source=copy;return source;}
  }catch(IOException|RuntimeException failure){copy.delete();throw failure;}
 }
 private void checkOpen()throws InterruptedIOException{FileAttachmentData.checkCancelled();if(closed)throw new InterruptedIOException("PDF 预览已关闭");}
 @Override public synchronized void close(){closed=true;if(temporary!=null)temporary.delete();}
}

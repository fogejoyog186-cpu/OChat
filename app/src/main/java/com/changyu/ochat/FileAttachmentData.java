package com.changyu.ochat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import okio.ByteString;

/** Immutable private-file reference. JSON serialization streams its bytes instead of a giant String. */
final class FileAttachmentData {
 final File file;final String mime;final boolean dataUri;final long size,modified;
 FileAttachmentData(File file,String mime,boolean dataUri){
  if(!file.isFile())throw new IllegalArgumentException("附件文件不可用");
  this.file=file;this.mime=mime;this.dataUri=dataUri;size=file.length();modified=file.lastModified();
 }
 FileAttachmentData base64(){return new FileAttachmentData(file,mime,false);}
 String prefix(){return dataUri?"data:"+mime+";base64,":"";}
 long encodedLength(){return Math.multiplyExact((size+2)/3,4);}
 void validate()throws IOException{if(!file.isFile()||file.length()!=size||file.lastModified()!=modified)throw new IOException("附件在发送前发生变化，请重新添加");}
 void writeBase64(OutputStream output)throws IOException{
  validate();long read=0;byte[] buffer=new byte[12288];
  try(InputStream input=new FileInputStream(file)){
   while(true){
    checkCancelled();int count=0,n;
    while(count<buffer.length&&(n=input.read(buffer,count,buffer.length-count))!=-1)count+=n;
    if(count==0)break;
    output.write(ByteString.of(buffer,0,count).base64().getBytes(StandardCharsets.US_ASCII));read+=count;
   }
  }
  if(read!=size)throw new IOException("附件读取不完整，请重新添加");
 }
 static void checkCancelled()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("附件处理已取消");}
 @Override public String toString(){return "[本地附件数据]";}
}

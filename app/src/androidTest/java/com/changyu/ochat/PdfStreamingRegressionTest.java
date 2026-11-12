package com.changyu.ochat;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.MediaType;
import okio.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PdfStreamingRegressionTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private File temporary(String suffix)throws Exception{File dir=new File(context.getFilesDir(),"imported_attachments");assertTrue(dir.isDirectory()||dir.mkdirs());return File.createTempFile("stream-pdf-",suffix,dir);}
 private Uri uri(File file){return FileProvider.getUriForFile(context,context.getPackageName()+".attachments",file);}
 private long crc(File file)throws Exception{CRC32 crc=new CRC32();try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)crc.update(bytes,0,n);}return crc.getValue();}
 private void ascii(RandomAccessFile file,String text)throws Exception{file.write(text.getBytes(StandardCharsets.US_ASCII));}
 private void largeScan(File file)throws Exception{
  try(RandomAccessFile out=new RandomAccessFile(file,"rw")){
   List<Long> offsets=new ArrayList<>();offsets.add(0L);ascii(out,"%PDF-1.4\n");
   String[] objects={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>","<< /Length 30 >>\nstream\nq 300 0 0 400 0 0 cm /Im0 Do Q\nendstream"};
   for(int i=0;i<objects.length;i++){offsets.add(out.getFilePointer());ascii(out,(i+1)+" 0 obj\n"+objects[i]+"\nendobj\n");}
   offsets.add(out.getFilePointer());long imageBytes=4096L*4097*3;
   ascii(out,"5 0 obj\n<< /Type /XObject /Subtype /Image /Width 4096 /Height 4097 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Length "+imageBytes+" >>\nstream\n");
   byte[] pixels=new byte[16384];Arrays.fill(pixels,(byte)90);
   for(long remaining=imageBytes;remaining>0;){int count=(int)Math.min(pixels.length,remaining);out.write(pixels,0,count);remaining-=count;}
   ascii(out,"\nendstream\nendobj\n");long xref=out.getFilePointer();ascii(out,"xref\n0 6\n0000000000 65535 f \n");
   for(int i=1;i<offsets.size();i++)ascii(out,String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));
   ascii(out,"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");
  }
 }
 private void pages(File file)throws Exception{
  PdfDocument document=new PdfDocument();
  try(OutputStream output=new FileOutputStream(file)){
   for(int i=1;i<=3;i++){PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(300,400,i).create());page.getCanvas().drawColor(i==1?Color.RED:i==2?Color.GREEN:Color.BLUE);document.finishPage(page);}document.writeTo(output);
  }finally{document.close();}
 }
 @Test public void validImagePdfAboveFiftyMbKeepsEveryByteWithoutInlineSnapshot()throws Exception{
  File original=temporary(".pdf");JSONObject descriptor=null;
  try{
   largeScan(original);assertTrue(original.length()>50_000_000L);descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri(original));
   String payload=AttachmentBatch.payload(context,descriptor);assertTrue(AttachmentSnapshots.isPdfPayload(payload));assertTrue(payload.length()<100);assertFalse(descriptor.has("payloadSnapshot"));assertTrue(descriptor.toString().length()<1024);
   FileAttachmentData data=AttachmentSnapshots.pdfData(context,payload);assertEquals(original.length(),data.size);assertEquals(crc(original),crc(data.file));
   try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(data.file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfRenderer.Page page=renderer.openPage(0)){
    assertEquals(1,renderer.getPageCount());Bitmap b=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888);try{page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);assertTrue(Color.red(b.getPixel(15,20))<200);}finally{b.recycle();}
   }
   JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(AttachmentBatch.part(context,descriptor,payload,ChatApiClient.GEMINI))));
   JSONObject body=new ChatApiClient().requestBody("fixture",history,.7,512,1,"default",ChatApiClient.GEMINI,SearchOptions.OFF);
   okhttp3.RequestBody request=JsonRequestBody.create(body,MediaType.get("application/json; charset=utf-8"));final long[] written={0};
   BufferedSink sink=Okio.buffer(Okio.sink(new OutputStream(){public void write(int b){written[0]++;}public void write(byte[] b,int o,int n){written[0]+=n;}}));request.writeTo(sink);sink.flush();
   assertEquals(request.contentLength(),written[0]);assertTrue(written[0]>data.size);
  }finally{if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);original.delete();}
 }
 @Test public void pdfPreparationStillRejectsUnsharedLocalFileUris()throws Exception{
  File original=temporary(".pdf");JSONObject descriptor=new JSONObject();
  try{
   pages(original);descriptor.put("uri",Uri.fromFile(original).toString()).put("displayName","private.pdf").put("mime","application/pdf");
   try{AttachmentBatch.payload(context,descriptor);fail("file:// must not bypass content URI validation");}
   catch(IOException expected){assertTrue(expected.getMessage().contains("附件地址无效"));}
   assertFalse(AttachmentSnapshots.has(descriptor));
  }finally{AttachmentSnapshots.discard(context,descriptor);original.delete();}
 }
 @Test public void previewUsesSavedPdfAfterOriginalDisappearsWithoutDeletingTheSnapshot()throws Exception{
  File original=temporary(".pdf");JSONObject descriptor=null;File saved=null;
  try{
   pages(original);descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri(original));String payload=AttachmentBatch.payload(context,descriptor);
   saved=AttachmentSnapshots.pdfData(context,payload).file;assertTrue(original.delete());
   try(PdfPreviewSource preview=new PdfPreviewSource(context,descriptor,Uri.parse(descriptor.getString("uri")))){
    assertEquals(saved,preview.file());
    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(preview.file(),ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){assertEquals(3,renderer.getPageCount());}
   }
   assertTrue(saved.isFile());
  }finally{if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);original.delete();}
  assertNotNull(saved);assertFalse(saved.exists());
 }
 @Test public void visualFallbackRetainsEveryPageForEveryProtocolAndCleansFiles()throws Exception{
  File original=temporary(".pdf");JSONObject descriptor=null;List<File> rendered=new ArrayList<>();
  try(PdfPageFallback.Session session=new PdfPageFallback.Session()){
   pages(original);descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri(original));String payload=AttachmentBatch.payload(context,descriptor);
   JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","Read all pages")).put(AttachmentBatch.part(context,descriptor,payload,ChatApiClient.GEMINI))));
   assertTrue(PdfPageFallback.shouldRetry("接口错误（400） · Unsupported Document",history));assertFalse(PdfPageFallback.shouldRetry("接口错误（401） · Unsupported Document",history));
   JSONArray converted=PdfPageFallback.render(context,history,session,()->true,status->{});assertFalse(PdfPageFallback.hasPdf(converted));
   JSONArray content=converted.getJSONObject(0).getJSONArray("content");assertEquals(7,content.length());
   for(int i=0;i<3;i++){assertTrue(content.getJSONObject(1+i*2).getString("text").contains("第 "+(i+1)+" / 3 页"));FileAttachmentData image=(FileAttachmentData)content.getJSONObject(2+i*2).getJSONObject("image_url").get("url");rendered.add(image.file);try(InputStream input=new FileInputStream(image.file)){assertEquals(255,input.read());assertEquals(216,input.read());}}
   for(String protocol:new String[]{ChatApiClient.GEMINI,ChatApiClient.ANTHROPIC,ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES}){
    JSONObject body=new ChatApiClient().requestBody("fixture",converted,.7,512,1,"default",protocol,SearchOptions.OFF);Buffer wire=new Buffer();JsonRequestBody.create(body,MediaType.get("application/json; charset=utf-8")).writeTo(wire);String json=wire.readUtf8();assertFalse(json.contains("本地附件数据"));assertTrue(json.contains("第 3 / 3 页"));
   }
  }finally{if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);original.delete();}
  for(File file:rendered)assertFalse(file.exists());
 }
 @Test public void rawPdfSnapshotSurvivesBackupAfterOriginalDisappears()throws Exception{
  File original=temporary(".pdf"),archive=temporary(".zip");JSONObject descriptor=null;JSONArray restored=null;
  try{
   pages(original);long expected=crc(original);descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri(original));AttachmentBatch.payload(context,descriptor);assertTrue(original.delete());
   JSONArray sessions=new JSONArray().put(new JSONObject().put("id","pdf-backup").put("messages",new JSONArray().put(new JSONObject().put("user",true).put("text","PDF").put("attachments",new JSONArray().put(descriptor)))));
   assertEquals(0,ConversationBackup.exportTo(context,uri(archive),sessions.toString()));restored=ConversationBackup.importFrom(context,uri(archive));
   JSONObject file=restored.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").getJSONObject(0);assertFalse(file.has("payloadSnapshotFile"));assertFalse(file.has("payloadSnapshot"));
   String payload=AttachmentBatch.payload(context,file);assertEquals(expected,crc(AttachmentSnapshots.pdfData(context,payload).file));AttachmentSnapshots.discard(context,file);
  }finally{if(restored!=null)ConversationBackup.discard(context,restored);if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);original.delete();archive.delete();}
 }
}

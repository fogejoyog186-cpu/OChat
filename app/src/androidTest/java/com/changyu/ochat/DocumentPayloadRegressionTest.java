package com.changyu.ochat;

import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DocumentPayloadRegressionTest {
 private void ascii(ByteArrayOutputStream out,String value)throws IOException{out.write(value.getBytes(StandardCharsets.US_ASCII));}
 private void object(ByteArrayOutputStream out,List<Integer> offsets,String body)throws IOException{offsets.add(out.size());ascii(out,(offsets.size()-1)+" 0 obj\n"+body+"\nendobj\n");}
 /** An actual one-page, image-only PDF with an uncompressed >3 MiB RGB scan. */
 private byte[] scannedPdf()throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();List<Integer> offsets=new ArrayList<>();offsets.add(0);ascii(out,"%PDF-1.4\n");
  object(out,offsets,"<< /Type /Catalog /Pages 2 0 R >>");
  object(out,offsets,"<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
  object(out,offsets,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 320 400] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>");
  String draw="q 280 0 0 360 20 20 cm /Im0 Do Q";
  object(out,offsets,"<< /Length "+draw.length()+" >>\nstream\n"+draw+"\nendstream");
  byte[] image=new byte[1024*1025*3];Arrays.fill(image,(byte)90);offsets.add(out.size());
  ascii(out,"5 0 obj\n<< /Type /XObject /Subtype /Image /Width 1024 /Height 1025 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Length "+image.length+" >>\nstream\n");out.write(image);ascii(out,"\nendstream\nendobj\n");
  int xref=out.size();ascii(out,"xref\n0 6\n0000000000 65535 f \n");for(int i=1;i<offsets.size();i++)ascii(out,String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));
  ascii(out,"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n");return out.toByteArray();
 }
 @Test public void largeScannedPdfKeepsItsImageBytesAndIsRenderable()throws Exception{
  byte[] pdf=scannedPdf();assertTrue(pdf.length>3*1024*1024);
  String payload=AttachmentBatch.pdfPayload(new ByteArrayInputStream(pdf));assertTrue(payload.startsWith("data:application/pdf;base64,"));
  assertArrayEquals(pdf,Base64.decode(payload.substring(payload.indexOf(',')+1),Base64.NO_WRAP));assertEquals(pdf.length,AttachmentBatch.estimatedBytes(payload));
  File file=File.createTempFile("scanned-document-",".pdf",InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
  try{
   try(FileOutputStream output=new FileOutputStream(file)){output.write(pdf);}
   try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor);PdfRenderer.Page page=renderer.openPage(0)){
    assertEquals(1,renderer.getPageCount());Bitmap bitmap=Bitmap.createBitmap(128,160,Bitmap.Config.ARGB_8888);
    try{bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);assertTrue(Color.red(bitmap.getPixel(64,80))<200);}finally{bitmap.recycle();}
   }
  }finally{file.delete();}
 }
 @Test public void moreThanEightMiBOfPdfAttachmentsUsesActualFileParts()throws Exception{
  String payload=AttachmentBatch.pdfPayload(new ByteArrayInputStream(scannedPdf()));List<JSONObject> files=new ArrayList<>();List<String> payloads=new ArrayList<>();
  for(int i=0;i<3;i++){files.add(new JSONObject().put("mime","application/pdf").put("displayName","scan"+i+".pdf"));payloads.add(payload);}
  JSONArray content=AttachmentBatch.contentForProtocol("Read scans",files,payloads,ChatApiClient.RESPONSES);assertEquals(4,content.length());
  for(int i=1;i<4;i++){assertEquals("input_file",content.getJSONObject(i).getString("type"));assertEquals(payload,content.getJSONObject(i).getString("file_data"));}
 }
 @Test public void invalidPdfAndCancelledPdfHaveSpecificErrors()throws Exception{
  try{AttachmentBatch.pdfPayload(new ByteArrayInputStream("not a PDF".getBytes(StandardCharsets.UTF_8)));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("PDF"));}
  Thread.currentThread().interrupt();try{AttachmentBatch.pdfPayload(new ByteArrayInputStream("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII)));fail();}catch(InterruptedIOException expected){assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
}

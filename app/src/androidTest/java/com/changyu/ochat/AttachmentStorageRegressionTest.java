package com.changyu.ochat;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttachmentStorageRegressionTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private File temporary(String suffix)throws IOException{
  File folder=new File(context.getFilesDir(),"imported_attachments");
  if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create test attachment directory");
  return File.createTempFile("storage-regression-",suffix,folder);
 }
 private Uri uri(File file){return FileProvider.getUriForFile(context,context.getPackageName()+".attachments",file);}
 private void write(File file,byte[] bytes)throws IOException{try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}}
 private JSONObject descriptor(File file,String mime)throws Exception{
  return new JSONObject().put("uri",uri(file).toString()).put("mime",mime).put("displayName",file.getName()).put("type","file");
 }
 private JSONArray sessions(JSONObject descriptor)throws Exception{
  JSONObject message=new JSONObject().put("id","storage-message").put("user",true).put("text","Read the document").put("attachments",new JSONArray().put(descriptor));
  return new JSONArray().put(new JSONObject().put("id","storage-session").put("title","Storage regression").put("messages",new JSONArray().put(message)));
 }
 private JSONObject attachment(JSONArray sessions)throws Exception{
  return sessions.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").getJSONObject(0);
 }
 private byte[] imagePdf()throws IOException{
  Bitmap bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);
  int[] pixels=new int[512*512];Random random=new Random(7193);
  for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|random.nextInt(0x1000000);
  bitmap.setPixels(pixels,0,512,0,0,512,512);
  PdfDocument document=new PdfDocument();
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(512,512,1).create());
   page.getCanvas().drawBitmap(bitmap,0,0,null);document.finishPage(page);document.writeTo(out);return out.toByteArray();
  }finally{document.close();bitmap.recycle();}
 }
 private void backupJson(File file,JSONArray sessions)throws Exception{
  JSONObject root=new JSONObject().put("format","ochat-backup").put("version",1).put("sessions",sessions);
  try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
   zip.putNextEntry(new ZipEntry("conversations.json"));zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
  }
 }
 @Test public void largeDiskSnapshotSurvivesPortableBackupWithoutOriginalFile()throws Exception{
  File original=temporary(".pdf"),archive=temporary(".zip");JSONObject source=null;JSONArray restored=null;
  try{
   byte[] pdf=imagePdf();write(original,pdf);source=descriptor(original,"application/pdf").put("importedCopy",true);
   String payload=AttachmentBatch.pdfPayload(new ByteArrayInputStream(pdf));
   assertTrue(payload.length()>AttachmentSnapshots.INLINE_CHARS);
   AttachmentSnapshots.capture(context,source,payload);
   assertFalse(source.has("payloadSnapshot"));assertTrue(source.has("payloadSnapshotFile"));
   String privateKey=source.getString("payloadSnapshotFile");
   JSONArray saved=new JSONArray(sessions(source).toString());
   assertTrue("Exercise snapshot fallback after source permission/file loss",original.delete());
   assertEquals(0,ConversationBackup.exportTo(context,uri(archive),saved.toString()));
   boolean sawJson=false,sawPdf=false;
   try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))){
    ZipEntry entry;
    while((entry=zip.getNextEntry())!=null){
     byte[] bytes=ConversationBackup.read(zip,ConversationBackup.MAX_FILE);
     if(entry.getName().equals("conversations.json")){
      sawJson=true;String json=new String(bytes,StandardCharsets.UTF_8);
      assertFalse(json.contains("payloadSnapshotFile"));assertFalse(json.contains("payloadSnapshotChars"));assertFalse(json.contains(privateKey));
     }else{assertTrue(entry.getName().startsWith("attachments/"));assertArrayEquals(pdf,bytes);sawPdf=true;}
     zip.closeEntry();
    }
   }
   assertTrue(sawJson);assertTrue(sawPdf);
   AttachmentSnapshots.discard(context,source);
   restored=ConversationBackup.importFrom(context,uri(archive));JSONObject imported=attachment(restored);
   assertFalse(imported.has("payloadSnapshotFile"));assertFalse(imported.has("payloadSnapshotChars"));
   assertTrue(imported.optBoolean("importedCopy"));assertTrue(imported.optBoolean("restoredCopy"));
   try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(imported.getString("uri")))){
    assertArrayEquals(pdf,ConversationBackup.read(in,ConversationBackup.MAX_FILE));
   }
   assertEquals(payload,AttachmentBatch.payload(context.getContentResolver(),imported));
  }finally{
   if(restored!=null)ConversationBackup.discard(context,restored);
   if(source!=null)AttachmentSnapshots.discard(context,source);
   original.delete();archive.delete();
  }
 }
 @Test public void missingOriginalAndSnapshotDoNotAbortBackupOfRemainingConversation()throws Exception{
  File missing=temporary(".pdf"),available=temporary(".txt"),archive=temporary(".zip");JSONArray restored=null;
  try{
   JSONObject unavailable=descriptor(missing,"application/pdf").put("payloadSnapshotFile",UUID.randomUUID()+".payload").put("payloadSnapshotChars",400_000);
   assertTrue(missing.delete());
   byte[] retained="Keep the other attachment and conversation".getBytes(StandardCharsets.UTF_8);write(available,retained);
   JSONArray source=sessions(unavailable);
   source.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").put(descriptor(available,"text/plain"));
   assertEquals("Only the unavailable PDF is reported missing",1,ConversationBackup.exportTo(context,uri(archive),source.toString()));
   assertTrue(archive.length()>0);
   restored=ConversationBackup.importFrom(context,uri(archive));
   JSONObject message=restored.getJSONObject(0).getJSONArray("messages").getJSONObject(0);
   assertEquals("Read the document",message.getString("text"));assertEquals(2,message.getJSONArray("attachments").length());
   JSONObject lost=message.getJSONArray("attachments").getJSONObject(0),kept=message.getJSONArray("attachments").getJSONObject(1);
   assertTrue(lost.optBoolean("backupUnavailable"));assertFalse(lost.has("payloadSnapshotFile"));assertFalse(lost.has("restoredCopy"));
   assertTrue(kept.optBoolean("restoredCopy"));
   try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(kept.getString("uri")))){
    assertArrayEquals(retained,ConversationBackup.read(in,1024));
   }
  }finally{
   if(restored!=null)ConversationBackup.discard(context,restored);
   missing.delete();available.delete();archive.delete();
  }
 }
 @Test public void textSnapshotStartingWithDataUriIsBackedUpAsLiteralText()throws Exception{
  File original=temporary(".txt"),archive=temporary(".zip");JSONObject source=null;JSONArray restored=null;
  try{
   String text="data:application/pdf;base64,SGVsbG8=";
   source=descriptor(original,"text/plain");AttachmentSnapshots.capture(context,source,text);assertTrue(original.delete());
   assertEquals(0,ConversationBackup.exportTo(context,uri(archive),sessions(source).toString()));
   restored=ConversationBackup.importFrom(context,uri(archive));JSONObject imported=attachment(restored);
   try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(imported.getString("uri")))){
    assertArrayEquals("The literal must not be decoded to Hello",text.getBytes(StandardCharsets.UTF_8),ConversationBackup.read(in,1024));
   }
   assertEquals(text,AttachmentBatch.payload(context.getContentResolver(),imported));
  }finally{
   if(restored!=null)ConversationBackup.discard(context,restored);
   if(source!=null)AttachmentSnapshots.discard(context,source);
   original.delete();archive.delete();
  }
 }
 @Test public void untrustedBackupCannotMarkExistingFilesForDeletionOrReferencePrivateSnapshots()throws Exception{
  File original=temporary(".txt"),archive=temporary(".zip");JSONObject local=null;JSONArray restored=null;
  try{
   byte[] originalBytes="Existing user attachment must survive cancelled import".getBytes(StandardCharsets.UTF_8);write(original,originalBytes);
   local=descriptor(original,"text/plain");
   StringBuilder text=new StringBuilder();while(text.length()<=AttachmentSnapshots.INLINE_CHARS)text.append("existing private snapshot\n");
   String payload=text.toString();AttachmentSnapshots.capture(context,local,payload);
   String key=local.getString("payloadSnapshotFile");
   JSONObject untrusted=new JSONObject(local.toString()).put("importedCopy",true).put("restoredCopy",true);
   backupJson(archive,sessions(untrusted));restored=ConversationBackup.importFrom(context,uri(archive));
   JSONObject imported=attachment(restored);
   assertFalse(imported.has("importedCopy"));assertFalse(imported.has("restoredCopy"));
   assertFalse(imported.has("payloadSnapshotFile"));assertFalse(imported.has("payloadSnapshotChars"));
   assertFalse(restored.toString().contains(key));
   ConversationBackup.discard(context,restored);restored=null;
   assertTrue(original.isFile());
   try(InputStream in=new FileInputStream(original)){assertArrayEquals(originalBytes,ConversationBackup.read(in,1024));}
   assertEquals(payload,AttachmentSnapshots.read(context,local));
  }finally{
   if(restored!=null)ConversationBackup.discard(context,restored);
   if(local!=null)AttachmentSnapshots.discard(context,local);
   original.delete();archive.delete();
  }
 }
 /** Isolate every GC test from actual application snapshots and preferences. */
 private final class StorageFixture implements AutoCloseable {
  final File root=new File(context.getCacheDir(),"snapshot-gc-"+UUID.randomUUID());
  final Context storage=new ContextWrapper(context){
   @Override public File getFilesDir(){return root;}
   @Override public Context getApplicationContext(){return this;}
  };
  final SharedPreferences prefs=context.getSharedPreferences(root.getName(),0);
  final SessionRepository repository=SessionRepository.get(prefs);
  StorageFixture(){assertTrue(root.mkdirs());}
  JSONObject snapshot(boolean old)throws Exception{
   File directory=new File(root,"attachment_payloads");if(!directory.exists())assertTrue(directory.mkdirs());
   String key=UUID.randomUUID()+".payload";File file=new File(directory,key);write(file,"snapshot".getBytes(StandardCharsets.UTF_8));
   if(old)assertTrue(file.setLastModified(System.currentTimeMillis()-AttachmentSnapshots.ORPHAN_GRACE_MILLIS-60_000));
   return new JSONObject().put("payloadSnapshotFile",key).put("uri","content://test.invalid/"+key).put("mime","application/pdf");
  }
  boolean exists(JSONObject descriptor){return new File(new File(root,"attachment_payloads"),descriptor.optString("payloadSnapshotFile")).isFile();}
  void sweep(){
   List<String> records=new ArrayList<>();for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(("sessions".equals(entry.getKey())||entry.getKey().startsWith("draft_"))&&entry.getValue() instanceof String)records.add((String)entry.getValue());
   AttachmentSnapshots.prune(storage,records,System.currentTimeMillis());
  }
  @Override public void close()throws Exception{repository.flush();prefs.edit().clear().commit();deleteTree(root);}
 }
 private void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
 @Test public void cleanupRetainsSharedBranchQueueCheckpointAndLiveDraftReferences()throws Exception{
  try(StorageFixture fixture=new StorageFixture()){
   JSONObject shared=fixture.snapshot(true),branch=fixture.snapshot(true),queued=fixture.snapshot(true),draft=fixture.snapshot(true);
   JSONObject pending=fixture.snapshot(true),orphan=fixture.snapshot(true),recent=fixture.snapshot(false);
   File original=new File(fixture.root,"original.pdf");write(original,new byte[]{1,2,3});
   JSONObject first=sessions(shared).getJSONObject(0);
   JSONObject second=sessions(shared).getJSONObject(0).put("id","second");
   second.put("branches",new JSONArray().put(sessions(branch).getJSONObject(0)));
   second.put("outbox",new JSONArray().put(new JSONObject().put("message",sessions(queued).getJSONObject(0).getJSONArray("messages").getJSONObject(0))));
   fixture.repository.replace(new JSONArray().put(first).put(second));
   fixture.repository.checkpoint("second",new JSONObject().put("message",sessions(draft).getJSONObject(0).getJSONArray("messages").getJSONObject(0)));
   fixture.repository.flush();
   List<JSONObject> pendingDraft=new ArrayList<>();pendingDraft.add(pending);
   fixture.repository.manageSnapshots(fixture.storage,pendingDraft);fixture.repository.flush();
   assertFalse("An abandoned old snapshot is collected",fixture.exists(orphan));
   for(JSONObject referenced:Arrays.asList(shared,branch,queued,draft,pending,recent))assertTrue(fixture.exists(referenced));
   fixture.repository.replace(new JSONArray().put(second));fixture.repository.flush();fixture.sweep();
   assertTrue("Another conversation still refers to the same snapshot",fixture.exists(shared));
   fixture.repository.replace(new JSONArray());fixture.repository.flush();
   assertTrue("Routine writes must not repeat expensive GC within the hour",fixture.exists(shared));fixture.sweep();
   assertFalse(fixture.exists(shared));assertFalse(fixture.exists(branch));assertFalse(fixture.exists(queued));
   assertTrue("A persisted checkpoint still owns its snapshot",fixture.exists(draft));
   fixture.repository.clearCheckpoint("second");fixture.repository.flush();assertTrue(fixture.exists(draft));fixture.sweep();assertFalse(fixture.exists(draft));
   assertTrue("An unsent draft with a live descriptor is protected",fixture.exists(pending));
   assertTrue("Fresh/preparing snapshots have a grace period",fixture.exists(recent));
   assertTrue("Cleanup must never inspect or delete original files",original.isFile());
  }
 }
 @Test public void cleanupDoesNothingWhenStoredReferenceDataIsUnreadable()throws Exception{
  try(StorageFixture fixture=new StorageFixture()){
   JSONObject old=fixture.snapshot(true);
   assertEquals(0,AttachmentSnapshots.prune(fixture.storage,Collections.singletonList("{broken"),System.currentTimeMillis()));
   assertTrue(fixture.exists(old));
   assertEquals(0,AttachmentSnapshots.prune(fixture.storage,Collections.singletonList("null"),System.currentTimeMillis()));
   assertTrue(fixture.exists(old));
  }
 }
 @Test public void snapshotTraversalCannotReadOrDeleteAnotherAttachment()throws Exception{
  File original=temporary(".txt");
  try{
   byte[] bytes="Do not read this through a snapshot key".getBytes(StandardCharsets.UTF_8);write(original,bytes);
   JSONObject forged=new JSONObject().put("payloadSnapshotFile","../imported_attachments/"+original.getName());
   try{AttachmentSnapshots.read(context,forged);fail("Traversal must be rejected");}
   catch(IOException expected){assertTrue(expected.getMessage().contains("地址无效"));}
   AttachmentSnapshots.discard(context,forged);assertTrue(original.isFile());
   try(InputStream in=new FileInputStream(original)){assertArrayEquals(bytes,ConversationBackup.read(in,1024));}
  }finally{original.delete();}
 }
}

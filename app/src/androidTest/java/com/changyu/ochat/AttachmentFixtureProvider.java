package com.changyu.ochat;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** Test APK only: deterministic local fixtures, never contacts the network. */
public class AttachmentFixtureProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public String getType(Uri uri){String path=uri.getLastPathSegment();return path.startsWith("photo")?"image/png":path.startsWith("pdf")?"application/pdf":"text/plain";}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  String type=getType(uri),extension=type.equals("image/png")?".png":type.equals("application/pdf")?".pdf":".txt";
  MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});cursor.addRow(new Object[]{uri.getLastPathSegment()+extension,32});return cursor;
 }
 public synchronized ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  File file=new File(getContext().getCacheDir(),"attachment-fixture-"+uri.getLastPathSegment());
  if(!file.isFile())try(FileOutputStream output=new FileOutputStream(file)){
   String type=getType(uri);
   if(type.equals("image/png")){
    Bitmap bitmap=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(20,145,173));
    Paint paint=new Paint();paint.setColor(Color.rgb(246,201,113));canvas.drawRect(10,12,56,66,paint);paint.setColor(Color.WHITE);canvas.drawCircle(86,40,20,paint);
    bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();
   }else if(type.equals("application/pdf")){
    PdfDocument document=new PdfDocument();
    try{for(int i=0;i<2;i++){PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(600,800,i+1).create());Paint paint=new Paint();paint.setTextSize(24);page.getCanvas().drawText("Fixture PDF page "+(i+1),40,60,paint);document.finishPage(page);}document.writeTo(output);}finally{document.close();}
   }else output.write(("Fixture contents "+uri.getLastPathSegment()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
  if(uri.getLastPathSegment().startsWith("pdf-pipe"))try{
   ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
   new Thread(()->{try(InputStream input=new FileInputStream(file);OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
    byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
   }catch(IOException ignored){}},"fixture-pdf-pipe").start();
   return pipe[0];
  }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
  return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
 }
 public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 public int delete(Uri uri,String selection,String[] args){return 0;}
 public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}

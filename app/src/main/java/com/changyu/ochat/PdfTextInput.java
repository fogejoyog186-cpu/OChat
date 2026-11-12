package com.changyu.ochat;

import android.content.Context;
import org.json.JSONArray;
import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Local PDF text extraction was replaced by opt-in model-based document recognition. */
final class PdfTextInput {
 static JSONArray read(Context context,File source,String name,String model,BooleanSupplier active,Consumer<String> status)throws IOException{
  FileAttachmentData.checkCancelled();if(!active.getAsBoolean())throw new InterruptedIOException("附件处理已取消");
 }
 private PdfTextInput(){}
}

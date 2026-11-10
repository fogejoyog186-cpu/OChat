package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class DocumentTextTest {
 private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
 private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
 private byte[] zip(Map<String,byte[]> entries)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
  return out.toByteArray();
 }
 private byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
 private String extract(byte[] bytes,String mime,String name)throws IOException{return DocumentText.extract(new ByteArrayInputStream(bytes),mime,name);}
 @Test public void docxLargerThanThreeMiBStreamsPastEmbeddedImages()throws Exception{
  byte[] image=new byte[4*1024*1024];new Random(19).nextBytes(image);Map<String,byte[]> entries=new LinkedHashMap<>();
  entries.put("word/media/scanned-image.bin",image);entries.put("word/document.xml",utf8("<w:document><w:p><w:t>报告 &amp; 附图</w:t></w:p></w:document>"));
  byte[] document=zip(entries);assertTrue(document.length>3*1024*1024);assertTrue(extract(document,DOCX,"large.docx").contains("报告 & 附图"));
 }
 @Test public void longTextIsReadIncrementallyAndTruncationIsExplicit()throws Exception{
  byte[] bytes=new byte[4*1024*1024];Arrays.fill(bytes,(byte)'x');
  final int[] consumed={0};InputStream source=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(n>0)consumed[0]+=n;return n;}};
  String text=DocumentText.extract(source,"text/plain","large.txt");assertTrue(text.startsWith("xxx"));assertTrue(text.endsWith("[文档文字超过 200000 字符，已截断]"));assertTrue(consumed[0]<300000);
 }
 @Test public void bomEncodedJsonArrayRemainsReadableAndIsNotAFailure()throws Exception{
  byte[] value="[\"中文\",42]".getBytes(StandardCharsets.UTF_16LE),bytes=new byte[value.length+2];bytes[0]=(byte)0xff;bytes[1]=(byte)0xfe;System.arraycopy(value,0,bytes,2,value.length);
  String text=extract(bytes,"application/json","array.json");assertEquals("[\"中文\",42]",text);assertFalse(AttachmentBatch.isFailure(text));
  String error=AttachmentBatch.failure("读取失败");assertTrue(AttachmentBatch.isFailure(error));assertEquals("读取失败",AttachmentBatch.failureMessage(error));
 }
 @Test public void spreadsheetResolvesSharedStringsEvenWhenTheyFollowSheets()throws Exception{
  Map<String,byte[]> entries=new LinkedHashMap<>();
  entries.put("xl/worksheets/sheet1.xml",utf8("<worksheet><sheetData><row><c t=\"s\"><v>1</v></c><c t=\"s\"><v>0</v></c><c t=\"inlineStr\"><is><t>inline</t></is></c><c><v>42</v></c></row></sheetData></worksheet>"));
  entries.put("xl/sharedStrings.xml",utf8("<sst><si><t>first</t></si><si><r><t>第二</t></r><r><t>项</t></r></si></sst>"));
  String text=extract(zip(entries),XLSX,"data.xlsx");assertTrue(text.contains("第二项\tfirst\tinline\t42"));assertFalse(text.contains("\u0000"));
 }
 @Test public void officeTextLimitAppliesToTheEntireDocument()throws Exception{
  String run=new String(new char[150000]).replace('\0','a');
  byte[] document=zip(Collections.singletonMap("word/document.xml",utf8("<document><t>"+run+"</t><t>"+run+"</t></document>")));
  String text=extract(document,DOCX,"large.docx");assertEquals(DocumentText.MAX_TEXT_CHARS,text.indexOf("\n[文档文字超过"));assertTrue(text.length()<201000);
 }
 @Test public void rejectsDtdBeforeExpandingEntities()throws Exception{
  byte[] document=zip(Collections.singletonMap("word/document.xml",utf8("<!DOCTYPE document [<!ENTITY x \"should not expand\">]><document><t>&x;</t></document>")));
  try{extract(document,DOCX,"entities.docx");fail("DTD should be rejected");}catch(IOException expected){assertTrue(expected.getMessage().contains("DTD"));}
 }
 @Test public void cumulativeZipExpansionIncludesUnselectedEntries()throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[65536];
  try(ZipOutputStream zip=new ZipOutputStream(out)){for(int e=0;e<2;e++){zip.putNextEntry(new ZipEntry("word/media/oversized"+e+".bin"));for(int i=0;i<528;i++)zip.write(block);zip.closeEntry();}zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(utf8("<document><t>text</t></document>"));zip.closeEntry();}
  try{extract(out.toByteArray(),DOCX,"bomb.docx");fail("Expansion should be bounded");}catch(IOException expected){assertTrue(expected.getMessage().contains("64 MiB"));}
 }
 @Test public void cancellationStopsBeforeOpeningUnderlyingDocumentBytes()throws Exception{
  InputStream unreadable=new InputStream(){@Override public int read(){throw new AssertionError("Cancelled input should not be read");}};
  Thread.currentThread().interrupt();try{DocumentText.extract(unreadable,"text/plain","cancel.txt");fail();}catch(InterruptedIOException expected){assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
 }
 @Test public void modernExtensionOverridesAProvidersGenericLegacyMime()throws Exception{
  byte[] document=zip(Collections.singletonMap("word/document.xml",utf8("<document><t>readable</t></document>")));
  assertTrue(extract(document,"application/msword","report.docx").contains("readable"));
 }
 @Test public void malformedOfficeAndBinaryLegacyFormatsHaveClearErrors()throws Exception{
  try{extract(utf8("not a zip"),DOCX,"broken.docx");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("正文"));}
  try{extract(new byte[]{0,1,2},"application/msword","old.doc");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("二进制"));}
 }
}

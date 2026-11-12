package com.changyu.ochat;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

/** Streaming, bounded local text extraction. PDF page images use native model file inputs. */
final class DocumentText {
 static final int MAX_TEXT_CHARS=200000;
 static final long MAX_EXPANDED_BYTES=64L*1024*1024;
 private static final int MAX_ENTRIES=4096, MAX_SHARED_CHARS=4*1024*1024;
 private static final String TRUNCATED="\n[文档文字超过 200000 字符，已截断]";
 private DocumentText(){}

 static String extract(InputStream input,String mime,String name)throws IOException{
  if(input==null)throw new IOException("文件不可读取");
  String n=name==null?"":name.toLowerCase(Locale.ROOT),m=mime==null?"":mime.toLowerCase(Locale.ROOT);
  boolean modern=n.endsWith(".docx")||n.endsWith(".pptx")||n.endsWith(".xlsx")||m.contains("wordprocessingml")||m.contains("presentationml")||m.contains("spreadsheetml");
  if(n.endsWith(".doc")||n.endsWith(".xls")||n.endsWith(".ppt")||!modern&&(m.equals("application/msword")||m.equals("application/vnd.ms-excel")||m.equals("application/vnd.ms-powerpoint")))
   throw new IOException("旧版 Office 二进制格式不支持，请转换为 DOCX、XLSX、PPTX 或 PDF");
  if(modern)return office(input,n.endsWith(".xlsx")||m.contains("spreadsheetml"));
  String value=text(input);
  if(n.endsWith(".rtf")||m.contains("rtf"))value=value.replaceAll("\\\\'[0-9a-fA-F]{2}"," ").replaceAll("\\\\[a-zA-Z]+-?\\d* ?"," ").replaceAll("[{}]","");
  return value;
 }

 static String text(InputStream input)throws IOException{
  if(input==null)throw new IOException("文件不可读取");
  BufferedInputStream buffered=new BufferedInputStream(new BoundedSource(input));buffered.mark(3);
  int a=buffered.read(),b=buffered.read(),c=buffered.read();Charset charset=StandardCharsets.UTF_8;
  if(a==0xff&&b==0xfe){charset=StandardCharsets.UTF_16LE;buffered.reset();buffered.read();buffered.read();}
  else if(a==0xfe&&b==0xff){charset=StandardCharsets.UTF_16BE;buffered.reset();buffered.read();buffered.read();}
  else if(!(a==0xef&&b==0xbb&&c==0xbf))buffered.reset();
  Reader reader=new InputStreamReader(buffered,charset);StringBuilder out=new StringBuilder();char[] chars=new char[8192];int count;
  while(out.length()<=MAX_TEXT_CHARS&&(count=reader.read(chars,0,Math.min(chars.length,MAX_TEXT_CHARS+1-out.length())))!=-1){
   interrupted();out.append(chars,0,count);
  }
  return out.length()>MAX_TEXT_CHARS?out.substring(0,MAX_TEXT_CHARS)+TRUNCATED:out.toString();
 }

 private static String office(InputStream input,boolean spreadsheet)throws IOException{
  TextBuffer text=new TextBuffer();List<String> shared=new ArrayList<>();long[] expanded={0};int entries=0;boolean found=false;
  try(ZipInputStream zip=new ZipInputStream(new BoundedSource(input))){
   ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){
    interrupted();if(++entries>MAX_ENTRIES)throw new IOException("Office 文档包含过多压缩条目（最多 4096），请精简文件");
    String path=entry.getName();InputStream data=new ExpandedEntry(zip,expanded);
    boolean strings=spreadsheet&&path.equals("xl/sharedStrings.xml");
    boolean sheet=spreadsheet&&path.matches("xl/worksheets/sheet[^/]*\\.xml");
    boolean document=!spreadsheet&&(path.equals("word/document.xml")||path.matches("ppt/slides/slide[^/]*\\.xml"));
    if(strings){parse(data,new SharedHandler(shared));found=true;}
    else if(sheet){try{parse(data,new SheetHandler(text));}catch(TextLimit stop){text.truncated=true;}found=true;}
    else if(document){try{parse(data,new TextHandler(text));text.add("\n");}catch(TextLimit stop){text.truncated=true;}found=true;if(text.truncated)break;}
    drain(data);zip.closeEntry();
   }
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Office 文档解析失败，请检查文件是否完整",e);}
  if(!found)throw new IOException("Office 文档中没有找到可读取的正文");
  if(spreadsheet)return resolveSheet(text,shared);
  String result=text.result();if(result.trim().isEmpty())throw new IOException("Office 文档中没有提取到文字");return result;
 }

 private static void parse(InputStream input,DefaultHandler2 handler)throws IOException,TextLimit{
  try{
   SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(false);
   try{factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);}catch(Exception ignored){}
   XMLReader reader=factory.newSAXParser().getXMLReader();
   for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"})
    try{reader.setFeature(feature,false);}catch(SAXException ignored){}
   reader.setContentHandler(handler);reader.setEntityResolver(handler);
   // Fail closed if a parser cannot reject DTDs before any entity expansion.
   reader.setProperty("http://xml.org/sax/properties/lexical-handler",handler);
   reader.parse(new InputSource(input));
  }catch(TextLimit stop){throw stop;}catch(SAXException e){
   Throwable cause=e.getException();if(cause instanceof IOException)throw(IOException)cause;
   throw new IOException("Office XML 无法读取："+e.getMessage(),e);
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Office XML 解析器不可用",e);}
 }
 private static void drain(InputStream input)throws IOException{byte[] bytes=new byte[8192];while(input.read(bytes)!=-1)interrupted();}
 private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("附件读取已取消");}
 private static String local(String q){int colon=q.indexOf(':');return colon<0?q:q.substring(colon+1);}

 private static class SafeHandler extends DefaultHandler2{
  @Override public void startDTD(String name,String publicId,String systemId)throws SAXException{throw new SAXException("文档不允许包含 DTD 或外部实体");}
  @Override public InputSource resolveEntity(String publicId,String systemId)throws SAXException{throw new SAXException("文档不允许引用外部实体");}
 }
 private static final class TextHandler extends SafeHandler{
  final TextBuffer output;int textDepth;
  TextHandler(TextBuffer output){this.output=output;}
  @Override public void startElement(String uri,String name,String q,Attributes attributes)throws SAXException{String tag=local(q);if(tag.equals("t"))textDepth++;if(tag.equals("tab"))output.add("\t");if(tag.equals("br"))output.add("\n");}
  @Override public void characters(char[] chars,int start,int count)throws SAXException{if(textDepth>0)output.add(new String(chars,start,count));}
  @Override public void endElement(String uri,String name,String q)throws SAXException{String tag=local(q);if(tag.equals("t")){textDepth--;output.add(" ");}if(tag.equals("p"))output.add("\n");}
 }
 private static final class SharedHandler extends SafeHandler{
  final List<String> values;StringBuilder current;int textDepth,total;
  SharedHandler(List<String> values){this.values=values;}
  @Override public void startElement(String uri,String name,String q,Attributes attributes){String tag=local(q);if(tag.equals("si"))current=new StringBuilder();if(tag.equals("t")&&current!=null)textDepth++;}
  @Override public void characters(char[] chars,int start,int count)throws SAXException{if(current!=null&&textDepth>0){if(count>MAX_SHARED_CHARS-total)throw new SAXException("电子表格共享文字过多，请另存为 CSV");current.append(chars,start,count);total+=count;}}
  @Override public void endElement(String uri,String name,String q)throws SAXException{String tag=local(q);if(tag.equals("t")&&textDepth>0)textDepth--;if(tag.equals("si")&&current!=null){if(values.size()>=200000)throw new SAXException("电子表格共享文字条目过多，请另存为 CSV");values.add(current.toString());current=null;}}
 }
 private static final class SheetHandler extends SafeHandler{
  final TextBuffer output;String type="";StringBuilder value;int textDepth;
  SheetHandler(TextBuffer output){this.output=output;}
  @Override public void startElement(String uri,String name,String q,Attributes attributes){String tag=local(q);if(tag.equals("c")){type=attributes.getValue("t");value=new StringBuilder();}if(value!=null&&(tag.equals("v")||tag.equals("t")))textDepth++;}
  @Override public void characters(char[] chars,int start,int count)throws SAXException{if(value!=null&&textDepth>0){if(value.length()+count>MAX_TEXT_CHARS)throw new TextLimit();value.append(chars,start,count);}}
  @Override public void endElement(String uri,String name,String q)throws SAXException{
   String tag=local(q);if((tag.equals("v")||tag.equals("t"))&&textDepth>0)textDepth--;
   if(tag.equals("c")&&value!=null){String s=value.toString();if("s".equals(type)){if(!s.matches("\\d{1,9}"))throw new SAXException("电子表格共享字符串索引无效");output.atomic("\u0000"+s+"\u0000");}else output.add(s);output.add("\t");value=null;}
   if(tag.equals("row"))output.add("\n");
  }
 }
 private static String resolveSheet(TextBuffer raw,List<String> shared)throws IOException{
  TextBuffer out=new TextBuffer();String value=raw.value.toString();int position=0;
  try{while(position<value.length()){int begin=value.indexOf('\u0000',position);if(begin<0){out.add(value.substring(position));break;}out.add(value.substring(position,begin));int end=value.indexOf('\u0000',begin+1);if(end<0)throw new IOException("电子表格共享字符串索引不完整");int index;try{index=Integer.parseInt(value.substring(begin+1,end));}catch(NumberFormatException error){throw new IOException("电子表格共享字符串索引无效");}if(index<0||index>=shared.size())throw new IOException("电子表格缺少引用的共享字符串");out.add(shared.get(index));position=end+1;}}catch(TextLimit stop){out.truncated=true;}
  out.truncated|=raw.truncated;String result=out.result();if(result.trim().isEmpty())throw new IOException("电子表格中没有提取到文字");return result;
 }
 private static final class TextLimit extends SAXException{}
 private static final class TextBuffer{
  final StringBuilder value=new StringBuilder();boolean truncated;
  void add(String text)throws TextLimit{if(text.length()>MAX_TEXT_CHARS-value.length()){int count=MAX_TEXT_CHARS-value.length();if(count>0)value.append(text,0,count);truncated=true;throw new TextLimit();}value.append(text);}
  void atomic(String text)throws TextLimit{if(text.length()>MAX_TEXT_CHARS-value.length()){truncated=true;throw new TextLimit();}value.append(text);}
  String result(){return value.toString()+(truncated?TRUNCATED:"");}
 }
 private static final class BoundedSource extends FilterInputStream{
  BoundedSource(InputStream input){super(input);}
  private void counted(int n)throws IOException{interrupted();}
  @Override public int read()throws IOException{interrupted();int n=in.read();counted(n<0?0:1);return n;}
  @Override public int read(byte[] b,int off,int len)throws IOException{interrupted();int n=in.read(b,off,len);counted(n);return n;}
  @Override public long skip(long count)throws IOException{long done=0;byte[] bytes=new byte[8192];while(done<count){int n=read(bytes,0,(int)Math.min(bytes.length,count-done));if(n<=0)break;done+=n;}return done;}
  @Override public void close(){} // The ContentResolver stream belongs to the caller.
 }
 private static final class ExpandedEntry extends FilterInputStream{
  final long[] total;long entry;
  ExpandedEntry(InputStream input,long[] total){super(input);this.total=total;}
  private void counted(int n)throws IOException{interrupted();if(n>0){entry+=n;total[0]+=n;if(entry>MAX_EXPANDED_BYTES||total[0]>MAX_EXPANDED_BYTES)throw new IOException("Office 解压内容超过 64 MiB 安全限制，请拆分文档");}}
  @Override public int read()throws IOException{interrupted();int n=in.read();counted(n<0?0:1);return n;}
  @Override public int read(byte[] b,int off,int len)throws IOException{interrupted();int n=in.read(b,off,len);counted(n);return n;}
  @Override public long skip(long count)throws IOException{long done=0;byte[] bytes=new byte[8192];while(done<count){int n=read(bytes,0,(int)Math.min(bytes.length,count-done));if(n<=0)break;done+=n;}return done;}
  @Override public void close(){}
 }
}

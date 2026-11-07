package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.text.*;
import android.text.style.ClickableSpan;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import io.noties.markwon.*;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Markdown renderer with segregated fenced code handling. Code source never touches preprocessing. */
final class MarkdownRenderer {
 // Preserve open fences too: a streaming snapshot is valid Markdown before its closing fence arrives.
 private static final Pattern OPEN_FENCE=Pattern.compile("(`{3,}|~{3,})([^\\r\\n]*)\\r?\\n");
 static final class FencedSource {final String raw,language;FencedSource(String r,String l){raw=r;language=l;}}
 static final class Fence {
  int start,end,bodyStart,bodyEnd;String language;boolean closed;
 }
 static List<Fence> fences(String source){
  List<Fence> blocks=new ArrayList<>();Matcher open=OPEN_FENCE.matcher(source);int cursor=0;
  while(open.find(cursor)){
   String marker=open.group(1),info=open.group(2).trim();Fence block=new Fence();
   block.start=open.start();block.bodyStart=open.end();
   block.language=info.isEmpty()?"代码":info.split("\\s+",2)[0];
   Pattern closing=Pattern.compile("(?m)^[ \\t]{0,3}"+Pattern.quote(marker.substring(0,1))+"{"+marker.length()+",}[ \\t]*(?:\\r?\\n|$)");
   Matcher close=closing.matcher(source);block.closed=close.find(block.bodyStart);
   block.bodyEnd=block.closed?close.start():source.length();block.end=block.closed?close.end():source.length();
   blocks.add(block);cursor=block.end;if(!block.closed||cursor>=source.length())break;
  }
  return blocks;
 }
 static List<FencedSource> extractFencedSources(String source){
  List<FencedSource> result=new ArrayList<>();if(source==null)return result;
  for(Fence block:fences(source))if(block.closed){
   String raw=source.substring(block.bodyStart,block.bodyEnd);
   if(raw.endsWith("\r\n"))raw=raw.substring(0,raw.length()-2);else if(raw.endsWith("\n"))raw=raw.substring(0,raw.length()-1);
   result.add(new FencedSource(raw,block.language));
  }
  return result;
 }
 static String processMarkdown(String source){
  if(source==null||source.isEmpty())return "";
  StringBuilder result=new StringBuilder();int cursor=0;
  for(Fence block:fences(source)){
   result.append(processProse(source.substring(cursor,block.start)));
   result.append(source,block.start,block.end);cursor=block.end;
  }
  return result.append(processProse(source.substring(cursor))).toString();
 }
 private static String processProse(String source){
  return MathDelimiters.normalize(BoldCompatibility.normalize(sanitizeHtml(source)));
 }

 /** Safe conversion for ordinary (non-fenced) assistant HTML. Fences are already extracted. */
 static String sanitizeHtml(String source){
  if(source==null)return "";
  String s=source;
  s=s.replaceAll("(?is)<(script|style|iframe|object|embed|noscript|template)\\b[^>]*>.*?</\\1\\s*>","");
  s=s.replaceAll("(?is)\\s+(?:on[a-z]+|srcdoc)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)","");
  s=s.replaceAll("(?is)(href|src)\\s*=\\s*(['\"])(?:javascript:|data:|file:|content:|https?:)[^'\"]*\\2","");
  s=s.replaceAll("(?is)<img\\b[^>]*\\balt\\s*=\\s*(['\"])(.*?)\\1[^>]*>","[$2]").replaceAll("(?is)<img\\b[^>]*>","[图片]");
  s=s.replaceAll("(?is)<h[1-6]\\b[^>]*>(.*?)</h[1-6]>","\n\n**$1**\n\n").replaceAll("(?is)<(strong|b)\\b[^>]*>(.*?)</\\1>","**$2**").replaceAll("(?is)<(em|i)\\b[^>]*>(.*?)</\\1>","*$2*").replaceAll("(?is)<(del|s)\\b[^>]*>(.*?)</\\1>","~~$2~~");
  s=s.replaceAll("(?is)<br\\s*/?>","\n").replaceAll("(?is)</?(p|div|section|article|header|footer|main|table|tr|td|th)\\b[^>]*>","\n\n").replaceAll("(?is)<li\\b[^>]*>","\n- ").replaceAll("(?is)</li>","").replaceAll("(?is)<blockquote\\b[^>]*>","\n> ").replaceAll("(?is)</blockquote>","\n");
  return s.replaceAll("(?is)<[^>]+>","");
 }
 
 private static final class CopyNode extends org.commonmark.node.CustomNode { final String raw,language; CopyNode(String r,String l){raw=r;language=l;} }
 
 static final class CodeCopySpan extends ClickableSpan {
  final String raw;
  CodeCopySpan(String r){raw=r;}
  public void onClick(View v){
   try{
    android.content.ClipboardManager m=(android.content.ClipboardManager)v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if(m==null)throw new IllegalStateException();
    m.setPrimaryClip(ClipData.newPlainText("代码",raw));
    Toast.makeText(v.getContext(),"代码已复制",Toast.LENGTH_SHORT).show();
   }catch(Exception e){
    Toast.makeText(v.getContext(),"复制失败：剪贴板不可用",Toast.LENGTH_SHORT).show();
   }
  }
  public void updateDrawState(android.text.TextPaint p){p.setUnderlineText(false);}
 }
 
 static boolean isPreviewLanguage(String language){return "svg".equalsIgnoreCase(language)||"html".equalsIgnoreCase(language)||"xhtml".equalsIgnoreCase(language);}
 static final class PreviewSpan extends ClickableSpan {
  final String raw,language;
  PreviewSpan(String r,String l){raw=r;language=l;}
  public void onClick(View v){showPreview(v.getContext(),raw,language);}
  public void updateDrawState(android.text.TextPaint p){p.setUnderlineText(false);}
 }
 
 // Preserve SVG references, styles, fonts and remote images. WebSettings isolates local files.
 static String sanitizePreviewDocument(String source){
  String s=source==null?"":source;
  if(Pattern.compile("(?i)<!doctype\\s+html|<html\\b").matcher(s).find())return s;
  boolean svg=Pattern.compile("(?is)<svg\\b").matcher(s).find();
  String canvas=svg?"body{min-height:100vh;display:grid;place-items:center;box-sizing:border-box;background-color:#fff;background-image:conic-gradient(#f1f4f6 25%,transparent 0 50%,#f1f4f6 0 75%,transparent 0);background-size:20px 20px}body>svg{display:block;width:100%;max-height:calc(100vh - 32px);height:auto}":"";
  return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>html{color-scheme:light}body{margin:0;padding:16px;background:#fff;color:#202a35;font:16px/1.6 sans-serif;overflow-wrap:anywhere}svg,img{max-width:100%;height:auto}pre{white-space:pre-wrap}"+canvas+"</style></head><body>"+s+"</body></html>";
 }

 static void showPreview(Context context,String raw,String language){CodePreview.show(context,raw,language);}

 static boolean isSafeLink(String value){
  if(value==null||value.matches("(?s).*[\\p{Cntrl}\\s\\\\].*"))return false;
  try{
   URI p=new URI(value);
   String s=p.getScheme();
   return ("http".equalsIgnoreCase(s)||"https".equalsIgnoreCase(s))&&p.getHost()!=null&&p.getRawUserInfo()==null&&(p.getPort()==-1||p.getPort()>0&&p.getPort()<=65535);
  }catch(Exception e){return false;}
 }
 
 static void render(Markwon renderer,TextView view,String source){
  view.setTextIsSelectable(true);
  view.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
  view.setLinksClickable(true);
  view.setOnTouchListener(new SelectableSpanTouch());
  renderer.setMarkdown(view,source==null?"":source);
 }
 
 static Markwon create(Context context){
  ThemePalette palette=ThemePalette.resolve(context);
  return Markwon.builder(context)
   .usePlugin(TablePlugin.create(context))
   .usePlugin(StrikethroughPlugin.create())
   .usePlugin(io.noties.markwon.inlineparser.MarkwonInlineParserPlugin.create())
   .usePlugin(io.noties.markwon.ext.latex.JLatexMathPlugin.create(15*context.getResources().getDisplayMetrics().scaledDensity,b->{
    b.inlinesEnabled(true).errorHandler((x,e)->null).theme().textColor(palette.ink).blockFitCanvas(true);
   }))
   .usePlugin(new AbstractMarkwonPlugin(){
    private final ThreadLocal<Boolean> guards=new ThreadLocal<>();
    public String processMarkdown(String markdown){
     guards.set(markdown.indexOf(BoldCompatibility.GUARD)<0);
     return MarkdownRenderer.processMarkdown(markdown);
    }
    public void beforeRender(org.commonmark.node.Node n){
     if(Boolean.TRUE.equals(guards.get()))BoldCompatibility.removeGuards(n);
     guards.remove();
    }
   })
   .usePlugin(new AbstractMarkwonPlugin(){
    public void configureSpansFactory(MarkwonSpansFactory.Builder b){
     b.appendFactory(org.commonmark.node.StrongEmphasis.class,(c,p)->new android.text.style.StyleSpan(android.graphics.Typeface.BOLD));
     int border=androidx.core.graphics.ColorUtils.blendARGB(palette.surface,palette.ink,palette.dark?.28f:.20f);
     float stroke=context.getResources().getDisplayMetrics().density;
     b.appendFactory(org.commonmark.node.FencedCodeBlock.class,(c,p)->new CodeBlockBorderSpan(border,stroke));
     b.appendFactory(org.commonmark.node.IndentedCodeBlock.class,(c,p)->new CodeBlockBorderSpan(border,stroke));
    }
    public void configureTheme(MarkwonTheme.Builder b){
     b.linkColor(palette.primary)
      .headingBreakColor(palette.muted)
      .thematicBreakColor(palette.muted)
      .codeTextColor(palette.ink)
      .codeBackgroundColor(androidx.core.graphics.ColorUtils.blendARGB(palette.surface,palette.primary,.16f))
      .codeBlockTextColor(palette.ink)
      .codeBlockBackgroundColor(androidx.core.graphics.ColorUtils.blendARGB(palette.surface,palette.ink,palette.dark?.07f:.035f))
      .codeTypeface(android.graphics.Typeface.MONOSPACE);
    }
    public void configureVisitor(MarkwonVisitor.Builder b){
     b.on(CopyNode.class,(v,node)->{
      int languageStart=v.length();
      v.builder().append(node.language.toUpperCase(Locale.ROOT));
      v.setSpans(languageStart,new android.text.style.ForegroundColorSpan(palette.primary));
      v.setSpans(languageStart,new android.text.style.StyleSpan(android.graphics.Typeface.BOLD));
      v.setSpans(languageStart,new android.text.style.RelativeSizeSpan(.8f));
      v.builder().append("   ");
      int copyStart=v.length();v.builder().append("复制源码");
      v.setSpans(copyStart,new CodeCopySpan(node.raw));
      v.setSpans(copyStart,new android.text.style.ForegroundColorSpan(palette.primary));
      v.setSpans(copyStart,new android.text.style.RelativeSizeSpan(.85f));
      if(isPreviewLanguage(node.language)){
       v.builder().append("   ");int previewStart=v.length();v.builder().append("预览图形");
       v.setSpans(previewStart,new PreviewSpan(node.raw,node.language));
       v.setSpans(previewStart,new android.text.style.ForegroundColorSpan(palette.primary));
       v.setSpans(previewStart,new android.text.style.RelativeSizeSpan(.85f));
      }
     });
    }
    public void beforeRender(org.commonmark.node.Node n){
     n.accept(new org.commonmark.node.AbstractVisitor(){
      public void visit(org.commonmark.node.FencedCodeBlock code){
       String info=code.getInfo();
       String lang=info==null||info.trim().isEmpty()?"代码":info.trim().split("\\s+",2)[0];
       org.commonmark.node.Paragraph cap=new org.commonmark.node.Paragraph();
       cap.appendChild(new CopyNode(code.getLiteral(),lang));
       code.insertBefore(cap);
      }
     });
    }
    public void configureConfiguration(MarkwonConfiguration.Builder b){
     b.linkResolver((v,l)->SafeLinks.open(v.getContext(),l));
    }
   })
   .build();
 }
 
 private static android.graphics.drawable.GradientDrawable createRoundDrawable(Context context,int color,int radius){
  android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
  d.setColor(color);
  d.setCornerRadius(radius);
  return d;
 }
 
 private static int dp(Context context,int n){
  return(int)(n*context.getResources().getDisplayMetrics().density+.5f);
 }
}

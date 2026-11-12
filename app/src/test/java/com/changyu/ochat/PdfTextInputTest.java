package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/** Regression for the replacement of local PDF text extraction with configured model recognition. */
public class PdfTextInputTest {
 private JSONObject model()throws Exception{return new JSONObject().put(DocumentOcr.PROVIDER,"reader").put(DocumentOcr.MODEL,"vision");}
 private JSONArray providers(String base,String model)throws Exception{return new JSONArray().put(new JSONObject().put("id","reader").put("api",base).put("apiKey","fixture").put("protocol",ChatApiClient.RESPONSES).put("models",new JSONArray().put(ModelStore.model(model))));}
 @Test public void documentRecognitionIsOptIn()throws Exception{assertFalse(DocumentOcr.enabled(new JSONObject()));assertTrue(DocumentOcr.enabled(model().put(DocumentOcr.ENABLED,true)));}
 @Test public void resolvesOnlyTheConfiguredTargetAndDoesNotCopyCredentials()throws Exception{
  JSONObject config=model();DocumentOcr.Target target=DocumentOcr.resolve(providers("https://reader.invalid/v1","vision"),config,"https://original.invalid/v1","text");
  assertEquals("vision",target.id);assertEquals("fixture",target.key);assertFalse(config.has("apiKey"));
 }
 @Test public void missingOrDeletedTargetFailsInsteadOfSilentlyChoosingAnother()throws Exception{
  try{DocumentOcr.resolve(new JSONArray(),model(),"https://original.invalid","text");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("已删除"));}
 }
 @Test public void sameEndpointAndModelCannotRecognizeItself()throws Exception{
  try{DocumentOcr.resolve(providers("https://original.invalid/","vision"),model(),"https://original.invalid","vision");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("不能与当前模型相同"));}
 }
 @Test public void knownTextOnlyDeepSeekCannotBeAnOcrTarget()throws Exception{
  try{DocumentOcr.resolve(providers("https://api.deepseek.com","deepseek-v4-pro"),model().put(DocumentOcr.MODEL,"deepseek-v4-pro"),"https://other.invalid","text");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("不支持图像"));}
 }
 @Test public void ordinaryTextIsNeverSentForRecognition()throws Exception{
  JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","private question"))));
  assertFalse(DocumentOcr.hasMedia(history));
 }
}

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
 @Test public void legacySwitchEnablesPdfButNeverEnablesStandaloneImageRecognition()throws Exception{
  JSONObject legacy=model().put(DocumentOcr.ENABLED,true);assertTrue(DocumentOcr.enabled(legacy,true));assertFalse(DocumentOcr.enabled(legacy,false));
  legacy.put(DocumentOcr.IMAGE_ENABLED,true).put(DocumentOcr.ENABLED,false);assertFalse(DocumentOcr.enabled(legacy,true));assertTrue(DocumentOcr.enabled(legacy,false));
 }
 @Test public void imageTargetUsesItsOwnProviderAndIsNotRequiredForPdfOnlyHistory()throws Exception{
  JSONObject config=model().put(DocumentOcr.IMAGE_PROVIDER,"image-reader").put(DocumentOcr.IMAGE_MODEL,"image-model").put(DocumentOcr.ENABLED,true);
  JSONArray all=providers("https://reader.invalid/v1","vision");all.put(new JSONObject().put("id","image-reader").put("api","https://image.invalid/v1").put("apiKey","image-fixture").put("models",new JSONArray().put(ModelStore.model("image-model"))));
  assertEquals("image-model",DocumentOcr.resolve(all,config,"https://original.invalid","text",false).id);
  JSONArray pdf=new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","input_file").put("file_data","data:application/pdf;base64,AQID"))));
  config.put(DocumentOcr.IMAGE_ENABLED,true).put(DocumentOcr.IMAGE_MODEL,"deleted");
  DocumentOcr.Plan plan=DocumentOcr.plan(all,config,"https://original.invalid","text",pdf);assertNotNull(plan.pdf);assertNull(plan.image);
 }
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

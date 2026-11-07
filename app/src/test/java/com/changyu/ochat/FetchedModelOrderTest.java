package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FetchedModelOrderTest {
 @Test public void fetchedChoicesSortByVisibleIdWithoutChangingInput()throws Exception{
  JSONArray data=new JSONArray().put("zeta").put(new JSONObject().put("id","Beta").put("displayName","not the fetched label")).put("alpha").put("ALPHA").put("gamma");
  String before=data.toString();
  assertArrayEquals(new String[]{"alpha","ALPHA","Beta","gamma","zeta"},ModelStore.sortedFetchedIds(data));
  assertEquals(before,data.toString());
 }
 @Test public void selectedSortedChoicesAddTheirActualIdsAndKeepConfiguredSelection()throws Exception{
  JSONObject provider=new JSONObject().put("selectedModelId","keep").put("models",new JSONArray().put(ModelStore.model("keep")));
  String[] ids=ModelStore.sortedFetchedIds(new JSONArray().put("zeta").put("alpha").put("Beta"));
  ModelStore.addChecked(provider,ids,new boolean[]{true,false,true});
  JSONArray configured=provider.getJSONArray("models");
  assertEquals(3,configured.length());assertEquals("keep",configured.getJSONObject(0).getString("id"));
  assertEquals("alpha",configured.getJSONObject(1).getString("id"));assertEquals("zeta",configured.getJSONObject(2).getString("id"));
  assertEquals("keep",provider.getString("selectedModelId"));
 }
}

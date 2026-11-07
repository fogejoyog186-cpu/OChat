package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionLaunchPolicyTest {
 @Test public void ordinaryActivityRecreationDoesNotRequestFreshConversation(){
  SessionLaunchPolicy.markTaskRemoved();assertTrue(SessionLaunchPolicy.claimColdStart());
  assertFalse(SessionLaunchPolicy.claimColdStart());assertFalse(SessionLaunchPolicy.claimColdStart());
 }
 @Test public void clearingTaskRequestsFreshConversationEvenIfProcessSurvives(){
  SessionLaunchPolicy.claimColdStart();SessionLaunchPolicy.markTaskRemoved();
  assertTrue(SessionLaunchPolicy.claimColdStart());assertFalse(SessionLaunchPolicy.claimColdStart());
 }
}

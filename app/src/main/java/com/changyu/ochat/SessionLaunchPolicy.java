package com.changyu.ochat;

/** One fresh conversation per app process, rather than per Activity recreation or settings return. */
final class SessionLaunchPolicy {
 private static boolean entered;
 static synchronized boolean claimColdStart(){boolean cold=!entered;entered=true;return cold;}
 static synchronized void markTaskRemoved(){entered=false;}
 private SessionLaunchPolicy(){}
}

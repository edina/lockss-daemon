package org.lockss.safenet;

import org.lockss.app.LockssManager;

public interface EntitlementRegistryClient extends LockssManager {
  boolean isUserEntitled(String issn, String institution, String start, String end);
}

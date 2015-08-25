package org.lockss.safenet;

import java.io.IOException;

import org.lockss.app.LockssManager;

public interface EntitlementRegistryClient extends LockssManager {
  boolean isUserEntitled(String issn, String institution, String start, String end) throws IOException;
  PublisherWorkflow getPublisherWorkflow(String publisherName) throws IOException;

  enum PublisherWorkflow { PRIMARY_SAFENET, PRIMARY_PUBLISHER, LIBRARY_NOTIFICATION };
}

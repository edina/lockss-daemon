package org.lockss.entitlement;

import java.io.IOException;
import java.util.Map;

import org.lockss.app.LockssManager;

import com.fasterxml.jackson.databind.JsonNode;

public interface EntitlementRegistryClient extends LockssManager {
  Map isUserEntitled(String issn, String affiliations, String start, String end) throws IOException;
  String getPublisher(String issn, String institution, String start, String end) throws IOException;
  PublisherWorkflow getPublisherWorkflow(String publisherGuid) throws IOException;
}

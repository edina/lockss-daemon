package org.lockss.safenet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.lockss.safenet.PublisherWorkflow;

import org.apache.commons.collections.map.MultiKeyMap;

public class MockEntitlementRegistryClient extends BaseEntitlementRegistryClient {
  public void expectEntitled(String issn, String institution, String start, String end) {
    entitlements.put(issn, institution, start, end, true);
  }

  public void expectUnentitled(String issn, String institution, String start, String end) {
    entitlements.put(issn, institution, start, end, false);
  }

  public void expectError(String issn, String institution, String start, String end) {
    entitlements.put(issn, institution, start, end, new IOException("Could not contact entitlement registry"));
  }

  public void expectWorkflow(String publisher, PublisherWorkflow workflow) {
    workflows.put(publisher, workflow);
  }

  public void expectPublisher(String issn, String start, String end, String publisher) {
    publishers.put(issn, start, end, publisher);
  }

  private MultiKeyMap entitlements = new MultiKeyMap();
  private MultiKeyMap publishers = new MultiKeyMap();
  private Map<String, Object> workflows = new HashMap<String, Object>();

  @Override
  public boolean isUserEntitled(String issn, String institution, String start, String end) throws IOException {
    Object result = entitlements.get(issn, institution, start, end);
    if (result instanceof IOException) {
      throw new IOException(((IOException) result).getMessage());
    }
    return (Boolean) result;
  }

  @Override
  public PublisherWorkflow getPublisherWorkflow(String publisher) throws IOException {
    Object result = workflows.get(publisher);
    if (result instanceof IOException) {
      throw (IOException) result;
    }
    return (PublisherWorkflow) result;
  }

  @Override
  public String getPublisher(String issn, String start, String end) throws IOException {
    Object result = publishers.get(issn, start, end);
    if (result instanceof IOException) {
      throw new IOException(((IOException) result).getMessage());
    }
    return (String) result;
  }
}


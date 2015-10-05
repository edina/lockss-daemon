package org.lockss.safenet;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import org.lockss.safenet.PublisherWorkflow;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.test.MockLockssUrlConnection;
import org.lockss.test.StringInputStream;
import org.lockss.util.urlconn.LockssUrlConnection;

public class TestEntitlementRegistryClient extends LockssTestCase {
  private MyMockEntitlementRegistryClient client;

  private Map<String,String> validEntitlementParams;
  private Map<String,String> validPublisherParams;

  public void setUp() throws Exception {
    super.setUp();
    client = new MyMockEntitlementRegistryClient();
    MockLockssDaemon daemon = getMockLockssDaemon();
    daemon.setEntitlementRegistryClient(client);
    daemon.setDaemonInited(true);
    Properties p = new Properties();
    p.setProperty(BaseEntitlementRegistryClient.PARAM_ER_URI, "http://dev-safenet.edina.ac.uk");
    p.setProperty(BaseEntitlementRegistryClient.PARAM_ER_APIKEY, "00000000-0000-0000-0000-000000000000");
    ConfigurationUtil.setCurrentConfigFromProps(p);
    client.initService(daemon);
    client.startService();

    validEntitlementParams = new HashMap<String, String>();
    validEntitlementParams.put("api_key", "00000000-0000-0000-0000-000000000000");
    validEntitlementParams.put("identifier_value", "0123-456X");
    validEntitlementParams.put("institution", "11111111-1111-1111-1111-111111111111");
    validEntitlementParams.put("start", "20120101");
    validEntitlementParams.put("end", "20151231");

    validPublisherParams = new HashMap<String, String>();
    validPublisherParams.put("name", "Wiley");
  }

  public void testEntitlementRegistryError() throws Exception {
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 500, "Internal server error");

    try {
      client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertEquals("Error communicating with entitlement registry. Response was 500 null", e.getMessage());
    }
    client.checkDone();
  }

  public void testEntitlementRegistryInvalidResponse() throws Exception {
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 200, "[]");

    try {
      client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertEquals("No matching entitlements returned from entitlement registry", e.getMessage());
    }
    client.checkDone();
  }

  public void testEntitlementRegistryInvalidJson() throws Exception {
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 200, "[{\"this\": isn't, JSON}]");

    try {
      client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertTrue(e.getMessage().startsWith("Unrecognized token 'isn': was expecting ('true', 'false' or 'null')"));
    }
    client.checkDone();
  }

  public void testEntitlementRegistryUnexpectedJson() throws Exception {
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 200, "{\"surprise\": \"object\"}");

    try {
      client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertTrue(e.getMessage().startsWith("No matching entitlements returned from entitlement registry"));
    }
    client.checkDone();
  }

  public void testUserEntitled() throws Exception {
    Map<String, String> responseParams = new HashMap<String,String>(validEntitlementParams);
    responseParams.remove("api_key");
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 200, "[" + client.mapToJson(responseParams) + "]");

    assertTrue(client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231"));
    client.checkDone();
  }

  public void testUserNotEntitled() throws Exception {
    client.expectAndReturn("/entitlements", client.mapToPairs(validEntitlementParams), 204, "");

    assertFalse(client.isUserEntitled("0123-456X", "11111111-1111-1111-1111-111111111111", "20120101", "20151231"));
    client.checkDone();
  }

  public void testGetPublisher() throws Exception {
    Map<String, String> queryParams = new HashMap<String, String>();
    queryParams.put("identifier", "0123-456X");
    Map<String, String> publisher = new HashMap<String, String>();
    publisher.put("id", "33333333-0000-0000-0000-000000000000");
    publisher.put("start", null);
    publisher.put("end", null);
    List<Map<String, String>> publishers = new ArrayList<Map<String, String>>();
    publishers.add(publisher);
    Map<String, Object> responseParams = new HashMap<String, Object>();
    responseParams.put("publishers", publishers);

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", "20120101", "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", null, "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", "20120101", null));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", null, null));

    publisher.put("start", "20120101");
    publisher.put("end", "20151231");

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", "20120101", "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", "20111231", "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", "20120101", "20160101"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", "20120102", "20151230"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", null, "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", "20120101", null));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", null, null));

    client.checkDone();
  }

  public void testGetPublisherNoResponse() throws Exception {
    Map<String, String> queryParams = new HashMap<String, String>();
    queryParams.put("identifier", "0123-456X");
    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[]");
    assertEquals(null, client.getPublisher("0123-456X", "20120101", "20151231"));
  }

  public void testGetPublisherMultipleResponses() throws Exception {
    Map<String, String> queryParams = new HashMap<String, String>();
    queryParams.put("identifier", "0123-456X");
    Map<String, String> publisher = new HashMap<String, String>();
    publisher.put("id", "33333333-0000-0000-0000-000000000000");
    publisher.put("start", "20120101");
    publisher.put("end", "20151231");
    Map<String, String> publisher2 = new HashMap<String, String>();
    publisher2.put("id", "33333333-1111-1111-1111-111111111111");
    publisher2.put("start", "20160101");
    publisher2.put("end", null);
    List<Map<String, String>> publishers = new ArrayList<Map<String, String>>();
    publishers.add(publisher);
    publishers.add(publisher2);
    Map<String, Object> responseParams = new HashMap<String, Object>();
    responseParams.put("publishers", publishers);

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-0000-0000-0000-000000000000", client.getPublisher("0123-456X", "20150101", "20151231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals("33333333-1111-1111-1111-111111111111", client.getPublisher("0123-456X", "20160101", "20161231"));

    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    assertEquals(null, client.getPublisher("0123-456X", "20150101", "20161231"));

    publisher2.put("start", null);
    client.expectAndReturn("/titles", client.mapToPairs(queryParams), 200, "[" + client.mapToJson(responseParams) + "]");
    try {
      client.getPublisher("0123-456X", "20150101", "20151231");
      fail("Expected exception not thrown");
    }
    catch (IOException e) {
      assertEquals("Multiple matching publishers returned from entitlement registry", e.getMessage());
    }

    client.checkDone();
  }

  public void testGetPublisherWorkflow() throws Exception {
    Map<String, String> responseParams = new HashMap<String,String>(validPublisherParams);
    responseParams.put("workflow", "primary_safenet");
    client.expectAndReturn("/publishers", client.mapToPairs(validPublisherParams), 200, "[" + client.mapToJson(responseParams) + "]");

    assertEquals(PublisherWorkflow.PRIMARY_SAFENET, client.getPublisherWorkflow("Wiley"));
    client.checkDone();
  }

  public void testGetPublisherWorkflowMissingWorkflow() throws Exception {
    Map<String, String> responseParams = new HashMap<String,String>(validPublisherParams);
    client.expectAndReturn("/publishers", client.mapToPairs(validPublisherParams), 200, "[" + client.mapToJson(responseParams) + "]");

    try {
      client.getPublisherWorkflow("Wiley");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertTrue(e.getMessage().startsWith("No valid workflow returned from entitlement registry"));
    }
    client.checkDone();
  }

  public void testGetPublisherWorkflowInvalidWorkflow() throws Exception {
    Map<String, String> responseParams = new HashMap<String,String>(validPublisherParams);
    responseParams.put("workflow", "gibberish");
    client.expectAndReturn("/publishers", client.mapToPairs(validPublisherParams), 200, "[" + client.mapToJson(responseParams) + "]");

    try {
      client.getPublisherWorkflow("Wiley");
      fail("Expected exception not thrown");
    }
    catch(IOException e) {
      assertTrue(e.getMessage().startsWith("No valid workflow returned from entitlement registry"));
    }
    client.checkDone();
  }

  private static class MyMockEntitlementRegistryClient extends BaseEntitlementRegistryClient {
    private static class Expectation {
      private String endpoint;
      private List<NameValuePair> params;
      private int responseCode;
      private String response;
    }

    private Queue<Expectation> expected = new LinkedList<Expectation>();

    public void expectAndReturn(String endpoint, List<NameValuePair> params, int responseCode, String response) {
      Expectation e = new Expectation();
      e.endpoint = endpoint;
      e.params = params;
      e.responseCode = responseCode;
      e.response = response;
      expected.add(e);
    }

    public void checkDone() {
      assertEmpty(expected);
    }

    protected LockssUrlConnection openConnection(String url) throws IOException {
      try {
        URIBuilder builder = new URIBuilder(url);
        assertEquals("http", builder.getScheme());
        assertEquals("dev-safenet.edina.ac.uk", builder.getHost());
        Expectation e = expected.poll();
        assertEquals(e.endpoint, builder.getPath());
        assertSameElements(e.params, builder.getQueryParams());
        MockLockssUrlConnection connection = new MockLockssUrlConnection(url);
        connection.setResponseCode(e.responseCode);
        connection.setResponseInputStream(new StringInputStream(e.response));
        return connection;
      }
      catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    public String mapToJson(Map<String, ? extends Object> params) throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(params);
    }


  }
}



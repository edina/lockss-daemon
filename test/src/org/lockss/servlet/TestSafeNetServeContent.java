/*
 * $Id$
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.servlet;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Tdb;
import org.lockss.config.TdbAu;
import org.lockss.daemon.TitleConfig;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.safenet.BaseEntitlementRegistryClient;
import org.lockss.safenet.EntitlementRegistryClient;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.MockArchivalUnit;
import org.lockss.test.MockCachedUrl;
import org.lockss.test.MockLockssDaemon;
import org.lockss.test.MockNodeManager;
import org.lockss.test.MockPlugin;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.httpunit.WebTable;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.servletunit.InvocationContext;

public class TestSafeNetServeContent extends LockssServletTestCase {

  private MockPluginManager pluginMgr = null;
  private MockEntitlementRegistryClient entitlementRegistryClient = null;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();
    pluginMgr = new MockPluginManager(theDaemon);
    theDaemon.setPluginManager(pluginMgr);
    theDaemon.setIdentityManager(new org.lockss.protocol.MockIdentityManager());
    entitlementRegistryClient = new MockEntitlementRegistryClient();
    theDaemon.setEntitlementRegistryClient(entitlementRegistryClient);
    theDaemon.getServletManager();
    theDaemon.setDaemonInited(true);
    theDaemon.setAusStarted(true);
    theDaemon.getRemoteApi().startService();

    pluginMgr.initService(theDaemon);
    pluginMgr.startService();

    ConfigurationUtil.addFromArgs(ConfigManager.PARAM_PLATFORM_PROJECT, "safenet");
    ConfigurationUtil.addFromArgs(SafeNetServeContent.PARAM_MISSING_FILE_ACTION, "Redirect");
    //ConfigurationUtil.addFromArgs("org.lockss.log.default.level", "debug3");
  }

  private MockArchivalUnit makeAu() throws Exception {
    return makeAu(null);
  }

  private MockArchivalUnit makeAu(Properties override) throws Exception {
    Plugin plugin = new MockPlugin(theDaemon);
    Tdb tdb = new Tdb();

    // Tdb with values for some metadata fields
    Properties tdbProps = new Properties();
    tdbProps.setProperty("title", "Air and Space Volume 1");
    tdbProps.setProperty("journalTitle", "Air and Space");
    tdbProps.setProperty("attributes.isbn", "976-1-58562-317-7");
    tdbProps.setProperty("issn", "0740-2783");
    tdbProps.setProperty("eissn", "0740-2783");
    tdbProps.setProperty("attributes.year", "2014");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.0135/12345678]");
    tdbProps.setProperty("attributes.provider", "Provider[10.0135/12345678]");

    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://dev-safenet.edina.ac.uk/test_journal/");
    tdbProps.setProperty("param.2.key", "volume");
    tdbProps.setProperty("param.2.value", "vol1");
    tdbProps.setProperty("plugin", plugin.getClass().toString());

    if(override != null) {
      tdbProps.putAll(override);
    }

    TdbAu tdbAu = tdb.addTdbAuFromProperties(tdbProps);
    TitleConfig titleConfig = new TitleConfig(tdbAu, plugin);
    MockArchivalUnit au = new MockArchivalUnit(plugin, "TestAU");
    au.setStartUrls(Arrays.asList("http://dev-safenet.edina.ac.uk/test_journal/"));
    au.setTitleConfig(titleConfig);
    return au;
  }

  protected void initServletRunner() {
    super.initServletRunner();
    sRunner.setServletContextAttribute(ServletManager.CONTEXT_ATTR_SERVLET_MGR, new ContentServletManager());
    sRunner.registerServlet("/SafeNetServeContent", SafeNetServeContent.class.getName() );
    sRunner.registerServlet("/test_journal/", RedirectServlet.class.getName());
  }

  public void testIndex() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu(), null);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    WebTable auTable = resp1.getTableStartingWith("Archival Unit");
    assertNotNull(auTable);
    assertEquals(2, auTable.getRowCount());
    assertEquals(3, auTable.getColumnCount());
    assertEquals("MockAU", auTable.getCellAsText(1, 0));
    assertEquals("/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F&auid=TestAU", auTable.getTableCell(1, 1).getLinks()[0].getURLString());
  }

  public void testMissingUrl() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu(), null);
    sClient.setExceptionsThrownOnErrorStatus(false);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("<html><head><title>Blah</title></head><body>Redirected content</body></html>", resp1.getText());
  }

  public void testMissingUrlExplicitAU() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu(), null);
    sClient.setExceptionsThrownOnErrorStatus(false);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F&auid=TestAU" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("<html><head><title>Blah</title></head><body>Redirected content</body></html>", resp1.getText());
  }

  public void testCachedUrl() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu());
    entitlementRegistryClient.expectEntitled("0740-2783", "03bd5fc6-97f0-11e4-b270-8932ea886a12", "20140101", "20141231");
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("<html><head><title>Blah</title></head><body>Cached content</body></html>", resp1.getText());
  }

  public void testUnauthorisedUrl() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu());
    entitlementRegistryClient.expectUnentitled("0740-2783", "03bd5fc6-97f0-11e4-b270-8932ea886a12", "20140101", "20141231");
    sClient.setExceptionsThrownOnErrorStatus(false);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertEquals(403, resp1.getResponseCode());
    assertTrue(resp1.getText().contains("<p>You are not authorised to access the requested URL on this LOCKSS box. Select link<sup><font size=-1><a href=#foottag1>1</a></font></sup> to view it at the publisher:</p><a href=\"http://dev-safenet.edina.ac.uk/test_journal/\">http://dev-safenet.edina.ac.uk/test_journal/</a>"));
  }

  public void testEntitlementRegistryError() throws Exception {
    initServletRunner();
    pluginMgr.addAu(makeAu());
    entitlementRegistryClient.expectError("0740-2783", "03bd5fc6-97f0-11e4-b270-8932ea886a12", "20140101", "20141231");
    sClient.setExceptionsThrownOnErrorStatus(false);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertEquals(503, resp1.getResponseCode());
    assertTrue(resp1.getText().contains("<p>An error occurred trying to access the requested URL on this LOCKSS box. This may be temporary and you may wish to report this, and try again later. Select link<sup><font size=-1><a href=#foottag1>1</a></font></sup> to view it at the publisher:</p><a href=\"http://dev-safenet.edina.ac.uk/test_journal/\">http://dev-safenet.edina.ac.uk/test_journal/</a>"));
  }

  public void testInvalidArchivalUnit() throws Exception {
    initServletRunner();
    Properties props = new Properties();
    props.setProperty("issn", "");
    props.setProperty("eissn", "");
    sClient.setExceptionsThrownOnErrorStatus(false);
    pluginMgr.addAu(makeAu(props));
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F" );
    InvocationContext ic = sClient.newInvocation(request);
    SafeNetServeContent snsc = (SafeNetServeContent) ic.getServlet();

    WebResponse resp1 = sClient.getResponse(request);
    assertEquals(404, resp1.getResponseCode());
    assertTrue(resp1.getText().contains("<p>The requested URL is not preserved on this LOCKSS box. Select link<sup><font size=-1><a href=#foottag1>1</a></font></sup> to view it at the publisher:</p><a href=\"http://dev-safenet.edina.ac.uk/test_journal/\">http://dev-safenet.edina.ac.uk/test_journal/</a>"));
  }

  private static class MockPluginManager extends PluginManager {
    private Map<ArchivalUnit, List<String>> aus;
    private MockLockssDaemon theDaemon;
    private MockNodeManager nodeManager;

    public MockPluginManager(MockLockssDaemon theDaemon) {
      this.aus = new HashMap<ArchivalUnit, List<String>>();
      this.theDaemon = theDaemon;
      this.nodeManager = new MockNodeManager();
    }

    public void addAu(ArchivalUnit au) {
      aus.put(au, new ArrayList<String>(au.getStartUrls()));
      theDaemon.setNodeManager(nodeManager, au);
    }

    public void addAu(ArchivalUnit au, List<String> urls) {
      aus.put(au, urls);
      theDaemon.setNodeManager(nodeManager, au);
    }

    @Override
    public List<ArchivalUnit> getAllAus() {
      return new ArrayList<ArchivalUnit>(aus.keySet());
    }

    @Override
    public CachedUrl findCachedUrl(String url, CuContentReq req) {
      return this.findCachedUrl(url);
    }

    @Override
    public CachedUrl findCachedUrl(String url) {
      for(ArchivalUnit au : aus.keySet()) {
        List<String> urls = aus.get(au);
        if(urls != null && urls.contains(url)) {
          MockCachedUrl cu = new MockCachedUrl(url, au);
          cu.addProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "text/html");
          cu.setContent("<html><head><title>Blah</title></head><body>Cached content</body></html>");
          return cu;
        }
      }
      return null;
    }
  }

  private static class MockEntitlementRegistryClient extends BaseEntitlementRegistryClient {
    private static class Expectation {
      private String issn;
      private String institution;
      private String start;
      private String end;
      private boolean entitled;
      private IOException exception;
    }

    public void expectEntitled(String issn, String institution, String start, String end) {
      Expectation e = new Expectation();
      e.issn = issn;
      e.institution = institution;
      e.start = start;
      e.end = end;
      e.entitled = true;
      expected.add(e);
    }

    public void expectUnentitled(String issn, String institution, String start, String end) {
      Expectation e = new Expectation();
      e.issn = issn;
      e.institution = institution;
      e.start = start;
      e.end = end;
      e.entitled = false;
      expected.add(e);
    }

    public void expectError(String issn, String institution, String start, String end) {
      Expectation e = new Expectation();
      e.issn = issn;
      e.institution = institution;
      e.start = start;
      e.end = end;
      e.exception = new IOException("Could not contact entitlement registry");
      expected.add(e);
    }

    private Queue<Expectation> expected = new LinkedList<Expectation>();

    public boolean isUserEntitled(String issn, String institution, String start, String end) throws IOException {
      Expectation e = expected.poll();
      assertEquals(e.issn, issn);
      assertEquals(e.institution, institution);
      assertEquals(e.start, start);
      assertEquals(e.end, end);
      if(e.exception != null) {
        throw e.exception;
      }

      return e.entitled;
    }
  }

  public static class RedirectServlet extends HttpServlet {
    public void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      resp.getWriter().print("<html><head><title>Blah</title></head><body>Redirected content</body></html>");
    }
  }
}

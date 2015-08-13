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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.lockss.app.LockssDaemon;
import org.lockss.config.TdbAu;
import org.lockss.daemon.TitleConfig;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.config.Tdb;
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

  private MockArchivalUnit mau = null;
  private MockPluginManager pluginMgr = null;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();
    pluginMgr = new MockPluginManager(theDaemon);
    theDaemon.setPluginManager(pluginMgr);
    theDaemon.setIdentityManager(new org.lockss.protocol.MockIdentityManager());
    theDaemon.getServletManager();
    theDaemon.setDaemonInited(true);
    theDaemon.setAusStarted(true);
    theDaemon.getRemoteApi().startService();

    pluginMgr.initService(theDaemon);
    pluginMgr.startService();

    mau = makeAu();

    ConfigurationUtil.addFromArgs(ConfigManager.PARAM_PLATFORM_PROJECT, "safenet");
    //ConfigurationUtil.addFromArgs("org.lockss.log.default.level", "debug3");
  }

  private MockArchivalUnit makeAu() throws Exception {
    Plugin plugin = new MockPlugin(theDaemon);
    Tdb tdb = new Tdb();

    // Tdb with values for some metadata fields
    Properties tdbProps = new Properties();
    tdbProps.setProperty("title", "Air and Space Volume 1");
    tdbProps.setProperty("issn", "0740-2783");
    tdbProps.setProperty("eissn", "0740-2783");
    tdbProps.setProperty("plugin", plugin.getClass().toString());

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
  }

  public void testIndex() throws Exception {
    initServletRunner();
    pluginMgr.addAu(mau, null);
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
    pluginMgr.addAu(mau, null);
    sClient.setExceptionsThrownOnErrorStatus(false);
    WebRequest request = new GetMethodWebRequest("http://null/SafeNetServeContent?url=http%3A%2F%2Fdev-safenet.edina.ac.uk%2Ftest_journal%2F&auid=MockAU0" );
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
          return new MockCachedUrl(url, au);
        }
      }
      return null;
    }
  }
}

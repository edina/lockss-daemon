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
import java.util.List;
import org.lockss.app.LockssDaemon;
import org.lockss.plugin.*;
import org.lockss.test.*;
import org.lockss.util.*;
import com.meterware.servletunit.*;
import com.meterware.httpunit.*;

public class TestSafeNetServeContent extends LockssServletTestCase {

  private MockArchivalUnit mau = null;
  private MockPluginManager pluginMgr = null;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();
    pluginMgr = new MockPluginManager();
    theDaemon.setPluginManager(pluginMgr);
    theDaemon.setIdentityManager(new org.lockss.protocol.MockIdentityManager());
    theDaemon.getServletManager();
    theDaemon.setDaemonInited(true);
    theDaemon.setAusStarted(true);
    theDaemon.getRemoteApi().startService();

    pluginMgr.initService(theDaemon);
    pluginMgr.startService();

    mau = new MockArchivalUnit(new MockPlugin(theDaemon), "TestAU");
    mau.setStartUrls(Arrays.asList("http://dev-safenet.edina.ac.uk/test_journal/"));
    List<ArchivalUnit> aus = Arrays.asList((ArchivalUnit) mau);
    pluginMgr.setAus(aus);
    theDaemon.setNodeManager(new MockNodeManager(), mau);

    ConfigurationUtil.addFromArgs(ConfigManager.PARAM_PLATFORM_PROJECT, "safenet");
    //ConfigurationUtil.addFromArgs("org.lockss.log.default.level", "debug3");
  }

  protected void initServletRunner() {
    super.initServletRunner();
    sRunner.setServletContextAttribute(ServletManager.CONTEXT_ATTR_SERVLET_MGR, new ContentServletManager());
    sRunner.registerServlet("/SafeNetServeContent", SafeNetServeContent.class.getName() );
  }

  public void testResponse() throws Exception {
    initServletRunner();
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

  private static class MockPluginManager extends PluginManager {
    private List<ArchivalUnit> aus;

    public void setAus(List<ArchivalUnit> aus) {
      this.aus = aus;
    }

    @Override
    public List<ArchivalUnit> getAllAus() {
      return aus;
    }
  }
}

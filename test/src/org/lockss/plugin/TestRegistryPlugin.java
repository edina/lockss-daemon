/*
 * $Id: TestRegistryPlugin.java,v 1.3 2004-09-29 18:58:03 tlipkis Exp $
 */

/*

Copyright (c) 2000-2004 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin;

import java.util.*;
import junit.framework.*;
import org.lockss.app.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.*;

/**
 * Test class for org.lockss.plugin.RegistryPlugin
 */
public class TestRegistryPlugin extends LockssTestCase {
  private RegistryPlugin m_plugin;
  private LockssDaemon m_theDaemon;

  public void setUp() throws Exception {
    super.setUp();
    m_theDaemon = getMockLockssDaemon();
    m_plugin = new RegistryPlugin();
    m_plugin.initPlugin(m_theDaemon);
  }

  public void tearDown() throws Exception {
    super.tearDown();
    // more...
  }

  public void testGetKey() throws Exception {
    assertNotNull(m_plugin);
    assertEquals("org.lockss.plugin.RegistryPlugin",
		 m_plugin.getPluginId());
  }

  public void testGetVersion() throws Exception {
    assertEquals("1", m_plugin.getVersion());
  }

  public void testGetPluginName() throws Exception {
    assertEquals("Registry", m_plugin.getPluginName());
  }

  public void testGetAuConfigDescrs() throws Exception {
    List descrs = m_plugin.getAuConfigDescrs();
    assertEquals(1, descrs.size());
    assertEquals(ConfigParamDescr.BASE_URL, descrs.get(0));
  }

  public void testCreateAu() throws Exception {
    Configuration auConf = ConfigManager.newConfiguration();
    auConf.put(ConfigParamDescr.BASE_URL.getKey(),
	       "http://foo.com/bar");
    ArchivalUnit au = m_plugin.createAu(auConf);
    assertTrue(au instanceof RegistryArchivalUnit);
    List urlStems = new ArrayList(au.getUrlStems());
    assertEquals(1, urlStems.size());
    assertEquals("http://foo.com", (urlStems.get(0).toString()));
  }

  // This is really a test of RegistryArchivalUnit, but it seems
  // pointless to create a whole new test class just for this.

  public void testShouldCallTopLevelPoll() throws Exception {
    RegistryArchivalUnit au = new RegistryArchivalUnit(m_plugin);
    // Expect that "shouldCallTopLevelPoll" will always return false.
    assertFalse(au.shouldCallTopLevelPoll(null));
  }

  // Both of these methods are currently empty implementations on
  // RegistryPlugin, but it's nice to exercise them anyway, since they
  // are part of Plugin's public interface.

  public void testSetTitleConfigFromConfig() throws Exception {
    m_plugin.setTitleConfigFromConfig(null);
  }

  public void testSetConfig() throws Exception {
    m_plugin.setConfig(null, null, null);
  }
}

/*
 * $Id: FuncArcExploder.java,v 1.10.12.1 2012-05-30 08:24:13 tlipkis Exp $
 */

/*

Copyright (c) 2007-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.crawler;

import java.io.*;
import java.util.*;
import java.net.*;

import org.apache.commons.collections.Bag;
import org.apache.commons.collections.bag.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.ExploderHelper;
import org.lockss.plugin.simulated.*;
import org.lockss.plugin.exploded.*;
import org.lockss.repository.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.state.*;
import org.lockss.app.*;

/**
 * Functional tests for the ARC file crawler.  It
 * does not test the non-ARC file functionality,
 * which is provided by FollowLinkCrawler.
 *
 * Uses SimulatedArcContentGenerator to create a
 * web site with a permission page that links to
 * an ARC file containing the rest of the content
 * that in the FollowLinkCrawler case would have
 * been generated by SimulatedContentGenerator.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public class FuncArcExploder extends LockssTestCase {
  static Logger log = Logger.getLogger("FuncArcExploder");

  private SimulatedArchivalUnit sau;
  private static MockLockssDaemon theDaemon;
  PluginManager pluginMgr;
  int lastCrawlResult = Crawler.STATUS_UNKNOWN;
  String lastCrawlMessage = null;
  boolean multipleStemsPerAu = false;
  private CrawlManagerImpl crawlMgr;

  private static final int DEFAULT_MAX_DEPTH = 1000;
  private static final int DEFAULT_FILESIZE = 3000;
  private static int fileSize = DEFAULT_FILESIZE;
  private static int maxDepth=DEFAULT_MAX_DEPTH;

  static String[] url = {
    "http://www.content.org/001file.bin",
    "http://www.content.org/002file.bin",
    "http://www.content.org/branch1/001file.bin",
    "http://www.content.org/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/001file.bin",
    "http://www.content.org/branch1/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/branch1/001file.bin",
    "http://www.content.org/branch1/branch1/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/branch1/index.html",
    "http://www.content.org/branch1/branch1/index.html",
    "http://www.content.org/branch1/index.html",

    "http://www.website.org/001file.bin",
    "http://www.website.org/002file.bin",
    "http://www.website.org/branch1/001file.bin",
    "http://www.website.org/branch1/002file.bin",
    "http://www.website.org/branch1/branch1/001file.bin",
    "http://www.website.org/branch1/branch1/002file.bin",
    "http://www.website.org/branch1/branch1/branch1/001file.bin",
    "http://www.website.org/branch1/branch1/branch1/002file.bin",
    "http://www.website.org/branch1/branch1/branch1/index.html",
    "http://www.website.org/branch1/branch1/index.html",
    "http://www.website.org/branch1/index.html",

    "http://www.library.org/001file.bin",
    "http://www.library.org/002file.bin",
    "http://www.library.org/branch1/001file.bin",
    "http://www.library.org/branch1/002file.bin",
    "http://www.library.org/branch1/branch1/001file.bin",
    "http://www.library.org/branch1/branch1/002file.bin",
    "http://www.library.org/branch1/branch1/branch1/001file.bin",
    "http://www.library.org/branch1/branch1/branch1/002file.bin",
    "http://www.library.org/branch1/branch1/branch1/index.html",
    "http://www.library.org/branch1/branch1/index.html",
    "http://www.library.org/branch1/index.html",
  };

  static String[] url2 = {
    "http://www.example.com/index.html",
    "http://www.example.com/content.arc.gz",
  };

  static final String GOOD_YEAR = "1968";


  public static void main(String[] args) throws Exception {
    // XXX should be much simpler.
    FuncArcExploder test = new FuncArcExploder();
    if (args.length>0) {
      try {
        maxDepth = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) { }
    }

    log.info("Setting up for depth " + maxDepth);
    test.setUp(maxDepth);
    log.info("Running up for depth " + maxDepth);
    test.testBadContentMultipleAU();
    test.testGoodContentMultipleAU();
    if (false) {
      test.dontTestBadContentSingleAU();
      test.dontTestGoodContentSingleAU();
    }
    test.tearDown();
  }

  public void setUp() throws Exception {
    super.setUp();
    this.setUp(DEFAULT_MAX_DEPTH);
  }

  public void setUp(int max) throws Exception {

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    Properties props = new Properties();
    props.setProperty(FollowLinkCrawler.PARAM_MAX_CRAWL_DEPTH, ""+max);
    maxDepth=max;
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);

    props.setProperty("org.lockss.plugin.simulated.SimulatedContentGenerator.doArcFile", "true");

    props.setProperty(FollowLinkCrawler.PARAM_EXPLODE_ARCHIVES, "true");
    props.setProperty(FollowLinkCrawler.PARAM_STORE_ARCHIVES, "true");
    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION, tempDirPath);
    String explodedPluginName =
      "org.lockss.crawler.FuncTarExploderMockExplodedPlugin";
    props.setProperty(Exploder.PARAM_EXPLODED_PLUGIN_NAME, explodedPluginName);
    props.setProperty(Exploder.PARAM_EXPLODED_AU_YEAR, GOOD_YEAR);
    props.setProperty(LockssApp.MANAGER_PREFIX + LockssDaemon.PLUGIN_MANAGER,
		      MyPluginManager.class.getName());

    theDaemon = getMockLockssDaemon();
    theDaemon.getAlertManager();
    pluginMgr = new MyPluginManager();
    pluginMgr.initService(theDaemon);
    theDaemon.setPluginManager(pluginMgr);
    crawlMgr = new NoPauseCrawlManagerImpl();
    theDaemon.setCrawlManager(crawlMgr);
    crawlMgr.initService(theDaemon);

    // pluginMgr.setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    pluginMgr.startService();
    pluginMgr.startLoadablePlugins();
    String explodedPluginKey = pluginMgr.pluginKeyFromName(explodedPluginName);
    pluginMgr.ensurePluginLoaded(explodedPluginKey);

    ConfigurationUtil.setCurrentConfigFromProps(props);

    sau = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin.class,
					     simAuConfig(tempDirPath));
  }

  public void tearDown() throws Exception {
    if (theDaemon != null) {
      theDaemon.stopDaemon();
    }
    super.tearDown();
  }

  Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("depth", "3");
    conf.put("branch", "1");
    conf.put("numFiles", "2");
    conf.put("fileTypes", "" + SimulatedContentGenerator.FILE_TYPE_BIN);
    conf.put("binFileSize", ""+fileSize);
    return conf;
  }

  public void testBadContentMultipleAU() throws Exception {
    multipleStemsPerAu = false;
    runTest(false);
  }

  public void testGoodContentMultipleAU() throws Exception {
    multipleStemsPerAu = false;
    runTest(true);
  }

  public void dontTestBadContentSingleAU() throws Exception {
    /*
     * XXX This test disabled because the Exploder now always explodes
     * XXX into multiple AUs.
     */
    ConfigurationUtil.addFromArgs(Exploder.PARAM_EXPLODED_AU_BASE_URL,
				  "http://www.stage.org/");
    multipleStemsPerAu = true;
    runTest(false);
  }

  public void dontTestGoodContentSingleAU() throws Exception {
    /*
     * XXX This test disabled because the Exploder now always explodes
     * XXX into multiple AUs.
     */
    ConfigurationUtil.addFromArgs(Exploder.PARAM_EXPLODED_AU_BASE_URL,
 				  "http://www.stage.org/");
    multipleStemsPerAu = true;
    runTest(true);
  }

  public void runTest(boolean good) throws Exception {
    log.debug3("About to create content");
    createContent();

    // get the root of the simContent
    String simDir = sau.getSimRoot();

    log.debug3("About to crawl content");
    boolean res = crawlContent(good ? null : url[url.length - 1]);
    if (good) {
      assertTrue("Crawl failed", res);
      if (false) assertTrue("Crawl should succeed but got " + lastCrawlResult +
		 (lastCrawlMessage == null ? "" : " with " + lastCrawlMessage),
		 lastCrawlResult == Crawler.STATUS_SUCCESSFUL);
    } else {
      assertFalse("Crawl succeeded", res);
      if (false) assertTrue("Crawl should get STATUS_PLUGIN_ERROR but got " +
		 lastCrawlResult +
		 (lastCrawlMessage == null ? "" : " with " + lastCrawlMessage),
		 lastCrawlResult == Crawler.STATUS_PLUGIN_ERROR);
      return;
    }

    // read all the files links from the root of the simcontent
    // check the link level of the file and see if it contains
    // in myCUS (check if the crawler crawl within the max. depth)
    CachedUrlSet myCUS = sau.getAuCachedUrlSet();
    File dir = new File(simDir);
    if(dir.isDirectory()) {
      File f[] = dir.listFiles();
      log.debug("Checking simulated content.");
      checkThruFileTree(f, myCUS);
      log.debug("Checking simulated content done.");
      checkExplodedUrls();
      checkUnExplodedUrls();

      log.debug("Check finished.");
    } else {
      log.error("Error: The root path of the simulated" +
		" content ["+ dir +"] is not a directory");
    }

    // Test PluginManager.getAuContentSize(), just because this is a
    // convenient place to do it.  If the simulated AU params are changed, or
    // SimulatedContentGenerator is changed, this number may have to
    // change.  NB - because the ARC files are compressed,  their
    // size varies randomly by a small amount.
    long expected = 5579;
    long actual = AuUtil.getAuContentSize(sau, true);
    long error = expected - actual;
    log.debug("Expected " + expected + " actual " + actual);
    long absError = (error < 0 ? -error : error);
    assertTrue("size mismatch " + expected + " vs. " + actual, absError < 60);

    List sbc = ((MySimulatedArchivalUnit)sau).sbc;
    Bag b = new HashBag(sbc);
    Set uniq = new HashSet(b.uniqueSet());
    for (Iterator iter = uniq.iterator(); iter.hasNext(); ) {
      b.remove(iter.next(), 1);
    }
    // Permission pages get checked twice.  Hard to avoid that, so allow it
    b.removeAll(sau.getCrawlSpec().getPermissionPages());
    // archives get checked twice - from checkThruFileTree & checkExplodedUrls
    b.remove(url2[url2.length - 1]);
    // This test is screwed up by the use of shouldBeCached() in
    // ArcExploder() to find the AU to store the URL in.
    //assertEmpty("shouldBeCached() called multiple times on same URLs.", b);

    // Test getUrlStems
    checkGetUrlStems();
    // Test crawl rules
    checkCrawlRules();
    // Test getPermissionPage
    //checkGetPermissionPages();

  }

  //recursive caller to check through the whole file tree
  private void checkThruFileTree(File f[], CachedUrlSet myCUS){
    for (int ix=0; ix<f.length; ix++) {
      log.debug3("Check: " + f[ix].getAbsolutePath());
      if (f[ix].isDirectory()) {
	// get all the files and links there and iterate
	checkThruFileTree(f[ix].listFiles(), myCUS);
      } else {

	// get the f[ix] 's level information
	String fileUrl = sau.mapContentFileNameToUrl(f[ix].getAbsolutePath());
	int fileLevel = sau.getLinkDepth(fileUrl);
	log.debug2("File: " + fileUrl + " in Level " + fileLevel);

	CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(fileUrl);
	if (fileLevel <= maxDepth) {
	  assertTrue(cu + " has no content", cu.hasContent());
	} else {
	  assertFalse(cu + " has content when it shouldn't",
		      cu.hasContent());
	}
      }
    }
    return; // when all "File" in the array are checked
  }


  private void checkExplodedUrls() {
    log.debug2("Checking Exploded URLs.");
    for (int i = 0; i < url.length; i++) {
      CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(url[i]);
      assertTrue(url[i] + " not in any AU", cu != null);
      log.debug2("Check: " + url[i] + " cu " + cu + " au " + cu.getArchivalUnit().getAuId());
      assertTrue(cu + " has no content", cu.hasContent());
      assertTrue(cu + " isn't ExplodedArchivalUnit",
		 (cu.getArchivalUnit() instanceof ExplodedArchivalUnit));
      assertNotEquals(sau, cu.getArchivalUnit());
    }
    log.debug2("Checking Exploded URLs done.");
  }

  private void checkUnExplodedUrls() {
    log.debug2("Checking UnExploded URLs.");
    for (int i = 0; i < url2.length; i++) {
      CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(url2[i]);
      assertTrue(url2[i] + " not in any AU", cu != null);
      log.debug2("Check: " + url2[i] + " cu " + cu + " au " + cu.getArchivalUnit().getAuId());
      assertTrue(cu + " has no content", cu.hasContent());
      assertTrue(cu + " isn't MySimulatedArchivalUnit",
		 (cu.getArchivalUnit() instanceof MySimulatedArchivalUnit));
      assertEquals(sau, cu.getArchivalUnit());
    }
    log.debug2("Checking UnExploded URLs done.");
  }

    static String[] expectedStems = {
      "http://www.stage.org/",
      "http://www.content.org/",
      "http://www.website.org/",
      "http://www.library.org/",
    };

  private void checkGetUrlStems() {
    if (multipleStemsPerAu) {
      String testUrl = expectedStems[1] + "branch1/index.html";
      CachedUrl cu = pluginMgr.findCachedUrl(testUrl);
      assertNotNull("No CU for " + testUrl, cu);
      ArchivalUnit au = cu.getArchivalUnit();
      assertNotNull(au);
      Collection stems = au.getUrlStems();
      assertNotNull("stems is null", stems);
      assertSameElements(expectedStems, stems);
    } else {
      for (int i = 1; i < expectedStems.length; i++) {
	String testUrl = expectedStems[i] + "branch1/index.html";
	CachedUrl cu = pluginMgr.findCachedUrl(testUrl);
	assertNotNull("No CU for " + testUrl, cu);
	ArchivalUnit au = cu.getArchivalUnit();
	assertNotNull(au);
	Collection stems = au.getUrlStems();
	assertNotNull("stems is null", stems);
	String[] expect = { expectedStems[i], };
	assertSameElements(expect, stems);
      }
    }
  }

  static String[] yes = {
    "http://www.content.org/index.html",
    "http://www.content.org/branch1/001file.html",
    "http://www.content.org/foo/bar/index.html",
    "http://www.website.org/index.html",
    "http://www.website.org/branch2/002file.html",
    "http://www.website.org/foo/bar/bletch/index.html",
    "http://www.library.org/index.html",
    "http://www.library.org/branch2/002file.html",
    "http://www.library.org/foo/bar/bletch/index.html",
  };
  static String[] no = {
    "http://www.example.com/",
    "http://www.example.com/index.html",
    "http://www.example.com/branch1/001file.html",
  };
  private void checkCrawlRules() {
    if (multipleStemsPerAu) {
      String testUrl = expectedStems[1] + "branch1/index.html";
      CachedUrl cu = pluginMgr.findCachedUrl(testUrl);
      assertNotNull("No CU for " + testUrl, cu);
      ArchivalUnit au = cu.getArchivalUnit();
      assertNotNull(au);
      for (int i = 0; i < yes.length; i++) {
	assertTrue(yes[i] + " should be cached", au.shouldBeCached(yes[i]));
      }
      for (int i = 0; i < no.length; i++) {
	assertFalse(no[i] + " should not be cached", au.shouldBeCached(no[i]));
      }
    } else {
      for (int i = 0; i < yes.length; i++) {
	CachedUrl cu = null;
	String testUrl = null;
	try {
	  testUrl = UrlUtil.getUrlPrefix(yes[i]) + "branch1/index.html";
	  cu = pluginMgr.findCachedUrl(testUrl);
	} catch (MalformedURLException ex) {
	  fail(ex.toString() + " " + testUrl);
	}
	assertNotNull("No CU for " + testUrl, cu);
	ArchivalUnit au = cu.getArchivalUnit();
	assertNotNull(au);
	assertTrue(yes[i] + " should be cached", au.shouldBeCached(yes[i]));
      }	
    }
  }

  private void createContent() {
    log.debug("Generating tree of size 3x1x2 with "+fileSize
	      +"byte files...");
    sau.generateContentTree();
  }

  private boolean crawlContent(String bad) {
    log.debug("Crawling tree..." + (bad == null ? "" : " fail at " + bad));
    List urls = sau.getNewContentCrawlUrls();
    CrawlSpec spec =
      new SpiderCrawlSpec(urls,
			  urls, // permissionUrls
			  new MyCrawlRule(), // crawl rules
			  1,    // refetch depth
			  null, // PermissionChecker
			  null, // LoginPageChecker
			  ".arc.gz$", // exploder pattern
			  new MyExploderHelper(bad) );
    AuState maus = new MyMockAuState();
    NewContentCrawler crawler = new NewContentCrawler(sau, spec, maus);
    crawler.setCrawlManager(crawlMgr);
    boolean res = crawler.doCrawl();
    lastCrawlResult = maus.getLastCrawlResult();
    lastCrawlMessage = maus.getLastCrawlResultMsg();
    log.debug2("End crawl " + res + " " + lastCrawlResult + " " +
	       (lastCrawlMessage != null ? lastCrawlMessage : "null"));
    return res;
  }

  public static class MySimulatedPlugin extends SimulatedPlugin {
    public ArchivalUnit createAu0(Configuration auConfig)
	throws ArchivalUnit.ConfigurationException {
      ArchivalUnit au = new MySimulatedArchivalUnit(this);
      au.setConfiguration(auConfig);
      return au;
    }
  }

  public static class MySimulatedArchivalUnit extends SimulatedArchivalUnit {
    List sbc = new ArrayList();

    public MySimulatedArchivalUnit(Plugin owner) {
      super(owner);
    }

    protected CrawlRule makeRules() {
      return new MyCrawlRule();
    }

    public boolean shouldBeCached(String url) {
      if (false) {
	// This can be helpful to track down problems - h/t TAL.
	log.debug3("shouldBeCached: " + url, new Throwable());
      } else {
	log.debug3("shouldBeCached: " + url);
      }
      if (url.startsWith("http://www.example.com/SimulatedCrawl")) {
	url2[url2.length-1] = url;
      }
      for (int i = 0; i < url2.length; i++) {
	if (url2[i].equals(url)) {
	  sbc.add(url);
	  return super.shouldBeCached(url);
	}
      }
      return (false);
    }
  }

  public static class MyCrawlRule implements CrawlRule {
    public int match(String url) {
      if (url.startsWith("http://www.example.com")) {
	return CrawlRule.INCLUDE;
      }
      return CrawlRule.EXCLUDE;
    }
  }

  public static class MyMockAuState extends MockAuState {

    public MyMockAuState() {
      super();
    }

    public void newCrawlFinished(int result, String msg) {
      log.debug("Crawl finished " + result + " " + msg);
    }
  }

  public static class MyExploderHelper implements ExploderHelper {
    private static String badName;
    public MyExploderHelper(String bad) {
      badName = bad;
    }

    private static final String suffix[] = {
      ".txt",
      ".html",
      ".pdf",
      ".jpg",
      ".bin",
    };
    public static final String[] mimeType = {
      "text/plain",
      "text/html",
      "application/pdf",
      "image/jpg",
      "application/octet-stream",
    };

    public void process(ArchiveEntry ae) {
      String baseUrl = null;
      String restOfUrl = ae.getName();
      log.debug3("process(" + restOfUrl + ") " + badName);
      if (restOfUrl == null || restOfUrl.equals(badName)) {
	log.debug("Synthetic failure at " + badName);
	return;
      }
      URL url;
      try {
	url = new URL(restOfUrl);
	if (!"http".equals(url.getProtocol())) {
	  log.debug2("ignoring: " + url.toString());
	}
      } catch (MalformedURLException ex) {
	log.debug2("Bad URL: " + (restOfUrl == null ? "null" : restOfUrl));
	return;
      }
      // XXX For now, put the content in an AU per host
      baseUrl = "http://" + url.getHost() + "/";
      restOfUrl = url.getFile();
      log.debug(ae.getName() + " mapped to " +
		   baseUrl + " plus " + restOfUrl);
      ae.setBaseUrl(baseUrl);
      ae.setRestOfUrl(restOfUrl);
      // XXX may be necessary to synthesize some header fields
      CIProperties props = new CIProperties();
      props.put(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
      ae.setAuProps(props);
    }
  }

  public static class MyPluginManager extends PluginManager {
    MyPluginManager() {
      super();
    }
    protected String getConfigurablePluginName(String pluginName) {
      pluginName = MockExplodedPlugin.class.getName();
      log.debug("getConfigurablePluginName returns " + pluginName);
      return pluginName;
    }
  }
}

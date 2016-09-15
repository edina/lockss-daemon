/*
 * $Id$
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.BaseCachedUrlSet;
import org.lockss.plugin.definable.*;
import org.lockss.state.AuState;
import org.lockss.test.*;
import org.lockss.util.*;

//
// This plugin test framework is set up to run the same tests in two variants - CLOCKSS and GLN
// without having to actually duplicate any of the written tests
//a
public class TestBaseAtyponArchivalUnit extends LockssTestCase {
  private MockLockssDaemon theDaemon;
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JID_KEY = ConfigParamDescr.JOURNAL_ID.getKey();
  static final String VOL_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
  static final String ROOT_URL = "http://www.BaseAtypon.com/"; //this is not a real url
  static final String ROOT_HOST = "www.BaseAtypon.com"; //this is not a real url
  
  // these two are currently the same, but for future possible divergence
  //static final String BASE_REPAIR_FROM_PEER_REGEXP = "(/(templates/jsp|(css|img|js)Jawr)/|/(css|img|js|wro)/.+\\.(css|gif|jpe?g|js|png)(_v[0-9]+)?$)";
  //static final String BOOK_REPAIR_FROM_PEER_REGEXP = "(/(templates/jsp|(css|img|js)Jawr)/|/(css|img|js|wro)/.+\\.(css|gif|jpe?g|js|png)(_v[0-9]+)?$)";

  static final String baseRepairList[] = 
    {
    "://[^/]+/(templates/jsp|(css|img|js)Jawr|pb-assets|resources|sda|wro)/",
    "/(css|img|js|wro)/.+\\.(css|gif|jpe?g|js|png)(_v[0-9]+)?$",
    "://[^/]+/na[0-9]+/home/(readonly|literatum)/publisher/.*/covergifs/.*\\.jpg$",
    };

  static final String bookRepairList[] = 
    {
    "://[^/]+/(templates/jsp|(css|img|js)Jawr|pb-assets|resources|sda|wro)/",
    "/(css|img|js|wro)/.+\\.(css|gif|jpe?g|js|png)(_v[0-9]+)?$",
    };

  
  private static final Logger log = Logger.getLogger(TestBaseAtyponArchivalUnit.class);

  static final String PLUGIN_ID = "org.lockss.plugin.atypon.BaseAtyponPlugin";
  static final String BOOK_PLUGIN_ID = "org.lockss.plugin.atypon.BaseAtyponBooksPlugin";
  static final String RESTRICTED_BOOK_PLUGIN_ID = "org.lockss.plugin.atypon.BaseAtyponISBNBooksPlugin";
  static final String PluginName = "Base Atypon Plugin";

  public void setUp() throws Exception {
    super.setUp();
    setUpDiskSpace();
    theDaemon = getMockLockssDaemon();
    theDaemon.getHashService();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  private DefinableArchivalUnit makeAu(URL url, int volume, String jid)
      throws Exception {

    Properties props = new Properties();
    props.setProperty(VOL_KEY, Integer.toString(volume));
    props.setProperty(JID_KEY, jid);
    if (url != null) {
      props.setProperty(BASE_URL_KEY, url.toString());
    }
    Configuration config = ConfigurationUtil.fromProps(props);

    DefinablePlugin ap = new DefinablePlugin();
    ap.initPlugin(getMockLockssDaemon(),
        PLUGIN_ID);
    DefinableArchivalUnit au = (DefinableArchivalUnit)ap.createAu(config);
    return au;
  }
  
  private DefinableArchivalUnit makeBookAu(URL url, String book_eisbn, boolean restricted)
      throws Exception {

    Properties props = new Properties();
    props.setProperty("book_eisbn", book_eisbn);
    if (url != null) {
      props.setProperty(BASE_URL_KEY, url.toString());
    }
    Configuration config = ConfigurationUtil.fromProps(props);

    DefinablePlugin ap = new DefinablePlugin();
    // if restricted use the book plugin that limits to ISBN-based doi
    if (restricted) {
      ap.initPlugin(getMockLockssDaemon(),
          RESTRICTED_BOOK_PLUGIN_ID);
    } else {
      ap.initPlugin(getMockLockssDaemon(),
          BOOK_PLUGIN_ID);
    }
    DefinableArchivalUnit au = (DefinableArchivalUnit)ap.createAu(config);
    return au;
  }  

  public void testConstructNullUrl() throws Exception {
    try {
      makeAu(null, 1, "ajph");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }
  }

  //
  // Test the crawl rules
  //
  public void testShouldCacheProperPages() throws Exception {
    URL base = new URL(ROOT_URL);
    ArchivalUnit ABAu = makeAu(base, 123, "xxxx");
    theDaemon.getLockssRepository(ABAu);
    theDaemon.getNodeManager(ABAu);
    BaseCachedUrlSet cus = new BaseCachedUrlSet(ABAu,
        new RangeCachedUrlSetSpec(base.toString()));
    // Test for pages that should get crawled
    //manifest page
    shouldCacheTest(ROOT_URL+"lockss/xxxx/123/index.html", true, ABAu, cus);    
    // images (etc.) but not as query arguments
    shouldCacheTest(ROOT_URL+"foo/bar/baz/qux.js", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"foo/bar/baz?url=qux.js", false, ABAu, cus);
    // Taylor & Francis' use of fastly.net revealed missing slash in boilerplate rule
    shouldCacheTest("http://" + ROOT_HOST + "/foo/bar/baz/qux.js", true, ABAu, cus);
    shouldCacheTest("https://" + ROOT_HOST + "/foo/bar/baz/qux.js", true, ABAu, cus);
    // now allowed; emerald also uses fastly
    shouldCacheTest("http://" + ROOT_HOST + ".global.prod.fastly.net/foo/bar/baz/qux.js", true, ABAu, cus);
    shouldCacheTest("https://" + ROOT_HOST + ".global.prod.fastly.net/foo/bar/baz/qux.js", true, ABAu, cus);
    // toc page for an issue
    shouldCacheTest(ROOT_URL+"toc/xxxx/123/5", true, ABAu, cus);
    // special issue
    shouldCacheTest(ROOT_URL+"doi/abs/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/pdf/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/full/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/pdfplus/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/suppl/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/suppl/11.1111/1234-abc.12G/suppl_stuff.docx", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/suppl/11.1111/1234-abc.12G/suppl_pres.ppt", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/ref/11.1111/1234-abc.12G", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/video_original/11.1111/1234-abc.12G", true, ABAu, cus);
    //other DOI forms
    shouldCacheTest(ROOT_URL+"doi/abs/11.1111/XX12FG", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/abs/11.1111/mypubfile", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"doi/abs/11.1111/1111111", true, ABAu, cus);
    // other argument based URLS
    shouldCacheTest(ROOT_URL+"action/showImage?doi=", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=T0003&doi=10.1080/19416520.2010.495530", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=CIT0013&doi=10.1080/19416521003732362", true, ABAu, cus);
    shouldCacheTest(ROOT_URL+"action/showFullPopup?doi=10.1206%2F3743.2&id=m05", true, ABAu, cus);
    // require option argument before doi has value
    shouldCacheTest(ROOT_URL+"action/showFullPopup?id=foo&doi=10.1206%2F3743.2", true, ABAu, cus);
    // missing value
    shouldCacheTest(ROOT_URL+"action/showFullPopup?id=&doi=10.1206%2F3743.2", false, ABAu, cus);
    // images figures and tables can live here
    shouldCacheTest(ROOT_URL+"na101/home/literatum/publisher/apha/journals/covergifs/xxxx/2005/15200477-86.6/cover.jpg", true, ABAu, cus);   

    // Now a couple that shouldn't get crawled
    // wrong volume
    shouldCacheTest(ROOT_URL+"toc/xxxx/12/index.html", false, ABAu, cus);
    // avoid spider trap
    shouldCacheTest(ROOT_URL+"doi/abs/11.1111/99.9999-99999", false, ABAu, cus);
    // LOCKSS
    shouldCacheTest("http://lockss.stanford.edu", false, ABAu, cus);
    // other sites

    // Later addition of crawl rules to avoid polling things we no longer collect or that we normalize off
    // this is normalized off but does exist in some aus from early crawls
    shouldCacheTest(ROOT_URL + "doi/abs/10.5504/50YRTIMB.2011.0036?queryID=%24%7BresultBean.queryID%7D", 
        false, ABAu, cus);
    // but if it were part of a doiname
    shouldCacheTest(ROOT_URL + "doi/abs/10.5504/50YRTIMB.2011.0036queryIDresultBean_name", 
        true, ABAu, cus);
    // no longer accepted citation formats
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=bibtex&include=cit", 
        false, ABAu, cus);
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=medlars&include=cit", 
        false, ABAu, cus);
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=endnote&include=cit", 
        false, ABAu, cus);
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=refworks&include=cit", 
        false, ABAu, cus);
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=refworks=cn&include=cit", 
        false, ABAu, cus);
    //no longer accepted include types.  we allow abs and cit, but not ref
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=ris&include=abs", 
        true, ABAu, cus);
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=ris&include=ref", 
        false, ABAu, cus);
    // this one is valid 
    shouldCacheTest(ROOT_URL + "action/downloadCitation?doi=10.1111%2F123456&format=ris&include=cit", 
        true, ABAu, cus);

    // ASCE use of relative link where it should be absolute causes ever-deeper URLS because
    // the "page not found" page uses the template with the same relative link problem.
    // added to crawl rules to combat this until they fix it
    shouldCacheTest(ROOT_URL + "action/showCart?backUri=/action/showLogin?uri=/action/showCart?backUri=/action/showLogin?uri=/doi/abs/10.1061/templates/jsp/js/googleAnalyticsPlugin.js", 
        false, ABAu, cus); 
    shouldCacheTest(ROOT_URL + "doi/abs/10.1061/templates/jsp/js/googleAnalyticsPlugin.js", 
        false, ABAu, cus); 
    shouldCacheTest(ROOT_URL + "doi/abs/10.1061/templates/jsp/js/templates/jsp/js/templates/jsp/js/templates/jsp/js/googleAnalyticsPlugin.js", 
        false, ABAu, cus); 

  }

  public void testShouldCacheBookPages() throws Exception {
    URL base = new URL(ROOT_URL);

    // Run this test twice - once with a generic book plugin
    // once with a restricted (eISBN) book plugin
    boolean restricted;
    for (int i=0; i < 2; i++) {
      if (i==0) {
        restricted = false;
        log.debug3("testing book crawl rules using a standard parent");
      } else {
        restricted = true;  
        log.debug3("testing book crawl rules using a restricted to eisbn parent");
      }
      ArchivalUnit aBookAu = makeBookAu(base, "9781780447636", restricted);
      theDaemon.getLockssRepository(aBookAu);
      theDaemon.getNodeManager(aBookAu);
      BaseCachedUrlSet cus = new BaseCachedUrlSet(aBookAu,
          new RangeCachedUrlSetSpec(base.toString()));
      boolean trueIfNotRestricted = !restricted;

      // Test for pages that should get crawled
      //manifest page/book landing page
      shouldCacheTest(ROOT_URL+"lockss/eisbn/9781780447636", true, aBookAu, cus);    
      // images (etc.) but not as query arguments
      shouldCacheTest(ROOT_URL+"doi/book/10.3362/9781780447636", true, aBookAu, cus);
      shouldCacheTest("http://" + ROOT_HOST + "/foo/bar/baz/qux.js", true, aBookAu, cus);
      shouldCacheTest("https://" + ROOT_HOST + "/foo/bar/baz/qux.js", true, aBookAu, cus);
      //abstract forms for chapters, landing, etc
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780447636", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780447636.000", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780447636_ch03", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780447636_03", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780447636-05", true, aBookAu, cus);
      
      // pdf forms for landing, chapters, etc
      shouldCacheTest(ROOT_URL+"doi/pdf/10.3362/9781780447636", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/pdf/10.3362/9781780447636.000", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/pdfplus/10.3362/9781780447636_ch03", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/pdfplus/10.3362/9781780447636_03", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/pdf/10.3362/9781780447636-05", true, aBookAu, cus);

      shouldCacheTest(ROOT_URL+"doi/ref/10.3362/9781780447636", true, aBookAu, cus);

      // check restrictions...
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780551234", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/book/10.3362/9781780551234", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9781780551234_03", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/pdf/10.3362/9781780551234-05", trueIfNotRestricted, aBookAu, cus);
      
      // check allowable variants even with restricted plugin
      // the eisbn portion of the DOI can have a prefix and/or a suffix but the eisbn
      // must be in the second part of the doi...
      //endocrine
      shouldCacheTest(ROOT_URL+"doi/book/10.3362/TEAM.9781780447636", true, aBookAu, cus);
      //siam-seg
      shouldCacheTest(ROOT_URL+"doi/book/10.3362/1.9781780447636", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/1.9781780447636.ch4", true, aBookAu, cus);
      
      
      // these are real urls from publishers - a book landing and a "chapter" if they 
      // support that. Keep these in to keep supporting the variations
      //aiaa
      shouldCacheTest(ROOT_URL+"doi/book/10.2514/4.476556", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/book/10.2514/4.476556", trueIfNotRestricted, aBookAu, cus);
      //emerald
      shouldCacheTest(ROOT_URL+"doi/book/10.1108/9780080549910", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/full/10.1108/9780080549910-003", trueIfNotRestricted, aBookAu, cus);
      //endocrine
      shouldCacheTest(ROOT_URL+"doi/book/10.1210/TEAM.9781936704071", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.1210/TEAM.9781936704071.ch3", trueIfNotRestricted, aBookAu, cus);
      //futurescience
      shouldCacheTest(ROOT_URL+"doi/book/10.2217/9781780840628", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/full/10.2217/ebo.11.226", trueIfNotRestricted, aBookAu, cus);
      //liverpool
      shouldCacheTest(ROOT_URL+"doi/book/10.3828/978-1-84631-495-7", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/book/10.3828/978-1-84631-495-7", trueIfNotRestricted, aBookAu, cus);
      //nrc
      shouldCacheTest(ROOT_URL+"doi/book/10.1139/9780660199795", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/book/10.1139/9780660199795", trueIfNotRestricted, aBookAu, cus);
      //practicalaction
      shouldCacheTest(ROOT_URL+"doi/book/10.3362/9780855986483", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3362/9780855986483.004", trueIfNotRestricted, aBookAu, cus);
      //siam
      shouldCacheTest(ROOT_URL+"doi/book/10.1137/1.9780898719833", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.1137/1.9780898719833.ch4", trueIfNotRestricted, aBookAu, cus);
      //seg
      shouldCacheTest(ROOT_URL+"doi/book/10.1190/1.9781560801795", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.1190/1.9781560801795.ch3", trueIfNotRestricted, aBookAu, cus);
      //wageningen
      shouldCacheTest(ROOT_URL+"doi/book/10.3920/978-90-8686-805-6", trueIfNotRestricted, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"doi/abs/10.3920/978-90-8686-805-6_5", trueIfNotRestricted, aBookAu, cus);

      // citation
      shouldCacheTest(ROOT_URL+"action/downloadCitation?doi=10.1108%2F9780857245168-015&format=ris&include=cit", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"action/showCitFormats?doi=10.1108%2F9780857245168-001", true, aBookAu, cus);
      // popups and images
      shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=App8-A-3&doi=10.1108%2F9780857245168-008", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=FN1&doi=10.1108%2F9780857245168-001", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=TFN6&doi=10.1108%2F9780857245168-009", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"action/showPopup?citid=citart1&id=eq3&doi=10.1108%2F9780857245168-008", true, aBookAu, cus);
      shouldCacheTest(ROOT_URL+"action/showPopup?doi=10.1108%2F9780857245168-008&id=fig8-2a", true, aBookAu, cus);

      // supporting stuff
      shouldCacheTest(ROOT_URL+"na101/home/literatum/publisher/emerald/books/content/books/2007/9780857245168/9780857245168-002/20160202/images/medium/figure5.jpg", true, aBookAu, cus);   
      shouldCacheTest(ROOT_URL+"na101/home/literatum/publisher/practical/books/content/books/2013/9781780447636/9781780447636/20150707/9781780447636.cover.gif", true, aBookAu, cus);

    }
  }


  private void shouldCacheTest(String url, boolean shouldCache,
      ArchivalUnit au, CachedUrlSet cus) {
    assertEquals(shouldCache, au.shouldBeCached(url));
  }

  public void testStartUrlConstruction() throws Exception {
    URL url = new URL(ROOT_URL);

    // 4 digit
    String expected = ROOT_URL+"lockss/xxxx/123/index.html";
    DefinableArchivalUnit ABAu = makeAu(url, 123, "xxxx");
    assertEquals(ListUtil.list(expected), ABAu.getStartUrls());
  }

  public void testShouldNotCachePageFromOtherSite() throws Exception {
    URL base = new URL("http://www.BaseAtypon.com/");
    int volume = 123;
    String jid = "xxxx";
    ArchivalUnit ABAu = makeAu(base, volume, jid);

    theDaemon.getLockssRepository(ABAu);
    theDaemon.getNodeManager(ABAu);
    CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(base.toString());
    BaseCachedUrlSet cus = new BaseCachedUrlSet(ABAu, spec);
    assertFalse(ABAu.shouldBeCached(
        "http://shadow2.stanford.edu/lockss/xxxx/123/index.html"));
  }


  public void testShouldDoNewContentCrawlTooEarly() throws Exception {
    ArchivalUnit ABAu =
        makeAu(new URL("http://www.BaseAtypon.com/"), 33, "yyyy");

    AuState aus = new MockAuState(null, TimeBase.nowMs(), -1, -1, null);

    assertFalse(ABAu.shouldCrawlForNewContent(aus));
  }

  public void testShouldDoNewContentCrawlFor0() throws Exception {
    ArchivalUnit ABAu =
        makeAu(new URL("http://www.BaseAtypon.com/"), 33, "yyyy");

    AuState aus = new MockAuState(null, 0, -1, -1, null);

    assertTrue(ABAu.shouldCrawlForNewContent(aus));
  }
  
  public void testPollSpecial() throws Exception {
    ArchivalUnit FooAu = makeAu(new URL("http://www.emeraldinsight.com/"), 33, "yyyy");
    theDaemon.getLockssRepository(FooAu);
    theDaemon.getNodeManager(FooAu);

    // if it changes in the plugin, you might need to change the test, so verify
  assertEquals(Arrays.asList(baseRepairList),
    RegexpUtil.regexpCollection(FooAu.makeRepairFromPeerIfMissingUrlPatterns()));
    
    // make sure that's the regexp that will match to the expected url string
    // this also tests the regexp (which is the same) for the weighted poll map
    List <String> repairList = ListUtil.list(
        "http://www.emeraldinsight.com.global.prod.fastly.net/jsJawr/1470752845/bundles/core.js",
        "http://www.emeraldinsight.com.global.prod.fastly.net/jsJawr/N315410081/script.js",
        "http://www.emeraldinsight.com.global.prod.fastly.net/jsJawr/N315410081/script.js_v1",
        "http://www.emeraldinsight.com.global.prod.fastly.net/wro/9pi3~product.js",
        "http://www.emeraldinsight.com/templates/jsp/_style2/_emerald/images/access_free.jpg",
        "http://www.emeraldinsight.com/templates/jsp/images/sfxbutton.gif",
        "http://www.emeraldinsight.com/resources/page-builder/img/widget-placeholder.png",
        "http://www.emeraldinsight.com/resources/page-builder/img/playPause.gif",
        "http://www.emeraldinsight.com/pb/css/t1459270391157-v1459207566000/head_14_18_329.css",
        "http://www.emeraldinsight.com/pb-assets/global-images/journals-blog-back.png",
        "http://www.emeraldinsight.com/pb-assets/icons/graphics.png",
        "http://www.emeraldinsight.com/resources/page-builder/newimg/playPause.gif",
        "http://www.inderscienceonline.com/na102/home/readonly/publisher/indersci/journals/covergifs/ijlt/cover.jpg",
        "http://www.inderscienceonline.com/na101/home/literatum/publisher/indersci/journals/covergifs/ijlt/cover.jpg",
        "http://www.emeraldinsight.com/wro/product.css");
     Pattern p0 = Pattern.compile(baseRepairList[0]);
     Pattern p1 = Pattern.compile(baseRepairList[1]);
     Pattern p2 = Pattern.compile(baseRepairList[2]);
     Matcher m0, m1, m2;
     for (String urlString : repairList) {
       m0 = p0.matcher(urlString);
       m1 = p1.matcher(urlString);
       m2 = p2.matcher(urlString);
       assertEquals(urlString, true, m0.find() || m1.find() || m2.find());
     }
     //and this one should fail - it needs to be weighted correctly and repaired from publisher if possible
     String notString ="http://www.emeraldinsight.com/na101/home/literatum/publisher/emerald/books/content/books/2013/9781781902868/9781781902868-007/20160215/images/small/figure1.gif";
     m0 = p0.matcher(notString);
     m1 = p1.matcher(notString);
     m2 = p2.matcher(notString);
     assertEquals(false, m0.find() && m1.find() && m2.find());

     
    PatternFloatMap urlPollResults = FooAu.makeUrlPollResultWeightMap();
    assertNotNull(urlPollResults);
    for (String urlString : repairList) {
      assertEquals(0.0,
          urlPollResults.getMatch(urlString, (float) 1),
          .0001);
    }
    assertEquals(1.0, urlPollResults.getMatch(notString, (float) 1), .0001);
  }
  
  public void testPollSpecialBooks() throws Exception {
    ArchivalUnit FooBookAu = makeBookAu(new URL("http://www.emeraldinsight.com/"), "9780585475226", false);
    theDaemon.getLockssRepository(FooBookAu);
    theDaemon.getNodeManager(FooBookAu);


    assertEquals(Arrays.asList(
        bookRepairList),
        RegexpUtil.regexpCollection(FooBookAu.makeRepairFromPeerIfMissingUrlPatterns()));
    
    // make sure that's the regexp that will match to the expected url string
    // this also tests the regexp (which is the same) for the weighted poll map
    List <String> repairList = ListUtil.list(
        "http://www.emeraldinsight.com.global.prod.fastly.net/jsJawr/1470752845/bundles/core.js",
        "http://www.emeraldinsight.com.global.prod.fastly.net/jsJawr/N315410081/script.js",
        "http://www.emeraldinsight.com.global.prod.fastly.net/wro/9pi3~product.js",
        "http://www.emeraldinsight.com/templates/jsp/_style2/_emerald/images/access_free.jpg",
        "http://www.emeraldinsight.com/templates/jsp/images/sfxbutton.gif",
        "http://www.emeraldinsight.com/resources/page-builder/img/widget-placeholder.png",
        "http://www.emeraldinsight.com/resources/page-builder/img/playPause.gif",
        "http://www.emeraldinsight.com/pb/css/t1459270391157-v1459207566000/head_14_18_329.css");
    Pattern p0 = Pattern.compile(baseRepairList[0]);
    Pattern p1 = Pattern.compile(baseRepairList[1]);
    Matcher m0, m1;
    for (String urlString : repairList) {
      m0 = p0.matcher(urlString);
      m1 = p1.matcher(urlString);
      assertEquals(urlString, true, m0.find() || m1.find());
    }
     //and this one should fail - it needs to be weighted correctly and repaired from publisher if possible
     String notString ="http://www.emeraldinsight.com/na101/home/literatum/publisher/emerald/books/content/books/2013/9781781902868/9781781902868-007/20160215/images/small/figure1.gif";
     m0 = p0.matcher(notString);
     m1 = p1.matcher(notString);
     assertEquals(false, m0.find() && m1.find());

     
    PatternFloatMap urlPollResults = FooBookAu.makeUrlPollResultWeightMap();
    assertNotNull(urlPollResults);
    for (String urlString : repairList) {
      assertEquals(0.0,
          urlPollResults.getMatch(urlString, (float) 1),
          .0001);
    }
    assertEquals(1.0, urlPollResults.getMatch(notString, (float) 1), .0001);
  }



  public void testGetName() throws Exception {
    DefinableArchivalUnit au =
        makeAu(new URL("http://www.BaseAtypon.com/"), 33, "yyyy");
    assertEquals(PluginName + ", Base URL http://www.BaseAtypon.com/, Journal ID yyyy, Volume 33", au.getName());
    DefinableArchivalUnit au1 =
        makeAu(new URL("http://www.apha.com/"), 24, "apha");
    assertEquals(PluginName + ", Base URL http://www.apha.com/, Journal ID apha, Volume 24", au1.getName());
  }

  private static final String gln_lockss_user_msg = 
      "Atypon Systems hosts this archival unit (AU) " +
          "and may require you to register the IP address "+
          "of this LOCKSS box as a crawler. For more information, visit the <a " +
          "href=\'http://www.lockss.org/support/use-a-lockss-box/adding-titles/publisher-ip-address-registration-contacts-for-global-lockss-network/\'>" +
          "LOCKSS IP address registration page</a>.";

  private static final String bq_msg = 
      "Atypon Systems hosts this archival unit (AU) and may require you to register the IP address of " +
          "this LOCKSS box as a crawler, by sending e-mail to <a href=\'mailto:pcoyne@qf.org.qa\'>Paul Coyne</a>. " +
          "Failure to comply with this publisher requirement may trigger crawler traps on the Atypon Systems platform, " +
          "and your LOCKSS box or your entire institution may be temporarily banned from accessing the site. " +
          "You only need to register the IP address of your LOCKSS box once for all AUs published by Bloomsbury Qatar.";

  private static final String default_msg_part1 = 
      "Atypon Systems hosts this archival unit (AU) and requires " +
          "that you <a href=\'";

  private static final String default_msg_part2 =
      "action/institutionLockssIpChange\'>register the " +
          "IP address of this LOCKSS box in your institutional account as a " +
          "crawler</a> before allowing your LOCKSS box to harvest this AU. " +
          "Failure to comply with this publisher requirement may trigger crawler traps on the " +
          "Atypon Systems platform, and your LOCKSS box or your entire institution may be " +
          "temporarily banned from accessing the site. You only need to register the IP address " +
          "of your LOCKSS box once for all AUs published by this publisher.";



  private static final String BASE_ATYPON_BASE = "http://www.BaseAtypon.org/";
  /* test all the atypon child au_config_user_msgs
   * by first checking the gln message either against a specific passed-in message
   * or by making sure it contains the appropriate base-url and publisher name (to guard
   * against cut-n-past errors
   * and then checking that the clockss plugin has a null message
   */
  public void testUserMsgs() throws Exception {
    // the default when a child doesn't set
    testSpecificUserMsg(BASE_ATYPON_BASE, default_msg_part1 + BASE_ATYPON_BASE + default_msg_part2, 
       "org.lockss.plugin.atypon.BaseAtyponPlugin",
       null);

    //AMetSoc - points users at our web page with registration info
    testSpecificUserMsg("http://journals.ametsoc.org/", gln_lockss_user_msg, 
        "org.lockss.plugin.atypon.americanmeteorologicalsociety.AMetSocPlugin",
        "org.lockss.plugin.atypon.americanmeteorologicalsociety.ClockssAMetSocPlugin");
    // with a null msg it will verify that the message follows the form "<base_url>/action/institutionLockssIpChange"
    testSpecificUserMsg("http://arc.aiaa.org/", null, 
        "org.lockss.plugin.atypon.aiaa.AIAAPlugin",
        "org.lockss.plugin.atypon.aiaa.ClockssAIAAPlugin");
    //asce - only a clockss plugin
    testSpecificUserMsg("http://ascelibrary.org/", null, 
        null,
        "org.lockss.plugin.atypon.americansocietyofcivilengineers.ClockssASCEPlugin");
    //ammons
    testSpecificUserMsg("http://www.amsciepub.com/", null, 
        null,
        "org.lockss.plugin.atypon.ammonsscientific.ClockssAmmonsScientificPlugin");
    //apha
    testSpecificUserMsg("http://ajph.aphapublications.org/", null, 
        "org.lockss.plugin.atypon.apha.AmPublicHealthAssocPlugin",
        null);
    //arrs
    testSpecificUserMsg("http://www.ajronline.org/", null, 
        "org.lockss.plugin.atypon.arrs.ARRSPlugin",
        null);
    //bir
    testSpecificUserMsg("http://www.birpublications.org/", null, 
        "org.lockss.plugin.atypon.bir.BIRAtyponPlugin",
        "org.lockss.plugin.atypon.bir.ClockssBIRAtyponPlugin");
    //bloomsburyqatar - this one has a special message, which is breaking the build XXX
//    testSpecificUserMsg("http://www.qscience.com/", bq_msg, 
//        "org.lockss.plugin.atypon.bloomsburyqatar.BloomsburyQatarPlugin",
//        "org.lockss.plugin.atypon.bloomsburyqatar.ClockssBloomsburyQatarPlugin");
    //emeraldgroup
    testSpecificUserMsg("http://www.emeraldinsight.com/", null, 
        "org.lockss.plugin.atypon.emeraldgroup.EmeraldGroupPlugin",
        null);
    //endocrine society
    testSpecificUserMsg("http://press.endocrine.org/", null, 
        "org.lockss.plugin.atypon.endocrinesociety.EndocrineSocietyPlugin",
        "org.lockss.plugin.atypon.endocrinesociety.ClockssEndocrineSocietyPlugin");
    //futurescience
    testSpecificUserMsg("http://www.future-science.com/", null,
        "org.lockss.plugin.atypon.futurescience.FutureSciencePlugin",
        "org.lockss.plugin.atypon.futurescience.ClockssFutureSciencePlugin");
    //inderscience
    testSpecificUserMsg("http://www.inderscienceonline.com/", null, 
        "org.lockss.plugin.atypon.inderscience.IndersciencePlugin",
        "org.lockss.plugin.atypon.inderscience.ClockssIndersciencePlugin");    
    //liverpool
    testSpecificUserMsg("http://online.liverpooluniversitypress.co.uk/", null, 
        "org.lockss.plugin.atypon.liverpool.LiverpoolJournalsPlugin",
        "org.lockss.plugin.atypon.liverpool.ClockssLiverpoolJournalsPlugin");
    //maney
    testSpecificUserMsg("http://www.maneyonline.com/", null, 
        "org.lockss.plugin.atypon.maney.ManeyAtyponPlugin",
        "org.lockss.plugin.atypon.maney.ClockssManeyAtyponPlugin");
    //mark allen group
    testSpecificUserMsg("http://www.magonlinelibrary.com/", null, 
        "org.lockss.plugin.atypon.markallen.MarkAllenPlugin",
        "org.lockss.plugin.atypon.markallen.ClockssMarkAllenPlugin");
    
    //Massachusetts Medical - uses the BaseAtypon default with its base_url
    testSpecificUserMsg("http://www.nejm.org/", default_msg_part1 + "http://www.nejm.org/" + default_msg_part2, 
       "org.lockss.plugin.atypon.massachusettsmedicalsociety.MassachusettsMedicalSocietyPlugin",
       null);
    
    //multiscience
    testSpecificUserMsg("http://multi-science.atypon.com/", null, 
        "org.lockss.plugin.atypon.multiscience.MultiSciencePlugin",
        "org.lockss.plugin.atypon.multiscience.ClockssMultiSciencePlugin");    
    //nrcresearch
    testSpecificUserMsg("http://www.nrcresearchpress.com/", null, 
        null,
        "org.lockss.plugin.atypon.nrcresearchpress.ClockssNRCResearchPressPlugin");
    //practicalaction
    testSpecificUserMsg("http://www.developmentbookshelf.com/", null, 
        null,
        "org.lockss.plugin.atypon.practicalaction.ClockssPracticalActionJournalsPlugin");    
    //seg
    testSpecificUserMsg("http://library.seg.org/", null, 
        null,
        "org.lockss.plugin.atypon.seg.ClockssSEGPlugin");
    //siam
    testSpecificUserMsg("http://epubs.siam.org/", null, 
        "org.lockss.plugin.atypon.siam.SiamPlugin",
        "org.lockss.plugin.atypon.siam.ClockssSiamPlugin");

    // and the ones that do not live below the atypon directory
    //bioone
    testSpecificUserMsg("http://www.bioone.org/", null, 
        "org.lockss.plugin.bioone.BioOneAtyponPlugin",
        "org.lockss.plugin.bioone.ClockssBioOneAtyponPlugin");
    //edinburgh
    testSpecificUserMsg("http://www.euppublishing.com/", null, 
        "org.lockss.plugin.edinburgh.EdinburghUniversityPressPlugin",
        "org.lockss.plugin.edinburgh.ClockssEdinburghUniversityPressPlugin");
    //t&f
    testSpecificUserMsg("http://www.tandfonline.com/", null, 
        "org.lockss.plugin.taylorandfrancis.TaylorAndFrancisPlugin",
        "org.lockss.plugin.taylorandfrancis.ClockssTaylorAndFrancisPlugin");
    //wageningen
    testSpecificUserMsg("http://www.wageningenacademic.com/", null, 
        null,
        "org.lockss.plugin.atypon.wageningen.ClockssWageningenJournalsPlugin");    
  }

  // Associate the base_url with the publisher name for convenience
  static private final Map<String, String> pluginPubMap =
      new HashMap<String,String>();
  static {
    pluginPubMap.put("http://journals.ametsoc.org/", "American Meteorological Society");
    pluginPubMap.put("http://arc.aiaa.org/", "American Institute of Aeronautics and Astronautics");
    pluginPubMap.put("http://www.ajronline.org/", "American Roentgen Ray Society");
    pluginPubMap.put("http://ascelibrary.org/", "American Society of Civil Engineers");
    pluginPubMap.put("http://www.amsciepub.com/", "Ammons Scientific Journals");
    pluginPubMap.put("http://ajph.aphapublications.org/", "American Public Health Association");
    pluginPubMap.put("http://www.birpublications.org/", "British Institute of Radiology");
    pluginPubMap.put("http://www.qscience.com/", "Bloomsbury Qatar Foundation Journals");
    pluginPubMap.put("http://press.endocrine.org/", "Endocrine Society");
    pluginPubMap.put("http://www.emeraldinsight.com/", "Emerald Group Publishing");
    pluginPubMap.put("http://www.future-science.com/", "Future Science");
    pluginPubMap.put("http://www.inderscienceonline.com/", "Inderscience");
    pluginPubMap.put("http://online.liverpooluniversitypress.co.uk/", "Liverpool University Press");
    pluginPubMap.put("http://www.maneyonline.com/", "Maney Publishing");
    pluginPubMap.put("http://www.magonlinelibrary.com/", "Mark Allen Group");
    pluginPubMap.put("http://www.nejm.org/", "Massachusetts Medical Society");
    pluginPubMap.put("http://multi-science.atypon.com/", "Multi-Science");    
    pluginPubMap.put("http://www.nrcresearchpress.com/", "NRC Research Press");
    pluginPubMap.put("http://www.developmentbookshelf.com/", "Practical Action Publishing");
    pluginPubMap.put("http://library.seg.org/", "Society of Exploration Geophysicists");
    pluginPubMap.put("http://epubs.siam.org/", "Society for Industrial and Applied Mathematics");
    pluginPubMap.put("http://www.bioone.org/", "BioOne");
    pluginPubMap.put("http://www.euppublishing.com/", "Edinburgh University Press");
    pluginPubMap.put("http://www.tandfonline.com/", "Taylor & Francis");
    pluginPubMap.put("http://www.wageningenacademic.com/", "Wageningen Academic Publishers");
  }


  private void testSpecificUserMsg(String plugin_base_url, String full_msg, 
      String gln_plugin, String clockss_plugin) throws Exception {

    Properties props = new Properties();
    props.setProperty(VOL_KEY, Integer.toString(17));
    props.setProperty(JID_KEY, "eint");
    props.setProperty(BASE_URL_KEY, plugin_base_url);
    Configuration config = ConfigurationUtil.fromProps(props);

    if (!StringUtils.isEmpty(gln_plugin)) {
      DefinablePlugin ap = new DefinablePlugin();
      ap.initPlugin(getMockLockssDaemon(),
          gln_plugin);
      DefinableArchivalUnit gAU = (DefinableArchivalUnit)ap.createAu(config);

      log.debug3("testing GLN user message");
      if (!StringUtils.isEmpty(full_msg)) {
        assertEquals(full_msg, gAU.getProperties().getString(DefinableArchivalUnit.KEY_AU_CONFIG_USER_MSG, null));
      } else {
        // check that the message includes "<base_url>/action/institutionLockssIpChange" and publisher name 
        String config_msg = gAU.getProperties().getString(DefinableArchivalUnit.KEY_AU_CONFIG_USER_MSG, null);
        log.debug3("config msg: " + config_msg);
        assertTrue(config_msg.contains(plugin_base_url + "action/institutionLockssIpChange"));
        assertTrue(config_msg.contains(pluginPubMap.get(plugin_base_url)));
      }
    }

    // now check clockss version non-message
    if (!StringUtils.isEmpty(clockss_plugin)) {
      DefinablePlugin cap = new DefinablePlugin();
      cap.initPlugin(getMockLockssDaemon(),
          clockss_plugin);
      DefinableArchivalUnit cAU = (DefinableArchivalUnit)cap.createAu(config);

      log.debug3("testing CLOCKSS absence of user message");
      assertEquals(null, cAU.getProperties().getString(DefinableArchivalUnit.KEY_AU_CONFIG_USER_MSG, null));
    }

  }

}


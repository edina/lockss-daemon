/*
/    * $Id: TestHighWirePressH20HtmlFilterFactory.java,v 1.10 2012-10-10 21:59:12 ldoan Exp $
 */

/*

 Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.util.*;
import org.lockss.daemon.PluginException;
import org.lockss.test.*;

public class TestHighWirePressH20HtmlFilterFactory extends LockssTestCase {
  static String ENC = Constants.DEFAULT_ENCODING;

  private HighWirePressH20HtmlFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new HighWirePressH20HtmlFilterFactory();
    mau = new MockArchivalUnit();
  }

  private static final String inst1 = "<div class=\"leaderboard-ads leaderboard-ads-two\"</div>"
      + "<ul>Fill in SOMETHING SOMETHING</ul>";

  private static final String inst2 = "<ul>Fill in SOMETHING SOMETHING</ul>";

  private static final String withAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<div class=\"leaderboard-ads-ft\">"
      + "<ul>"
      + "<li><a href=\"com%2FAbout.html\"><img title=\"Advertiser\""
      + "src=\"http:/adview=true\""
      + "alt=\"Advertiser\" /></a></li>"
      + "</ul>"
      + "</div>"
      + "<p class=\"disclaimer\">The content of this site is intended for health care professionals</p>"
      + "<p class=\"copyright\">Copyright © 2012 by "
      + "The Journal of Rheumatology" + "</p>" + "<ul class=\"issns\">"
      + "<li><span>Print ISSN: </span>"
      + "<span class=\"issn\">0315-162X</span></li>"
      + "<li><span>Online ISSN: </span>"
      + "<span class=\"issn\">1499-2752</span></li>" + "</ul>" + "</div>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withoutAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<p class=\"disclaimer\">The content of this site is intended for health care professionals</p>"
      + "<p class=\"copyright\">Copyright © 2012 by "
      + "The Journal of Rheumatology" + "</p>" + "<ul class=\"issns\">"
      + "<li><span>Print ISSN: </span>"
      + "<span class=\"issn\">0315-162X</span></li>"
      + "<li><span>Online ISSN: </span>"
      + "<span class=\"issn\">1499-2752</span></li>" + "</ul>" + "</div>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withCol4SquareAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<ul class=\"col4-square\">"
      + "<li><a href=\"/cgi/adclick/?ad=35597&amp;adclick=true&amp;url=http%3A%2F%2Fwww.facebook.com%2FPlantphysiology\">"
      + "<img class=\"adborder0\" title=\"PlantPhysFacebook\" width=\"160\" height=\"150\" src=\"http://www.plantphysiol.org/adsystem/graphics/5602385865303331/plantphysiol/squarepp.jpg?ad=35597&amp;adview=true\" alt=\"PlantPhysFacebook\" /></a></li>"
      + "</ul>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withoutCol4SquareAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withCol4TowerAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<ul class=\"col4-tower\">"
      + "<li><a href=\"/cgi/adclick/?ad=35598&amp;adclick=true&amp;url=http%3A%2F%2Fwww.plantphysiol.org%2F\">"
      + "<img class=\"adborder10\" title=\"10pdfPromo\" width=\"160\" height=\"600\" src=\"http://www.plantphysiol.org/adsystem/graphics/06456092319841111/plantphysiol/vertauthors.jpg?ad=35598&amp;adview=true alt=\"10pdfPromo\" /></a></li>"
      + "</ul>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withoutCol4TowerAds = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withCopyright = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<p class=\"disclaimer\">The content of this site is intended for health care professionals</p>"
      + "<p class=\"copyright\">Copyright © 2012 by "
      + "The Journal of Rheumatology" + "</p>" + "<ul class=\"issns\">"
      + "<li><span>Print ISSN: </span>"
      + "<span class=\"issn\">0315-162X</span></li>"
      + "<li><span>Online ISSN: </span>"
      + "<span class=\"issn\">1499-2752</span></li>" + "</ul>" + "</div>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withoutCopyright = "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<p class=\"disclaimer\">The content of this site is intended for health care professionals</p>"
      + "<ul class=\"issns\">" + "<li><span>Print ISSN: </span>"
      + "<span class=\"issn\">0315-162X</span></li>"
      + "<li><span>Online ISSN: </span>"
      + "<span class=\"issn\">1499-2752</span></li>" + "</ul>" + "</div>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

  private static final String withCurrentIssue = "<div class=\"col-3-top sb-div\"></div>"
      + "<div class=\"content-box\" id=\"sidebar-current-issue\">"
      + "<div class=\"cb-contents\">"
      + "<h3 class=\"cb-contents-header\"><span>Current Issue</span></h3>"
      + "<div class=\"cb-section\">"
      + "<ol>"
      + "<li><span><a href=\"/content/current\" rel=\"current-issue\">May 2012, 39 (5)</a></span></li>"
      + "</ol>"
      + "</div>"
      + "<div class=\"cb-section\">"
      + "<ol>"
      + "<div class=\"current-issue\"><a href=\"/content/current\" rel=\"current-issue\"><img src=\"/local/img/sample_cover.gif\" width=\"67\" height=\"89\" alt=\"Current Issue\" /></a></div>"
      + "</ol>"
      + "</div>"
      + "<div class=\"cb-section sidebar-etoc-link\">"
      + "<ol>"
      + "<li><a href=\"/cgi/alerts/etoc\">Alert me to new issues of The Journal"
      + "</a></li>" + "</ol>" + "</div>" + "</div>" + "</div>";
  private static final String withoutCurrentIssue = "<div class=\"col-3-top sb-div\"></div>";

  private static final String headHtml = "<html><head>Title</head></HTML>";
  private static final String headHtmlFiltered = "<html></HTML>";

  private static final String withSporadicDivs =
      "<div><div id=\"fragment-reference-display\"></div>" +
          "<div class=\"cit-extra\">stuff</div></div";
  private static final String withoutSporadicDivs =
      "<div></div>";

  private static final String withCmeCredit =
      "<ol><li><a href=\"/content/28/7/911/suppl/DC1\" rel=\"supplemental-data\"" +
          "class=\"dslink-earn-free-cme-credit\">Earn FREE CME Credit</a></li></ol>";
  private static final String withoutCmeCredit = 
      "<ol></ol>";

  private static final String withCbSection =
      "<div><div class=\"cb-section collapsible default-closed\" id=\"cb-art-gs\">Content" +
          "<h4></h4><ol><li></li></ol></div></div>";
  private static final String withoutCbSection =
      "<div></div>";

  private static final String withHwGenPage =
      "<div class=\"hw-gen-page pagetype-content hw-pub-id-article\" " +
          "id=\"pageid-content\" itemscope=\"itemscope\" " +
          "itemtype=\"http://schema.org/ScholarlyArticle\">content</div>";
  private static final String withoutHwGenPage =
      "<div class=\"hw-gen-page pagetype-content hw-pub-id-article\" " +
          "id=\"pageid-content\">content</div>";

  private static final String withNavCurrentIssue =
      "<li id=\"nav_current_issue\" title=\"Current\">" +
          "<a href=\"/content/current\">" +
          "<span>View Current Issue (Volume 175 Issue 12 June 15, 2012)" +
          "</span></a></li>";

  private static final String withoutNavCurrentIssue =
      "<li id=\"nav_current_issue\" title=\"Current\">" +
          "<a href=\"/content/current\">" +
          "<span>View Current Issue (Volume 174 Issue 12 December 15, 2011)" +
          "</span>>/a></li>";

  private static final String withRelatedURLs =
      "<div><span id=\"related-urls\"" +
          "/span></div>";
  private static final String withoutRelatedURLs =
      "<div></div>";

  public void testFiltering() throws Exception {
    assertFilterToSame(inst1, inst2);
    assertFilterToSame(withAds, withoutAds);
    assertFilterToSame(withCopyright, withoutCopyright);
    assertFilterToSame(withCurrentIssue, withoutCurrentIssue);
    assertFilterToSame(withSporadicDivs, withoutSporadicDivs);
    assertFilterToSame(withCmeCredit, withoutCmeCredit);
    assertFilterToSame(withCbSection, withoutCbSection);
    assertFilterToSame(withRelatedURLs, withoutRelatedURLs);
    assertFilterToSame(withHwGenPage, withoutHwGenPage);
    assertFilterToSame(withNavCurrentIssue, withoutNavCurrentIssue);
    assertFilterToSame(withCol4SquareAds, withoutCol4SquareAds);
    assertFilterToSame(withCol4TowerAds, withoutCol4TowerAds);
  }

  private void assertFilterToSame(String str1, String Str2) throws Exception {

    InputStream inA = fact.createFilteredInputStream(mau, new StringInputStream(str1),
        Constants.DEFAULT_ENCODING);
    InputStream inB = fact.createFilteredInputStream(mau, new StringInputStream(Str2),
        Constants.DEFAULT_ENCODING);
    assertEquals(StringUtil.fromInputStream(inA),
        StringUtil.fromInputStream(inB));
  }

  public void testHeadFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(headHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(headHtmlFiltered, StringUtil.fromInputStream(actIn));
  }
  
}

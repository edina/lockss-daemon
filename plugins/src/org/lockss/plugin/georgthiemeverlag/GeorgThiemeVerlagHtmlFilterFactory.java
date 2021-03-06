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

Except as contained in this notice, tMassachusettsMedicalSocietyHtmlFilterFactoryhe name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.georgthiemeverlag;

import java.io.*;
import java.util.Vector;

import org.htmlparser.*;
import org.htmlparser.filters.*;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.visitors.NodeVisitor;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class GeorgThiemeVerlagHtmlFilterFactory implements FilterFactory {
  
  Logger log = Logger.getLogger(GeorgThiemeVerlagHtmlFilterFactory.class);
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    // First filter with HtmlParser
    NodeFilter[] filters = new NodeFilter[] {
        // Aggressive filtering of non-content tags
        // Contains scripts and tags that change values, do not contain content
        // head & scipt tags contents might change, aggressive filtering
        HtmlNodeFilters.tag("head"),
        HtmlNodeFilters.tag("script"),
        // Remove header/footer items
        HtmlNodeFilters.tag("header"),
        HtmlNodeFilters.tag("footer"),
        // remove ALL comments
        HtmlNodeFilters.comment(),
        // Contains ads that change, not content
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "adSidebar"),
        // Contains navigation items that are not content
        HtmlNodeFilters.tagWithAttribute("div", "id", "navPanel"),
        HtmlNodeFilters.tagWithAttribute("ul", "id", "overviewNavigation"),
        // Contains functional links, not content
        HtmlNodeFilters.tagWithAttribute("div", "class", "pageFunctions"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "articleFunctions"),
        HtmlNodeFilters.tagWithAttribute("span", "class", "articleCategories"),
        // Contains non-functional anchor, not content
        HtmlNodeFilters.tagWithAttribute("ul", "class", "articleTocList"),
        HtmlNodeFilters.tagWithAttribute("a", "name"),
        // contains information, not content
        HtmlNodeFilters.tagWithAttribute("div", "id", "access-profile-box"),
        // Debug ids change
        HtmlNodeFilters.tagWithAttributeRegex("img", "src", "_debugResources="),
    };
    
    
    // HTML transform to remove generated href attribute like <a href="#N66454">
    HtmlTransform xform = new HtmlTransform() {
      @Override
      public NodeList transform(NodeList nodeList) throws IOException {
        try {
          nodeList.visitAllNodesWith(new NodeVisitor() {
            @Override
            public void visitTag(Tag tag) {
              String tagName = tag.getTagName().toLowerCase();
              try {
                if ("a".equals(tagName) ||
                    "div".equals(tagName) ||
                    "section".equals(tagName)) {
                  Attribute a = tag.getAttributeEx(tagName);
                  Vector<Attribute> v = new Vector<Attribute>();
                  v.add(a);
                  if (tag.isEmptyXmlTag()) {
                    Attribute end = tag.getAttributeEx("/");
                    v.add(end);
                  }
                  tag.setAttributesEx(v);
                }
                super.visitTag(tag);
              }
              catch (Exception exc) {
                log.debug2("Internal error (visitor)", exc); // Ignore this tag and move on
              }
            }
          });
        }
        catch (ParserException pe) {
          log.debug2("Internal error (parser)", pe); // Bail
        }
        return nodeList;
      }
    };
    
    HtmlFilterInputStream filtered = new HtmlFilterInputStream(in, encoding,
        new HtmlCompoundTransform(HtmlNodeFilterTransform.exclude(
            new OrFilter(filters)),xform));
    Reader filteredReader = FilterUtil.getReader(filtered, encoding);
    return new ReaderInputStream(new WhiteSpaceFilter(filteredReader));
  }
  
}

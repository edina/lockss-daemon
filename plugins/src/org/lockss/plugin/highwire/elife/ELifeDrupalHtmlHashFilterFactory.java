/*
 * $Id: ELifeDrupalHtmlHashFilterFactory.java,v 1.2 2014-09-11 02:46:41 etenbrink Exp $
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

package org.lockss.plugin.highwire.elife;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Vector;

import org.htmlparser.Attribute;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Tag;
import org.htmlparser.filters.*;
import org.htmlparser.util.NodeList;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.FilterFactory;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class ELifeDrupalHtmlHashFilterFactory implements FilterFactory {
  
  Logger log = Logger.getLogger(ELifeDrupalHtmlHashFilterFactory.class);
  
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    NodeFilter[] filters = new NodeFilter[] {
        // Publisher adding/updating meta tags
        new TagNameFilter("head"),
        // remove ALL comments
        // HtmlNodeFilters.comment(),
        // No content to compare in header/footer
        new TagNameFilter("header"),
        new TagNameFilter("footer"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "zone-header-wrapper"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "page_header"),
        HtmlNodeFilters.tagWithAttribute("ul", "class", "elife-article-categories"),
        // citation reference extras, right sidebar can change
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "elife-reflink-links-wrapper"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "sidebar-wrapper"),
        // most scripts are in head, however, if any are in the body they are filtered
        new TagNameFilter("script"),
        new TagNameFilter("noscript"),
    };
    
    // Transform to remove attributes from select tags
    // some attributes changed over time, either arbitrarily or sequentially
    HtmlTransform xform = new HtmlTransform() {
      @Override
      public NodeList transform(NodeList nodeList) throws IOException {
        NodeList nl = new NodeList();
        for (int sx = 0; sx < nodeList.size(); sx++) {
          Node snode = nodeList.elementAt(sx);
          if (snode instanceof Tag) {
            Tag tag = (Tag) snode;
            if (tag.isEmptyXmlTag()) {
              continue;
            }
            String tagName = tag.getTagName().toLowerCase();
            NodeList knl = tag.getChildren();
            if (knl != null) {
              tag.setChildren(transform(knl));
            }
            if ("div".equals(tagName)) {
              Attribute a = tag.getAttributeEx(tagName);
              Vector<Attribute> v = new Vector<Attribute>();
              v.add(a);
              tag.setAttributesEx(v);
            }
            nl.add(tag);
          }
          else {
            nl.add(snode);
          }
        }
        return nl;
      }
    };
    
    InputStream filtered =  new HtmlFilterInputStream(in, encoding,
        new HtmlCompoundTransform(
            HtmlNodeFilterTransform.exclude(new OrFilter(filters)), xform));
    
    Reader filteredReader = FilterUtil.getReader(filtered, encoding);
    return new ReaderInputStream(new WhiteSpaceFilter(filteredReader));
    
  }
  
}

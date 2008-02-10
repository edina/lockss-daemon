/*
 * $Id: AmericanChemicalSocietyHtmlFilterFactory.java,v 1.1 2008-02-10 07:01:33 thib_gc Exp $
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.acs;

import java.io.*;

import org.htmlparser.filters.TagNameFilter;
import org.lockss.daemon.PluginException;
import org.lockss.filter.*;
import org.lockss.filter.HtmlTagFilter.TagPair;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.ReaderInputStream;

public class AmericanChemicalSocietyHtmlFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    HtmlTransform[] transforms = new HtmlTransform[] {
        // Filter out <iframe>...</iframe>
        HtmlNodeFilterTransform.exclude(new TagNameFilter("iframe")),
        // Filter out <span class="ACS_copyright">...</span>
        HtmlNodeFilterTransform.exclude(HtmlNodeFilters.tagWithAttribute("span",
                                                                         "class",
                                                                         "ACS_copyright")),
        // Filter out <td id="id_ACS_RightNav">...</td>
        HtmlNodeFilterTransform.exclude(HtmlNodeFilters.tagWithAttribute("td",
                                                                         "id",
                                                                         "id_ACS_RightNav")),
        // Filter out <meta http-equiv="expires">...</meta>
        HtmlNodeFilterTransform.exclude(HtmlNodeFilters.tagWithAttribute("meta",
                                                                         "http-equiv",
                                                                         "expires")),
    };
    InputStream stream = new HtmlFilterInputStream(in,
                                                   encoding,
                                                   new HtmlCompoundTransform(transforms));


    // Also remove everything between "// Generated by Database Call //" and "// End Database Call //"
    return new ReaderInputStream(new HtmlTagFilter(FilterUtil.getReader(stream, encoding),
                                                   new TagPair("// Generated by Database Call //",
                                                               "// End Database Call //")));
  }

}

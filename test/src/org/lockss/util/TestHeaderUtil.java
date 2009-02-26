/*
 * $Id: TestHeaderUtil.java,v 1.4 2009-02-26 05:14:16 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.util;
import org.lockss.test.*;


public class TestHeaderUtil extends LockssTestCase {

  public void testGetMimeTypeFromContentType() {
    assertNull(HeaderUtil.getMimeTypeFromContentType(null));
    assertEquals("text/html",
		 HeaderUtil.getMimeTypeFromContentType("text/html"));
    assertEquals("text/html",
		 HeaderUtil.getMimeTypeFromContentType(" Text/Html "));
    assertEquals("text/html",
		 HeaderUtil.getMimeTypeFromContentType("TEXT/HTML ; charset=foo"));
    assertEquals("text/html",
		 HeaderUtil.getMimeTypeFromContentType(" text/html ; charset=foo"));
    assertEquals("application/binary",
		 HeaderUtil.getMimeTypeFromContentType("Application/Binary; charset=foo"));
    assertSame(HeaderUtil.getMimeTypeFromContentType(" Text/Html "),
	       HeaderUtil.getMimeTypeFromContentType(" Text/Html "));
  }

  public void testGetCharsetFromContentType() {
    assertNull(HeaderUtil.getCharsetFromContentType(null));
    assertNull(HeaderUtil.getCharsetFromContentType("text/html"));
    assertNull(HeaderUtil.getCharsetFromContentType("text/html;"));
    assertNull(HeaderUtil.getCharsetFromContentType("text/html;foobar"));
    assertNull(HeaderUtil.getCharsetFromContentType("text/html;charset"));
    assertEquals("utf-8",
		 HeaderUtil.getCharsetFromContentType("text/html;charset=UTF-8"));
    assertEquals("iso8859-1",
		 HeaderUtil.getCharsetFromContentType("text/html;charset=\"iso8859-1\""));
    assertEquals("foo-1",
		 HeaderUtil.getCharsetFromContentType("text/html;charset=\"foo-1\";other=stuff"));
    assertEquals("foo-1",
		 HeaderUtil.getCharsetFromContentType("text/html;charset=foo-1;other=stuff"));
    assertSame(HeaderUtil.getCharsetFromContentType("text/html;charset=\"iso8859-1\""),
	       HeaderUtil.getCharsetFromContentType("text/html;charset=\"iso8859-1\""));
  }

  public void testIsEarlier() throws Exception {
    String t1 = "Wed, 17 Sep 2008 18:24:58 GMT";
    String t2 = "Thu, 18 Sep 2008 18:24:58 GMT";
    assertFalse(HeaderUtil.isEarlier(t1, t1));
    assertFalse(HeaderUtil.isEarlier(t1, new String(t1)));
    assertTrue(HeaderUtil.isEarlier(t1, t2));
    assertFalse(HeaderUtil.isEarlier(t2, t1));
  }
}

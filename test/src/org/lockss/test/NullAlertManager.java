/*
 * $Id: NullAlertManager.java,v 1.5 2005-10-07 23:42:27 tlipkis Exp $
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

package org.lockss.test;

import java.util.*;

import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.alert.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.app.*;

/**
 * Null version of the AlertManager.
 */
public class NullAlertManager extends BaseLockssDaemonManager
  implements AlertManager {
  private static Logger log = Logger.getLogger("NullAlertManager");

  protected void setConfig(Configuration config, Configuration oldConfig,
			   Configuration.Differences changedKeys) {
  }

  public void raiseAlert(Alert alert, String text) {
  }

  public void raiseAlert(Alert alert) {
  }

  public AlertConfig getConfig() {
    return new AlertConfig();
  }

  public void updateConfig(AlertConfig config) throws Exception {
  }
}

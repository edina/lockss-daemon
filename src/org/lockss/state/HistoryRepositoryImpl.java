/*
 * $Id: HistoryRepositoryImpl.java,v 1.19 2003-03-11 19:10:11 aalto Exp $
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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


package org.lockss.state;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;

import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.mapping.Mapping;

import org.lockss.util.*;
import org.lockss.repository.*;
import org.lockss.daemon.Configuration;
import org.lockss.app.*;
import org.lockss.plugin.*;
import java.net.URL;


/**
 * HistoryRepository is an inner layer of the NodeManager which handles the actual
 * storage of NodeStates.
 */
public class HistoryRepositoryImpl implements HistoryRepository, LockssManager {
  /**
   * Configuration parameter name for Lockss history location.
   */
  public static final String PARAM_HISTORY_LOCATION =
    Configuration.PREFIX + "history.location";

  /**
   * Configuration parameter name for Lockss history mapping file location.
   */
  public static final String PARAM_MAPPING_FILE_LOCATION =
    Configuration.PREFIX + "history.mapping.file";
  /**
   * Name of top directory in which the histories are stored.
   */
  public static final String HISTORY_ROOT_NAME = "cache";

  static final String HISTORY_FILE_NAME = "history.xml";
  static final String AU_FILE_NAME = "au_state.xml";
  // this contains a '#' so that it's not defeatable by strings which
  // match the prefix in a url (like '../tmp/')
  private static final String TEST_PREFIX = "/#tmp";

  private static String rootDir;
  private Mapping mapping = null;
  private static Logger logger = Logger.getLogger("HistoryRepository");

  private static LockssDaemon theDaemon;
  private static HistoryRepositoryImpl theRepository;

  HistoryRepositoryImpl(String repository_location) {
    rootDir = repository_location;
  }

  public HistoryRepositoryImpl() {}

  /**
   * init the plugin manager.
   * @param daemon the LockssDaemon instance
   * @throws LockssDaemonException if we already instantiated this manager
   * @see org.lockss.app.LockssManager#initService(LockssDaemon daemon)
   */
  public void initService(LockssDaemon daemon) throws LockssDaemonException {
    if(theRepository == null) {
      theDaemon = daemon;
      theRepository = this;
      if (rootDir==null) {
        rootDir = Configuration.getParam(PARAM_HISTORY_LOCATION);
        if (rootDir==null) {
          logger.error("Couldn't get "+PARAM_HISTORY_LOCATION+" from Configuration");
          throw new LockssRepository.RepositoryStateException("Couldn't load param.");
        }
      }
    }
    else {
      throw new LockssDaemonException("Multiple Instantiation.");
    }
  }

  /**
   * start the plugin manager.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    // we want to checkpoint here

    theRepository = null;
  }



  public void storePollHistories(NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    try {
      File nodeDir = new File(getNodeLocation(cus));
      if (!nodeDir.exists()) {
        nodeDir.mkdirs();
      }
      File nodeFile = new File(nodeDir, HISTORY_FILE_NAME);
      NodeHistoryBean nhb = new NodeHistoryBean();
      nhb.historyBeans = ((NodeStateImpl)nodeState).getPollHistoryBeanList();
      Marshaller marshaller = new Marshaller(new FileWriter(nodeFile));
      marshaller.setMapping(getMapping());
      marshaller.marshal(nhb);
    } catch (Exception e) {
      logger.error("Couldn't store poll history: ", e);
      throw new LockssRepository.RepositoryStateException("Couldn't store history.");
    }
  }

  public void loadPollHistories(NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    File nodeFile = null;
    try {
      nodeFile = new File(getNodeLocation(cus) + File.separator +
                               HISTORY_FILE_NAME);
      if (!nodeFile.exists()) {
        ((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList());
        logger.warning("No history file found.");
        return;
      }
      Unmarshaller unmarshaller = new Unmarshaller(NodeHistoryBean.class);
      unmarshaller.setMapping(getMapping());
      NodeHistoryBean nhb = (NodeHistoryBean)unmarshaller.unmarshal(new FileReader(nodeFile));
      if (nhb.historyBeans==null) {
        logger.warning("Empty history list loaded.");
        nhb.historyBeans = new ArrayList();
      }
      ((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList(nhb.historyBeans));
    } catch (org.exolab.castor.xml.MarshalException me) {
      logger.error("Parsing exception.  Moving file to '.old'");
      nodeFile.renameTo(new File(nodeFile.getAbsolutePath()+".old"));
      ((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList());
    } catch (Exception e) {
      logger.error("Couldn't load poll history: ", e);
      throw new LockssRepository.RepositoryStateException("Couldn't load history.");
    }
  }

  public void storeAuState(AuState auState) {
    try {
      File nodeDir = new File(getAuLocation(auState.getArchivalUnit()));
      if (!nodeDir.exists()) {
        nodeDir.mkdirs();
      }
      File auFile = new File(nodeDir, AU_FILE_NAME);
      Marshaller marshaller = new Marshaller(new FileWriter(auFile));
      marshaller.setMapping(getMapping());
      marshaller.marshal(new AuStateBean(auState));
    } catch (Exception e) {
      logger.error("Couldn't store au state: ", e);
      throw new LockssRepository.RepositoryStateException("Couldn't store history.");
    }
  }

  public AuState loadAuState(ArchivalUnit au) {
    try {
      File auFile = new File(getAuLocation(au) + File.separator + AU_FILE_NAME);
      if (!auFile.exists()) {
        logger.warning("No au file found.");
        return new AuState(au, -1, -1, -1);
      }
      Unmarshaller unmarshaller = new Unmarshaller(AuStateBean.class);
      unmarshaller.setMapping(getMapping());
      AuStateBean asb = (AuStateBean)unmarshaller.unmarshal(new FileReader(auFile));
      return new AuState(au, asb.getLastCrawlTime(),
                         asb.getLastTopLevelPollTime(),
                         asb.getLastTreeWalkTime());
    } catch (Exception e) {
      logger.error("Couldn't load au state: ", e);
      throw new LockssRepository.RepositoryStateException("Couldn't load au state.");
    }
  }

  public static HistoryRepository getHistoryRepository() {
    if (rootDir==null) {
      rootDir = Configuration.getParam(PARAM_HISTORY_LOCATION);
      if (rootDir==null) {
        logger.error("Couldn't get "+PARAM_HISTORY_LOCATION+" from Configuration");
        throw new LockssRepository.RepositoryStateException("Couldn't load param.");
      }
    }
    return new HistoryRepositoryImpl(rootDir);
  }

  protected String getNodeLocation(CachedUrlSet cus)
      throws MalformedURLException {
    StringBuffer buffer = new StringBuffer(rootDir);
    if (!rootDir.endsWith(File.separator)) {
      buffer.append(File.separator);
    }
    buffer.append(HISTORY_ROOT_NAME);
    buffer.append(File.separator);
    String auLoc = LockssRepositoryServiceImpl.mapAuToFileLocation(buffer.toString(),
        cus.getArchivalUnit());
    String urlStr = (String)cus.getUrl();
    if (AuUrl.isAuUrl(urlStr)) {
      return auLoc;
    } else {
      try {
        URL testUrl = new URL(urlStr);
        String path = testUrl.getPath();
        if (path.indexOf("..")>=0) {
          // filtering to remove urls including '..' and such
          path = TEST_PREFIX + path;
          File testFile = new File(path);
          String canonPath = testFile.getCanonicalPath();
          if (canonPath.startsWith(TEST_PREFIX)) {
            urlStr = testUrl.getProtocol() + "://" +
                testUrl.getHost().toLowerCase()
                + canonPath.substring(TEST_PREFIX.length());
          }
          else {
            logger.error("Illegal URL detected: " + urlStr);
            throw new MalformedURLException("Illegal URL detected.");
          }
        }
      } catch (IOException ie) {
        logger.error("Error testing URL: "+ie);
        throw new MalformedURLException ("Error testing URL.");
      }
      return LockssRepositoryServiceImpl.mapUrlToFileLocation(auLoc, urlStr);
    }
  }

  protected String getAuLocation(ArchivalUnit au) {
    StringBuffer buffer = new StringBuffer(rootDir);
    if (!rootDir.endsWith(File.separator)) {
      buffer.append(File.separator);
    }
    buffer.append(HISTORY_ROOT_NAME);
    buffer.append(File.separator);
    return LockssRepositoryServiceImpl.mapAuToFileLocation(buffer.toString(), au);
  }

  protected Mapping getMapping() throws Exception {
    if (mapping==null) {
      String mappingFile = Configuration.getParam(PARAM_MAPPING_FILE_LOCATION);
      if (mappingFile==null) {
        logger.error("Couldn't get "+PARAM_MAPPING_FILE_LOCATION+" from Configuration");
        throw new LockssRepository.RepositoryStateException("Couldn't load param.");
      }
      mapping = new Mapping();
      mapping.loadMapping(mappingFile);
    }
    return mapping;
  }
}
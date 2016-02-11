/*
 * $Id$
 */

/*

Copyright (c) 2000-2016 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.servlet;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.*;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.lockss.account.UserAccount;
import org.lockss.alert.Alert;
import org.lockss.app.LockssDaemon;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;
import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo.ResolvedTo;
import org.lockss.exporter.biblio.BibliographicItem;
import org.lockss.exporter.counter.*;
import org.lockss.exporter.counter.CounterReportsRequestRecorder.PublisherContacted;
import org.lockss.plugin.*;
import org.lockss.plugin.AuUtil.AuProxyInfo;
import org.lockss.plugin.PluginManager.CuContentReq;
import org.lockss.plugin.base.BaseUrlFetcher;
import org.lockss.proxy.ProxyManager;
import org.lockss.rewriter.LinkRewriterFactory;
import org.lockss.safenet.EntitlementRegistryClient;
import org.lockss.safenet.PublisherWorkflow;
import org.lockss.state.AuState;
import org.lockss.util.*;
import org.lockss.util.CloseCallbackInputStream.DeleteFileOnCloseInputStream;
import org.lockss.util.urlconn.*;
import org.mortbay.html.*;
import org.mortbay.http.*;

@SuppressWarnings("serial")
public class SafeNetServeContent extends ServeContent {
  
  private static final Logger log = Logger.getLogger(SafeNetServeContent.class);

  /** Ediauth configuration **/
  public static final String PARAM_EDIAUTH_URL = PREFIX + "ediauthUrl";

  private static final String INSTITUTION_HEADER = "X-SafeNet-Institution";

  private static String ediauthUrl;

  private PublisherWorkflow workflow;
  private String institution;
  private EntitlementRegistryClient entitlementRegistry;

  // don't hold onto objects after request finished
  protected void resetLocals() {
    workflow = null;
    super.resetLocals();
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    LockssDaemon daemon = getLockssDaemon();
    entitlementRegistry = daemon.getEntitlementRegistryClient();
  }

  /** Called by ServletUtil.setConfig() */
  static void setConfig(Configuration config,
                        Configuration oldConfig,
                        Configuration.Differences diffs) {
      ServeContent.setConfig(config, oldConfig, diffs);
    if (diffs.contains(PREFIX)) {
      ediauthUrl = config.get(PARAM_EDIAUTH_URL);
    }
  }

  protected boolean isNeverProxyForAu(ArchivalUnit au) {
    return super.isNeverProxyForAu(au) || workflow == PublisherWorkflow.PRIMARY_SAFENET;
  }

  /**
   * Handle a request
   * @throws IOException
   */
  public void lockssHandleRequest() throws IOException {
    
      
	  // Redirect user to ediauth login page if doesn't have a institution allocated
	  if( this.getSession().getAttribute("scope") == null ){
	    String token = req.getParameter("ediauthToken");
	    if(token != null){
	      // Reassign userAccount
	      String userInstScope = this.getAccountManager().getFromMapToken(token);
	      
	      // UserAccount user = getUserAccount();
	      log.debug("Assigning inst. scope to user:"+userInstScope);
	      this.getSession().setAttribute("scope", userInstScope);
	    } else {
	      log.debug("Redirecting user to ediauth: "+ediauthUrl);
	      resp.sendRedirect(ediauthUrl);
	      return;
	    }
	  } else {
	    log.debug("User already have inst. allocated!");
	  }
      
    updateInstitution();

    super.lockssHandleRequest();
  }

  protected boolean setCachedUrlAndAu() throws IOException {
    // Find a CU that the user is entitled to access, and with content if possible.  If none, find an AU where
    // it would fit so can rewrite content from publisher if necessary.
    List<CachedUrl> cachedUrls = pluginMgr.findCachedUrls(url, CuContentReq.PreferContent);
    if(cachedUrls != null && !cachedUrls.isEmpty()) {
      for(CachedUrl cachedUrl: cachedUrls) {
        try {
          if(isUserEntitled(cachedUrl.getArchivalUnit())) {
            cu = cachedUrl;
            au = cu.getArchivalUnit();
            if (log.isDebug3()) log.debug3("cu: " + cu + " au: " + au);
            break;
          }
        }
        catch (IOException e) {
          // We can't communicate with the ER, so we have to assume that we can't give the user access to the content at the moment
          log.error("Error communicating with entitlement registry: " + e);
          handleEntitlementRegistryErrorUrlRequest(url);
          return false;
        }
        catch (IllegalArgumentException e) {
          // We don't have enough information about the AU to determine if the user is entitled, but there's nothing they can do about it
          log.error("Error with AU configuration: " + e);
          handleMissingUrlRequest(url, PubState.KnownDown);
          return false;
        }
      }
      if(cu == null) {
        // We found at least one CachedUrl, which means the content is preserved, but the user wasn't entitled to any of them
        handleUnauthorisedUrlRequest(url);
        return false;
      }
    }
    return true;
  }


  /**
   * Handle request for content that belongs to one of our AUs, whether or not
   * we have content for that URL.  If this request contains a version param,
   * serve it from cache with a Memento-Datetime header and no
   * link-rewriting.  For requests without a version param, rewrite links,
   * and serve from publisher if publisher provides it and the daemon options
   * allow it; otherwise, try to serve from cache.
   *
   * @throws IOException for IO errors
   */
  protected void handleAuRequest() throws IOException {
    String host = UrlUtil.getHost(url);
    boolean isInCache = isInCache();
    boolean isHostDown = proxyMgr.isHostDown(host);
    PublisherContacted pubContacted =
	CounterReportsRequestRecorder.PublisherContacted.FALSE;

    try {
      if (!isUserEntitled(au)) {
        handleUnauthorisedUrlRequest(url);
        return;
      }
      workflow = getPublisherWorkflow(au);
      if (workflow == PublisherWorkflow.LIBRARY_NOTIFICATION) {
        handleUnauthorisedUrlRequest(url);
        return;
      }
    }
    catch (IOException e) {
      // We can't communicate with the ER, so we have to assume that we can't give the user access to the content at the moment
      log.error("Error communicating with entitlement registry: " + e);
      handleEntitlementRegistryErrorUrlRequest(url);
      return;
    }
    catch (IllegalArgumentException e) {
      // We don't have enough information about the AU to determine if the user is entitled, but there's nothing they can do about it
      log.error("Error with AU configuration: " + e);
      handleMissingUrlRequest(url, PubState.KnownDown);
      return;
    }

    if (isNeverProxyForAu(au) || isMementoRequest()) {
      if (isInCache) {
        serveFromCache();
        logAccess("200 from cache");
        // Record the necessary information required for COUNTER reports.
	recordRequest(url, pubContacted, 200);
      } else {
	/*
	 * We don't want to redirect to the publisher, so pass KnownDown below
	 * in order to ensure that. It's true that we might be lying, because
	 * the publisher might be up.
	 */
        handleMissingUrlRequest(url, PubState.KnownDown);
      }
      return;
    }

    LockssUrlConnectionPool connPool = proxyMgr.getNormalConnectionPool();

    if (!isInCache && isHostDown) {
      switch (proxyMgr.getHostDownAction()) {
        case ProxyManager.HOST_DOWN_NO_CACHE_ACTION_504:
          handleMissingUrlRequest(url, PubState.RecentlyDown);
          return;
        case ProxyManager.HOST_DOWN_NO_CACHE_ACTION_QUICK:
          connPool = proxyMgr.getQuickConnectionPool();
          break;
        default:
        case ProxyManager.HOST_DOWN_NO_CACHE_ACTION_NORMAL:
          connPool = proxyMgr.getNormalConnectionPool();
          break;
      }
    }

    // Send request to publisher
    LockssUrlConnection conn = null;
    PubState pstate = PubState.Unknown;
    try {
      conn = openLockssUrlConnection(connPool);

      // set proxy for connection if specified
      AuProxyInfo info = AuUtil.getAuProxyInfo(au);
      String proxyHost = info.getHost();
      int proxyPort = info.getPort();
      if (!StringUtil.isNullString(proxyHost) && (proxyPort > 0)) {
        try {
          conn.setProxy(info.getHost(), info.getPort());
        } catch (UnsupportedOperationException ex) {
          log.warning(  "Unsupported connection request proxy: "
                        + proxyHost + ":" + proxyPort);
        }
      }
      conn.execute();
      pubContacted = CounterReportsRequestRecorder.PublisherContacted.TRUE;
    } catch (IOException ex) {
      if (log.isDebug3()) log.debug3("conn.execute", ex);

      // mark host down if connection timed out
      if (ex instanceof LockssUrlConnection.ConnectionTimeoutException) {
        proxyMgr.setHostDown(host, isInCache);
      } else {
	pubContacted = CounterReportsRequestRecorder.PublisherContacted.TRUE;
      }
      pstate = PubState.KnownDown;

      // tear down connection
      IOUtil.safeRelease(conn);
      conn = null;
    }

    int response = 0;
    try {
      if (conn != null) {
	response = conn.getResponseCode();
        if (log.isDebug2())
          log.debug2("response: " + response + " " + conn.getResponseMessage());
        if (response == HttpResponse.__200_OK) {
          // If publisher responds with content, serve it to user
          // XXX Should check for a login page here
          try {
            serveFromPublisher(conn);
            logAccess(present(isInCache, "200 from publisher"));
            // Record the necessary information required for COUNTER reports.
	    recordRequest(url, pubContacted, response);
            return;
          } catch (CacheException.PermissionException ex) {
            logAccess("login exception: " + ex.getMessage());
            pstate = PubState.NoContent;
          }
        } else {
          pstate = PubState.NoContent;
        }
      }
    } finally {
      // ensure connection is closed
      IOUtil.safeRelease(conn);
    }

    // Either failed to open connection or got non-200 response.
    if (isInCache) {
      serveFromCache();
      logAccess("present, 200 from cache");
      // Record the necessary information required for COUNTER reports.
      recordRequest(url, pubContacted, response);
    } else {
      log.debug2("No content for: " + url);
      // return 404 with index
      handleMissingUrlRequest(url, pstate);
    }
  }

  protected LockssUrlConnection doOpenConnection(String url, LockssUrlConnectionPool pool) throws IOException {
    return super.openConnection(url, pool);
  }

  protected LockssUrlConnection openConnection(String url, LockssUrlConnectionPool pool) throws IOException {
    LockssUrlConnection conn = doOpenConnection(url, pool);
    conn.addRequestProperty(INSTITUTION_HEADER, (String) getSession().getAttribute("scope"));
    conn.addRequestProperty(HttpFields.__Via,
        proxyMgr.makeVia(getMachineName(),
            reqURL.getPort()));

    String cookiePolicy = proxyMgr.getCookiePolicy();
    if (cookiePolicy != null &&
        !cookiePolicy.equalsIgnoreCase(ProxyManager.COOKIE_POLICY_DEFAULT)) {
      conn.setCookiePolicy(cookiePolicy);
    }

    return conn;
  }

  protected LockssUrlConnection openConnection(String url, LockssUrlConnectionPool pool) throws IOException {
    return UrlUtil.openConnection(url, pool);
  }

  protected void handleEntitlementRegistryErrorUrlRequest(String missingUrl)
      throws IOException {
    handleUrlRequestError(missingUrl, PubState.KnownDown, "An error occurred trying to access the requested URL on this LOCKSS box. This may be temporary and you may wish to report this, and try again later. ", HttpResponse.__503_Service_Unavailable, "entitlement registry error");
  }

  protected void handleUnauthorisedUrlRequest(String missingUrl)
      throws IOException {
    handleUrlRequestError(missingUrl, PubState.KnownDown, "You are not authorised to access the requested URL on this LOCKSS box. ", HttpResponse.__403_Forbidden, "unauthorised");
  }

  protected void handleMissingUrlRequest(String missingUrl, PubState pstate)
      throws IOException {
    handleUrlRequestError(missingUrl, pstate, "The requested URL is not preserved on this LOCKSS box. ", HttpResponse.__404_Not_Found, "not present");
  }

  protected void handleUrlRequestError(String missingUrl, PubState pstate, String errorMessage, int responseCode, String logMessage)
      throws IOException {
    String missing =
        missingUrl + ((au != null) ? " in AU: " + au.getName() : "");

    Block block = new Block(Block.Center);
    // display publisher page
    block.add("<p>");
    block.add(errorMessage);
    block.add("Select link");
    block.add(addFootnote(
        "Selecting publisher link takes you away from this LOCKSS box."));
    block.add(" to view it at the publisher:</p>");
    block.add("<a href=\"" + missingUrl + "\">" + missingUrl + "</a><br/><br/>");

    switch (getMissingFileAction(pstate)) {
      case Error_404:
        resp.sendError(responseCode,
            missing + " is not preserved on this LOCKSS box");
        logAccess(logMessage + ", " + responseCode);
        break;
      case Redirect:
      case AlwaysRedirect:
        redirectToUrl();
        break;
      case HostAuIndex:
        Collection<ArchivalUnit> candidateAus = Collections.emptyList();
        try {
            candidateAus = pluginMgr.getCandidateAus(missingUrl);
        } catch (MalformedURLException ex) {
          // ignore error, serve file
          log.warning("Handling URL: " + url + " throws ", ex);
        }
        if (candidateAus != null && !candidateAus.isEmpty()) {
          displayIndexPage(candidateAus,
              responseCode,
              block,
              candidates404Msg);
          logAccess(logMessage + ", " + responseCode + " with index");
        } else {
          displayIndexPage(Collections.<ArchivalUnit>emptyList(),
              responseCode,
              block,
              null);
          logAccess(logMessage + ", " + responseCode);
        }
        break;
      case AuIndex:
        displayIndexPage(pluginMgr.getAllAus(),
            responseCode,
            block,
            null);
        logAccess(logMessage + ", " + responseCode + " with index");
        break;
    }
  }

  void displayIndexPage(Collection<ArchivalUnit> auList,
                        int result,
                        Element headerElement,
                        String headerText)
      throws IOException {
    Predicate pred;
    boolean offerUnfilteredList = false;
    if (enabledPluginsOnly) {
      pred = PredicateUtils.andPredicate(enabledAusPred, allAusPred);
      offerUnfilteredList = areAnyExcluded(auList, enabledAusPred);
    } else {
      pred = allAusPred;
    }
    Page page = newPage();
    
    String scope = (String)this.getSession().getAttribute("scope");
    if(scope != null) {
      page.add("User inst: " + scope);
    } else {
      page.add("User inst: unknown");
    }

    if (headerElement != null) {
      page.add(headerElement);
    }

    Block centeredBlock = new Block(Block.Center);

    if (areAllExcluded(auList, pred) && !offerUnfilteredList) {
      ServletUtil.layoutExplanationBlock(centeredBlock,
          "No matching content has been preserved on this LOCKSS box");
    } else {
      // Layout manifest index w/ URLs pointing to this servlet
      Element ele =
          ServletUtil.manifestIndex(pluginMgr,
              auList,
              pred,
              headerText,
              new ServletUtil.ManifestUrlTransform() {
                public Object transformUrl(String url,
                                           ArchivalUnit au){
                  Properties query =
                      PropUtil.fromArgs("url", url);
                  if (au != null) {
                    query.put("auid", au.getAuId());
                  }
                  return srvLink(myServletDescr(),
                      url, query);
                }},
              true);
      centeredBlock.add(ele);
      if (offerUnfilteredList) {
        centeredBlock.add("<br>");
        centeredBlock.add("Other possibly relevant content has not yet been "
                          + "certified for use with SafeNetServeContent and may not "
                          + "display correctly.  Click ");
        Properties args = getParamsAsProps();
        args.put("filterPlugins", "no");
        centeredBlock.add(srvLink(myServletDescr(), "here", args));
        centeredBlock.add(" to see the complete list.");
      }
    }
    page.add(centeredBlock);
    if (result > 0) {
      resp.setStatus(result);
    }
    endPage(page);
  }


  void updateInstitution() throws IOException {
      //This is currently called in lockssHandleRequest, it needs to be called from wherever we do the SAML authentication
      String scope = (String)this.getSession().getAttribute("scope");
      institution = entitlementRegistry.getInstitution(scope);
  }

  boolean isUserEntitled(ArchivalUnit au) throws IOException, IllegalArgumentException {
      TdbAu tdbAu = au.getTdbAu();
      String issn = tdbAu.getIssn();
      if(StringUtil.isNullString(issn)) {
        throw new IllegalArgumentException("ArchivalUnit has no ISSN");
      }
      String start = tdbAu.getStartYear() + "0101";
      String end = tdbAu.getEndYear() + "1231";
      
      return entitlementRegistry.isUserEntitled(issn, institution, start, end);
  }

  PublisherWorkflow getPublisherWorkflow(ArchivalUnit au) throws IOException, IllegalArgumentException {
      TdbAu tdbAu = au.getTdbAu();
      String issn = tdbAu.getIssn();
      if(StringUtil.isNullString(issn)) {
        throw new IllegalArgumentException("ArchivalUnit has no ISSN");
      }
      String start = tdbAu.getStartYear() + "0101";
      String end = tdbAu.getEndYear() + "1231";

//      return PublisherWorkflow.PRIMARY_SAFENET;
      String publisher = entitlementRegistry.getPublisher(issn, start, end);
      if(StringUtil.isNullString(publisher)) {
        throw new IllegalArgumentException("No publisher found");
      }

      return entitlementRegistry.getPublisherWorkflow(publisher);
  }

}


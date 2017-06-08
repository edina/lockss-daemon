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

import org.apache.commons.collections.*;
import org.lockss.account.UserAccount;
import org.lockss.app.LockssDaemon;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.daemon.OpenUrlResolver.OpenUrlInfo;
import org.lockss.exporter.biblio.*;
import org.lockss.exporter.counter.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.plugin.PluginManager.CuContentReq;
import org.lockss.safenet.EntitlementRegistryClient;
import org.lockss.safenet.PublisherWorkflow;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.mortbay.html.*;
import org.mortbay.http.*;

@SuppressWarnings("serial")
public class SafeNetServeContent extends ServeContent {
  
  private static final Logger log = Logger.getLogger(SafeNetServeContent.class);

  /** Ediauth configuration **/
  public static final String PARAM_EDIAUTH_URL = PREFIX + "ediauthUrl";
  // If true, scope can be 'mocked' from the URL parameters. This is for testing purposes, and should never be true in production
  public static final String PARAM_EDIAUTH_MOCK_SCOPE = PREFIX + "mockScope";

  private static final String INSTITUTION_HEADER = "X-SafeNet-Institution";

  public static final String INSTITUTION_SCOPE_SESSION_KEY = "scope";

  private static String ediauthUrl;
  private static boolean mockScope = false;

  private PublisherWorkflow workflow;
  private String institution;
  private String issn;
  private String start;
  private String end;
  private EntitlementRegistryClient entitlementRegistry;

  // don't hold onto objects after request finished
  protected void resetLocals() {
    workflow = null;
    institution = null;
    issn = null;
    start = null;
    end = null;
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
      mockScope = config.getBoolean(PARAM_EDIAUTH_MOCK_SCOPE, false);
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

          if ( mockScope ) {
	    String userInstScope = req.getParameter(INSTITUTION_SCOPE_SESSION_KEY);

            if ( ! "".equals(userInstScope) ) {
              log.warning("Setting scope from parameters:"+userInstScope+" This should not be done in production");
              this.getSession().setAttribute(INSTITUTION_SCOPE_SESSION_KEY, userInstScope);
            }
          }

	  // Redirect user to ediauth login page if doesn't have a institution allocated
	  if( this.getSession().getAttribute(INSTITUTION_SCOPE_SESSION_KEY) == null ){
	    String token = req.getParameter("ediauthToken");
	    if(token != null){
	      // Reassign userAccount
	      String userInstScope = this.getAccountManager().getFromMapToken(token);

	      // UserAccount user = getUserAccount();
	      log.debug("Assigning inst. scope to user:"+userInstScope);
	      this.getSession().setAttribute(INSTITUTION_SCOPE_SESSION_KEY, userInstScope);
	    } else {
	      log.debug("Redirecting user to ediauth: "+ediauthUrl);
	      // Build current Url removing ediauthToken if exists
	      StringBuffer requestURL = req.getRequestURL();
	      if (req.getQueryString() != null) {
	          requestURL.append("?");
	          Enumeration<String> paramNames = req.getParameterNames();
	          while (paramNames.hasMoreElements()) {
	            String paramName = paramNames.nextElement();
	            if(!paramName.equals("ediauthToken")){
  	            String paramValue = req.getParameter(paramName);
  	            requestURL.append(paramName).append("=").append(paramValue);
  	            if(paramNames.hasMoreElements()){
  	              requestURL.append("&");
  	            }
	            }
	          }
	      }
	      String completeURL = requestURL.toString();
              log.debug("Current url: "+completeURL);
	      String redirectUrl = ediauthUrl+"?context="+URLEncoder.encode(completeURL, "UTF-8");
              log.debug("Redirect url: "+redirectUrl);
	      resp.sendRedirect(redirectUrl);
	      return;
	    }
	  } else {
	    log.debug("User already have inst. allocated!");
	  }

    updateInstitution();

    super.lockssHandleRequest();
  }

  protected boolean setCachedUrlAndAu() throws IOException {
    // Find a CU that the user is entitled to access, and with content
    List<CachedUrl> cachedUrls = pluginMgr.findCachedUrls(url, CuContentReq.HasContent);
    if(cachedUrls != null && !cachedUrls.isEmpty()) {
      for(CachedUrl cachedUrl: cachedUrls) {
        try {
          if(isUserEntitled(cachedUrl.getArchivalUnit())) {
            cu = cachedUrl;
            au = cu.getArchivalUnit();
            if (log.isDebug3()) log.debug("cu: " + cu + " au: " + au);
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


  protected void handleOpenUrlInfo(OpenUrlInfo info) throws IOException {
    setBibInfoFromOpenUrl(info);
    super.handleOpenUrlInfo(info);
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

    super.handleAuRequest();
  }

  protected LockssUrlConnection doOpenConnection(String url, LockssUrlConnectionPool pool) throws IOException {
    return super.openConnection(url, pool);
  }

  protected LockssUrlConnection openConnection(String url, LockssUrlConnectionPool pool) throws IOException {
    LockssUrlConnection conn = doOpenConnection(url, pool);
    conn.addRequestProperty(INSTITUTION_HEADER, (String) getSession().getAttribute(INSTITUTION_SCOPE_SESSION_KEY));
    return conn;
  }

  protected void handleEntitlementRegistryErrorUrlRequest(String missingUrl)
      throws IOException {
    handleUrlRequestError(missingUrl, PubState.KnownDown, "An error occurred trying to access the requested URL on this LOCKSS box. This may be temporary and you may wish to report this, and try again later. ", HttpResponse.__503_Service_Unavailable, "entitlement registry error");
  }

  protected void handleUnauthorisedUrlRequest(String missingUrl)
      throws IOException {
    handleUrlRequestError(missingUrl, PubState.KnownDown, "You are not authorised to access the requested URL on this LOCKSS box. ", HttpResponse.__403_Forbidden, "unauthorised");
  }


  void updateInstitution() throws IOException {
      //This is currently called in lockssHandleRequest, it needs to be called from wherever we do the SAML authentication
      String scope = (String)this.getSession().getAttribute(INSTITUTION_SCOPE_SESSION_KEY);
      institution = entitlementRegistry.getInstitution(scope);
  }

  boolean isUserEntitled(ArchivalUnit au) throws IOException, IllegalArgumentException {
      setBibInfoFromCu(cu, au);
      setBibInfoFromTdb(au);
      setBibInfoFromArticleFiles(au, cu);
      validateBibInfo();
      String startDate = start + "0101";
      String endDate = end + "1231";

      return entitlementRegistry.isUserEntitled(issn, institution, startDate, endDate);
  }

  PublisherWorkflow getPublisherWorkflow(ArchivalUnit au) throws IOException, IllegalArgumentException {
      setBibInfoFromCu(cu, au);
      setBibInfoFromTdb(au);
      setBibInfoFromArticleFiles(au, cu);
      validateBibInfo();
      String startDate = start + "0101";
      String endDate = end + "1231";

      String publisher = entitlementRegistry.getPublisher(issn, institution, startDate, endDate);
      if(StringUtil.isNullString(publisher)) {
        throw new IllegalArgumentException("No publisher found");
      }

      return entitlementRegistry.getPublisherWorkflow(publisher);
  }

  private void setBibInfoFromOpenUrl(OpenUrlInfo info) throws IllegalArgumentException {
    log.debug("Setting bib info from OpenURL");
    if(info != null) {
      log.debug("Info set");
      BibliographicItem item = info.getBibliographicItem();
      if(item != null) {
        log.debug("Item set");
        if(StringUtil.isNullString(issn)) {
          log.debug("Getting ISSN");
          issn = item.getIssn();
        }

        if(StringUtil.isNullString(start)) {
          log.debug("Getting start");
          start = item.getStartYear();
        }

        if(StringUtil.isNullString(end)) {
          log.debug("Getting end");
          end = item.getEndYear();
        }
      }
    }
  }

  private void setBibInfoFromArticleFiles(ArchivalUnit au, final CachedUrl cu) throws IllegalArgumentException {
    log.debug("Setting bib info from TDB");
    MetadataTarget target = new MetadataTarget(MetadataTarget.PURPOSE_OPENURL);
    if(au != null) {
      log.debug("AU set");
      Iterator<ArticleFiles> afs = au.getArticleIterator();
      ArticleMetadataExtractor mdExtractor = au.getPlugin().getArticleMetadataExtractor(target, au);
      if(mdExtractor != null) {
        log.debug("Extractor set");
        if(afs != null) {
          log.debug("Article Files set");
          while(afs.hasNext()) {
            ArticleFiles af = afs.next();
            ArticleMetadataExtractor.Emitter emitter = new ArticleMetadataExtractor.Emitter() {
              public void emitMetadata(ArticleFiles af2, ArticleMetadata md) {
                log.debug("ArticleMetadata found");
                String mdUrl = md.get("access.url");
                String cuUrl = cu.getUrl();
                log.debug("Comparing " + mdUrl + " to " + cuUrl);
                if(mdUrl.equals(cuUrl)) {
                  BibliographicItemImpl item = new BibliographicItemImpl();
                  item.setPrintIssn(md.get("issn"));
                  item.setEissn(md.get("eissn"));
                  item.setIssnL(md.get("issnl"));
                  item.setYear(md.get("date"));
                  if(StringUtil.isNullString(issn)) {
                    log.debug("Getting ISSN");
                    issn = item.getIssn();
                  }

                  if(StringUtil.isNullString(start)) {
                    log.debug("Getting start");
                    start = item.getStartYear();
                  }

                  if(StringUtil.isNullString(end)) {
                    log.debug("Getting end");
                    end = item.getEndYear();
                  }
                }
              }
            };
            try {
              mdExtractor.extract(target, af, emitter);
            }
            catch(IOException e) {
              log.error("Error extracting article metadata", e);
            }
            catch(PluginException e) {
              log.error("Error extracting article metadata", e);
            }
          }
        }
      }
    }
  }

  private void setBibInfoFromTdb(ArchivalUnit au) throws IllegalArgumentException {
    log.debug("Setting bib info from TDB");
    if(au != null) {
      log.debug("AU set");
      TdbAu tdbAu = au.getTdbAu();
      if(tdbAu != null) {
        log.debug("TdbAU set");

        if(StringUtil.isNullString(issn)) {
          log.debug("Getting ISSN");
          issn = tdbAu.getIssn();
        }

        if(StringUtil.isNullString(start)) {
          log.debug("Getting start");
          start = tdbAu.getStartYear();
        }

        if(StringUtil.isNullString(end)) {
          log.debug("Getting end");
          end = tdbAu.getEndYear();
        }
      }
    }
  }

  private void setBibInfoFromCu(CachedUrl cu, ArchivalUnit au) {
    log.debug("Setting bib info from CU");
    if(cu != null) {
      log.debug("CU set");
      if(au != null) {
        log.debug("AU set");
        MetadataTarget target = new MetadataTarget(MetadataTarget.PURPOSE_OPENURL);
        Plugin plugin = au.getPlugin();
        if(plugin != null) {
          log.debug("Plugin set");
          FileMetadataExtractor mdExtractor = plugin.getFileMetadataExtractor(target, cu.getContentType(), au);
          if(mdExtractor != null) {
            log.debug("Extractor set");
            FileMetadataExtractor.Emitter emitter = new FileMetadataExtractor.Emitter() {
              public void emitMetadata(CachedUrl cu, ArticleMetadata md) {
                log.debug("ArticleMetadata found");
                BibliographicItemImpl item = new BibliographicItemImpl();
                item.setPrintIssn(md.get("issn"));
                item.setEissn(md.get("eissn"));
                item.setIssnL(md.get("issnl"));
                item.setYear(md.get("date"));
                if(StringUtil.isNullString(issn)) {
                  log.debug("Getting ISSN");
                  issn = item.getIssn();
                }

                if(StringUtil.isNullString(start)) {
                  log.debug("Getting start");
                  start = item.getStartYear();
                }

                if(StringUtil.isNullString(end)) {
                  log.debug("Getting end");
                  end = item.getEndYear();
                }
              }
            };
            try {
              mdExtractor.extract(target, cu, emitter);
            }
            catch(IOException e) {
              log.error("Error extracting CU metadata", e);
            }
            catch(PluginException e) {
              log.error("Error extracting CU metadata", e);
            }
          }
        }
      }
    }
  }

  private void validateBibInfo() {
     if(StringUtil.isNullString(issn)) {
       throw new IllegalArgumentException("ArchivalUnit has no ISSN");
     }
     if(StringUtil.isNullString(start)) {
       throw new IllegalArgumentException("ArchivalUnit has no start year");
     }
     if(StringUtil.isNullString(end)) {
       throw new IllegalArgumentException("ArchivalUnit has no end year");
     }
  }

  void logAccess(String url, String msg) {
      super.logAccess(url, "UA: \"" + req.getHeader("User-Agent") + "\" " + msg);
  }

  /**
   * @overwrite ServeContent to add scope
   * Record the request in COUNTER if appropriate
   */
  void recordRequest(String url,
		     CounterReportsRequestRecorder.PublisherContacted contacted,
		     int publisherCode) {
    log.debug("## Recording Request");
    System.out.println("Calling recordRequest()");
    String scope = (String)session.getAttribute(INSTITUTION_SCOPE_SESSION_KEY);
    if (proxyMgr.isCounterCountable(req.getHeader(HttpFields.__UserAgent))) {
      log.debug("RecordRequest: "+url+" scope: "+publisherCode);
      CounterReportsRequestRecorder.getInstance().recordRequest(url, contacted,
	  publisherCode, null, scope);
    }
  }
}


/*
 * $Id: OpenUrlResolver.java,v 1.29.4.1 2012-05-30 21:08:40 pgust Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.daemon;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.lang.StringEscapeUtils;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Tdb;
import org.lockss.config.TdbAu;
import org.lockss.config.TdbPublisher;
import org.lockss.config.TdbTitle;
import org.lockss.daemon.ConfigParamDescr.InvalidFormatException;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.PrintfConverter;
import org.lockss.plugin.PrintfConverter.UrlListConverter;
import org.lockss.util.ExternalizableMap;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;
import org.lockss.util.MetadataUtil;
import org.lockss.util.NumberUtil;
import org.lockss.util.StringUtil;
import org.lockss.util.TypedEntryMap;
import org.lockss.util.UrlUtil;
import org.lockss.util.urlconn.LockssUrlConnection;
import org.lockss.util.urlconn.LockssUrlConnectionPool;


/**
 * This class  implements an OpenURL resolver that locates an article matching 
 * properties corresponding to OpenURL keys.  Both OpenURL 1.0 and the earlier 
 * OpenURL 0.1 syntax are supported. Queries can be made by:
 * <ul>
 * <li>URL</li> 
 * <li>DOI</li>
 * 
 * <li>ISSN/volume/issue/page</li>
 * <li>ISSN/volume/issue/article-number</li>
 * <li>ISSN/volume/issue/author</li>
 * <li>ISSN/volume/issue/article-title</li>
 * <li>ISSN/date/page</li>
 * <li>ISSN/date/article-number</li>
 * <li>ISSN/date/author</li>
 * <li>ISSN/date/article-title</li>
 *
 * <li>journal-title/volume/issue/page</li>
 * <li>journal-title/volume/issue/article-number</li>
 * <li>journal-title/volume/issue/author</li>
 * <li>journal-title/volume/issue/article-title</li>
 * <li>journal-title/date/page</li>
 * <li>journal-title/date/article-number</li>
 * <li>journal-title/date/author</li>
 * <li>journal-title/date/article-title</li>
 *
 * <li>ISBN/page</li>
 * <li>ISBN/chapter-author</li>
 * <li>ISBN/chapter-title</li>
 * 
 * <li>book-title/page</li>
 * <li>book-title/chapter-author</li>
 * <li>book-title/chapter-title</li>
 *
 * <li>book-publisher/book-title/page</li>
 * <li>book-publisher/book-title/chapter-author</li>
 * <li>book-publisher/book-title/chapter-title</li>
 * 
 * <li>SICI</li>
 * <li>BICI</li>
 * </ul>
 * <p>
 * Note: the TDB of the current configuration is used to resolve journal or
 * if the entry is not in the metadata database, or if the query gives a
 * journal or book title but no ISSN or ISBN.  If there are multiple entries 
 * for the journal or book title, one of them is selected. OpenURL 1.0 allows
 * specifying a book publisher, so if both publisher and title are specified,
 * there is a good chance that the match will be unique.
 * 
 * @author  Philip Gust
 * @version 1.0
 */
public class OpenUrlResolver {
  private static Logger log = Logger.getLogger("OpenUrlResolver");

  /** the LOCKSS daemon */
  private final LockssDaemon daemon;
  
  /** maximum redirects for looking up DOI url */
  private static final int MAX_REDIRECTS = 10;
  
  private static final class FeatureEntry {
    final String auFeatureKey;
    final OpenUrlInfo.ResolvedTo resolvedTo;
    
    public FeatureEntry(String auFeatureKey, 
                        OpenUrlInfo.ResolvedTo resolvedTo) {
      this.auFeatureKey = auFeatureKey;
      this.resolvedTo = resolvedTo;
    }
  }
  
  /**
   * Keys to search for a matching journal feature. The order of the keys 
   * is the order they will be tried, from article, to issue, to volume, 
   * to title TOC.
   */
  private static final FeatureEntry[] auJournalauFeatures = {
//    "au_feature_urls/au_abstract",
    new FeatureEntry("au_feature_urls/au_article",OpenUrlInfo.ResolvedTo.ARTICLE),
    new FeatureEntry("au_feature_urls/au_issue", OpenUrlInfo.ResolvedTo.ISSUE),
    new FeatureEntry("au_issue_url", OpenUrlInfo.ResolvedTo.ISSUE),
    new FeatureEntry("au_feature_urls/au_volume", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_volume_url", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_start_url", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_feature_urls/au_title", OpenUrlInfo.ResolvedTo.TITLE),
    new FeatureEntry("au_title_url", OpenUrlInfo.ResolvedTo.TITLE)
  };
  
  /**
   * Keys to search for a matching book feature. The order of the keys is the
   * the order they will be tried, from chapter, to volume, to title TOC.
   */
  private static final FeatureEntry[] auBookauFeatures = {
    new FeatureEntry("au_feature_urls/au_chapter", OpenUrlInfo.ResolvedTo.CHAPTER),
    new FeatureEntry("au_chapter_url", OpenUrlInfo.ResolvedTo.CHAPTER),
    new FeatureEntry("au_feature_urls/au_volume", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_volume_url", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_start_url", OpenUrlInfo.ResolvedTo.VOLUME),
    new FeatureEntry("au_feature_urls/au_title", OpenUrlInfo.ResolvedTo.TITLE),
    new FeatureEntry("au_title_url", OpenUrlInfo.ResolvedTo.TITLE),
  };
  
  /** The name of the TDB au_feature key selector */
  static final String AU_FEATURE_KEY = "au_feature_key";
  
  // pre-defined OpenUrlInfo for no url
  public static final OpenUrlInfo noOpenUrlInfo = 
      new OpenUrlInfo(null, OpenUrlInfo.ResolvedTo.NONE);

  /**
   * Information returned by OpenUrlResolver includes the resolvedUrl
   * and the resolvedTo enumeration.
   */
  public static class OpenUrlInfo {
    static public enum ResolvedTo {
      PUBLISHER,
      TITLE,
      VOLUME,
      ISSUE,
      CHAPTER,
      ARTICLE,
      OTHER,
      NONE,
    };
    
    public final String resolvedUrl;
    public final ResolvedTo resolvedTo;
    
    private OpenUrlInfo(String resolvedUrl, ResolvedTo resolvedTo) {
      this.resolvedUrl = resolvedUrl;
      this.resolvedTo = resolvedTo;
    }

    public static OpenUrlInfo newInstance(
        String resolvedUrl, ResolvedTo resolvedTo) {
      return ((resolvedTo == ResolvedTo.NONE) || (resolvedUrl == null)) 
          ? OpenUrlResolver.noOpenUrlInfo
          : new OpenUrlInfo(resolvedUrl, resolvedTo);
    }
    public static OpenUrlInfo newInstance(String resolvedUrl) {
      return (resolvedUrl == null) 
          ? noOpenUrlInfo 
          : new OpenUrlInfo(resolvedUrl, OpenUrlInfo.ResolvedTo.OTHER);
    }
  }

  /**
   * Create a resolver for the specified metadata manager.
   * 
   * @param daemon the LOCKSS daemon
   */
  public OpenUrlResolver(LockssDaemon daemon) {
    if (daemon == null) {
      throw new IllegalArgumentException("LOCKSS daemon not specified");
    }
    this.daemon = daemon;
  }
  
  /**
   * Get an parameter either without or with the "rft." prefix.
   * 
   * @param params the parameters
   * @param key the key
   * @return the value or <code>null</code> if not present
   */
  private String getRftParam(Map<String,String> params, String key) {
    String value = params.get(key);
    if (value == null) {
      value = params.get("rft." + key);
    }
    return value;
  }
  
  /**
   * Get date based on date, ssn (season), and quarter rft parameters.
   * 
   * @param params the parameters
   * @return a normalized date string of the form YYYY{-MM{-DD}}
   *   or YYYY-Qn for nth quarter, or YYYY-Sn for nth season for
   *   n between 1 and 4.
   */
  private String getRftDate(Map<String,String> params) {
    String ssn = getRftParam(params, "ssn"); // spring, summer, fall, winter
    String quarter = getRftParam(params, "quarter");  // 1, 2, 3, 4
    String date = getRftParam(params, "date"); // YYYY{-MM{-DD}}
    
    // fill in month if only year specified
    if ((date != null) && (date.indexOf('-') < 0)) {
      if (quarter != null) {
        // fill in month based on quarter
        if (quarter.equals("1")) {
          date += "-Q1";
        } else if (quarter.equals("2")) {
          date += "-Q2";
        } else if (quarter.equals("3")) {
          date += "-Q3";
        } else if (quarter.equals("4")) {
          date += "-Q4";
        } else {
          log.warning("Invalid quarter: " + quarter);
        }
      } else if (ssn != null) {
        // fill in month based on season
        if (ssn.equalsIgnoreCase("spring")) {
          date += "-S1";
        } else if (ssn.equalsIgnoreCase("summer")) {
          date += "-S2";
        } else if (ssn.equalsIgnoreCase("fall")) {
          date += "-S3";
        } else if (ssn.equalsIgnoreCase("winter")) {
          date += "-S4";
        }
        log.warning("Invalid ssn: " + ssn);
      }
    }
    return date;
  }
  
  /**
   * Get start page base rft spage parameter or artnum if page not specified.
   * 
   * @param params the parameters
   * @return a start page -- not necessarily numeric
   */
  private String getRftStartPage(Map<String,String> params) {
    String spage = getRftParam(params, "spage");
    if (spage == null) {
      spage = getRftParam(params, "artnum");
    }
    return spage;
  }
  
  /**
   * Returns the TdbTitle corresponding to the specified OpenUrl params.
   * 
   * @param params the OpenURL parameters
   * @return a TdbTitle or <code>null</code> if not found
   */
  public TdbTitle resolveTdbTitleForOpenUrl(Map<String,String> params) {
    Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
    if (tdb != null) {
      // get TdbTitle for ISBN
      String isbn = getRftParam(params, "isbn");
      if (isbn != null) {
        Collection<TdbAu> tdbAus = tdb.getTdbAusByIsbn(isbn);
        return tdbAus.isEmpty() ? null : tdbAus.iterator().next().getTdbTitle();
      }
      
      // get TdbTitle for ISSN
      String issn = getRftParam(params, "issn");
      if (issn != null) {
        return tdb.getTdbTitleByIssn(issn);
      }
      
      
      // get TdbTitle for BICI
      String bici = getRftParam(params, "bici");
      if (bici != null) {
        int i = bici.indexOf('(');
        if (i > 0) {
          isbn = bici.substring(0,i);
          Collection<TdbAu> tdbAus = tdb.getTdbAusByIsbn(isbn);
          return tdbAus.isEmpty() ? null : tdbAus.iterator().next().getTdbTitle();
        }
      }

      // get TdbTitle for SICI
      String sici = getRftParam(params, "sici");
      if (sici != null) {
        int i = sici.indexOf('(');
        if (i > 0) {
          issn = sici.substring(0,i);
          return tdb.getTdbTitleByIssn(issn);
        }
      }

      // get TdbTitle for journal pubisher and title
      String publisher = getRftParam(params, "publisher");
      String title = getRftParam(params, "jtitle");
      if (title == null) {
        title = getRftParam(params, "title");
      }
      if (title != null) {
        Collection<TdbTitle> tdbTitles;
        if (publisher != null) {
          TdbPublisher tdbPublisher = tdb.getTdbPublisher(publisher);
          tdbTitles = (tdbPublisher == null) 
              ? Collections.<TdbTitle>emptyList() 
              :tdbPublisher.getTdbTitlesLikeName(title);
        } else {
          tdbTitles = tdb.getTdbTitlesByName(title);
        }
        return tdbTitles.isEmpty() ? null : tdbTitles.iterator().next();
      }
      
      // get TdbTitle for book pubisher and title
      String btitle = getRftParam(params, "btitle");
      if (btitle != null) {
        Collection<TdbAu> tdbAus;
        if (publisher != null) {
          TdbPublisher tdbPublisher = tdb.getTdbPublisher(publisher);
          tdbAus = (tdbPublisher == null) 
              ? Collections.<TdbAu>emptyList() 
              :tdbPublisher.getTdbAusLikeName(title);
        } else {
          tdbAus = tdb.getTdbAusByName(title);
        }
        return tdbAus.isEmpty() ? null : tdbAus.iterator().next().getTdbTitle();
      }
    }

    return null;
  }

  /**
   * Resolve an OpenURL from a set of parameter keys and values.
   * 
   * @param params the OpenURL parameters
   * @return a url or <code>null</code> if not found
   */
  public OpenUrlInfo resolveOpenUrl(Map<String,String> params) {
    if (params.containsKey("rft_id")) {
      String rft_id = params.get("rft_id");
      // handle rft_id that is an HTTP or HTTPS URL
      if (UrlUtil.isHttpUrl(rft_id)) {
        OpenUrlInfo resolved = resolveFromUrl(rft_id);
        if (resolved.resolvedUrl != null) {
          return resolved;
        }
        log.debug3("Failed to resolve from URL: " + rft_id);
      } else if (rft_id.startsWith("info:doi/")) {
        String doi = rft_id.substring("info:doi/".length());
        OpenUrlInfo resolved = resolveFromDOI(doi); 
        if (resolved.resolvedUrl != null) {
          return resolved;
        }
        log.debug3("Failed to resolve from DOI: " + doi);
      }
    }
    
    if (params.containsKey("id")) {
      // handle OpenURL 0.1 DOI specification
      String id = params.get("id");
      if (id.startsWith("doi:")) {
        String doi = id.substring("doi:".length());
        OpenUrlInfo resolved = resolveFromDOI(doi);
        if (resolved.resolvedUrl != null) {
          return resolved;
        }
        log.debug3("Failed to resolve from DOI: " + doi);
      }
    }

    String spage = getRftStartPage(params);
    String author = getRftParam(params, "au");
    String atitle = getRftParam(params, "atitle");
    String isbn = getRftParam(params, "isbn");
    String edition = getRftParam(params, "edition");
    String date = getRftDate(params);
    String volume = getRftParam(params, "volume");

    if (isbn != null) {
      // process a book or monographic series based on ISBN
      OpenUrlInfo resolved = resolveFromIsbn(
    	  isbn, date, volume, edition, spage, author, atitle);
      if (resolved.resolvedUrl != null) {
        log.debug3(
            "Located url " + resolved.resolvedUrl + " for book ISBN " + isbn); 
        return resolved;
      }
      log.debug3("Failed to resolve from ISBN: " + isbn);
    }
    
    String eissn = getRftParam(params, "eissn");
    String issn = getRftParam(params, "issn");
    String issue = getRftParam(params, "issue");
    
    // process a journal based on EISSN or ISSN
    String anyIssn = (eissn != null) ? eissn : issn;
    if (anyIssn != null) {

      // resolve from its eISSN
      OpenUrlInfo resolved = 
        resolveFromIssn(anyIssn, date, volume, issue, spage, author, atitle);
      if (resolved.resolvedUrl != null) {
        if (log.isDebug3()) {
          String title = getRftParam(params, "jtitle");
          if (title == null) {
            title = getRftParam(params, "title");
          }
          log.debug3("Located url " + resolved.resolvedUrl +
                    " for article \"" + atitle + "\"" +
                    ", ISSN " + anyIssn +
                    ", title \"" + title + "\"");
        }
        return resolved;
      }
      log.debug3("Failed to resolve from ISSN: " + anyIssn);
    }
    
    
    String bici = params.get("rft.bici");
    if (bici != null) {
      // get cached URL from book book ICI code
      OpenUrlInfo resolved = null;
      try {
        resolved = resolveFromBici(bici);
        if (resolved.resolvedUrl != null) {
          log.debug3(
              "Located url " + resolved.resolvedUrl + "for bici " + bici);
          return resolved;
        }
      } catch (ParseException ex) {
        log.warning(ex.getMessage());
      }
      log.debug3("Failed to resolve from BICI: " + bici);
    }

    String sici = params.get("rft.sici");
    // get cached URL from serial ICI code
    if (sici != null) {
      OpenUrlInfo resolved = null;
      try {
        resolved = resolveFromSici(sici);
        if (resolved.resolvedUrl != null) {
          log.debug3(
              "Located url " + resolved.resolvedUrl + "for sici " + sici);
          return resolved;
        }
      } catch (ParseException ex) {
        log.warning(ex.getMessage());
      }
      log.debug3("Failed to resolve from SICI: " + sici);
    }

    // process a journal or book based on its title
    String title = getRftParam(params, "title");
    boolean isbook = false;
    boolean isjournal = false;
    if (title == null) {
      title = params.get("rft.btitle");
      isbook = title != null;
    }
    if (title == null) {
      title = params.get("rft.jtitle");
      isjournal = title != null;
    }
    if (title != null) {
      Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
      if (tdb != null) {
        Collection<TdbTitle> titles = Collections.emptySet();
        String pub = getRftParam(params, "pub");
        // only search the named publisher
        TdbPublisher tdbPub = (pub == null) ? null : tdb.getTdbPublisher(pub);
        
        if (isbook) {
          // search as though it is a book title
          Collection<TdbAu> tdbAus;
          if (tdbPub != null) {
            tdbAus = tdbPub.getTdbAusLikeName(title);
          } else {
            tdbAus = tdb.getTdbAusLikeName(title);
          }
          
          Collection<TdbAu> noTdbAus = new ArrayList<TdbAu>();
          for (TdbAu tdbAu : tdbAus) {
            // search for journal through its ISBN
            String id = tdbAu.getIsbn();
            if (id != null) {
              // try resolving from ISBN
              OpenUrlInfo resolved = 
                resolveFromIsbn(id, date, volume, issue, spage, author, atitle);
              if (resolved.resolvedUrl != null) {
                if (log.isDebug3()) {
                  log.debug3("Located url " + resolved.resolvedUrl +
                             " for article \"" + atitle + "\"" +
                             ", ISBN " + id +
                             ", title \"" + title + "\"" +
                             ", publisher \""  + 
                             tdbAu.getTdbPublisher().getName() + "\"");
                }
                return resolved;
              }
            } else {
              // add to list of titles with no ISBN
              noTdbAus.add(tdbAu);
            }
          }
        
          // search matching titles without ISBNs
          OpenUrlInfo resolved = 
            resolveBookFromTdbAus(noTdbAus, date, volume, edition, spage);
          if (resolved != null) {
            if (log.isDebug3()) {
              log.debug3(  "Located url " + resolved.resolvedUrl 
                         + ", title \"" + title + "\"");
            }
            return resolved;
          }       
        } else {
          // search as though it is a journal title
          Collection<TdbTitle> tdbTitles;
          if (tdbPub != null) {
            tdbTitles = tdbPub.getTdbTitlesLikeName(title);
          } else {
            tdbTitles = tdb.getTdbTitlesLikeName(title);
          }
          
          Collection<TdbTitle> noTdbTitles = new ArrayList<TdbTitle>();
          for (TdbTitle tdbTitle : tdbTitles) {
            // search for journal through its ISSN
            String id = tdbTitle.getIssn();
            if (id != null) {
              // try resolving from ISSN
              OpenUrlInfo resolved = 
                resolveFromIssn(id, date, volume, issue, spage, author, atitle);
              if (resolved.resolvedUrl != null) {
                if (log.isDebug3()) {
                  log.debug3("Located url " + resolved.resolvedUrl +
                             " for article \"" + atitle + "\"" +
                             ", ISSN " + id +
                             ", title \"" + title + "\"" +
                             ", publisher \""  + 
                             tdbTitle.getTdbPublisher().getName() + "\"");
                }
                return resolved;
              }
            } else {
              // add to list of titles with no ISBN or ISSN
              noTdbTitles.add(tdbTitle);
            }
          }

          // search matching titles without ISSNs
          for (TdbTitle noTdbTitle : noTdbTitles) {
            Collection<TdbAu> tdbAus =  noTdbTitle.getTdbAus();
            OpenUrlInfo resolved = 
              resolveJournalFromTdbAus(tdbAus,date,volume,issue, spage);
            if (resolved.resolvedUrl != null) {
              if (log.isDebug3()) {
                log.debug3(  "Located url " + resolved.resolvedUrl 
                           + ", title \"" + title + "\"");
              }
              return resolved;
            }            
          }
        }
      }
      log.debug3("Failed to resolve from title: \"" + title + "\"");
    }

    return noOpenUrlInfo;
  }

  /**
   * Resolve serials article based on the SICI descriptor. For an article 
   * "Who are These Independent Information Brokers?", Bulletin of the 
   * American Society for Information Science, Feb-Mar 1995, Vol. 21, no 3, 
   * page 12, the SICI would be: 0095-4403(199502/03)21:3<12:WATIIB>2.0.TX;2-J
   * 
   * @param sici a string representing the serials article SICI
   * @return the article url or <code>null</code> if not resolved
   * @throws ParseException if error parsing SICI
   */
  public OpenUrlInfo resolveFromSici(String sici) throws ParseException {
    int i = sici.indexOf('(');
    if (i < 0) {
      // did not find end of date section
      throw new ParseException("Missing start of date section", 0);
    }

    // validate ISSN after normalizing to remove punctuation
    String issn = sici.substring(0,i).replaceFirst("-", "");
    if (!MetadataUtil.isIssn(issn)) {
      // ISSN is 8 characters
      throw new ParseException("Malformed ISSN", 0);
    }
    
    // skip over date section (199502/03)
    int j = sici.indexOf(')',i+1);
    if (j < 0) {
      // did not find end of date section
      throw new ParseException("Missing end of date section", i+1);
    }

    // get volume and issue between end of
    // date section and start of article section
    i = j+1;   // advance to start of volume
    j = sici.indexOf('<',i);
    if (j < 0) {
      // did not find start of issue section
      throw new ParseException("Missing start of issue section", i);
    }
    // get volume delimiter
    int k = sici.indexOf(':', i);
    if ((k < 0) || (k >= j)) {
      // no volume delimiter before start of issue section 
      throw new ParseException("Missing volume delimiter", i);
    }
    String volume = sici.substring(i,k);
    String issue = sici.substring(k+1,j);
    
    // get end of issue section
    i = j+1;
    k = sici.indexOf('>', i+1);
    if (k < 0) {
      // did not find end of issue section
      throw new ParseException("Missing end of issue section", i+1);
    }
    j = sici.indexOf(':',i+1);
    if ((j < 0) || (j >= k)) {
      throw new ParseException("Missing page delimiter", i+1);
    }
    String spage = sici.substring(i,j);
    
    // get the cached URL from the parsed paramaters
    OpenUrlInfo resolved = 
        resolveFromIssn(issn, null, volume, issue, spage, null, null);
    if ((resolved.resolvedUrl != null) && log.isDebug()) {
      // report on the found article
      Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
      String jTitle = null;
      if (tdb != null) {
        TdbTitle title = tdb.getTdbTitleByIssn(issn);
        if (title != null) {
          jTitle = title.getName();
        }
      }
      if (log.isDebug3())  {
        String s = "Located cachedURL " + resolved.resolvedUrl
                   + " for ISSN " + issn
                   + ", volume: " + volume
                   + ", issue: " + issue 
                   + ", start page: " + spage;
        if (jTitle != null) {
          s += ", journal title \"" + jTitle + "\"";
        }
        log.debug3(s);
      }
    }
    
    return noOpenUrlInfo;
  }

  /**
   * Resolve a book chapter based on the BICI descriptor. For an item "English 
   * as a World Language", Chapter 10, in "The English Language: A Historical 
   * Introduction", 1993, pp. 234-261, ISBN 0-521-41620-5, the BICI would be 
   * 0521416205(1993)(10;EAAWL;234-261)2.2.TX;1-1
   * 
   * @param bici a string representing the book chapter BICI
   * @return the article url or <code>null</code> if not resolved
   * @throws ParseException if error parsing BICI
   */
  public OpenUrlInfo resolveFromBici(String bici) throws ParseException {
    int i = bici.indexOf('(');
    if (i < 0) {
      // did not find end of date section
      throw new ParseException("Missing start of date section", 0);
    }
    String isbn = bici.substring(0,i).replaceAll("-", "");

    // match ISBN-10 or ISBN-13 with 0-9 or X checksum character
    if (!MetadataUtil.isIsbn(isbn, false)) {
      // ISSB is 10 or 13 characters
      throw new ParseException("Malformed ISBN", 0);
    }

    // skip over date section (1993)
    int j = bici.indexOf(')',i+1);
    if (j < 0) {
      // did not find end of date section
      throw new ParseException("Missing end of date section", i+5);
    }
    String date = bici.substring(i+1, j);

    // get volume and issue between end of
    // date section and start of article section
    if (bici.charAt(j+1) != '(') {
      // did not find start of chapter section
      throw new ParseException("Missing start of chapter section", j+1);
    }
    
    i = j+2;   // advance to start of chapter
    j = bici.indexOf(')',i);
    if (j < 0) {
      // did not find end of chapter section
      throw new ParseException("Missing end of chapter section", i);
    }
    
    // get chapter number delimiter
    int k = bici.indexOf(';', i);
    if ((k < 0) || (k >= j)) {
      // no chapter number delimiter before end of chapter section 
      throw new ParseException("Missing chapter number delimiter", i);
    }
    String chapter = bici.substring(i,k);
    
    // get end of chapter section
    i = k+1;
    k = bici.indexOf(';', i+1);
    if ((k < 0) || (k >= j)) {
      // no chapter abbreviation delimiter before end of chapter section
      throw new ParseException("Missing chapter abbreviation delimiter", i);
    }
    
    // extract the start page
    String spage = bici.substring(k+1,j);
    if (spage.indexOf('-') > 0) {
      spage = spage.substring(0, spage.indexOf('-'));
    }
    
    // PJG: what about chapter number?
    // (isbn, date, volume, edition, spage, author, title) 
    OpenUrlInfo resolved = 
        resolveFromIsbn(isbn, date, null, null, spage, null, null);
    if ((resolved.resolvedUrl != null) && log.isDebug()) {
      Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
      String bTitle = null;
      if (tdb != null) {
        Collection<TdbAu> tdbAus = tdb.getTdbAusByIsbn(isbn);
        if (!tdbAus.isEmpty()) {
          bTitle = tdbAus.iterator().next().getJournalTitle();
        }
      }
      if (log.isDebug3())  {
        String s = "Located cachedURL " + resolved.resolvedUrl +
        " for ISBN " + isbn +
        ", year: " + date + 
        ", chapter: " + chapter +
        ", start page: " + spage;
        if (bTitle != null) {
          s += ", book title \"" + bTitle + "\"";
        }
        log.debug3(s);
      }
    }
    
    return noOpenUrlInfo;
    
  }

  /**
   * Resolves from a url.
   *  
   * @param aUrl the URL
   * @return a resolved URL
   */
  public OpenUrlInfo resolveFromUrl(String aUrl) {
    String url = resolveUrl(aUrl);
    return OpenUrlInfo.newInstance(url);
  }
  
  /**
   * Validates a URL and resolve it by following indirects, and stopping
   * early if a URL that is in the LOCKSS cache is found.
   *  
   * @param aUrl the URL
   * @return a resolved URL
   */
  public String resolveUrl(String aUrl) {
    String url = aUrl;
    try {
      final PluginManager pluginMgr = daemon.getPluginManager();
      final LockssUrlConnectionPool connectionPool =
        daemon.getProxyManager().getQuickConnectionPool();
      for (int i = 0; i < MAX_REDIRECTS; i++) {
        // no need to look further if content already cached
        if (pluginMgr.findCachedUrl(url,true) != null) {
          return url;
        }
        
        // test URL by opening connection
        LockssUrlConnection conn = null;
        try {
          conn = UrlUtil.openConnection(url, connectionPool);
          conn.setFollowRedirects(false);
          conn.setRequestProperty("user-agent", LockssDaemon.getUserAgent());
          conn.execute();
          
          // if not redirected, validate based on response code
          String url2 = conn.getResponseHeaderValue("Location");
          if (url2 == null) {
            int response = conn.getResponseCode();
            log.debug3(i + " response code: " + response);
            if (response == HttpURLConnection.HTTP_OK) {
              return url;
            }
            return null;
          }
          
          // resolve redirected URL and try again
          url = UrlUtil.resolveUri(url, url2);
          log.debug3(i + " resolved to: " + url);
        } finally {
          IOUtil.safeRelease(conn);
        }
      }
    } catch (IOException ex) {
      log.error("resolving from URL:" + aUrl + " with URL: " + url, ex);
    }
    return null;
  }
  
  /**
   * Return the article URL from a DOI, using either the MDB or TDB.
   * @param doi the DOI
   * @return the article url
   */
  public OpenUrlInfo resolveFromDOI(String doi) {
    if (!MetadataUtil.isDoi(doi)) {
      return null;
    }
    OpenUrlInfo resolved = noOpenUrlInfo;
    try {
      // resolve from metadata manager
      MetadataManager metadataMgr = daemon.getMetadataManager();
      resolved = resolveFromDoi(metadataMgr, doi);
    } catch (IllegalArgumentException ex) {
    }
    
    if (resolved.resolvedUrl == null) {
      // use DOI International resolver for DOI
      resolved = resolveFromUrl("http://dx.doi.org/" + doi);
    }
    return resolved;
  }    

  /**
   * Return the article URL from a DOI using the MDB.
   * @param metadataMgr the metadata manager
   * @param doi the DOI
   * @return the OpenUrlInfo
   */
  private OpenUrlInfo resolveFromDoi(MetadataManager metadataMgr, String doi) {
    String url = null;
    Connection conn = null;
    try {
      conn = metadataMgr.newConnection();

      String MTN = MetadataManager.METADATA_TABLE;
      String DTN = MetadataManager.DOI_TABLE;
      String MDID = MetadataManager.MD_ID_FIELD;
      String query =           
        "select " + MetadataManager.ACCESS_URL_FIELD 
      + " from " + MTN + "," + DTN 
      + " where " + DTN + "." + MDID + " = " + MTN + "." + MDID
      + " and upper(" + MetadataManager.DOI_FIELD + ") = ?";
      PreparedStatement stmt = conn.prepareStatement(query);
      stmt.setString(1, doi.toUpperCase());
      ResultSet resultSet = stmt.executeQuery();
      if (resultSet.next()) {
        url = resultSet.getString(1);
      }
    } catch (SQLException ex) {
      log.error("Getting DOI:" + doi, ex);
      
    } finally {
      MetadataManager.safeClose(conn);
    }
    return new OpenUrlInfo(url, OpenUrlInfo.ResolvedTo.ARTICLE);
  }

  /**
   * Return article URL from an ISSN, date, volume, issue, spage, and author. 
   * The first author will only be used when the starting page is not given.
   * 
   * @param issn the issn
   * @param date the publication date
   * @param volume the volume
   * @param issue the issue
   * @param spage the starting page
   * @param author the first author's full name
   * @param atitle the article title 
   * @return the article URL
   */
  public OpenUrlInfo resolveFromIssn(
    String issn, String date, String volume, String issue, 
    String spage, String author, String atitle) {
    OpenUrlInfo resolved = noOpenUrlInfo;

    Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
    TdbTitle title = (tdb == null) ? null : tdb.getTdbTitleByIssn(issn);
    
    // only go to metadata manager if requesting individual article
    try {
      // resolve article from metadata manager
      String[] issns = (title == null) ? 
        new String[] { issn } : title.getIssns();
      MetadataManager metadataMgr = daemon.getMetadataManager();
      resolved = resolveFromIssn(metadataMgr, issns, date, 
                                volume, issue, spage, author, atitle);
    } catch (IllegalArgumentException ex) {
      // intentionally ignore input error
    }
    if (resolved.resolvedUrl == null) {
      // resolve title, volume, AU, or issue TOC from TDB
      if (title == null) {
        log.debug3("No TdbTitle for issn " + issn);
      } else {
    	resolved = resolveJournalFromTdbAus(
    	    title.getTdbAus(), date, volume, issue, spage);
      }
    }
    return resolved;
  }
  
  /**
   * Return article URL from an ISSN, date, volume, issue, spage, and author. 
   * The first author will only be used when the starting page is not given.
   * 
   * @param metadataMgr the metadata manager
   * @param issns a list of alternate ISSNs for the title
   * @param date the publication date
   * @param volume the volume
   * @param issue the issue
   * @param spage the starting page
   * @param author the first author's full name
   * @param atitle the article title 
   * @return the article URL
   */
  private OpenUrlInfo resolveFromIssn(
      MetadataManager metadataMgr,
      String[] issns, String date, String volume, String issue, 
      String spage, String author, String atitle) {
          
    Connection conn = null;
    OpenUrlInfo resolved = noOpenUrlInfo;
    try {
      conn = metadataMgr.newConnection();
      StringBuilder query = new StringBuilder();
      ArrayList<String> args = new ArrayList<String>();
      buildAuxiliaryTableQuery(
        MetadataManager.ISSN_TABLE, MetadataManager.ISSN_FIELD, 
        issns, query, args);
      
      // true if properties specify an article
      boolean hasArticleSpec = 
          (spage != null) || (author != null) || (atitle != null);

      // true if properties specified a journal item
      boolean hasJournalSpec =
          (date != null) || (volume != null) || (issue != null);

      if (hasJournalSpec) {
        // can specify an issue by a combination of date, volume and issue;
        // how these combine varies, so do the most liberal match possible
        // and filter based on multiple results
        query.append(" and ");
        if (date != null) {
          // enables query "2009" to match "2009-05-10" in database
          query.append(MetadataManager.DATE_FIELD);
          query.append(" like ? escape '\\'");
          args.add(date.replace("\\","\\\\").replace("%","\\%") + "%");
        }
        
        if (volume != null) {
          if (date != null) {
            query.append(" and ");
          }
          query.append(MetadataManager.VOLUME_FIELD);
          query.append(" = ?");
          args.add(volume);
        }

        if (issue != null) {
          if ((date != null) || (volume != null)) {
        		query.append(" and ");
          }
          query.append(MetadataManager.ISSUE_FIELD);
          query.append(" = ?");
          args.add(issue);
        }
      }
                  
      // handle start page, author, and article title as
      // equivalent ways to specify an article within an issue
      if (hasArticleSpec) {
        // accept any of the three
        query.append(" and ( ");
      
        if (spage != null) {
          query.append(MetadataManager.START_PAGE_FIELD);
          query.append(" = ?");
          args.add(spage);
        }
        if (atitle != null) {
          if (spage != null) {
            query.append(" or ");
          }
          query.append("upper(");
          query.append(MetadataManager.ARTICLE_TITLE_FIELD);
          query.append(") like ? escape '\\'");
          args.add(atitle.toUpperCase().replace("%","\\%") + "%");
        }
        if ( author != null) {
          if ((spage != null) || (atitle != null)) {
            query.append(" or ");
          }

          // add the author query to the query
          addAuthorQuery(author, query, args);
        }
        query.append(" )");
      }
      
      String url = resolveFromQuery(conn, query.toString(), args);
      return OpenUrlInfo.newInstance(url, OpenUrlInfo.ResolvedTo.ARTICLE);

    } catch (SQLException ex) {
      log.error("Getting ISSNs:" + Arrays.toString(issns), ex);
        
    } finally {
      MetadataManager.safeClose(conn);
    }
    return resolved;
  }

  /** 
   * Resolve query if a single URL matches.
   * 
   * @param conn the connection
   * @param query the query
   * @param args the args
   * @return a single URL
   * @throws SQLException
   */
  private String resolveFromQuery(
	  Connection conn, String query, List<String> args) throws SQLException {
	
    log.debug3("query: " + query);
    PreparedStatement stmt = conn.prepareStatement(query.toString());
    for (int i = 0; i < args.size(); i++) {
      log.debug3("  query arg:  " + args.get(i));      
      stmt.setString(i+1, args.get(i));
    }
    stmt.setMaxRows(2);  // only need 2 to to determine if unique
    ResultSet resultSet = stmt.executeQuery();
    String url = null;
    if (resultSet.next()) {
      url = resultSet.getString(1);
      if (resultSet.next()) {
        log.debug3("entry not unique: " + url + " " + resultSet.getString(1));
        url = null;
      }
    }
    return url;
  }

  /**
   * Return article URL from a TdbTitle, date, volume, and issue. 
   * 
   * @param title the TdbTitle
   * @param date the publication date
   * @param volume the volume
   * @param issue the issue
   * @param spage the start page or article number
   * @return the article URL
   */
  private OpenUrlInfo resolveJournalFromTdbAus(
    Collection<TdbAu> tdbAus, 
    String date, String volume, String issue, String spage) {
    
    // get the year from the date
    String year = null;
    if (date != null) {
      int y = new PublicationDate(date).getYear();
      year = (y == 0) ? null : Integer.toString(y);
    }

    // list of AUs that match volume and year specified
    ArrayList<TdbAu> foundTdbAuList = new ArrayList<TdbAu>();
    
    // list of AUs that do not match volume and year specified
    ArrayList<TdbAu> notFoundTdbAuList = new ArrayList<TdbAu>();
    
    // find a TdbAu that matches the date, and volume
    for (TdbAu tdbAu : tdbAus) {

      // if neither year or volume specified, pick any TdbAu
      if ((volume == null) && (year == null)) {
        notFoundTdbAuList.add(tdbAu);
        continue;
      }
      
      // if volume specified, see if this TdbAu matches
      if (volume != null) {
        if (!tdbAu.includesVolume(volume)) {
          notFoundTdbAuList.add(tdbAu);
          continue;
        }
      }

      // if year specified, see if this TdbAu matches
      if (year != null) {
        if (!tdbAu.includesYear(year)) {
          notFoundTdbAuList.add(tdbAu);
          continue;
        }
      }
      
      foundTdbAuList.add(tdbAu);
    }

    final PluginManager pluginMgr = daemon.getPluginManager();
    OpenUrlInfo resolved = noOpenUrlInfo;
    if (!foundTdbAuList.isEmpty()) {
      // look for URL that is cached from list of matching AUs
      for (TdbAu tdbau : foundTdbAuList) {
        String aYear = year;
        if (aYear == null) {
          aYear = tdbau.getStartYear();
        }
        String aVolume = volume;
        if (aVolume == null) {
          aVolume = tdbau.getStartVolume();
        }
        String anIssue = issue;
        if (anIssue == null) {
          anIssue  = tdbau.getStartIssue();
        }
        OpenUrlInfo aResolved = getJournalUrl(tdbau, aYear, aVolume, anIssue, spage);
        if (aResolved.resolvedUrl != null) {
          // found the URL if in cache
          if  (pluginMgr.findCachedUrl(aResolved.resolvedUrl, true) != null) {
            resolved = aResolved;
            return resolved;
          }
          // not a viable URL if the AU is down
          // note: even though getJournalUrl() checks that page exists,
          // we can't rely on it being usable if TdbAu is down
          if (!tdbau.isDown()) {
            resolved = aResolved;
            return resolved;
          }
          log.debug2(  "discarding URL " + aResolved.resolvedUrl 
                     + " because tdbau is down: " + tdbau.getName());
        }
      }
    }
    // look for URL that is cached from list of non-matching AUs
    for (TdbAu tdbau : notFoundTdbAuList) {
      OpenUrlInfo aResolved = 
          getJournalUrl(tdbau, year, volume, issue, spage);
      if (aResolved != null) {
        // found the URL if in cache
        if  (pluginMgr.findCachedUrl(aResolved.resolvedUrl, true) != null) {
          resolved = aResolved;
          return resolved;
        }
        // not a viable URL if the AU is down
        // note: even though getJournalUrl() checks that page exists,
        // we can't rely on it being usable if TdbAu is down
        if (!tdbau.isDown()) {
          resolved = aResolved;
          return resolved;
        }
        log.debug2(  "discarding URL " + aResolved.resolvedUrl 
                   + " because tdbau is down: " + tdbau.getName());
      }
    }

    return resolved;
  }
  
  /**
   * Return the type entry parameter map for the specified Plugin and TdbAu.
   * @param plugin the plugin
   * @param tdbau the AU
   * @return the parameter map
   */
  private static TypedEntryMap getParamMap(Plugin plugin, TdbAu tdbau) {
    TypedEntryMap paramMap = new TypedEntryMap();
    List<ConfigParamDescr> descrs = plugin.getAuConfigDescrs();
    for (ConfigParamDescr descr : descrs) {
      String key = descr.getKey();
      String sval = tdbau.getParam(key);
      if (sval == null) {
        sval = tdbau.getPropertyByName(key);
        if (sval == null) {
          sval = tdbau.getAttr(key);
        }
      }
      if (sval != null) {
        try {
          Object val = descr.getValueOfType(sval);
          paramMap.setMapElement(key, val);
        } catch (InvalidFormatException ex) {
          log.warning("invalid value for key: " + key + " value: " + sval, ex);
        }
      }
    }
    
    // add entries for attributes that do not correspond to AU params
    for (Map.Entry<String,String> entry : tdbau.getAttrs().entrySet()) {
      if (!paramMap.containsKey(entry.getKey())) {
        paramMap.setMapElement(entry.getKey(), entry.getValue());
      }
    }
    
	return paramMap;
  }
  
  /**
   * Return the type entry parameter map for the specified AU.
   * @param au the AU
   * @return the parameter map
   */
 /* for later use (pjg)
  private static TypedEntryMap getParamMap(ArchivalUnit au) {
    TypedEntryMap paramMap = new TypedEntryMap();

    Configuration config = au.getConfiguration();
    Plugin plugin = au.getPlugin();
    for (ConfigParamDescr descr : plugin.getAuConfigDescrs()) {
      String key = descr.getKey();
      if (config.containsKey(key)) {
        try {
          Object val = descr.getValueOfType(config.get(key));
          paramMap.setMapElement(key, val);
        } catch (Exception ex) {
          log.error("Error configuring: " + key + " "  + ex.getMessage());
        }
      }
    }
    return paramMap;
  }
*/
  
  /**
   * Gets the book URL for an AU indicated by the DefinablePlugin 
   * and parameter definitions specified by the TdbAu.
   * 
   * @param plugin the DefinablePlugin
   * @param tdbau the TdbAu
   * @param year the year
   * @param volumeName the volume name
   * @param issue the issue
   * @return the issue URL
   */
/* for later use (pjg)  
  private static String getBooklUrl(
	  ArchivalUnit au, String volumeName, String year, String edition) {
	TypedEntryMap paramMap = getParamMap(au);
	Plugin plugin = au.getPlugin();
	String url = getBookUrl(plugin, paramMap, volumeName, year, edition);
    return url;
  }
*/

  /**
   * Gets the book URL for a TdbAU indicated by the DefinablePlugin 
   * and parameter definitions specified by the TdbAu.
   * @param tdbau the TdbAu
   * @param year the year
   * @param volumeName the volume name
   * @param edition the edition
   * @param spage the start page
   * @return the starting URL
   */
  private OpenUrlInfo getBookUrl(
	  TdbAu tdbau, String year, String volumeName, String edition, String spage) {
    PluginManager pluginMgr = daemon.getPluginManager();
    String pluginKey = PluginManager.pluginKeyFromId(tdbau.getPluginId());
    Plugin plugin = pluginMgr.getPlugin(pluginKey);

    OpenUrlInfo resolved = null;
    if (plugin != null) {
      log.debug3(  "getting issue url for plugin: " 
                 + plugin.getClass().getName());
      // get starting URL from a DefinablePlugin
  	  TypedEntryMap paramMap = getParamMap(plugin, tdbau);
      
  	  // add volume with type and spelling of existing element
  	  paramMap.setMapElement("volume", volumeName);
  	  paramMap.setMapElement("volume_str",volumeName);
  	  paramMap.setMapElement("volume_name", volumeName);
      paramMap.setMapElement("year", year);
      if (!StringUtil.isNullString(year)) {
        try {
          paramMap.setMapElement("au_short_year",
              String.format("%02d", NumberUtil.parseInt(year)%100));
        } catch (NumberFormatException ex) {
          log.info(  "Error parsing year '" + year 
                   + "' as an int -- not setting au_short_year");
        }
      }
  	  paramMap.setMapElement("edition", edition);
      paramMap.setMapElement("chapter", spage);
  	  // auFeatureKey selects feature from a map of values
  	  // for the same feature (e.g. au_feature_urls/au_year)
      paramMap.setMapElement("auFeatureKey", tdbau.getAttr(AU_FEATURE_KEY));
      String isbn = tdbau.getAttr("isbn");
      if (isbn != null) {
        paramMap.setMapElement("isbn", isbn);
      }
      String eisbn = tdbau.getAttr("eisbn");
      if (eisbn != null) {
        paramMap.setMapElement("eisbn", eisbn);
      }
      
      resolved = getBookUrl(plugin, paramMap);
      log.debug3(  "Found starting url from definable plugin: " 
                 + resolved.resolvedUrl);
    } else {
      log.debug3("No plugin found for key: " + pluginKey); 
    }
    return resolved;
  }
    

  /**
   * Gets the book URL for a DefinablePlugin and parameter definitions.
   * @param plugin the plugin
   * @param paramMap the param map
   * @return the issue URL
   */
  private OpenUrlInfo getBookUrl(Plugin plugin, TypedEntryMap paramMap) {
    OpenUrlInfo resolved = getPluginUrl(plugin, auBookauFeatures, paramMap);
    if (resolved.resolvedUrl == null) {
      resolved = OpenUrlInfo.newInstance(
          paramMap.getString("base_url"), OpenUrlInfo.ResolvedTo.PUBLISHER);
    }
    return resolved;
  }

  /**
   * Gets the issue URL for an AU indicated by the DefinablePlugin 
   * and parameter definitions specified by the TdbAu.
   * 
   * @param plugin the DefinablePlugin
   * @param tdbau the TdbAu
   * @param year the year
   * @param volumeName the volume name
   * @param issue the issue
   * @return the issue URL
   */
/*  for later use (pjg)
  private static String getJournalUrl(
	  ArchivalUnit au, String year, String volumeName, String issue) {
	TypedEntryMap paramMap = getParamMap(au);
	Plugin plugin = au.getPlugin();
	String url = getJournalUrl(plugin, paramMap, year, volumeName, issue);
    return url;
  }
*/
  /**
   * Get starting url from TdbAu.
   * @param tdbau the TdbAu
   * @param year the year
   * @param volumeName the volume name
   * @param issue the issue
   * @param spage the start page or article number
   * @return the starting URL
   */
  private OpenUrlInfo getJournalUrl(
	  TdbAu tdbau, String year, String volumeName, String issue, String spage) {
    PluginManager pluginMgr = daemon.getPluginManager();
    String pluginKey = PluginManager.pluginKeyFromId(tdbau.getPluginId());
    Plugin plugin = pluginMgr.getPlugin(pluginKey);

    OpenUrlInfo resolved = noOpenUrlInfo;
    if (plugin != null) {
      log.debug3(  "getting issue url for plugin: " 
                 + plugin.getClass().getName());
      // get starting URL from a DefinablePlugin
      // add volume with type and spelling of existing element
  	  TypedEntryMap paramMap = getParamMap(plugin, tdbau);
  	  paramMap.setMapElement("volume", volumeName);
      paramMap.setMapElement("volume_str", volumeName);
      paramMap.setMapElement("volume_name", volumeName);
      paramMap.setMapElement("year", year);
      String issn = tdbau.getPrintIssn();
      if (issn != null) {
        paramMap.setMapElement("issn", issn);
      }
      String eissn = tdbau.getEissn();
      if (eissn != null) {
        paramMap.setMapElement("eissn", eissn);
      }
      if (!StringUtil.isNullString(year)) {
        try {
          paramMap.setMapElement("au_short_year",
              String.format("%02d", NumberUtil.parseInt(year)%100));
        } catch (NumberFormatException ex) {
          log.info("Error parsing year '" + year
                   + "' as an integer -- not setting au_short_year");
        }
      }
      paramMap.setMapElement("issue", issue);
      paramMap.setMapElement("article", spage);
      // AU_FEATURE_KEY selects feature from a map of values
      // for the same feature (e.g. au_feature_urls/au_year)
      paramMap.setMapElement(AU_FEATURE_KEY, tdbau.getAttr(AU_FEATURE_KEY));
      resolved = getJournalUrl(plugin, paramMap);
      log.debug3(  "Found starting url from definable plugin: " 
                 + resolved.resolvedUrl);
    } else {
      log.debug3("No plugin found for key: " + pluginKey); 
    }
    return resolved;
  }
    
  /**
   * Get the issueURL for the plugin.
   * @param plugin the plugin
   * @param paramMap the param map
   * @return the issue URL
   */
  private OpenUrlInfo getJournalUrl(Plugin plugin, TypedEntryMap paramMap) { 
    OpenUrlInfo resolved = getPluginUrl(plugin, auJournalauFeatures, paramMap);
    if (resolved.resolvedUrl == null) {
      resolved = OpenUrlInfo.newInstance(paramMap.getString("base_url"), 
                                         OpenUrlInfo.ResolvedTo.PUBLISHER);
    }
    return resolved;
  }
  
  /**
   * Get the URL for the specified key from the plugin.
   * @param plugin the plugin
   * @param pluginKeys the plugin keys
   * @param paramMap the param map
   * @return the URL for the specified key
   */
  private OpenUrlInfo
  	getPluginUrl(Plugin plugin, FeatureEntry[] pluginEntries, TypedEntryMap paramMap) {
    ExternalizableMap map;

    // get printf pattern for pluginKey property
    try {
      Method method = 
        plugin.getClass().getMethod("getDefinitionMap", (new Class[0]));
      Object obj = method.invoke(plugin);
      if (!(obj instanceof ExternalizableMap)) {
       return noOpenUrlInfo;
      }
      map = (ExternalizableMap)obj;
    } catch (Exception ex) {
      log.error("getDefinitionMap", ex);
      return noOpenUrlInfo;
    }

    for (FeatureEntry pluginEntry : pluginEntries) {
      // locate object value for plugin key path
      String pluginKey = pluginEntry.auFeatureKey;
      String[] pluginKeyPath = pluginKey.split("/");
      Object obj = map.getMapElement(pluginKeyPath[0]);
      for (int i = 1; (i < pluginKeyPath.length); i++) {
        if (obj instanceof Map) {
          obj = ((Map<String,?>)obj).get(pluginKeyPath[i]);
        } else {
          // all path elements except last one must be a map;
          obj = null;
          break;
        }
      }
      
      if (obj instanceof Map) {
        // match TDB AU_FEATURE_KEY value to key in map 
        String auFeatureKey = "*";  // default entry
        try {
          auFeatureKey = paramMap.getString(AU_FEATURE_KEY);
        } catch (NoSuchElementException ex) {}
        
        // entry may have multiple keys; '*' is the default entry
        Object val = null;
        for (Map.Entry<String,?> entry : ((Map<String,?>)obj).entrySet()) {
          String key = entry.getKey();
          if (   key.equals(auFeatureKey)
              || key.startsWith(auFeatureKey + ";")
              || key.endsWith(";" + auFeatureKey)
              || (key.indexOf(";" + auFeatureKey + ";") >= 0)) {
            val = entry.getValue();
            break;
          }
        }
        obj = val;
        pluginKey += "/" + auFeatureKey;
      }

      if (obj == null) {
        log.debug("unknown plugin key: " + pluginKey);
        continue;
      } 
      
      Collection<String> printfStrings = null;
      if (obj instanceof String) {
        // get single pattern for start url
        printfStrings = Collections.singleton((String)obj);
      } else if (obj instanceof Collection) {
        printfStrings = (Collection<String>)obj;
      } else {
        log.debug(  "unknown type for plugin key: " + pluginKey 
                  + ": " + obj.getClass().getName());
        continue;
      }
      
      log.debug3(  "Trying plugin key: " + pluginKey 
                 + " for plugin: " + plugin.getPluginId()
                 + " with " + printfStrings.size() + " printf strings");

      // set up converter for use with feature URL printf strings
      UrlListConverter converter = 
        PrintfConverter.newUrlListConverter(plugin, paramMap);
      
      for (String s : printfStrings) {
        String url = null;
        s = StringEscapeUtils.unescapeHtml(s);
        try {
          List<String> urls = converter.getUrlList(s);
          if ((urls != null) && !urls.isEmpty()) {
            // if multiple urls match, the first one will do
            url = urls.get(0);
          } 
        } catch (Throwable ex) {
          log.debug("invalid  conversion for " + s, ex);
          continue;
        }
            
        // validate URL: either it's cached, or it can be reached
        if (!StringUtil.isNullString(url)) {
          log.debug3("Resolving from url: " + url);
          url = resolveUrl(url);
          if (url != null) {
            return OpenUrlInfo.newInstance(url, pluginEntry.resolvedTo);
          }
        }
      }
    }
      
    return noOpenUrlInfo;
  }
  
  /**
   * Return the book URL from TdbTitle and edition.
   * 
   * @param tdbAus a collection of TdbAus that match an ISBN
   * @param date the publication date
   * @param volume the volume
   * @param edition the edition
   * @param spage the start page
   * @return the book URL
   */
  private OpenUrlInfo resolveBookFromTdbAus(
	  Collection<TdbAu> tdbAus, String date, 
	  String volume, String edition, String spage) {

    // get the year from the date
    String year = null;
    if (date != null) {
      int y = new PublicationDate(date).getYear();
      year = (y == 0) ? null : Integer.toString(y);
    }

    // list of AUs that match volume and year specified
    ArrayList<TdbAu> foundTdbAuList = new ArrayList<TdbAu>();
    
    // list of AUs that do not match volume, edition, and year specified
    ArrayList<TdbAu> notFoundTdbAuList = new ArrayList<TdbAu>();
    
    for (TdbAu tdbAu : tdbAus) {
      
      // if none of year, volume, or edition specified, pick any TdbAu
      if ((volume == null) && (year == null) && (edition == null)) {
        notFoundTdbAuList.add(tdbAu);
        continue;
      }
      
      // if volume specified, see if this TdbAu matches
      if (volume != null) {
        if (!tdbAu.includesVolume(volume)) {
          notFoundTdbAuList.add(tdbAu);
          continue;
        }
      }
      // if year specified, see if this TdbAu matches
      if (year != null) {
        if (!tdbAu.includesYear(year)) {
          notFoundTdbAuList.add(tdbAu);
          continue;
        }
      }
      
      // get the plugin id for the TdbAu that matches the specified edition
      if (edition != null) {
        String auEdition = tdbAu.getEdition();
        if ((auEdition != null) && !edition.equals(auEdition)) {
          notFoundTdbAuList.add(tdbAu);
          continue;
        }
      }
      foundTdbAuList.add(tdbAu);
    }
    
    final PluginManager pluginMgr = daemon.getPluginManager();
    OpenUrlInfo resolved = noOpenUrlInfo;
    if (!foundTdbAuList.isEmpty()) {
      // look for URL that is cached from list of matching AUs
      for (TdbAu tdbau : foundTdbAuList) {
        String aYear = year;
        if (aYear == null) {
          aYear = tdbau.getStartYear();
        }
        String aVolume = volume;
        if (aVolume == null) {
          aVolume = tdbau.getStartVolume();
        }
        String anEdition = edition;
        if (edition == null) {
          anEdition  = tdbau.getEdition();
        }
        OpenUrlInfo aResolved = getBookUrl(tdbau, year, aVolume, anEdition, spage);
        if (aResolved != null) {
          // found the URL if in cache
          if  (pluginMgr.findCachedUrl(aResolved.resolvedUrl, true) != null) {
            resolved = aResolved;
            break;
          }
          // not a viable URL if the AU is down
          // note: even though getBookUrl() checks that page exists,
          // we can't rely on it being usable if TdbAu is down
          if (!tdbau.isDown()) {
            resolved = aResolved;
          } else {
            log.debug2(  "discarding URL " + aResolved.resolvedUrl 
                       + " because tdbau is down: " + tdbau.getName());
          }
        }
      }
    } else {
      // look for URL that is cached from list of non-matching AUs
      for (TdbAu tdbau : notFoundTdbAuList) {
        OpenUrlInfo aResolved = getBookUrl(tdbau, year, volume, edition, spage);
        if (aResolved.resolvedUrl != null) {
          // found the URL if in cache
          if  (pluginMgr.findCachedUrl(aResolved.resolvedUrl, true) != null) {
            resolved = aResolved;
            break;
          }
          // not a viable URL if the AU is down
          // note: even though getBookUrl() checks that page exists,
          // we can't rely on it being usable if TdbAu is down
          if (!tdbau.isDown()) {
            resolved = aResolved;
          } else {
            log.debug2(  "discarding URL " + aResolved.resolvedUrl 
                       + " because tdbau is down: " + tdbau.getName());
          }
        }
      }
    }

    return resolved;
  }
  
  /**
   * Return the article URL from an ISBN, edition, spage, and author.
   * The first author will only be used when the starting page is not given.
   * "Volume" is used to hold edition information in the metadata manager 
   * schema for books.  First author can be used in place of start page.
   * 
   * @param isbn the isbn
   * @param date the date
   * @param volume the volume
   * @param edition the edition
   * @param spage the start page
   * @param author the first author
   * @param atitle the chapter title
   * @return the article URL
   */
  public OpenUrlInfo resolveFromIsbn(
    String isbn, String date, String volume, String edition, 
    String spage, String author, String atitle) {
    OpenUrlInfo resolved = noOpenUrlInfo;
    // only go to metadata manager if requesting individual article/chapter
    try {
      // resolve from metadata manager
      MetadataManager metadataMgr = daemon.getMetadataManager();
      resolved = resolveFromIsbn(
          metadataMgr, isbn, date, volume, edition, spage, author, atitle);
    } catch (IllegalArgumentException ex) {
    }

    if (resolved.resolvedUrl == null) {
      // resolve from TDB
      Tdb tdb = ConfigManager.getCurrentConfig().getTdb();
      Collection<TdbAu> tdbAus = (tdb == null)
        ? Collections.<TdbAu>emptySet() : tdb.getTdbAusByIsbn(isbn); 
      if (tdbAus.isEmpty()) {
        log.debug3("No TdbAus for isbn " + isbn);
        return null;
      }
      resolved = resolveBookFromTdbAus(tdbAus, date, volume, edition, spage);
    }
    return resolved;
  }

  /**
   * Return the article URL from an ISBN, edition, start page, author, and
   * article title using the metadata database.
   * <p>
   * The algorithm matches the ISBN and optionally the edition, and either 
   * the start page, author, or article title. The reason for matching on any
   * of the three is that typos in author and article title are always 
   * possible so we want to be more forgiving in matching an article.
   * <p>
   * If none of the three are specified, the URL for the book table of contents 
   * is returned.
   * 
   * @param metadataMgr the metadata manager
   * @param isbn the isbn
   * @param String date the date
   * @param String volumeName the volumeName
   * @param edition the edition
   * @param spage the start page
   * @param author the first author
   * @param atitle the chapter title
   * @return the url
   */
  private OpenUrlInfo resolveFromIsbn(
      MetadataManager metadataMgr, String isbn, 
      String date, String volume, String edition, 
      String spage, String author, String atitle) {
      OpenUrlInfo resolved = noOpenUrlInfo;
    Connection conn = null;
    try {
      conn = metadataMgr.newConnection();
      // strip punctuation
      isbn = isbn.replaceAll("[- ]", "");
      
      StringBuilder query = new StringBuilder();
      ArrayList<String> args = new ArrayList<String>();
      buildAuxiliaryTableQuery(
        MetadataManager.ISBN_TABLE, MetadataManager.ISBN_FIELD,
        new String[] {isbn}, query, args);

      boolean hasBookSpec = 
    	(date != null) || (volume != null) || (edition != null); 
      
      boolean hasArticleSpec = 
    	(spage != null) || (author != null) || (atitle != null);
      
      if (hasBookSpec) {
        // can specify an issue by a combination of date, volume and issue;
        // how these combine varies, so do the most liberal match possible
        // and filter based on multiple results
        query.append(" and ");
        if (date != null) {
          // enables query "2009" to match "2009-05-10" in database
          query.append(MetadataManager.DATE_FIELD);
          query.append(" like ? escape '\\'");
          args.add(date.replace("\\","\\\\").replace("%","\\%") + "%");
        }
        
        if (volume != null) {
          if (date != null) {
            query.append(" and ");
          }
          query.append(MetadataManager.VOLUME_FIELD);
          query.append(" = ?");
          args.add(volume);
        }

        if (edition != null) {
          if ((date != null) || (volume != null)) {
        	query.append(" and ");
          }
          query.append(MetadataManager.EDITION_FIELD);
          query.append(" = ?");
          args.add(edition);
        }
      }

      // handle start page, author, and article title as
      // equivalent ways to specify an article within an issue
      if (hasArticleSpec) {
        // accept any of the three
        query.append(" and ( ");
          
        if (spage != null) {
          query.append(MetadataManager.START_PAGE_FIELD);
          query.append(" = ?");
          args.add(spage);
        }
        if (atitle != null) {
          if (spage != null) {
            query.append(" or ");
          }
          query.append(MetadataManager.ARTICLE_TITLE_FIELD);
          query.append(" like ? escape '\\'");
          args.add(atitle.replace("%","\\%") + "%");
        }
        if ( author != null) {
          if ((spage != null) || (atitle != null)) {
            query.append(" or ");
          }
          // add the author query to the query
          addAuthorQuery(author, query, args);
        }
    
        query.append(" )");

      }
      
      String url = resolveFromQuery(conn, query.toString(), args);
      resolved = OpenUrlInfo.newInstance(url, OpenUrlInfo.ResolvedTo.CHAPTER);
      
    } catch (SQLException ex) {
      log.error("Getting ISBN:" + isbn, ex);
        
    } finally {
      MetadataManager.safeClose(conn);
    }
    return resolved;
  }
  
  /**
   * Build a query for the field in the specified auxiliary table 
   * (e.g. MetadataManager.ISSN_TABLE, MetadataManager.ISBN_TABLE
   * or MetadataManager.DOI_TABLE) that shares a foreign key with
   * the Metadata table.
   * @param tableId the table ID
   * @param fieldId the field ID in the specified table 
   * @param fieldValues the list of acceptable field values
   * @param query the query string builder
   * @param args the query args
   */
  private void buildAuxiliaryTableQuery(
                  String tableId, String fieldId, String[] fieldValues, 
                  StringBuilder query, List<String>args) {
    String MTN = MetadataManager.METADATA_TABLE;
    query.append("select distinct ");
    query.append(MetadataManager.ACCESS_URL_FIELD);
    query.append(",");
    query.append(MetadataManager.PLUGIN_ID_FIELD);
    query.append(",");
    query.append(MetadataManager.AU_KEY_FIELD);
    query.append(" from ");
    query.append(MTN);
    query.append(",");
    query.append(tableId);
    query.append(" where ");
    query.append(tableId);
    query.append(".");
    query.append(MetadataManager.MD_ID_FIELD);
    query.append(" = ");
    query.append(MTN);
    query.append(".");
    query.append(MetadataManager.MD_ID_FIELD);
    query.append(" and ");
    query.append(fieldId);
    query.append(" in (");
    String querystr = "?";
    for (String issn : fieldValues) {
      query.append(querystr);
      args.add(issn.replaceAll("-", "")); // strip punctuation
      querystr = ",?";
    }
    query.append(")");
  }
  
  /**
   * Add author query to the query buffer and argument list.  
   * @param author the author
   * @param query the query buffer
   * @param args the argument list
   */
  private void addAuthorQuery(
    String author, StringBuilder query, List<String>args) {
	String authorUC = author.toUpperCase();
	// match single author
    // (last, first name separated by ',')
    query.append("upper(");
	query.append(MetadataManager.AUTHOR_FIELD);
    query.append(") = ?");
    args.add(authorUC);

    // escape escape character and then wildcard characters
    String authorEsc = authorUC.replace("\\", "\\\\").replace("%","\\%");
            
    for (int i = 0; i < 5; i++) {
      query.append(" or upper(");
      query.append(MetadataManager.AUTHOR_FIELD);
      query.append(") like ? escape '\\'");
    }
    // match last name of first author 
    // (last, first name separated by ',')
    args.add(authorEsc+",%");
            
    // match entire first author 
    // (authors separated by ';', last, first name separated by ',')
    args.add(authorEsc+";%");
            
    // match last name of middle or last author 
    // (authors separated by ';', last, first name separated by ',')
    args.add("%;" + authorEsc + ",%");
            
    // match entire middle author 
    // (authors separated by ';')
    args.add("%;" + authorEsc + ";%");
            
    // match entire last author 
    // (authors separated by ';')
    args.add("%;" + authorEsc);
  }
}

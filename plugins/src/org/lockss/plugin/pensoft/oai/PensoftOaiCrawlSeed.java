/*
 * $Id:$
 */

/*

Copyright (c) 2017 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.pensoft.oai;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lockss.daemon.Crawler.CrawlerFacade;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.UrlCacher;
import org.lockss.plugin.UrlData;
import org.lockss.util.CIProperties;
import org.lockss.util.Constants;
import org.lockss.util.Logger;
import org.lockss.util.UrlUtil;

import com.lyncode.xoai.model.oaipmh.Record;
import com.lyncode.xoai.serviceprovider.exceptions.BadArgumentException;
import com.lyncode.xoai.serviceprovider.exceptions.InvalidOAIResponse;
import com.lyncode.xoai.serviceprovider.model.Context;
import com.lyncode.xoai.serviceprovider.model.Context.KnownTransformer;
import com.lyncode.xoai.serviceprovider.parameters.ListRecordsParameters;
import com.lyncode.xoai.services.api.MetadataSearch;


public class PensoftOaiCrawlSeed extends RecordFilteringOaiPmhCrawlSeed {
  public static final String DEFAULT_DATE_TAG = "dc.date";
  public static final String DEFAULT_IDENTIFIER_TAG = "dc.identifier";
  protected Collection<String> startUrls;
  protected int year;
  protected Pattern yearPattern = Pattern.compile("^([0-9]{4})$");
  public static final String OAI_DC_METADATA_PREFIX = "oai_dc";
  private static Logger logger =
	      Logger.getLogger(PensoftOaiCrawlSeed.class);

  public PensoftOaiCrawlSeed(CrawlerFacade cf) {
    super(cf);
    setMetadataPrefix(OAI_DC_METADATA_PREFIX);
    setUrlPostfix("oai.php");
  }

  @Override
  protected Context buildContext(String url) {
    Context con = super.buildContext(url);
    con.withMetadataTransformer(OAI_DC_METADATA_PREFIX, KnownTransformer.OAI_DC);
    return con;
  }
  
  @Override
  protected Collection<String> getRecordList(ListRecordsParameters params)
		  throws ConfigurationException, IOException {
      String storeUrl = baseUrl + "auid=" + UrlUtil.encodeUrl(au.getAuId());
      String link;
      Boolean error = false;
      Set<String> idSet = new HashSet<String>();
      try {
	      for (Iterator<Record> recIter = getServiceProvider().listRecords(params);
	           recIter.hasNext();) {
	        Record rec = recIter.next();
	        MetadataSearch<String> metaSearch = 
	            rec.getMetadata().getValue().searcher();
	        if (checkMetaRules(metaSearch)) {
	        	link = findRecordArticleLink(rec);
	        	if(link != null) {
	        		idSet.add(link);
	        	}
	        }
	      }
      } catch (InvalidOAIResponse e) {
    	  if(e.getCause() != null && e.getCause().getMessage().contains("LOCKSS")) {
    		  error = true;
    		  logger.debug("OAI result errored due to LOCKSS audit proxy. Trying alternate start Url", e);
    	  } else {
    		  throw e;
    	  }
      } catch (BadArgumentException e) {
    	  throw new ConfigurationException("Incorrectly formatted OAI parameter", e);
      }
      
      List<String> idList = new ArrayList<String>();
	  if(error) {
		  idList.add(storeUrl);
	  } else if(!idSet.isEmpty()) {
		  idList.addAll(idSet);
		  Collections.sort(idList);
		  storeStartUrls(idList, storeUrl);
	  }
	  return idList;
  }
  
  protected void storeStartUrls(Collection<String> urlList, String url) throws IOException {
	  StringBuilder sb = new StringBuilder();
	  sb.append("<html>\n");
	  for (String u : urlList) {
		  sb.append("<a href=\"" + u + "\">" + u + "</a><br/>\n");
	  }
	  sb.append("</html>");
	  CIProperties headers = new CIProperties();
	  //Should use a constant here
	  headers.setProperty("content-type", "text/html; charset=utf-8");
      UrlData ud = new UrlData(new ByteArrayInputStream(sb.toString().getBytes(Constants.ENCODING_UTF_8)), headers, url);
      UrlCacher cacher = facade.makeUrlCacher(ud);
      cacher.storeContent();
  }
  
  protected String findRecordArticleLink(Record rec) { 
    MetadataSearch<String> recSearcher = rec.getMetadata().getValue().searcher();
    List<String> idTags = recSearcher.findAll(DEFAULT_IDENTIFIER_TAG);
    if(idTags != null && !idTags.isEmpty()) {
      for(String value : idTags) {
        if (AuUtil.normalizeHttpHttpsFromBaseUrl(au, value).startsWith(baseUrl)) {
          logger.debug("To Follow: " + value);
          return value;
        }
      }
    }
    return null;
  }
  
  
  @Override
  protected void parseRules(String rule) throws ConfigurationException {
    if(rule.length() == 4) {
      try {
        year = Integer.parseInt(rule);
      } catch(NumberFormatException ex) {
        throw new ConfigurationException("OAI date must be a 4 digit year");
      }
    } else {
      throw new ConfigurationException("OAI date must be a 4 digit year");
    }
    
  }

  @Override
  protected boolean checkMetaRules(MetadataSearch<String> metaSearch) {
    List<String> matchingTags;
    matchingTags = metaSearch.findAll(DEFAULT_DATE_TAG);
    if(matchingTags!= null && !matchingTags.isEmpty()) {
      for(String value : matchingTags) {
        try{
          String subYear;
          Matcher yearMatch = yearPattern.matcher(value);
          if(yearMatch.find()) {
            subYear = yearMatch.group(1);
            if(year == Integer.parseInt(subYear)) {
              return true;
            }
          }
        } catch(NumberFormatException|IllegalStateException ex) {
          //wasn't a correctly formatted date, so we ignore it
          //log here
        }
      }
    }
    return false;
  }
  
  /**
   * Override this to provide different logic to convert OAI PMH ids to
   * corresponding article urls
   * 
   * @param id
   * @param url
   * @return
   */
  public Collection<String> idsToUrls(Collection<String> ids) {
    return ids;
  }
}

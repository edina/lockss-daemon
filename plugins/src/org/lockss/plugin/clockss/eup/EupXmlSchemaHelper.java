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

package org.lockss.plugin.clockss.eup;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;
import org.lockss.util.*;
import org.lockss.extractor.*;
import org.lockss.extractor.XmlDomMetadataExtractor.NodeValue;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;

import java.util.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  A helper class that defines a schema for Edinburgh University Press
 *  XML files (EUP DTD)
 *  @author alexohlson
 */
public class EupXmlSchemaHelper
implements SourceXmlSchemaHelper {
  static Logger log = Logger.getLogger(EupXmlSchemaHelper.class);
  
  private static final String AUTHOR_SEPARATOR = ",";

  /* 
   * AUTHOR INFORMATION:
   *
   *   <contrib-group>
   *     <contrib contrib-type="author">
   *       <string-name name-style="western">
   *         <given-names>Heiko</given-names>
   *         <surname>Motschenbacher</surname>
   *       </string-name>
   *       <xref ref-type="fn" rid="fn1">
   *         <sup>1</sup>
   *       </xref>
   *     </contrib>
   *   </contrib-group>
   */
  static private final NodeValue EUP_AUTHOR_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {

      log.debug3("getValue of eup author");
      NodeList elementChildren = node.getChildNodes();
      if (elementChildren == null) return null;
      
      String givenNames = null;
      String surname = null;
      // look at each child 
      for (int j = 0; j < elementChildren.getLength(); j++) {
        Node checkNode = elementChildren.item(j);
        String nodeName = checkNode.getNodeName();
        if ("given-names".equals(nodeName)) {
          givenNames = checkNode.getTextContent();
        } else if ("surname".equals(nodeName) ) {
          surname = checkNode.getTextContent();
        }
      }

      StringBuilder valbuilder = new StringBuilder();
      //isBlank checks for null, empty & whitespace only
      if (!StringUtils.isBlank(surname)) {
        valbuilder.append(surname);
        if (!StringUtils.isBlank(givenNames)) {
          valbuilder.append(AUTHOR_SEPARATOR + " " + givenNames);
        }
      } else {
        log.debug3("no author found");
        return null;
      }
      log.debug3("author found: " + valbuilder.toString());
      return valbuilder.toString();
    }
  };

  /* 
   * DATE INFORMATION
   *
   *   <pub-date pub-type="ppub">
   *     <month>April</month>
   *     <year>2016</year>
   *   </pub-date>
   */
  static private final NodeValue EUP_DATE_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {

      log.debug3("getValue of EUP publishing date");
      NodeList elementChildren = node.getChildNodes();
      if (elementChildren == null) return null;
      
      // perhaps pick up iso attr if it's available 
      String year = null;
      String day = null;
      String month = null;
      // look at each child of the TitleElement for information
      for (int j = 0; j < elementChildren.getLength(); j++) {
        Node checkNode = elementChildren.item(j);
        String nodeName = checkNode.getNodeName();
        if ("day".equals(nodeName)) {
          day = checkNode.getTextContent();
        } else if ("month".equals(nodeName) ) {
          month = checkNode.getTextContent();
        } else if ("year".equals(nodeName)) {
          year = checkNode.getTextContent();
        }
      }

      StringBuilder valbuilder = new StringBuilder();
      if (year != null) {
        valbuilder.append(year);
        if (day != null && month != null) {
          valbuilder.append("-" + month + "-" + day);
        }
      } else {
        log.debug3("no date found");
        return null;
      }
      log.debug3("date found: " + valbuilder.toString());
      return valbuilder.toString();
    }
  };


  /* 
   *  Edinburgh University Press specific XPATH key definitions that we care about
   *  There is only one article per xml file and the 
   *  filename.xml == filename.pdf == Article PII (internal ID)
   */
  private static String EUP_article = "/article";

  private static String EUP_journal_info_node = "/article/front/journal-meta";
  private static String EUP_article_node = "/article/front/article-meta";
  
  //journal level
  private static String EUP_jtitle = EUP_journal_info_node + "/journal-title-group/journal-title";
  private static String EUP_eissn = EUP_journal_info_node + "/issn[@pub-type='epub']";
  private static String EUP_pissn = EUP_journal_info_node + "/issn[@pub-type='ppub']";

  // article level
  private static String EUP_doi = EUP_article_node + "/article-id[@pub-id-type='doi']";
  private static String EUP_atitle = EUP_article_node + "/title-group/article-title";
  private static String EUP_fpage = EUP_article_node + "/fpage";
  private static String EUP_lpage = EUP_article_node + "/lpage"; 
  private static String EUP_contrib = EUP_article_node + "/contrib-group/contrib[@contrib-type='author']/string-name[@name-style='western']";
  private static String EUP_volume = EUP_article_node + "/volume";
  private static String EUP_issue = EUP_article_node + "/issue";
  private static String EUP_date = EUP_article_node + "/pub-date[@pub-type='ppub']";

  /*
   *  The following 3 variables are needed to construct the XPathXmlMetadataParser
   */
  
  /* 1.  MAP associating xpath with value type with evaluator */
  static private final Map<String,XPathValue> EUP_articleMap = 
      new HashMap<String,XPathValue>();
  static {
    EUP_articleMap.put(EUP_jtitle, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_atitle, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_pissn, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_eissn, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_doi, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_atitle, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_volume, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_issue, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_fpage, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_lpage, XmlDomMetadataExtractor.TEXT_VALUE);
    EUP_articleMap.put(EUP_date, EUP_DATE_VALUE);
    EUP_articleMap.put(EUP_contrib, EUP_AUTHOR_VALUE);
  }

  /* 2. Each item (article) has its own XML file */
  static private final String EUP_articleNode = EUP_article; 

  /* 3. in EUP there is no global information because one file/article */
  static private final Map<String,XPathValue> EUP_globalMap = null;

  /*
   * The emitter will need a map to know how to cook EUP raw values
   */
  private static final MultiValueMap cookMap = new MultiValueMap();
  static {
    // do NOT cook publisher_name; get from TDB file for consistency
    cookMap.put(EUP_jtitle, MetadataField.FIELD_PUBLICATION_TITLE);
    cookMap.put(EUP_atitle, MetadataField.FIELD_ARTICLE_TITLE);
    cookMap.put(EUP_doi, MetadataField.FIELD_DOI);
    // pick up both pissn and issn...unlikely both are present
    cookMap.put(EUP_pissn, MetadataField.FIELD_ISSN);
    cookMap.put(EUP_eissn, MetadataField.FIELD_EISSN);
    //cookMap.put(EUP_pubname, MetadataField.FIELD_PUBLISHER);
    cookMap.put(EUP_volume, MetadataField.FIELD_VOLUME);
    cookMap.put(EUP_issue, MetadataField.FIELD_ISSUE);
    cookMap.put(EUP_fpage, MetadataField.FIELD_START_PAGE);
    cookMap.put(EUP_lpage, MetadataField.FIELD_END_PAGE);
    cookMap.put(EUP_contrib, MetadataField.FIELD_AUTHOR);
    cookMap.put(EUP_date, MetadataField.FIELD_DATE);
  }

  /**
   * EUP does not contain needed global information outside of article records
   * return NULL
   */
  @Override
  public Map<String, XPathValue> getGlobalMetaMap() {
    return EUP_globalMap;
  }

  /**
   * return EUP article map to identify xpaths of interest
   */
  @Override
  public Map<String, XPathValue> getArticleMetaMap() {
    return EUP_articleMap;
  }

  /**
   * Return the article node path
   */
  @Override
  public String getArticleNode() {
    return EUP_articleNode;
  }

  /**
   * Return a map to translate raw values to cooked values
   */
  @Override
  public MultiValueMap getCookMap() {
    return cookMap;
  }

  /**
   * No duplicate data 
   */
  @Override
  public String getDeDuplicationXPathKey() {
    return null;
  }

  /**
   * No consolidation required
   */
  @Override
  public String getConsolidationXPathKey() {
    return null;
  }

  /**
   * The filenames are the same as the XML filenames with .pdf suffix
   */
  @Override
  public String getFilenameXPathKey() {
    return null;
  }
}

/*
 * $Id: WoltersKluwerSourceXmlMetadataExtractorFactory.java,v 1.2 2014-07-25 17:34:46 aishizaki Exp $
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

package org.lockss.plugin.clockss.wolterskluwer;

import java.io.IOException;
import java.util.*;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FilenameUtils;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;

import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;

import org.lockss.plugin.clockss.wolterskluwer.WoltersKluwerXPathXmlMetadataParser;
import org.xml.sax.SAXException;


public class WoltersKluwerSourceXmlMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  static Logger log = Logger.getLogger(WoltersKluwerSourceXmlMetadataExtractorFactory.class);
  private static SourceXmlSchemaHelper WKHelper = null;

  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
    return new WoltersKluwerSourceXmlMetadataExtractor();
  }

  public static class WoltersKluwerSourceXmlMetadataExtractor extends SourceXmlMetadataExtractor {

    // this version shouldn't get called. It will ultimately get removed
    // in favor of the version that takes a CachedUrl
    @Override
    protected SourceXmlSchemaHelper setUpSchema() {
      return null; // cause a plugin exception to get thrown
    }

    @Override
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
    // Once you have it, just keep returning the same one. It won't change.
      if (WKHelper != null) {
        return WKHelper;
      }
      WKHelper = new WoltersKluwerSourceXmlSchemaHelper();
      return WKHelper;
    }
       
    /*
     * (non-Javadoc)
     * @see org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory.SourceXmlMetadataExtractor#extract(org.lockss.extractor.MetadataTarget, org.lockss.plugin.CachedUrl, org.lockss.extractor.FileMetadataExtractor.Emitter)
     * using the WoltersKluwerXPathXmlMetadataParser, WoltersKluwerSgmlAdapter and
     * RewritingReader to parse WoltersKluwer's SGML metadata file
     * 
     */
    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter) 
      throws IOException, PluginException {
      try {
        SourceXmlSchemaHelper schemaHelper;
        // 1. figure out which XmlMetadataExtractorHelper class to use to get
        // the schema specific information
        if ((schemaHelper = setUpSchema(cu)) == null) {
          log.debug("Unable to set up XML schema. Cannot extract from XML");
          throw new PluginException("XML schema not set up for " + cu.getUrl());
        }
        // 2. Gather all the metadata in to a list of AM records
        // WoltersKluwerXPathXmlMetadataParser uses an sgml reader for 
        // WoltersKluwer's not-too-unlike-xml sgml files
        // XPathXmlMetadataParser is not thread safe, must be called each time
        List<ArticleMetadata> amList = 
            new WoltersKluwerXPathXmlMetadataParser(schemaHelper.getGlobalMetaMap(), 
                schemaHelper.getArticleNode(), 
                schemaHelper.getArticleMetaMap(),
                getDoXmlFiltering()).extractMetadata(target, cu);

        //3. Optional consolidation of duplicate records within one XML file
        // a child plugin can leave the default (no deduplication) or 
        // AMCollection pointing to just a subset of the full
        // AM list
        // 3. Consolidate identical records based on DeDuplicationXPathKey
        // consolidating as specified by the consolidateRecords() method
        
        Collection<ArticleMetadata> AMCollection = getConsolidatedAMList(schemaHelper,
            amList);

        // 4. check, cook, and emit every item in resulting AM collection (list)
        for ( ArticleMetadata oneAM : AMCollection) {
          if (preEmitCheck(schemaHelper, cu, oneAM)) {
            oneAM.cook(schemaHelper.getCookMap());
            postCookProcess(schemaHelper, cu, oneAM); // hook for optional processing
            emitter.emitMetadata(cu,oneAM);
          }
        }
        
      } catch (XPathExpressionException e) {
        log.debug3("Xpath expression exception:",e);
      } catch (SAXException e) {
        handleSAXException(cu, emitter, e);
      } catch (IOException ex) {
        handleIOException(cu, emitter, ex);

      }
    }

  /**
   * overriding existing routine because: WoltersKluwer includes a partial name
   * of its pdf file (article), so we need to add a the full_path at the front
   * plus the prefix "0" and ".pdf" on the end
   * @param cu
   * @param oneAM
   * @return
   */
  protected ArrayList<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, 
      CachedUrl cu,
      ArticleMetadata oneAM) {
    final String ZERO = "0";
    final String DOT_PDF = ".pdf";

    // get the key for a piece of metadata used in building the filename
    String fn_key = helper.getFilenameXPathKey();  
    // the schema doesn't define a filename so don't do a default preEmitCheck
    if (fn_key == null) {
      return null; // no preEmitCheck 
    }
    String filenameValue = oneAM.getRaw(fn_key);
    // we expected a value, but didn't get one...we need to return something
    // for preEmitCheck to fail
    if (filenameValue == null) {
      filenameValue = "NOFILEINMETADATA"; // we expected a value, but got none
    }
    
    ArrayList<String> returnList = new ArrayList<String>();
    String cuBase = FilenameUtils.getFullPath(cu.getUrl());
    // MUST add "0" to the front to make it match the pdf in the zipfile.  GRRR
    returnList.add(cuBase + ZERO + filenameValue + DOT_PDF);
    return returnList;
    }

  }
}

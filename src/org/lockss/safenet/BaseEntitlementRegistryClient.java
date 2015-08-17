package org.lockss.safenet;

import java.io.IOException;
import java.net.URISyntaxException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.utils.URIBuilder;
import org.lockss.app.BaseLockssManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.config.Configuration;
import org.lockss.util.Logger;
import org.lockss.util.UrlUtil;
import org.lockss.util.urlconn.LockssUrlConnection;

public class BaseEntitlementRegistryClient extends BaseLockssManager implements EntitlementRegistryClient, ConfigurableManager {

  private static final Logger log = Logger.getLogger(BaseEntitlementRegistryClient.class);

  public static final String PREFIX = Configuration.PREFIX + "safenet.";
  public static final String PARAM_ER_URI = PREFIX + "registryUri";
  static final String DEFAULT_ER_URI = "";
  public static final String PARAM_ER_APIKEY = PREFIX + "apiKey";
  static final String DEFAULT_ER_APIKEY = "";

  private static String erUri;
  private static String apiKey;

  public void setConfig(Configuration config, Configuration oldConfig, Configuration.Differences diffs) {
    if (diffs.contains(PREFIX)) {
      erUri = config.get(PARAM_ER_URI, DEFAULT_ER_URI);
      apiKey = config.get(PARAM_ER_APIKEY, DEFAULT_ER_APIKEY);
    }
  }

  public boolean isUserEntitled(String issn, String institution, String start, String end){
    try {
      URIBuilder builder = new URIBuilder(erUri);
      builder.setPath(builder.getPath() + "/entitlements");
      builder.setParameter("api_key", apiKey);
      builder.setParameter("identifier_value", issn);
      builder.setParameter("institution", institution);
      builder.setParameter("start", start);
      builder.setParameter("end", end);

      String url = builder.toString();
      log.debug("Connecting to ER at " + url);
      LockssUrlConnection connection = openConnection(url);
      connection.execute();
      int responseCode = connection.getResponseCode();
      if (responseCode == 200) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode entitlements = mapper.readTree(connection.getResponseInputStream());
        for(JsonNode entitlement : entitlements) {
          if (institution.equals(entitlement.get("institution").asText())) {
            log.warning("TODO: Verify title and dates");
            return true;
          }
        }
      }
    }
    catch (IOException e) {
      log.error("Couldn't contact entitlement registry", e);
    }
    catch (URISyntaxException e) {
      log.error("Couldn't contact entitlement registry", e);
    }
    return false;
  }

  // protected so that it can be overriden with mock connections in tests
  protected LockssUrlConnection openConnection(String url) throws IOException {
    return UrlUtil.openConnection(url);
  }
}

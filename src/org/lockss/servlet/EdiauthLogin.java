package org.lockss.servlet;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import org.mortbay.html.*;
import java.net.URLDecoder;

import org.lockss.util.*;
import java.util.HashMap;

import org.lockss.account.*;

/**
 * Handle Ediauth authentication
 *
 * Listen to localhost for ediauth login If authentication successful create
 * User in order to keep information in the session.
 *
 */
@SuppressWarnings("serial")
public class EdiauthLogin extends LockssServlet {

  private static final Logger log = Logger
      .getLogger(DisplayContentStatus.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  /**
   * Handle a request
   *
   * @throws IOException
   */
  public void lockssHandleRequest() throws IOException {
		log.debug("start EdiauthLogin...");
		
	    Page page = ServletUtil.doNewPage("Ediauth", false);

	    page.add("Container: <b>" + getMachineName() + "</b>");
	    page.add("<br/>");
	    
	    // TODO: Remove true
	    if(true || isLocalhost(req)){
	    	// Get parameter
	    	String edinaUserId = req.getParameter("ea_edinaUserId");
	    	String context = req.getParameter("ea_context");
	    	String encodedExtra = req.getParameter("ea_extra");
	    	
	    	// Collect encodedExtra inside a Map
        log.debug("ea_extra:");
	    	if(encodedExtra != null){
    			HashMap<String, String> map = new HashMap<String, String>();
    			String[] pairs = encodedExtra.split("&");
    			for(String p : pairs){
    				String[] pair = p.split("=");
    				String key = null;
    				if(pair.length>0){
    				  key = URLDecoder.decode(pair[0], "UTF-8");
    				}
    				String val = null;
    				if(pair.length>1){
    				  val = URLDecoder.decode(pair[1], "UTF-8");
    				}
    				log.debug(key+": "+val);
    				map.put(key,val);
    			}
  			
    			/* The IdP entityID can be used to make WAYFless URLs if needed (the Ediauth recommended URL would be
    			 * http://edina.ac.uk/Login/kbplus?idp=<URLENCODEDIDP>
    			 * note that this is not what shibboleth would say is a WAYFless URL -- it's better
    			 * def idpEntityId = extraHash.shippIdP
    		     *
    			 * accountable is a shibb thing about the eduPersonTargetedID values
    			 * it should be 1 if you're extra paranoid about user IDs being re-used.
    			 * see section 3.1.2 of http://www.ukfederation.org.uk/library/uploads/Documents/federation-technical-specifications.pdf
    			 * and section 6.4 of http://www.ukfederation.org.uk/library/uploads/Documents/rules-of-membership.pdf */
    			boolean accountable = Integer.valueOf(map.get("shibbAccountable")) == 1;
    			boolean requireAccountable = true; //this is up to you.
    		
    			/* you could set userId to eduPersonPrincipalName if it's available. if not, use eduPersonTargetedId.
    			 * I recommend just using eduPersonTargetedID (less data protection worry with the logs and it also means
    			 * the userId won't change if an institution suddenly starts releasing ePPN). */
    			String userId = (requireAccountable && accountable) ? map.get("eduPersonTargetedID") : null ;
    	    	
    			// Return nothing (failure) if there is no userId
    			if (userId == null){
    				log.debug("No suitable value for userId\n");
    				if (requireAccountable && !accountable) log.debug("IdP doesn't assert accountability\n");
    				page.add("IdP doesn't assert accountability");
    			} else {
    			  log.debug("Session: " + getSession());
    			  UserAccount user = LockssSessionManager.getUserAccount(getSession());
            boolean authenticated = LockssSessionManager.isAuthenticated(getSession());

            log.debug("User: " + user);
            log.debug("authenticated: " + authenticated);
    				/* Build Session User
    				 * We might want to make sure that the same user doesn't already have a SessionObject.
    				 */
            StringBuffer message = new StringBuffer();
            if(user != null) {
              String name = user.getName();
              String roles = user.getRoles();
              message.append("user name: " + name);
              message.append("<br/>");
              message.append("roles: " + roles);
              message.append("<br/>");
            } else {
              message.append("No User Account.");
              message.append("<br/>");
            }
            message.append("authenticated: " + authenticated);
    
            page.add(getPageContent(message.toString()));
    			}
	    	} else {
	    	  log.debug("extra parameter is missing.");
          buildEnvInfoPage(page, req);
	    	}
	    	
	    } else {
	    	buildEnvInfoPage(page, req);
	    }
	    endPage(page);
	}
  
  /* TODO: Only for development, should be removed once it's working */
  protected void buildEnvInfoPage(Page page, HttpServletRequest req) {
    String remoteAddr = req.getRemoteAddr();
    String remoteHost = req.getRemoteHost();
    int remotePort = req.getRemotePort();

    String localAddress = req.getLocalAddr();
    int localPort = req.getLocalPort();
    String serverName = req.getServerName();
    
    String machineId = getMachineName();

    StringBuffer message = new StringBuffer();
    message.append("remoteAddr: " + remoteAddr);
    message.append("<br/>");
    message.append("remoteHost: " + remoteHost);
    message.append("<br/>");
    message.append("remotePort: " + remotePort);
    message.append("<br/>");
    message.append("localAddress: " + localAddress);
    message.append("<br/>");
    message.append("localPort: " + localPort);
    message.append("<br/>");
    message.append("serverName: " + serverName);
    message.append("<br/>");
    message.append("Container: " + machineId);

    page.add(getPageContent(message.toString()));
  }

  protected Table getPageContent(String message) {
    Table tab = new Table(0, "align=\"center\" width=\"80%\"");
    tab.newRow();
    tab.newCell("align=\"center\"");
    tab.add(message);
    tab.newCell("align=\"center\"");
    tab.add("&nbsp;");
    return tab;
  }

  protected boolean isLocalhost(HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr();
		String localAddress = req.getLocalAddr();
		
		return remoteAddr.equals(localAddress);
	}
}
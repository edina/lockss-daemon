package org.lockss.servlet;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.mockito.Mockito;
import org.lockss.plugin.PluginManager;
import org.lockss.test.ConfigurationUtil;
import org.lockss.util.Logger;
import org.hamcrest.Matcher;
import org.lockss.config.ConfigManager;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.httpunit.GetMethodWebRequest;

import org.lockss.test.MockLockssUrlConnection;
import org.lockss.util.urlconn.LockssUrlConnection;
import org.lockss.util.urlconn.LockssUrlConnectionPool;
import com.meterware.servletunit.InvocationContext;
import org.mockito.Mockito;

import com.meterware.httpunit.HttpInternalErrorException;
import java.io.IOException;

public class TestEdiauthLogin extends LockssServletTestCase {
  
  static Logger log = Logger.getLogger("TestEdiauthLogin");
  
  private EdiauthLogin serv;
  
  public void setUp() throws Exception {
    super.setUp();
    serv = new EdiauthLogin();

    String tempDirPath = setUpDiskSpace();
    theDaemon.setIdentityManager(new org.lockss.protocol.MockIdentityManager());
    theDaemon.getServletManager();
    theDaemon.setDaemonInited(true);
    theDaemon.getRemoteApi().startService();
    theDaemon.setAusStarted(true);
    
    Properties p = new Properties();
    p.setProperty(EdiauthLogin.PARAM_EDIAUTH_IP, "1111.1111.1111.1113");
    p.setProperty(EdiauthLogin.PARAM_EDIAUTH_REDIRECT_URL, "http://localhost:8888");
    p.setProperty(ConfigManager.PARAM_PLATFORM_PROJECT, "safenet");

    ConfigurationUtil.setCurrentConfigFromProps(p);
  }
  
  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  /**
   * Test the isLocalhost() method remote and local addresses being the same
   * It should return true
   */
  public void testIsLocalhostSameAddr() {
    HttpServletRequest req1 = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req1.getRemoteAddr()).thenReturn("1111.1111.1111.1112");
    Mockito.when(req1.getLocalAddr()).thenReturn("1111.1111.1111.1112");
    
    assertTrue(serv.isLocalhost(req1));
  }
  
  /**
   * Test the isLocalhost() method remote and local addresses being different
   * It should return false
   */
  public void testIsLocalhostDiffAddr() {
    HttpServletRequest req2 = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req2.getRemoteAddr()).thenReturn("1111.1111.1111.1111");
    Mockito.when(req2.getLocalAddr()).thenReturn("1111.1111.1111.1112");
    
    assertFalse(serv.isLocalhost(req2));
  }
  
  /**
   * Test the isLocalhost() method remote addresses being same as ediauth IP
   * It should return true
   */
  public void testIsLocalhostSameAsEdiauthIp() {
    HttpServletRequest req3 = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req3.getRemoteAddr()).thenReturn("1111.1111.1111.1113");
    Mockito.when(req3.getLocalAddr()).thenReturn("1111.1111.1111.1112");
    assertTrue(serv.isLocalhost(req3));
  }
  
  /*
   * Doesn't work: Need to fins a way to get InvocationContext.getRequest() to return mocked object
   * Test that request not coming from localhost are return HTTP 500
   */
//  public void testLoginNotLocalhost() throws Exception {
//    HttpServletRequest failingReq = Mockito.mock(HttpServletRequest.class);
//    Mockito.when(failingReq.getRemoteAddr()).thenReturn("1.1.1.3");
//    
//    initServletRunner();
//    WebRequest request = new GetMethodWebRequest("http://null/ediauth" );
//    InvocationContext ic = sClient.newInvocation(request);
//    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
//    
//    Mockito.when(ic.getRequest()).thenReturn(failingReq);
//    
//    try
//    {
//      sClient.getResponse(request);
//      fail("Should have thrown HTTP 5 exception");
//    }catch(HttpInternalErrorException hiee){
//      final String msg = "Error on HTTP request: 500 Internal Error [http://null/ediauth]";
//      assertEquals(msg, hiee.getMessage());
//    }
//  }
  
  /**
   * Test that request with no encodedExtra parameter are returning nothing
   */
  public void testLoginNoEncodedExtra() throws Exception {
    initServletRunner();
    WebRequest request = new GetMethodWebRequest("http://null/ediauth" );
    InvocationContext ic = sClient.newInvocation(request);
    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
    
    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    assertEquals("content lenght", "", resp1.getText());
  }
  
  /**
   * Test that request with no userId are returning nothing
   */
  public void testLoginNoUserId() throws Exception {    
    initServletRunner();
    WebRequest request = new GetMethodWebRequest("http://null/ediauth?"+
        "ea_context=http%3A%2F%2Flocalhost%3A8082%2FSafeNetServeContent&"+
        "ea_extra=shibbAccountable%3D1%26shibbScope%3Ded.ac.uk&"+
        "context=http%3A%2F%2Flocalhost%3A8082%2FSafeNetServeContent" );
    InvocationContext ic = sClient.newInvocation(request);
    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
    
    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    assertEquals("content lenght", "", resp1.getText());
  }
  

  
  /**
   * Test that request with no userId are returning nothing
   */
  public void testLoginNotAccountable() throws Exception {    
    initServletRunner();
    WebRequest request = new GetMethodWebRequest("http://null/ediauth?"+
        "ea_context=http%3A%2F%2Flocalhost%3A8082%2FSafeNetServeContent&"+
        "ea_extra=eduPersonTargetedID%3DSOMETARGETID%26shibbAccountable%3D0%26shibbScope%3Ded.ac.uk&"+
        "context=http%3A%2F%2Flocalhost%3A8082%2FSafeNetServeContent" );
    InvocationContext ic = sClient.newInvocation(request);
    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
    
    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    assertEquals("content lenght", "", resp1.getText());
  }
  
  /**
   * Test that request not coming from localhost are return HTTP 500
   */
  public void testLoginNoContext() throws Exception {
    initServletRunner();
    WebRequest request = new GetMethodWebRequest("http://null/ediauth?"+
      "ea_extra=eduPersonTargetedID%3DSOMETARGETID%26shibbAccountable%3D1%26shibbScope%3Ded.ac.uk&" );
    InvocationContext ic = sClient.newInvocation(request);
    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
    
    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    System.out.println("getText: " + resp1.getText());
    // http://localhost:8082/SafeNetServeContent?ediauthToken=dd5e05b9-64f9-449a-b9da-70d382676d22
    assertTrue("response text not as expected: ["+resp1.getText()+"]", 
        resp1.getText().matches("http://localhost:8888\\?ediauthToken=[\\p{XDigit}\\-]*"));
  }
  
  /**
   * Test that request not coming from localhost are return HTTP 500
   */
  public void testLoginWithValidRequest() throws Exception {
    initServletRunner();
    WebRequest request = new GetMethodWebRequest("http://null/ediauth?"+
      "ea_context=http%3A%2F%2Flocalhost%3A8082%2FSomePlaceToGoTo&"+
      "ea_extra=eduPersonTargetedID%3DSOMETARGETID%26shibbAccountable%3D1%26shibbScope%3Ded.ac.uk&"+
      "context=http%3A%2F%2Flocalhost%3A8082%2FSafeNetServeContent" );
    InvocationContext ic = sClient.newInvocation(request);
    EdiauthLogin el = (EdiauthLogin) ic.getServlet();
    
    WebResponse resp1 = sClient.getResponse(request);
    assertResponseOk(resp1);
    assertEquals("content type", "text/html", resp1.getContentType());
    System.out.println("getText: " + resp1.getText());
    // http://localhost:8082/SafeNetServeContent?ediauthToken=dd5e05b9-64f9-449a-b9da-70d382676d22
    assertTrue("response text not as expected: ["+resp1.getText()+"]", 
        resp1.getText().matches("http://localhost:8082/SomePlaceToGoTo\\?ediauthToken=[\\p{XDigit}\\-]*"));
  }
  
  protected void initServletRunner() {
    super.initServletRunner();
    sRunner.setServletContextAttribute(ServletManager.CONTEXT_ATTR_SERVLET_MGR, new ContentServletManager());
    sRunner.registerServlet("/ediauth", EdiauthLogin.class.getName() );
  }
  
}

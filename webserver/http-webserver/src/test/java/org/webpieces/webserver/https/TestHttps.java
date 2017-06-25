package org.webpieces.webserver.https;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.webpieces.httpclient11.api.HttpFullRequest;
import org.webpieces.httpclient11.api.HttpSocket;
import org.webpieces.httpparser.api.common.Header;
import org.webpieces.httpparser.api.common.KnownHeaderName;
import org.webpieces.httpparser.api.dto.HttpRequest;
import org.webpieces.httpparser.api.dto.KnownHttpMethod;
import org.webpieces.httpparser.api.dto.KnownStatusCode;
import org.webpieces.util.file.VirtualFileClasspath;
import org.webpieces.webserver.Requests;
import org.webpieces.webserver.ResponseExtract;
import org.webpieces.webserver.WebserverForTest;
import org.webpieces.webserver.test.AbstractWebpiecesTest;
import org.webpieces.webserver.test.FullResponse;


public class TestHttps extends AbstractWebpiecesTest {
	
	private HttpSocket httpSocket;
	private HttpSocket httpsSocket;

	@Before
	public void setUp() throws InterruptedException, ExecutionException, TimeoutException {
		VirtualFileClasspath metaFile = new VirtualFileClasspath("httpsMeta.txt", WebserverForTest.class.getClassLoader());
		WebserverForTest webserver = new WebserverForTest(platformOverrides, null, false, metaFile);
		webserver.start();
		httpSocket = createHttpSocket(webserver.getUnderlyingHttpChannel().getLocalAddress());
		httpsSocket = createHttpsSocket(null, webserver.getUnderlyingHttpsChannel().getLocalAddress());		
	}

	@Test
	public void testSecureLoginHasHttpsPage() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secure/internal"); 
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		//before we can show you the page, you need to be logged in, redirect to login page...
		response.assertStatusCode(KnownStatusCode.HTTP_303_SEEOTHER);
	}
	
	@Test
	public void testBasicPageOverHttps() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secureRoute");
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("home page");
	}
	
	@Test
	public void testAccessHttpsPageOverHttp() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secureRoute");
		
		httpSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpSocket);
		//Even though the page exists....if accessed over http, it does not exist...
		response.assertStatusCode(KnownStatusCode.HTTP_404_NOTFOUND);
	}
	
	@Test
	public void testSameRouteHttpAndHttpsWrongOrder() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/same"); 
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Http Route"); //notice the Https Route page is not shown		

		httpSocket.send(req);
		
		response = ResponseExtract.assertSingleResponse(httpSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Http Route"); //notice the Https Route page is not shown	
	}

	@Test
	public void testSameRouteHttpAndHttpsCorrectOrder() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/same2");
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Https Route");

		httpSocket.send(req);
		
		response = ResponseExtract.assertSingleResponse(httpSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Http Route");
	}
	
	@Test
	public void testUseHttpButGoThroughLoginFilter() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secure/randomPage");
		
		httpSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpSocket);
		//Even though the page exists....if accessed over http, it does not exist...
		response.assertStatusCode(KnownStatusCode.HTTP_404_NOTFOUND);		
	}
	
	@Test
	public void testSecureLoginNotFoundHttpsPage() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secure/notFoundPage");
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		//Even though the page doesn't exist, we redirect all /secure/* to login page
		response.assertStatusCode(KnownStatusCode.HTTP_303_SEEOTHER);
	}
	
	@Test
	public void testSecureAndLoggedInAlready() {
		Header cookie = simulateLogin();
		
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/secure/internal"); 
		req.addHeader(cookie);
		
		httpsSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpsSocket);
		//before we can show you the page, you need to be logged in, redirect to login page...
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("This is some home page");
	}

	@Test
	public void testReverseUrlLookupOnHttpPageForHttpsUrl() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/same");
		
		httpSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("https://myhost.com:8443"); //notice the Https Route page is not shown	
	}

	@Test
	public void testReverseUrlLookupOnHttpPageForHttpsUrl8443() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/same", 8080);
		
		httpSocket.send(req);
		
		FullResponse response = ResponseExtract.assertSingleResponse(httpSocket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("https://myhost.com:8443"); //notice the Https Route page is not shown	
	}
	
	private Header simulateLogin() {
		HttpRequest req1 = Requests.createRequest(KnownHttpMethod.POST, "/postLogin");
		
		httpsSocket.send(req1);
		
		FullResponse response1 = ResponseExtract.assertSingleResponse(httpsSocket);
		Header header = response1.getResponse().getHeaderLookupStruct().getHeader(KnownHeaderName.SET_COOKIE);
		String value = header.getValue();
		value = value.replace("; path=/; HttpOnly", "");
		Header cookie = new Header(KnownHeaderName.COOKIE, value);
		
		return cookie;
	}
}

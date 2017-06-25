package org.webpieces.webserver.tags;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.webpieces.httpclient11.api.HttpFullRequest;
import org.webpieces.httpclient11.api.HttpSocket;
import org.webpieces.httpparser.api.dto.KnownHttpMethod;
import org.webpieces.httpparser.api.dto.KnownStatusCode;
import org.webpieces.util.file.VirtualFileClasspath;
import org.webpieces.webserver.Requests;
import org.webpieces.webserver.ResponseExtract;
import org.webpieces.webserver.WebserverForTest;
import org.webpieces.webserver.api.TagOverridesModule;
import org.webpieces.webserver.test.AbstractWebpiecesTest;
import org.webpieces.webserver.test.FullResponse;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class TestIncludeTypeTags extends AbstractWebpiecesTest {

	
	private HttpSocket http11Socket;
	
	
	@Before
	public void setUp() throws InterruptedException, ExecutionException, TimeoutException {
		Module allOverrides = Modules.combine(platformOverrides, new TagOverridesModule(TagOverrideLookupForTesting.class));
		VirtualFileClasspath metaFile = new VirtualFileClasspath("tagsMeta.txt", WebserverForTest.class.getClassLoader());
		WebserverForTest webserver = new WebserverForTest(allOverrides, null, false, metaFile);
		webserver.start();
		http11Socket = createHttpSocket(webserver.getUnderlyingHttpChannel().getLocalAddress());
	}

	@Test
	public void testCustomTag() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/customtag");
		
		http11Socket.send(req);
		
        FullResponse response = ResponseExtract.assertSingleResponse(http11Socket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Page Using Custom Tag");
		response.assertContains("This is a custom tag which can also use tags in itself <a href=`/verbatim`>Some Link Here</a>".replace('`', '"'));
		response.assertContains("The user is override and Dean Hiller tag argument cool"); //using variable in custom tag
		response.assertContains("After Custom Tag");
		response.assertContains("supertemplate BEGIN");
		response.assertContains("supertemplate END");
	}
	
	@Test
	public void testRenderTagArgsTag() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/renderTagArgs");
		
		http11Socket.send(req);
		
        FullResponse response = ResponseExtract.assertSingleResponse(http11Socket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Page Using renderTagArgs Tag");
		response.assertContains("The user is override"); //using variable from tag args in the called template
		response.assertContains("After renderTagArgs Tag");
	}
	
	@Test
	public void testRenderPageArgsTag() {
		HttpFullRequest req = Requests.createRequest(KnownHttpMethod.GET, "/renderPageArgs");
		
		http11Socket.send(req);
		
        FullResponse response = ResponseExtract.assertSingleResponse(http11Socket);
		response.assertStatusCode(KnownStatusCode.HTTP_200_OK);
		response.assertContains("Page Using renderPageArgs Tag");
		response.assertContains("The user is Dean Hiller"); //using variable from page args in the called template
		response.assertContains("After renderPageArgs Tag");
	}
}

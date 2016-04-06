package org.webpieces.httpproxy.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.handlers.ConnectionListener;
import org.webpieces.nio.api.handlers.DataChunk;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.nio.impl.util.DataChunkImpl;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.webpieces.httpparser.api.HttpParser;
import com.webpieces.httpparser.api.HttpParserFactory;
import com.webpieces.httpparser.api.dto.HttpRequest;

public class TestHttpProxy {
	
	private HttpParser parser = HttpParserFactory.createParser();
	private MockChannelManager mockChannelMgr = new MockChannelManager();
	private MockTcpChannel mockTcpChannel = new MockTcpChannel();
	private DataListener dataListener;
	
	@Before
	public void setUp() throws IOException {
		Map<String, Object> props = new HashMap<>();
		props.put(HttpProxyFactory.OVERRIDE_MODULE, new TestModule());
		HttpProxy proxy = HttpProxyFactory.createHttpProxy("myproxy", props );
		proxy.start();

		List<ConnectionListener> serverListeners = mockChannelMgr.getServerListeners();
		ConnectionListener connectionListener = serverListeners.get(0);
		
		connectionListener.connected(mockTcpChannel);
		
		dataListener = mockTcpChannel.getDataListener();
	}
	
	@Test
	public void testBasicProxy() throws IOException {
		HttpRequest req = new HttpRequest();
		byte[] array = parser.marshalToBytes(req);
		
		ByteBuffer buffer = ByteBuffer.wrap(array);
		DataChunk chunk = new MyDataChunk(buffer); 
		dataListener.incomingData(mockTcpChannel, chunk);
	}
	
	private class TestModule implements Module {

		@Override
		public void configure(Binder binder) {
			binder.bind(ChannelManager.class).toInstance(mockChannelMgr);
		}
	}
}

package org.webpieces.http2client;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.http2client.api.Http2Client;
import org.webpieces.http2client.api.Http2ClientFactory;
import org.webpieces.http2client.api.Http2Socket;
import org.webpieces.http2client.mock.MockChanMgr;
import org.webpieces.http2client.mock.MockHttp2Channel;
import org.webpieces.util.threading.DirectExecutor;

import com.webpieces.http2engine.api.client.Http2Config;
import com.webpieces.http2engine.impl.shared.HeaderSettings;
import com.webpieces.http2parser.api.dto.GoAwayFrame;

/**
 * Test this section of rfc..
 * http://httpwg.org/specs/rfc7540.html#SETTINGS
 */
public class Test6_5SettingsFrameErrors {

	private MockChanMgr mockChanMgr;
	private MockHttp2Channel mockChannel;
	private Http2Socket socket;
	private HeaderSettings localSettings = Requests.createSomeSettings();

	@Before
	public void setUp() throws InterruptedException, ExecutionException {
		
        mockChanMgr = new MockChanMgr();
        mockChannel = new MockHttp2Channel();
        mockChannel.setIncomingFrameDefaultReturnValue(CompletableFuture.completedFuture(mockChannel));

        Http2Config config = new Http2Config();
        config.setInitialRemoteMaxConcurrent(1); //start with 1 max concurrent
        config.setLocalSettings(localSettings);
        Http2Client client = Http2ClientFactory.createHttpClient(config, mockChanMgr, new DirectExecutor());
        
        mockChanMgr.addTCPChannelToReturn(mockChannel);
		socket = client.createHttpSocket("simple");
		
		CompletableFuture<Http2Socket> connect = socket.connect(new InetSocketAddress(555));
		Assert.assertTrue(connect.isDone());
		Assert.assertEquals(socket, connect.get());

		//clear preface and settings frame from client
		mockChannel.getFramesAndClear();
	}
	
	@Ignore
	@Test
	public void testSection6_5_3SettingsAckNotReceivedInReasonableTime() {
	}
	
	@Test
	public void testSection6_5AckNonEmptyPayload() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
		HeaderSettings settings = new HeaderSettings();
		settings.setMaxConcurrentStreams(1L);
		mockChannel.write(HeaderSettings.createSettingsFrame(settings));
		mockChannel.getFrameAndClear(); //clear the ack frame 
		
	    String badAckFrame =
	            "00 00 01" + // length
	            "04" +  // type
	            "01" + // flags (ack)
	            "00 00 00 00" + // R + streamid
	            "00"; //payload 
		mockChannel.writeHexBack(badAckFrame); //ack client frame

		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		DataWrapper debugData = goAway.getDebugData();
		String msg = debugData.createStringFromUtf8(0, debugData.getReadableSize());
		Assert.assertEquals("size of payload of a settings frame ack must be 0 but was=1 reason=FRAME_SIZE_INCORRECT stream=0", msg);
		Assert.assertTrue(mockChannel.isClosed());
	}

	@Test
	public void testSection6_5SettingsStreamIdNonZeroValue() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
	    String badStreamIdSettings =
	            "00 00 0C" + // length
	            "04" +  // type
	            "00" + //flags
	            "00 00 00 01" + //R + streamid
	            "00 02 00 00 00 01" + //setting 1 (enable push)
	            "00 03 00 00 01 00"; //setting 2 (max streams)
	    
		mockChannel.writeHexBack(badStreamIdSettings);
		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		
		Assert.assertTrue(mockChannel.isClosed());
	}
	
	@Test
	public void testSection6_5SettingsFrameLengthMultipleNotSixOctects() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
	    String badStreamIdSettings =
	            "00 00 0B" + // length
	            "04" +  // type
	            "00" + //flags
	            "00 00 00 00" + //R + streamid
	            "00 02 00 00 00 01" + //setting 1 (enable push)
	            "00 03 00 00 01"; //setting 2 (max streams)
	    
		mockChannel.writeHexBack(badStreamIdSettings);
		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		
		Assert.assertTrue(mockChannel.isClosed());
	}
	
	@Test
	public void testSection6_5_2PushPromiseOffButServerSentIt() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
	    String badStreamIdSettings =
	            "00 00 0C" + // length
	            "04" +  // type
	            "00" + //flags
	            "00 00 00 00" + //R + streamid
	            "00 02 00 00 00 04" + //setting 1 (enable push)
	            "00 03 00 00 01 00"; //setting 2 (max streams)
	    
		mockChannel.writeHexBack(badStreamIdSettings);
		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		
		Assert.assertTrue(mockChannel.isClosed());
	}
	
	@Test
	public void testSection6_5_2InitialWindowSizeTooLarge() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
	    String badStreamIdSettings =
	            "00 00 0C" + // length
	            "04" +  // type
	            "00" + //flags
	            "00 00 00 00" + //R + streamid
	            "00 02 00 00 00 01" + //setting 1 (enable push)
	            "00 04 FF FF FF FF"; //setting 2 (initial window size)
	    
		mockChannel.writeHexBack(badStreamIdSettings);
		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		
		Assert.assertTrue(mockChannel.isClosed());
	}
	
	@Test
	public void testSection6_5_2MaxFrameSizeOutsideAllowedRange() {
		//server's settings frame is finally coming in as well with maxConcurrent=1
	    String badStreamIdSettings =
	            "00 00 0C" + // length
	            "04" +  // type
	            "00" + //flags
	            "00 00 00 00" + //R + streamid
	            "00 02 00 00 00 01" + //setting 1 (enable push)
	            "00 05 00 00 00 00"; //setting 2 (initial window size)
	    
		mockChannel.writeHexBack(badStreamIdSettings);
		//remote receives goAway
		GoAwayFrame goAway = (GoAwayFrame) mockChannel.getFrameAndClear();
		
		Assert.assertTrue(mockChannel.isClosed());
	}
	
}
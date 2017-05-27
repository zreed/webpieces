package org.webpieces.http2client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.webpieces.http2client.mock.MockResponseListener;
import org.webpieces.http2client.mock.MockStreamWriter;
import org.webpieces.http2client.util.RequestHolder;
import org.webpieces.http2client.util.Requests;
import org.webpieces.http2client.util.RequestsSent;

import com.webpieces.hpack.api.dto.Http2Headers;
import com.webpieces.hpack.api.dto.Http2Request;
import com.webpieces.http2engine.api.StreamWriter;
import com.webpieces.http2engine.impl.shared.data.HeaderSettings;
import com.webpieces.http2parser.api.dto.DataFrame;
import com.webpieces.http2parser.api.dto.SettingsFrame;
import com.webpieces.http2parser.api.dto.lib.Http2Msg;
import com.webpieces.http2parser.api.dto.lib.PartialStream;

public class TestCMaxConcurrentSetting extends AbstractTest {
	
	@Test
	public void testSend2ndRequestOnlyOnCompletionOfFirst() throws InterruptedException, ExecutionException {
		RequestsSent sent = sendTwoRequests();

		int streamId1 = sent.getRequest1().getRequest().getStreamId();
		CompletableFuture<StreamWriter> future2 = sent.getRequest2().getFuture();
		RequestHolder holder1 = sent.getRequest1();
		RequestHolder holder2 = sent.getRequest2();
		
		MockResponseListener listener1 = holder1.getListener();
		MockStreamWriter writer1 = holder1.getWriter();
		mockChannel.write(Requests.createResponse(streamId1)); //endOfStream=false
		listener1.getSingleReturnValueIncomingResponse();
				
		mockChannel.write(new DataFrame(streamId1, false)); //endOfStream=false
		writer1.getSingleFrame();
		
		//at this point, should not have a call outstanding
		mockChannel.assertNoIncomingMessages();
		Assert.assertFalse(future2.isDone());
		
		MockResponseListener listener2 = holder2.getListener();
		MockStreamWriter writer2 = holder2.getWriter();
		listener2.addReturnValueIncomingResponse(CompletableFuture.completedFuture(writer2));
		
		Assert.assertFalse(future2.isDone());
		DataFrame dataFrame = new DataFrame(streamId1, true);
		mockChannel.write(dataFrame);//endOfStream = true
		
		PartialStream data = writer1.getSingleFrame();
		Assert.assertEquals(dataFrame.getStreamId(), data.getStreamId());
		
		Http2Headers frame = (Http2Headers) mockChannel.getFrameAndClear();
		Assert.assertEquals(sent.getRequest2().getRequest(), frame);
		Assert.assertTrue(future2.isDone());
	}
	
	@Test
	public void testSend2ndRequestOnlyOnAfterSettingsFrameMaxConcurrentBigger() throws InterruptedException, ExecutionException {
		RequestsSent sent = sendTwoRequests();

		Assert.assertFalse(sent.getRequest2().getFuture().isDone());

		//server's settings frame is finally coming in as well with maxConcurrent=1
		HeaderSettings settings = new HeaderSettings();
		settings.setMaxConcurrentStreams(2L);
		mockChannel.write(HeaderSettings.createSettingsFrame(settings));
		mockChannel.write(new SettingsFrame(true)); //ack client frame
		List<Http2Msg> msgs = mockChannel.getFramesAndClear();

		Assert.assertEquals(sent.getRequest2().getRequest(), msgs.get(0));
		Assert.assertTrue(sent.getRequest2().getFuture().isDone());
		
		SettingsFrame ack = (SettingsFrame) msgs.get(1);
		Assert.assertEquals(true, ack.isAck());
	}
	
	private RequestsSent sendTwoRequests() {
		Http2Request request1 = Requests.createRequest();
		Http2Request request2 = Requests.createRequest();
		MockStreamWriter writer1 = new MockStreamWriter();
		MockStreamWriter writer2 = new MockStreamWriter();
		MockResponseListener listener1 = new MockResponseListener();
		listener1.setIncomingRespDefault(CompletableFuture.completedFuture(writer1));

		//do not set default incoming response as we want to delay the resolution of the future
		MockResponseListener listener2 = new MockResponseListener();

		CompletableFuture<StreamWriter> future = httpSocket.openStream(listener1).process(request1);
		CompletableFuture<StreamWriter> future2 = httpSocket.openStream(listener2).process(request2);
		
		RequestHolder r1 = new RequestHolder(request1, listener1, writer1, future);
		RequestHolder r2 = new RequestHolder(request2, listener2, writer2, future2);		
		RequestsSent requests = new RequestsSent(r1, r2);
		
		Http2Msg req = mockChannel.getFrameAndClear();
		Assert.assertEquals(1, req.getStreamId());
		Assert.assertEquals(request1, req);
		
		Assert.assertTrue(future.isDone());
		Assert.assertFalse(future2.isDone());
		return requests;
	}

}

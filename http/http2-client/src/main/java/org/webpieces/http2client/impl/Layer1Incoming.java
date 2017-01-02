package org.webpieces.http2client.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.http2client.api.Http2ResponseListener;
import org.webpieces.http2client.api.Http2SocketDataWriter;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.http2engine.api.Http2ClientEngine;
import com.webpieces.http2engine.api.dto.Http2Data;
import com.webpieces.http2engine.api.dto.Http2Headers;

public class Layer1Incoming implements DataListener {

	private static final Logger log = LoggerFactory.getLogger(Layer1Incoming.class);
	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	private Http2ClientEngine layer2;
	private int nextAvailableStreamId = 1;
	private Layer5Outgoing outgoing;

	public Layer1Incoming(Http2ClientEngine layer2, Layer5Outgoing outgoing) {
		this.layer2 = layer2;
		this.outgoing = outgoing;
	}

	public CompletableFuture<Void> sendInitialFrames() {
		return layer2.sendInitializationToSocket();
	}
	
	public CompletableFuture<Http2SocketDataWriter> sendRequest(Http2Headers request, Http2ResponseListener listener,
			boolean isComplete) {
		int streamId = getNextAvailableStreamId();
		
		outgoing.addResponseListener(streamId, listener);
		
		Http2SocketDataWriter writer;
		if(isComplete)
			writer = new NullWriter(streamId);
		else
			writer = new Writer(streamId);

		return layer2.sendFrameToSocket(request)
					.thenApply(c -> writer);
	}
	
	private class NullWriter implements Http2SocketDataWriter {
		protected int streamId;
		public NullWriter(int streamId) {
			this.streamId = streamId;
		}
		@Override
		public CompletableFuture<Http2SocketDataWriter> sendData(DataWrapper data, boolean isComplete) {
			throw new IllegalStateException("Client called sendRequest(..., ..., true) meaning that was supposed to be the complete request");
		}
		@Override
		public CompletableFuture<Http2SocketDataWriter> sendTrailingHeaders(Http2Headers endHeaders) {
			throw new IllegalStateException("Client called sendRequest(..., ..., true) meaning that was supposed to be the complete request");
		}
		@Override
		public final void cancel() {
			layer2.cancel(streamId);
		}
	}
	
	private class Writer extends NullWriter {
		public Writer(int streamId) {
			super(streamId);
		}

		@Override
		public CompletableFuture<Http2SocketDataWriter> sendData(DataWrapper payload, boolean isComplete) {
			Http2Data data = new Http2Data();
			data.setStreamId(streamId);
			data.setEndOfStream(isComplete);
			data.setPayload(payload);
			return layer2.sendFrameToSocket(data).thenApply(c -> this);
		}

		@Override
		public CompletableFuture<Http2SocketDataWriter> sendTrailingHeaders(Http2Headers endHeaders) {
			return layer2.sendFrameToSocket(endHeaders).thenApply(c -> this);
		}
	}
	
	private synchronized int getNextAvailableStreamId() {
		int temp = nextAvailableStreamId;
		nextAvailableStreamId += 2;
		return temp;
	}

	@Override
	public void incomingData(Channel channel, ByteBuffer b) {
		log.info("incoming data. size="+b.remaining());
		DataWrapper data = dataGen.wrapByteBuffer(b);
		layer2.parse(data);
	}

	@Override
	public void farEndClosed(Channel channel) {
		layer2.closeEngine();
	}

	@Override
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.warn("failure", e);
	}

	@Override
	public void applyBackPressure(Channel channel) {
		log.info("apply back pressure");
	}

	@Override
	public void releaseBackPressure(Channel channel) {
		log.info("apply back pressure");
	}

}

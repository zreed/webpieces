package com.webpieces.http2engine.impl.client;

import org.webpieces.javasm.api.Memento;

import com.webpieces.http2.api.streaming.PushPromiseListener;
import com.webpieces.http2.api.streaming.ResponseStreamHandle;
import com.webpieces.http2.api.streaming.StreamWriter;
import com.webpieces.http2engine.impl.shared.data.Stream;

public class ClientStream extends Stream {

	private ResponseStreamHandle responseListener;
	private StreamWriter responseWriter;
	private PushPromiseListener pushListener;

	public ClientStream(String logId, int streamId, Memento currentState, ResponseStreamHandle responseListener,
			long localInitialWindowSize, long remoteInitialWindowSize) {
		super(logId, streamId, currentState, localInitialWindowSize, remoteInitialWindowSize, true);
		this.responseListener = responseListener;
	}

	public ResponseStreamHandle getResponseListener() {
		return responseListener;
	}
	
	public void setResponseWriter(StreamWriter w) {
		responseWriter = w;
	}

	public void setPushListener(PushPromiseListener pushListener) {
		this.pushListener = pushListener;
	}

	public StreamWriter getResponseWriter() {
		return responseWriter;
	}
	
}

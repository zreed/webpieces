package org.webpieces.httpclient.impl;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.SSLEngine;
import javax.xml.bind.DatatypeConverter;

import com.webpieces.http2parser.api.Http2Parser;
import com.webpieces.http2parser.api.ParserResult;
import com.webpieces.http2parser.api.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.httpclient.api.CloseListener;
import org.webpieces.httpclient.api.HttpSocket;
import org.webpieces.httpclient.api.HttpsSslEngineFactory;
import org.webpieces.httpclient.api.ResponseListener;
import org.webpieces.httpparser.api.HttpParser;
import org.webpieces.httpparser.api.Memento;
import org.webpieces.httpparser.api.common.Header;
import org.webpieces.httpparser.api.common.KnownHeaderName;
import org.webpieces.httpparser.api.dto.*;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.exceptions.NioClosedChannelException;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.nio.api.handlers.RecordingDataListener;

import static com.webpieces.http2parser.api.dto.Http2FrameType.SETTINGS;
import static com.webpieces.http2parser.api.dto.Http2Settings.Parameter.*;
import static org.webpieces.httpclient.impl.HttpSocketImpl.Protocol.HTTP11;
import static org.webpieces.httpclient.impl.HttpSocketImpl.Protocol.HTTP2;
import static org.webpieces.httpclient.impl.Stream.StreamStatus.*;

public class HttpSocketImpl implements HttpSocket, Closeable {

	private static final Logger log = LoggerFactory.getLogger(HttpSocketImpl.class);
	private static DataWrapperGenerator wrapperGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	
	private TCPChannel channel;

	private CompletableFuture<HttpSocket> connectFuture;
	private boolean isClosed;
	private boolean connected;

	enum Protocol { HTTP11, HTTP2 };
	private Protocol protocol = HTTP11;

	private ProxyDataListener dataListener;
	private CloseListener closeListener;
	private HttpsSslEngineFactory factory;
	private ChannelManager mgr;
	private String idForLogging;
	private boolean isRecording = false;

	// HTTP 2
	private Http2Parser http2Parser;
	private boolean tryHttp2 = true;
	private Map<Http2Settings.Parameter, Integer> localPreferredSettings = new HashMap<>();

	// TODO: Initialize these two with the protocol defaults
	private ConcurrentHashMap<Http2Settings.Parameter, Integer> remoteSettings = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Http2Settings.Parameter, Integer> localSettings = new ConcurrentHashMap<>();

	private ConcurrentHashMap<Integer, Stream> activeStreams = new ConcurrentHashMap<>();

	// start with streamId 3 because 1 might be used for the upgrade stream.
	private int nextStreamId = 0x3;

	// TODO: figure out how to deal with the goaway. For now we're just
	// going to record what they told us.
	private boolean remoteGoneAway = false;
	private int goneAwayLastStreamId;
	private Http2ErrorCode goneAwayErrorCode;
	private DataWrapper additionalDebugData;

	// HTTP 1.1
	private HttpParser httpParser;
	private ConcurrentLinkedQueue<PendingRequest> pendingRequests = new ConcurrentLinkedQueue<>();
	private ConcurrentLinkedQueue<ResponseListener> responsesToComplete = new ConcurrentLinkedQueue<>();



	public HttpSocketImpl(ChannelManager mgr, String idForLogging, HttpsSslEngineFactory factory, HttpParser parser2,
						  Http2Parser http2Parser,
			CloseListener listener) {
		this.factory = factory;
		this.mgr = mgr;
		this.idForLogging = idForLogging;
		this.httpParser = parser2;
		this.http2Parser = http2Parser;
		this.closeListener = listener;
		this.dataListener = new ProxyDataListener();

		// Initialize to defaults
		remoteSettings.put(SETTINGS_HEADER_TABLE_SIZE, 4096);
		localSettings.put(SETTINGS_HEADER_TABLE_SIZE, 4096);

		remoteSettings.put(SETTINGS_ENABLE_PUSH, 1);
		localSettings.put(SETTINGS_ENABLE_PUSH, 1);

		// No limit for MAX_CONCURRENT_STREAMS by default so it isn't in the map

		remoteSettings.put(SETTINGS_INITIAL_WINDOW_SIZE, 65535);
		localSettings.put(SETTINGS_INITIAL_WINDOW_SIZE, 65535);

		remoteSettings.put(SETTINGS_MAX_FRAME_SIZE, 16384);
		localSettings.put(SETTINGS_MAX_FRAME_SIZE, 16384);

		// No limit for MAX_HEADER_LIST_SIZE by default, so not in the map
	}

	@Override
	public CompletableFuture<HttpSocket> connect(InetSocketAddress addr) {
		if(factory == null) {
			channel = mgr.createTCPChannel(idForLogging);
		} else {
			SSLEngine engine = factory.createSslEngine(addr.getHostName(), addr.getPort());
			channel = mgr.createTCPChannel(idForLogging, engine);
		}
		DataListener dataListenerToUse;

		if(isRecording) {
			dataListenerToUse = new RecordingDataListener("httpSock-", dataListener);
		} else {
			dataListenerToUse = dataListener;
		}
		
		connectFuture = channel.connect(addr, dataListenerToUse).thenCompose(channel -> {
			if(tryHttp2) {
				return negotiateHttpVersion(addr);
			}
			else {
				return CompletableFuture.completedFuture(connected());
			}
		});
		return connectFuture;
	}

	private void sendHttp2Preface() {
		String prefaceString = "505249202a20485454502f322e300d0a0d0a534d0d0a0d0a";
		channel.write(ByteBuffer.wrap(DatatypeConverter.parseHexBinary(prefaceString)));
		Http2Settings settingsFrame = new Http2Settings();

		settingsFrame.setSettings(localPreferredSettings);
		channel.write(ByteBuffer.wrap(http2Parser.marshal(settingsFrame).createByteArray()));
	}

	private CompletableFuture<HttpSocket> negotiateHttpVersion(InetSocketAddress addr) {
		// First check if ALPN says HTTP2, in which case, set the protocol to HTTP2 and we're done
		if (false) { // alpn says HTTP2 or we have some other prior knowledge
			protocol = HTTP2;
			dataListener.setProtocol(HTTP2);
			sendHttp2Preface();

			return CompletableFuture.completedFuture(connected());

		} else { // Try the HTTP1.1 upgrade technique
			HttpRequest upgradeRequest = new HttpRequest();
			HttpRequestLine requestLine = new HttpRequestLine();

			// TODO: switch this back to HEAD
			// HEAD doesn't work when connecting to a chunking server
			// because the parser stays in chunked mode because there was no final chunk
			// We have to fix the parser so that HEAD responses that have no chunks don't
			// leave the parser in chunking mode.
			requestLine.setMethod(KnownHttpMethod.GET);
			requestLine.setUri(new HttpUri("/"));
			requestLine.setVersion(new HttpVersion());
			upgradeRequest.setRequestLine(requestLine);
			upgradeRequest.addHeader(new Header("Connection", "Upgrade, HTTP2-Settings"));
			upgradeRequest.addHeader(new Header("Upgrade", "h2c"));

			Http2Settings settingsFrame = new Http2Settings();
			settingsFrame.setSettings(localPreferredSettings);
			upgradeRequest.addHeader(new Header("HTTP2-Settings",
					Base64.getEncoder().encodeToString(http2Parser.marshal(settingsFrame).createByteArray())));

			CompletableFuture<HttpResponse> response = sendIgnoreConnected(upgradeRequest);
			return response.thenCompose(r -> {
				if(r.getStatusLine().getStatus().getCode() != 101) {
					// That didn't work, let's not try http2 and try again
					tryHttp2 = false;

					// If the response was not chunked, then we're going to assume
					// that the connection will be closed and we have to reconnect without
					// HTTP2. If the response was chunked, then we can just say we're connected
					// and proceed with HTTP1.1.
					// TODO: Find a way to actually just see if the connection has been closed (or is about to be?) or not.
					Header transferEncodingHeader = r.getHeaderLookupStruct().getHeader(KnownHeaderName.TRANSFER_ENCODING);
					if(transferEncodingHeader != null && transferEncodingHeader.getValue().equals("chunked"))
						return CompletableFuture.completedFuture(connected());
					else
						return connect(addr);
				} else {
					protocol = HTTP2;
					dataListener.setProtocol(HTTP2);
					sendHttp2Preface();

					return CompletableFuture.completedFuture(connected());
				}
			});
		}
	}

	private CompletableFuture<HttpResponse> sendIgnoreConnected(HttpRequest request) {
		CompletableFuture<HttpResponse> future = new CompletableFuture<HttpResponse>();
		ResponseListener l = new CompletableListener(future, true);
		actuallySendRequest(request, l);
		return future;
	}

	@Override
	public CompletableFuture<HttpResponse> send(HttpRequest request) {
		CompletableFuture<HttpResponse> future = new CompletableFuture<HttpResponse>();
		ResponseListener l = new CompletableListener(future);
		send(request, l);
		return future;
	}
	
	private synchronized HttpSocket connected() {
		connected = true;
		
		while(!pendingRequests.isEmpty()) {
			PendingRequest req = pendingRequests.remove();
			actuallySendRequest(req.getRequest(), req.getListener());
		}
		
		return this;
	}

	@Override
	public void send(HttpRequest request, ResponseListener listener) {
		if(connectFuture == null) 
			throw new IllegalArgumentException("You must at least call httpSocket.connect first(it "
					+ "doesn't have to complete...you just have to call it before caling send)");

		boolean wasConnected = false;
		synchronized (this) {
			if(!connected) {
				pendingRequests.add(new PendingRequest(request, listener));
			} else
				wasConnected = true;
		}
		
		if(wasConnected) 
			actuallySendRequest(request, listener);
	}

	private Map<String, String> requestToHeaders(HttpRequest request) {
		HttpRequestLine requestLine = request.getRequestLine();
		List<Header> requestHeaders = request.getHeaders();

		Map<String, String> headerMap = new HashMap<>();

		// Add regular headers
		for(Header header: requestHeaders) {
			headerMap.put(header.getName().toLowerCase(), header.getValue());
		}

		// add special headers
		headerMap.put(":method", requestLine.getMethod().getMethodAsString());

		UrlInfo urlInfo = requestLine.getUri().getUriBreakdown();
		headerMap.put(":scheme", urlInfo.getPrefix());
		if(urlInfo.getPort() == null)
			headerMap.put(":authority", urlInfo.getHost());
		else
			headerMap.put(":authority", String.format("{}:{}", urlInfo.getHost(), urlInfo.getPort()));

		headerMap.put(":path", urlInfo.getFullPath());

		return headerMap;
	}

	private CompletableFuture<Channel> sendDataFrames(DataWrapper body, int streamId, Stream stream) {
		Http2Data newFrame = new Http2Data();
		newFrame.setStreamId(streamId);

		// writes only one frame at a time
		if(body.getReadableSize() <= remoteSettings.get(SETTINGS_MAX_FRAME_SIZE)) {
			newFrame.setData(body);
			newFrame.setEndStream(true);
			return channel.write(ByteBuffer.wrap(http2Parser.marshal(newFrame).createByteArray())).thenApply(
					channel -> {
						stream.setStatus(HALF_CLOSED_LOCAL);
						return channel;
					}
			);
		} else {
			List<? extends DataWrapper> split = wrapperGen.split(body, remoteSettings.get(SETTINGS_MAX_FRAME_SIZE));
			newFrame.setData(split.get(0));
			return channel.write(ByteBuffer.wrap(http2Parser.marshal(newFrame).createByteArray())).thenCompose(
					channel ->  sendDataFrames(split.get(1), streamId, stream)
			);
		}
	}

	// we never send endstream on the header frame to make our life easier. we always just send
	// endstream on a data frame.
	private CompletableFuture<Channel> sendHeaderFrames(Map<String, String> headerMap, int streamId, Stream stream, boolean firstFrame) {
		// Assume for now we can send all the headers in one frame
		// we're going to have to create a 'hasHeaders' interface so we can
		// abstract between Headers and Continuation frames here
		// if firstFrame == true then create Http2Headers, otherwise create Http2Continuation
		Http2Headers newFrame = new Http2Headers();
		newFrame.setStreamId(streamId);
		ByteBuffer byteBuffer;

		// If it all fits into one frame
		if(true) {
			newFrame.setEndHeaders(true);
			newFrame.setHeaders(headerMap);
			return channel.write(ByteBuffer.wrap(http2Parser.marshal(newFrame).createByteArray())).thenApply(
					channel ->
					{
						stream.setStatus(OPEN);
						return channel;
					}
			);
		} else {
			// figure out how to split up the headermap into what can fit and what can't.
			newFrame.setHeaders(headerMap);
			return channel.write(ByteBuffer.wrap(http2Parser.marshal(newFrame).createByteArray())).thenCompose(
					channel -> sendHeaderFrames(Collections.emptyMap(), streamId, stream, false)
			);
		}
	}

	private void actuallySendRequest(HttpRequest request, ResponseListener listener) {
		ResponseListener l = new CatchResponseListener(listener);

		if(protocol == HTTP11) {
			ByteBuffer wrap = ByteBuffer.wrap(httpParser.marshalToBytes(request));

			//put this on the queue before the write to be completed from the listener below
			responsesToComplete.offer(l);

			log.info("sending request now. req=" + request.getRequestLine().toString());
			CompletableFuture<Channel> write = channel.write(wrap);
			write.exceptionally(e -> fail(l, e));
		} else { // HTTP2
			// Create a stream
			Stream newStream = new Stream();

			// Find a new Stream id
			activeStreams.put(nextStreamId, newStream);
			nextStreamId += 2;

			Map<String, String> headers = requestToHeaders(request);
			sendHeaderFrames(headers, nextStreamId, newStream, true).thenApply(
					channel -> sendDataFrames(request.getBodyNonNull(), nextStreamId, newStream));
			}
	}
	
	private Channel fail(ResponseListener l, Throwable e) {
		l.failure(e);
		return null;
	}

	@Override
	public void close() throws IOException {
		if(isClosed)
			return;
		
		//best effort and ignore exception except log it
		CompletableFuture<HttpSocket> future = closeSocket();
		future.exceptionally(e -> {
			log.info("close failed", e);
			return this;
		});
	}
	
	@Override
	public CompletableFuture<HttpSocket> closeSocket() {
		if(isClosed) {
			return CompletableFuture.completedFuture(this);
		}
		
		cleanUpPendings("You closed the socket");
		
		CompletableFuture<Channel> future = channel.close();
		return future.thenApply(chan -> {
			isClosed = true;
			return this;
		});
	}

	private void cleanUpPendings(String msg) {
		//do we need an isClosing state and cache that future?  (I don't think so but time will tell)
		while(!responsesToComplete.isEmpty()) {
			ResponseListener listener = responsesToComplete.poll();
			if(listener != null) {
				listener.failure(new NioClosedChannelException(msg+" before responses were received"));
			}
		}

		synchronized (this) {
			while(!pendingRequests.isEmpty()) {
				PendingRequest pending = pendingRequests.poll();
				pending.getListener().failure(new NioClosedChannelException(msg+" before requests were sent"));
			}
		}
	}

	private class ProxyDataListener implements DataListener {
		private Protocol protocol = HTTP11;
		private Map<Protocol, DataListener> dataListenerMap = new HashMap<>();

		void setProtocol(Protocol protocol) {
			this.protocol = protocol;
		}

		ProxyDataListener() {
			dataListenerMap.put(HTTP11, new Http11DataListener());
			dataListenerMap.put(HTTP2, new Http2DataListener());
		}

		@Override
		public void incomingData(Channel channel, ByteBuffer b) {
			dataListenerMap.get(protocol).incomingData(channel, b);
		}

		@Override
		public void farEndClosed(Channel channel) {
			dataListenerMap.get(protocol).farEndClosed(channel);
		}

		@Override
		public void failure(Channel channel, ByteBuffer data, Exception e) {
			dataListenerMap.get(protocol).failure(channel, data, e);
		}

		@Override
		public void applyBackPressure(Channel channel) {
			dataListenerMap.get(protocol).applyBackPressure(channel);
		}

		@Override
		public void releaseBackPressure(Channel channel) {
			dataListenerMap.get(protocol).releaseBackPressure(channel);
		}
	}

	private class Http2DataListener implements DataListener {
		private DataWrapper oldData = http2Parser.prepareToParse();
		private boolean gotSettings = false;

		private void updateListener(boolean isComplete, Stream stream) {
			stream.getListener().incomingResponse(stream.getResponse(), stream.getRequest(), isComplete);

			if(isComplete) {
				// Make sure status can accept ES
				switch(stream.getStatus()) {
					case OPEN:
					case HALF_CLOSED_LOCAL:
						stream.setStatus(HALF_CLOSED_REMOTE);

						// Now send ES back
						Http2Data sendFrame = new Http2Data();
						sendFrame.setEndStream(true);

						// Set the stream status to closed after the final ES frame is sent back.
						// we want to keep track somewhere of our window
						channel.write(ByteBuffer.wrap(http2Parser.marshal(sendFrame).createByteArray()))
								.thenAccept(channel -> stream.setStatus(CLOSED));
						break;
					default:
						// throw error here
				}
			}
		}

		private void handleData(Http2Data frame, Stream stream) {
			// Only allowable if stream is open or half closed local
			switch(stream.getStatus()) {
				case OPEN:
				case HALF_CLOSED_LOCAL:
					stream.getResponse().appendBody(frame.getData());
					boolean isComplete = frame.isEndStream();
					updateListener(isComplete, stream);
					break;
				default:
					// Throw
			}
		}

		private HttpResponse createResponseFromHeaders(Map<String, String> headers) {
			HttpResponse response = new HttpResponse();

			// Set special header
			String statusString = headers.get(":status");
			// TODO: throw if no such header

			HttpResponseStatusLine statusLine = new HttpResponseStatusLine();
			HttpResponseStatus status = new HttpResponseStatus();
			status.setCode(Integer.parseInt(statusString));
			// TODO: throw if can't parse

			statusLine.setStatus(status);
			response.setStatusLine(statusLine);

			// Set all other headers
			for(Map.Entry<String, String> entry: headers.entrySet()) {
				if(!entry.getKey().equals(":status"))
					response.addHeader(new Header(entry.getKey(), entry.getValue()));
			}

			return response;
		}

		private HttpRequest createRequestFromHeaders(Map<String, String> headers) {
			HttpRequest request = new HttpRequest();

			// Set special headers
			// TODO: throw if no such headers
			String method = headers.get(":method");
			String scheme = headers.get(":scheme");
			String authority = headers.get(":authority");
			String path = headers.get(":path");

			// See https://svn.tools.ietf.org/svn/wg/httpbis/specs/rfc7230.html#asterisk-form
			if(method.toLowerCase().equals("options") && path.equals("*")) {
				path = "";
			}

			HttpRequestLine requestLine = new HttpRequestLine();
			requestLine.setUri(new HttpUri(String.format("{}://{}{}", scheme, authority, path)));
			requestLine.setMethod(new HttpRequestMethod(method));
			request.setRequestLine(requestLine);

			List<String> specialHeaders = Arrays.asList(":method", ":scheme", ":authority", ":path");

			// Set all other headers
			for(Map.Entry<String, String> entry: headers.entrySet()) {
				if(!specialHeaders.contains(entry.getKey()))
					request.addHeader(new Header(entry.getKey(), entry.getValue()));
			}

			return request;
		}

		private void handleHeaders(Http2Headers frame, Stream stream) {
			switch (stream.getStatus()) {
				case IDLE:
					break;
				default:
					// throw appropriate error
			}

			// start accumulating headers
			stream.setHeaderHeaders(frame.getHeaders());

			if(frame.isEndHeaders()) {
				// If we are done getting headers, create the response
				HttpResponse response = createResponseFromHeaders(frame.getHeaders());
				stream.setResponse(response);
				stream.setStatus(OPEN);
				updateListener(frame.isEndStream(), stream);
			} else {
				stream.setStatus(WAITING_MORE_NORMAL_HEADERS);
			}
		}


		private void handlePriority(Http2Priority frame, Stream stream) {
			// ignore priority for now. priority can be received in any state.

		}

		private void handleRstStream(Http2RstStream frame, Stream stream) {
			switch(stream.getStatus()) {
				case OPEN:
				case HALF_CLOSED_REMOTE:
				case HALF_CLOSED_LOCAL:
				case RESERVED_LOCAL:
				case RESERVED_REMOTE:
				case CLOSED:
				case WAITING_MORE_NORMAL_HEADERS:
				case WAITING_MORE_PUSH_PROMISE_HEADERS:
					// TODO: put the error code in the appropriate exception
					stream.getListener().failure(new RuntimeException("blah"));
					stream.setStatus(CLOSED);
					break;
				default:
					// throw the error here
			}
		}

		private void handlePushPromise(Http2PushPromise frame, Stream stream) {
			// Can get this on any stream id, creates a new stream
			Stream promisedStream = new Stream();
			int newStreamId = frame.getPromisedStreamId();

			// TODO: make sure streamid is valid
			// TODO: close all lower numbered even IDLE streams
			activeStreams.put(newStreamId, promisedStream);
			promisedStream.setPushPromiseHeaders(frame.getHeaders());

			// Uses the same listener as the stream it came in on
			promisedStream.setListener(stream.getListener());

			if(frame.isEndHeaders()) {
				// If we are done getting headers, set the request
				HttpRequest request = createRequestFromHeaders(frame.getHeaders());
				promisedStream.setRequest(request);
				promisedStream.setStatus(RESERVED_REMOTE);
				updateListener(false, stream);
			} else {
				promisedStream.setStatus(WAITING_MORE_NORMAL_HEADERS);
			}
		}

		private void handleContinuation(Http2Continuation frame, Stream stream) {
			Map<String, String> headerMap;

			switch(stream.getStatus()) {
				case WAITING_MORE_PUSH_PROMISE_HEADERS: // after a PUSH_PROMISE
					headerMap = stream.getPushPromiseHeaders();
					break;
				case WAITING_MORE_NORMAL_HEADERS: // after a HEADERS
					headerMap = stream.getHeaderHeaders();
					break;
				default:
					// throw, can't get a continuation here, spit out PROTOCOL_ERROR
					throw new RuntimeException("blah");
			}
			// Add the headers to the msg
			// Set all other headers
			headerMap.putAll(frame.getHeaders());

			if(frame.isEndHeaders()) {
				// If we're done getting headers, add them to the request/response
				stream.setStatus(RESERVED_REMOTE);
				switch(stream.getStatus()) {
					case WAITING_MORE_NORMAL_HEADERS:
						HttpResponse response = createResponseFromHeaders(headerMap);
						stream.setResponse(response);
						break;
					case WAITING_MORE_PUSH_PROMISE_HEADERS:
						HttpRequest request = createRequestFromHeaders(headerMap);
						stream.setRequest(request);
						break;
					default:
						// should throw in the prior switch if this is the case
						throw new RuntimeException("should not happen");
				}

				// Send what we got back to the listener
				updateListener(false, stream);
			}
		}

		private void handleWindowUpdate(Http2WindowUpdate frame, Stream stream) {
			// can get this on any stream id
			stream.setWindowIncrement(frame.getWindowSizeIncrement());
		}

		private void handleSettings(Http2Settings frame) {
			if(frame.isAck()) {
				// we received an ack, so the settings we sent have been accepted.
				for(Map.Entry<Http2Settings.Parameter, Integer> entry: localPreferredSettings.entrySet()) {
					localSettings.put(entry.getKey(), entry.getValue());
				}
			} else {
				// We've received a settings. Update remoteSettings and send an ack
				gotSettings = true;
				for(Map.Entry<Http2Settings.Parameter, Integer> entry: frame.getSettings().entrySet()) {
					remoteSettings.put(entry.getKey(), entry.getValue());
				}
				Http2Settings responseFrame = new Http2Settings();
				responseFrame.setAck(true);
				channel.write(ByteBuffer.wrap(http2Parser.marshal(responseFrame).createByteArray()));
			}
		}

		// TODO: actually deal with this goaway stuff where necessary
		private void handleGoAway(Http2GoAway frame) {
			remoteGoneAway = true;
			goneAwayLastStreamId = frame.getLastStreamId();
			goneAwayErrorCode = frame.getErrorCode();
			additionalDebugData = frame.getDebugData();
		}

		private void handlePing(Http2Ping frame) {
			if(!frame.isPingResponse()) {
				// Send the same frame back, setting ping response
				frame.setIsPingResponse(true);
				channel.write(ByteBuffer.wrap(http2Parser.marshal(frame).createByteArray()));
			} else {
				// measure latency from the ping that was sent. The opaqueData we sent is
				// System.nanoTime() so we just measure the difference
				long latency = System.nanoTime() - frame.getOpaqueData();
				log.info("Ping: %ld ns", latency);
			}
		}

		private void handleFrame(Http2Frame frame) {
			if(frame.getFrameType() != SETTINGS && !gotSettings) {
				// TODO: throw here, must get settings as the first frame from the server
			}

			// Transition the stream state
			if(frame.getStreamId() != 0x0) {
				Stream stream = activeStreams.get(frame.getStreamId());
				// TODO: throw here if we don't have a record of this stream

				switch (frame.getFrameType()) {
					case DATA:
						handleData((Http2Data) frame, stream);
						break;
					case HEADERS:
						handleHeaders((Http2Headers) frame, stream);
						break;
					case PRIORITY:
						handlePriority((Http2Priority) frame, stream);
						break;
					case RST_STREAM:
						handleRstStream((Http2RstStream) frame, stream);
						break;
					case PUSH_PROMISE:
						handlePushPromise((Http2PushPromise) frame, stream);
						break;
					case WINDOW_UPDATE:
						handleWindowUpdate((Http2WindowUpdate) frame, stream);
						break;
					case CONTINUATION:
						handleContinuation((Http2Continuation) frame, stream);
						break;
					default:
						// throw a protocol error
				}
			} else {
				switch (frame.getFrameType()) {
					case SETTINGS:
						handleSettings((Http2Settings) frame);
						break;
					case GOAWAY:
						handleGoAway((Http2GoAway) frame);
						break;
					case PING:
						handlePing((Http2Ping) frame);
						break;
					default:
						// Throw a protocol error
				}
			}
		}

		@Override
		public void incomingData(Channel channel, ByteBuffer b) {
			DataWrapper newData = wrapperGen.wrapByteBuffer(b);
			ParserResult parserResult = http2Parser.parse(oldData, newData);

			for(Http2Frame frame: parserResult.getParsedFrames()) {
				handleFrame(frame);
			}
		}

		@Override
		public void farEndClosed(Channel channel) {
			log.info("far end closed");
			isClosed = true;
			cleanUpPendings("Remote end closed");

			if(closeListener != null)
				closeListener.farEndClosed(HttpSocketImpl.this);
		}

		@Override
		public void failure(Channel channel, ByteBuffer data, Exception e) {
			log.error("Failure on channel="+channel, e);
			while(!responsesToComplete.isEmpty()) {
				ResponseListener listener = responsesToComplete.poll();
				if(listener != null) {
					listener.failure(e);
				}
			}
		}

		@Override
		public void applyBackPressure(Channel channel) {

		}

		@Override
		public void releaseBackPressure(Channel channel) {

		}
	}

	private class Http11DataListener implements DataListener {
		private boolean processingChunked = false;
		private Memento memento = httpParser.prepareToParse();

		@Override
		public void incomingData(Channel channel, ByteBuffer b) {
			log.info("size="+b.remaining());
			DataWrapper wrapper = wrapperGen.wrapByteBuffer(b);
			memento = httpParser.parse(memento, wrapper);

			List<HttpPayload> parsedMessages = memento.getParsedMessages();
			for(HttpPayload msg : parsedMessages) {
				if(processingChunked) {
					HttpChunk chunk = (HttpChunk) msg;
					ResponseListener listener = responsesToComplete.peek();
					if(chunk.isLastChunk()) {
						processingChunked = false;
						responsesToComplete.poll();
					}
					
					listener.incomingChunk(chunk, chunk.isLastChunk());
				} else if(!msg.isHasChunkedTransferHeader()) {
					HttpResponse resp = (HttpResponse) msg;
					ResponseListener listener = responsesToComplete.poll();
					listener.incomingResponse(resp, true);
				} else {
					processingChunked = true;
					HttpResponse resp = (HttpResponse) msg;
					ResponseListener listener = responsesToComplete.peek();
					listener.incomingResponse(resp, false);
				}
			}
		}

		@Override
		public void farEndClosed(Channel channel) {
			log.info("far end closed");
			isClosed = true;
			cleanUpPendings("Remote end closed");
			
			if(closeListener != null)
				closeListener.farEndClosed(HttpSocketImpl.this);
		
		}

		@Override
		public void failure(Channel channel, ByteBuffer data, Exception e) {
			log.error("Failure on channel="+channel, e);
			while(!responsesToComplete.isEmpty()) {
				ResponseListener listener = responsesToComplete.poll();
				if(listener != null) {
					listener.failure(e);
				}
			}			
		}

		@Override
		public void applyBackPressure(Channel channel) {
			
		}

		@Override
		public void releaseBackPressure(Channel channel) {
		}
	}
	
}

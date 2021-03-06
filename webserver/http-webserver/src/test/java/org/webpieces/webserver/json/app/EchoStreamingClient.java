package org.webpieces.webserver.json.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.webpieces.ctx.api.Current;
import org.webpieces.router.impl.proxyout.ProxyStreamHandle;

import com.webpieces.http2.api.dto.highlevel.Http2Request;
import com.webpieces.http2.api.dto.highlevel.Http2Response;
import com.webpieces.http2.api.dto.lowlevel.CancelReason;
import com.webpieces.http2.api.dto.lowlevel.lib.StreamMsg;
import com.webpieces.http2.api.streaming.ResponseStreamHandle;
import com.webpieces.http2.api.streaming.StreamRef;
import com.webpieces.http2.api.streaming.StreamWriter;

public class EchoStreamingClient {

	public StreamRef stream(ResponseStreamHandle handle) {
		ProxyStreamHandle h = (ProxyStreamHandle) handle;
		
		Http2Request req = Current.request().originalRequest;
        Http2Response response = h.createBaseResponse(req, "application/ndjson", 200, "OK");
        try {
			StreamWriter writer = h.process(response).get();
			
			return new ProxyStreamRef(new EchoWriter(writer));
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class EchoWriter implements StreamWriter {

		private StreamWriter writer;

		public EchoWriter(StreamWriter writer) {
			this.writer = writer;
		}

		@Override
		public CompletableFuture<Void> processPiece(StreamMsg data) {
			return writer.processPiece(data);
		}
		
	}
	private static class ProxyStreamRef implements StreamRef {

		private EchoWriter echoWriter;

		public ProxyStreamRef(EchoWriter echoWriter) {
			this.echoWriter = echoWriter;
		}

		@Override
		public CompletableFuture<StreamWriter> getWriter() {
			return CompletableFuture.completedFuture(echoWriter);
		}

		@Override
		public CompletableFuture<Void> cancel(CancelReason reason) {
			return CompletableFuture.completedFuture(null);
		}
	}
}

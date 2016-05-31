package org.webpieces.httpproxy.api;

import com.webpieces.httpparser.api.dto.HttpRequest;
import com.webpieces.httpparser.api.dto.KnownStatusCode;

public interface HttpRequestListener {

	/**
	 * This is the main method that is invoked on every incoming http request on every channel giving
	 * you the channel it came in from
	 * 
	 * @param channel
	 * @param req
	 */
	void processHttpRequests(FrontendSocket channel, HttpRequest req, boolean isHttps);
	
	/**
	 * In the event the client sends a bad unparseable request, OR your HttpRequestListener 
	 * throws an exception, we call this method to pass in the status you 'should' return to
	 * the client as well as the channel to feed that response into
	 * 
	 * @param channel
	 * @param exc
	 * @param http500
	 */
	void sendServerResponse(FrontendSocket channel, Throwable exc, KnownStatusCode status);

	/**
	 * The client closed their channel.
	 * 
	 * @param channel
	 */
	void clientClosedChannel(FrontendSocket channel);

	/**
	 * As you write back to the client, this is called if writes are backing up in which case 
	 * you need to apply back pressure to whatever thing is causing so many writes to the channel
	 * which 'may' be the channel itself in which case you can call channel.unregisterForReads to
	 * stop reading from the socket or you could also close the socket as well.
	 * 
	 * @param channel
	 */
	void applyWriteBackPressure(FrontendSocket channel);

	void releaseBackPressure(FrontendSocket channel);

}

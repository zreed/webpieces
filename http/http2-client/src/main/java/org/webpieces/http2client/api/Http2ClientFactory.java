package org.webpieces.http2client.api;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.webpieces.data.api.BufferCreationPool;
import org.webpieces.http2client.impl.Http2ClientImpl;
import org.webpieces.nio.api.BackpressureConfig;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.ChannelManagerFactory;
import org.webpieces.util.threading.NamedThreadFactory;

import com.webpieces.hpack.api.HpackParser;
import com.webpieces.hpack.api.HpackParserFactory;
import com.webpieces.http2engine.api.client.Http2ClientEngineFactory;
import com.webpieces.http2engine.api.client.Http2Config;
import com.webpieces.http2engine.api.client.InjectionConfig;
import com.webpieces.util.time.Time;
import com.webpieces.util.time.TimeImpl;

public abstract class Http2ClientFactory {

	public static Http2Client createHttpClient(int numThreads) {
		Http2Config config = new Http2Config();
		Executor executor = Executors.newFixedThreadPool(numThreads, new NamedThreadFactory("httpclient"));
		BufferCreationPool pool = new BufferCreationPool();
		HpackParser hpackParser = HpackParserFactory.createParser(pool, false);
		
		ChannelManagerFactory factory = ChannelManagerFactory.createFactory();
		ChannelManager mgr = factory.createMultiThreadedChanMgr("httpClientChanMgr", pool, new BackpressureConfig(), executor);

		InjectionConfig injConfig = new InjectionConfig(hpackParser, new TimeImpl(), config);
		return createHttpClient(mgr, injConfig);
	}
	
	public static Http2Client createHttpClient(Http2Config config, ChannelManager mgr, Executor executor, Time time) {
		BufferCreationPool pool = new BufferCreationPool();
		HpackParser hpackParser = HpackParserFactory.createParser(pool, false);
		
		InjectionConfig injConfig = new InjectionConfig(hpackParser, time, config);

		return createHttpClient(mgr, injConfig);
	}
	
	public static Http2Client createHttpClient(ChannelManager mgr, InjectionConfig injectionConfig) {
		Http2ClientEngineFactory engineFactory = new Http2ClientEngineFactory(injectionConfig);
		return new Http2ClientImpl(mgr, engineFactory );
	}
}

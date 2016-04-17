package org.webpieces.httpproxy.impl;

import javax.inject.Singleton;

import org.webpieces.asyncserver.api.AsyncServerManager;
import org.webpieces.asyncserver.api.AsyncServerMgrFactory;
import org.webpieces.httpproxy.api.HttpProxy;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.ChannelManagerFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.webpieces.httpparser.api.DataWrapperGenerator;
import com.webpieces.httpparser.api.HttpParser;
import com.webpieces.httpparser.api.HttpParserFactory;

public class HttpProxyModule implements Module {

	@Override
	public void configure(Binder binder) {
		binder.bind(HttpProxy.class).to(HttpProxyImpl.class);

		binder.bind(HttpParser.class).toInstance(HttpParserFactory.createParser());
		binder.bind(DataWrapperGenerator.class).toInstance(HttpParserFactory.createDataWrapperGenerator());
	}

	@Provides
	@Singleton
	public ChannelManager providesChannelManager() {
		return ChannelManagerFactory.createChannelManager("id", null);
		//ChannelManagerFactory.createBasicChannelManager("proxyChanMgr");
	}
	
	@Provides
	@Singleton
	public AsyncServerManager providesAsyncServerMgr(ChannelManager mgr) {
		return AsyncServerMgrFactory.createChannelManager(mgr);
	}
	
}
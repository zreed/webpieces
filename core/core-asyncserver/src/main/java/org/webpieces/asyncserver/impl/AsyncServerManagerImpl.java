package org.webpieces.asyncserver.impl;

import org.webpieces.asyncserver.api.AsyncConfig;
import org.webpieces.asyncserver.api.AsyncDataListener;
import org.webpieces.asyncserver.api.AsyncServer;
import org.webpieces.asyncserver.api.AsyncServerManager;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.SSLEngineFactory;
import org.webpieces.nio.api.channels.TCPServerChannel;

import io.micrometer.core.instrument.MeterRegistry;

public class AsyncServerManagerImpl implements AsyncServerManager {

	private ChannelManager channelManager;
	private MeterRegistry metrics;

	public AsyncServerManagerImpl(ChannelManager channelManager, MeterRegistry metrics) {
		this.channelManager = channelManager;
		this.metrics = metrics;
	}
	@Override
	public AsyncServer createTcpServer(AsyncConfig config, AsyncDataListener listener) {
		return createTcpServerImpl(config, listener, null, false);
	}
	
	@Override
	public AsyncServer createTcpServer(
			AsyncConfig config, AsyncDataListener listener, SSLEngineFactory sslFactory) {
		return createTcpServerImpl(config, listener, sslFactory, false);
	}
	
	@Override
	public AsyncServer createUpgradableServer(AsyncConfig config, AsyncDataListener listener, SSLEngineFactory sslFactory) {
		return createTcpServerImpl(config, listener, sslFactory, true);
	}
	
	private AsyncServer createTcpServerImpl(AsyncConfig config,
			AsyncDataListener listener, SSLEngineFactory sslFactory, boolean isUpgradable) {
		if(config.id == null)
			throw new IllegalArgumentException("config.id must not be null");
		
		String id = channelManager.getName()+"."+config.id;
		ConnectedChannels connectedChannels = new ConnectedChannels(id, metrics);
		ProxyDataListener proxyListener = new ProxyDataListener(connectedChannels, listener);
		DefaultConnectionListener connectionListener = new DefaultConnectionListener(id, connectedChannels, proxyListener); 

		TCPServerChannel serverChannel;
		if(sslFactory != null) {
			if(isUpgradable) {
				//create plain text that can accept SSL immediately OR change to SSL later
				serverChannel = channelManager.createTCPUpgradableChannel(config.id, connectionListener, sslFactory);
			} else {
				serverChannel = channelManager.createTCPServerChannel(config.id, connectionListener, sslFactory);
			}
		} else {
			serverChannel = channelManager.createTCPServerChannel(config.id, connectionListener);
		}

		//MUST be called before bind...
		serverChannel.setReuseAddress(true);
		
		serverChannel.configure(config.functionToConfigureBeforeBind);
		
		return new AsyncServerImpl(serverChannel, connectionListener, proxyListener, sslFactory);
	}

	@Override
	public String getName() {
		return channelManager.getName();
	}
}

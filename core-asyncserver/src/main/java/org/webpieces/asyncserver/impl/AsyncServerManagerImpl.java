package org.webpieces.asyncserver.impl;

import java.net.SocketAddress;

import org.webpieces.asyncserver.api.AsyncServer;
import org.webpieces.asyncserver.api.AsyncServerManager;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.channels.TCPServerChannel;
import org.webpieces.nio.api.handlers.DataListener;

public class AsyncServerManagerImpl implements AsyncServerManager {

	private ChannelManager channelManager;

	public AsyncServerManagerImpl(ChannelManager channelManager) {
		this.channelManager = channelManager;
	}

	@Override
	public AsyncServer createTcpServer(
			String id, SocketAddress addr, DataListener listener) {
		TCPServerChannel serverChannel = channelManager.createTCPServerChannel(id);
		
		ConnectedChannels connectedChannels = new ConnectedChannels();
		DefaultConnectionListener connectionListener = new DefaultConnectionListener(listener, connectedChannels); 
		
		serverChannel.bind(addr);
		serverChannel.setReuseAddress(true);
		serverChannel.registerServerSocketChannel(connectionListener);
		
		return new AsyncServerImpl(serverChannel, connectionListener, connectedChannels);
	}

}

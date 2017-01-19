package org.webpieces.http2client.mock;

import javax.net.ssl.SSLEngine;

import org.webpieces.mock.MethodEnum;
import org.webpieces.mock.MockSuperclass;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.SSLEngineFactory;
import org.webpieces.nio.api.channels.DatagramChannel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.channels.TCPServerChannel;
import org.webpieces.nio.api.channels.UDPChannel;
import org.webpieces.nio.api.handlers.ConnectionListener;
import org.webpieces.nio.api.handlers.DatagramListener;

public class MockChanMgr extends MockSuperclass implements ChannelManager {

	private enum Method implements MethodEnum {
		CREATE_TCP_CHANNEL, CREATE_SSL_CHANNEL
	}
	
	@Override
	public TCPServerChannel createTCPServerChannel(String id, ConnectionListener connectionListener) {
		throw new UnsupportedOperationException("not implemented but could easily be with a one liner");
	}

	@Override
	public TCPServerChannel createTCPServerChannel(String id, ConnectionListener connectionListener,
			SSLEngineFactory factory) {
		throw new UnsupportedOperationException("not implemented but could easily be with a one liner");
	}

	@Override
	public TCPChannel createTCPChannel(String id) {
		return (TCPChannel) super.calledMethod(Method.CREATE_TCP_CHANNEL, id);
	}

	public void addTCPChannelToReturn(TCPChannel toReturn) {
		super.addValueToReturn(Method.CREATE_TCP_CHANNEL, toReturn);
	}
	
	@Override
	public TCPChannel createTCPChannel(String id, SSLEngine engine) {
		return (TCPChannel) super.calledMethod(Method.CREATE_SSL_CHANNEL, id);
	}

	@Override
	public UDPChannel createUDPChannel(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DatagramChannel createDatagramChannel(String id, int bufferSize, DatagramListener listener) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

}
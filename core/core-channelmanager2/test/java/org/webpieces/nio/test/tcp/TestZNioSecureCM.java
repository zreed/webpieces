package org.webpieces.nio.test.tcp;

import java.util.HashMap;
import java.util.Map;

import org.webpieces.nio.api.deprecated.ChannelManagerOld;
import org.webpieces.nio.api.deprecated.ChannelService;
import org.webpieces.nio.api.deprecated.ChannelServiceFactory;
import org.webpieces.nio.api.deprecated.Settings;
import org.webpieces.nio.api.libs.FactoryCreator;
import org.webpieces.nio.api.libs.PacketProcessorFactory;
import org.webpieces.nio.api.libs.SSLEngineFactory;
import org.webpieces.nio.api.testutil.MockSSLEngineFactory;


public class TestZNioSecureCM extends ZNioSuperclassTest {
	
	private ChannelServiceFactory secureFactory;
	private SSLEngineFactory sslEngineFactory;
	private Settings clientFactoryHolder;
	private Settings serverFactoryHolder;
	
	public TestZNioSecureCM() {
		ChannelServiceFactory basic = ChannelServiceFactory.createFactory(null);
		
		Map<String, Object> factoryName = new HashMap<String, Object>();
		factoryName.put(ChannelServiceFactory.KEY_IMPLEMENTATION_CLASS, ChannelServiceFactory.VAL_SECURE_CHANNEL_MGR);
		factoryName.put(ChannelServiceFactory.KEY_CHILD_CHANNELMGR_FACTORY, basic);
		ChannelServiceFactory sslLayer = ChannelServiceFactory.createFactory(factoryName);
		
		Map<String, Object> props = new HashMap<String, Object>();
		props.put(ChannelServiceFactory.KEY_IMPLEMENTATION_CLASS, ChannelServiceFactory.VAL_PACKET_CHANNEL_MGR);
		props.put(ChannelServiceFactory.KEY_CHILD_CHANNELMGR_FACTORY, sslLayer);
		secureFactory = ChannelServiceFactory.createFactory(props);		
		
		sslEngineFactory = new MockSSLEngineFactory();
		FactoryCreator creator = FactoryCreator.createFactory(null);
		PacketProcessorFactory procFactory = creator.createPacketProcFactory(null);
		clientFactoryHolder = new Settings(sslEngineFactory, procFactory);
		serverFactoryHolder = new Settings(sslEngineFactory, procFactory);
	}

	@Override
	protected ChannelService getClientChanMgr() {
		Map<String, Object> p = new HashMap<String, Object>();
		p.put(ChannelManagerOld.KEY_ID, "client");		
		p.put(ChannelManagerOld.KEY_BUFFER_FACTORY, getBufFactory());
		ChannelService chanMgr = secureFactory.createChannelManager(p);		
		return chanMgr;
	}

	@Override
	protected ChannelService getServerChanMgr() {
		Map<String, Object> p = new HashMap<String, Object>();
		p.put(ChannelManagerOld.KEY_ID, "server");
		p.put(ChannelManagerOld.KEY_BUFFER_FACTORY, getBufFactory());
		ChannelService svcChanMgr = secureFactory.createChannelManager(p);
		
		return svcChanMgr;
	}

	@Override
	protected Settings getClientFactoryHolder() {
		return clientFactoryHolder;
	}
	@Override
	protected Settings getServerFactoryHolder() {
		return serverFactoryHolder;
	}
	@Override
	protected String getChannelImplName() {
		return "org.webpieces.nio.impl.cm.packet.PacTCPChannel";
	}
	@Override
	protected String getServerChannelImplName() {
		return "org.webpieces.nio.impl.cm.packet.PacTCPServerChannel";
	}	
//	public void testHandshakeFailure() {
//		
//	}
//	
//	public void testTooManyBytesGivenFromAppToSSLEngine() {
//		
//	}

	@Override
	public void testConnectClose() throws Exception {
		
		super.testConnectClose();
	}

	
	
}

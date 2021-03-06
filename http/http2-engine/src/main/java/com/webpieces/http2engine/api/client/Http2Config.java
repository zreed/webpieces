package com.webpieces.http2engine.api.client;

import com.webpieces.http2engine.impl.shared.data.HeaderSettings;

public class Http2Config {

	private String id = "client";
	//you may want to start off with 1 rather than 100.  some apis like Apple send a settings frame of
	//only 1 max concurrent request and the other 49 will blow up if you just start doing http2 off the bat
	private int initialRemoteMaxConcurrent = 100;
	private HeaderSettings localSettings = new HeaderSettings();
	
	//unfortunately, since the spec has no ack for a sent stream reset, we must keep state around to discard 
	//messages for a time period when you the client send a stream reset.  
	private int afterResetExpireSeconds = 5;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public int getInitialRemoteMaxConcurrent() {
		return initialRemoteMaxConcurrent;
	}
	public void setInitialRemoteMaxConcurrent(int initialMaxConcurrent) {
		this.initialRemoteMaxConcurrent = initialMaxConcurrent;
	}
	public HeaderSettings getLocalSettings() {
		return localSettings;
	}
	public void setLocalSettings(HeaderSettings localSettings) {
		this.localSettings = localSettings;
	}
	public int getAfterResetExpireSeconds() {
		return afterResetExpireSeconds;
	}
	public void setAfterResetExpireSeconds(int afterResetExpireSeconds) {
		this.afterResetExpireSeconds = afterResetExpireSeconds;
	}
}

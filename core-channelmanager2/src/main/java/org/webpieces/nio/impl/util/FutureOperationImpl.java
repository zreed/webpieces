package org.webpieces.nio.impl.util;

import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.RegisterableChannel;
import org.webpieces.nio.api.exceptions.RuntimeInterruptedException;
import org.webpieces.nio.api.exceptions.TimeoutException;
import org.webpieces.nio.api.handlers.FutureOperation;
import org.webpieces.nio.api.handlers.OperationCallback;


public class FutureOperationImpl implements FutureOperation, OperationCallback {

	private RegisterableChannel channel;
	private Throwable e;
	private OperationCallback operationCallback;

	@Override
	public synchronized void finished(Channel channel) {
		this.channel = channel;
		this.notify();
		if(operationCallback != null)
			operationCallback.finished(channel);
	}

	@Override
	public synchronized void failed(RegisterableChannel channel, Throwable e) {
		this.channel = channel;
		this.e = e;
		this.notify();
		if(operationCallback != null)
			operationCallback.failed(channel, e);
	}

	@Override
	public synchronized void waitForOperation(long timeoutInMillis) {
		if(channel != null) {
			if(e != null)
				throw new RuntimeException(e);
			return;
		}
		
		try {
			if(timeoutInMillis > 0) {
				this.wait(timeoutInMillis);
			} else
				this.wait();
		} catch(InterruptedException e) {
			throw new RuntimeInterruptedException(e);
		}
		
		if(channel == null)
			throw new TimeoutException("Waited for operation for time="+timeoutInMillis+" but did not complete");
	}

	@Override
	public synchronized void waitForOperation() {
		waitForOperation(0);
	}

	@Override
	public synchronized void setListener(OperationCallback cb) {
		if(channel != null) {
			if(e != null) {
				cb.failed(channel, e);
			} else
				fireFinished(cb);
			return;
		}
		operationCallback = cb;
	}

	private void fireFinished(OperationCallback cb) {
		cb.finished((Channel) channel);
	}

}
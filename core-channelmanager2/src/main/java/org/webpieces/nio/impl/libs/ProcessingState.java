package org.webpieces.nio.impl.libs;

public enum ProcessingState {
	PROCESSING_HEADER,
	PROCESSING_BODY,
	PROCESSING_TAIL,
	RECOVERING;
}

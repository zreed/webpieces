package org.webpieces.util.logging;

import java.util.ArrayList;
import java.util.List;

public class SupressedExceptionLog {

	private static final Logger log = LoggerFactory.getLogger(SupressedExceptionLog.class);
	
	/**
	 * logback does not yet log the suppressed exceptions that occured after the main failure
	 * which can be useful so tracing recovery through the system.
	 * 
	 * We want to print these in the order they occurred which will be bottom up
	 * 
	 * @param exc
	 */
	public static void log(Throwable exc) {
		List<Throwable> reverseOrder = new ArrayList<>();
		while(exc != null) {
			reverseOrder.add(0, exc);
			exc = exc.getCause();
		}
		
		for(Throwable t : reverseOrder) {
			Throwable[] suppressed = t.getSuppressed();
			logSuppressed(suppressed);
		}
	}

	private static void logSuppressed(Throwable[] suppressed) {
		for(Throwable s: suppressed) {
			log.info("SUPPRESSED exception(meaning it's secondary after a main failure", s);
		}
	}

}

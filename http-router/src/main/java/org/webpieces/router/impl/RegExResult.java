package org.webpieces.router.impl;

import java.util.List;

public class RegExResult {

	public String regExToMatch;
	public List<String> argNames;
	
	public RegExResult(String regEx, List<String> argNames2) {
		this.regExToMatch = regEx;
		this.argNames = argNames2;
	}
	
}

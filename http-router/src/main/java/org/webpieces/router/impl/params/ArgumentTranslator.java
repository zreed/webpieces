package org.webpieces.router.impl.params;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.webpieces.router.api.dto.Request;
import org.webpieces.router.api.routing.Param;
import org.webpieces.router.impl.RouteMeta;

public class ArgumentTranslator {

	private ParamValueTreeCreator treeCreator;

	@Inject
	public ArgumentTranslator(ParamValueTreeCreator treeCreator) {
		this.treeCreator = treeCreator;
	}
	
	public Object[] createArgs(RouteMeta meta, Request req) {
		Parameter[] paramMetas = meta.getMethod().getParameters();
		
		ParamTreeNode paramTree = new ParamTreeNode();
		//For multipart AND for query params such as ?var=xxx&var=yyy&var2=xxx
		
		//query params first
		treeCreator.createTree(paramTree, req.params);
		//next multi-part overwrites query params (if duplicates which there should not be)
		treeCreator.createTree(paramTree, req.multiPartFields);
		//lastly path params overwrites multipart and query params (if duplicates which there should not be)
		treeCreator.createTree(paramTree, createStruct(meta, req));
		
		List<Object> args = new ArrayList<>();
		for(Parameter paramMeta : paramMetas) {
			Object arg = translate(paramTree, paramMeta);
			args.add(arg);
		}
		return args.toArray();
	}

	private Object translate(ParamTreeNode paramTree, Parameter paramMeta) {
		Param annotation = paramMeta.getAnnotation(Param.class);
		String name = annotation.value();
		ParamNode valuesToUse = paramTree.get(name);
		
		Class<?> paramTypeToCreate = paramMeta.getType();
		if(paramTypeToCreate.isPrimitive()) {
			throw new UnsupportedOperationException("not done yet...let me know and I will do it");
		} else if(paramTypeToCreate.isArray()) {
			throw new UnsupportedOperationException("not done yet...let me know and I will do it");
		} else if(paramTypeToCreate.isEnum()) {
			throw new UnsupportedOperationException("not done yet...let me know and I will do it");
		} else if(isPluginManaged(paramTypeToCreate)) {
			throw new UnsupportedOperationException("not done yet...let me know and I will do it");
		} else {
			throw new UnsupportedOperationException("not done yet...let me know and I will do it");
		}
	}

	private boolean isPluginManaged(Class<?> paramTypeToCreate) {
		//TODO: implement so we can either
		// load existing from db from hidden id field in the form
		// create a new db object that is filled in that can easily be saved
		return false;
	}

	private Map<String, String[]> createStruct(RouteMeta meta, Request req) {
		Map<String, String> params = meta.getRoute().createParams(req);
		//why is this not easy to do in jdk8 as this is pretty ugly...
		return params.entrySet().stream()
	            .collect(Collectors.toMap(entry -> entry.getKey(), entry -> new String[] { entry.getValue() } ));
	}

}
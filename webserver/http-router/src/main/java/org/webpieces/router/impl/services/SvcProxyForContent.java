package org.webpieces.router.impl.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.controller.actions.RenderContent;
import org.webpieces.router.api.extensions.BodyContentBinder;
import org.webpieces.router.api.routes.MethodMeta;
import org.webpieces.router.impl.model.SvcProxyLogic;
import org.webpieces.router.impl.params.ParamToObjectTranslatorImpl;
import org.webpieces.util.filters.ExceptionUtil;
import org.webpieces.util.filters.Service;

public class SvcProxyForContent implements Service<MethodMeta, Action> {

	private final ParamToObjectTranslatorImpl translator;
	private final ServiceInvoker invoker;
	private final BodyContentBinder binder;
	
	public SvcProxyForContent(SvcProxyLogic svcProxyLogic,  BodyContentBinder binder) {
		this.binder = binder;
		this.translator = svcProxyLogic.getTranslator();
		this.invoker = svcProxyLogic.getServiceInvoker();
	}

	@Override
	public CompletableFuture<Action> invoke(MethodMeta meta) {
		return ExceptionUtil.wrap(() -> invokeMethod(meta));
	}

	private CompletableFuture<Action> invokeMethod(MethodMeta meta) 
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		RouteInfoForContent info = (RouteInfoForContent) meta.getRoute();
		
		Method m = meta.getLoadedController2().getControllerMethod();
		
		//We chose to do this here so any filters ESPECIALLY API filters 
		//can catch and translate api errors and send customers a logical response
		//On top of that ORM plugins can have a transaction filter and then in this
		//createArgs can look up the bean before applying values since it is in
		//the transaction filter
		List<Object> argsResult = translator.createArgs(m, meta.getCtx(), info.getBodyContentBinder());
		
		Object retVal = invoker.invokeController(meta.getLoadedController2(), argsResult.toArray());
		
		if(binder != null)
			return unwrapResult(m, retVal, info.getBodyContentBinder());

		return invoker.coerceReturnValue(retVal);
	}

	@SuppressWarnings("unchecked")
	private CompletableFuture<Action> unwrapResult(Method method, Object retVal, BodyContentBinder binder) {
		Class<?> returnType = method.getReturnType();
		
		if(CompletableFuture.class.isAssignableFrom(returnType)) {
			if(retVal == null)
				throw new IllegalStateException("Your method returned a null CompletableFuture which it not allowed.  method="+method);
			CompletableFuture<Object> future = (CompletableFuture<Object>) retVal;
			return future.thenApply((bean) -> binder.marshal(bean));
		} else {
			RenderContent content = binder.marshal(retVal);
			return CompletableFuture.completedFuture(content);
		}
	}
}
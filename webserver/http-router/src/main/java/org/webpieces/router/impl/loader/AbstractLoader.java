package org.webpieces.router.impl.loader;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;

import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.routes.MethodMeta;
import org.webpieces.router.api.routes.RouteFilter;
import org.webpieces.router.impl.hooks.MetaLoaderProxy;
import org.webpieces.router.impl.hooks.ServiceCreationInfo;
import org.webpieces.router.impl.routebldr.FilterInfo;
import org.webpieces.util.filters.Service;

import com.google.inject.Injector;

public abstract class AbstractLoader implements MetaLoaderProxy {

	private MetaLoader loader;

	public AbstractLoader(MetaLoader loader) {
		this.loader = loader;
	}

	protected LoadedController loadRouteImpl(Injector injector, ResolvedMethod method) {
		String controllerStr = method.getControllerStr();
		String methodStr = method.getMethodStr();
		
		Object controllerInst = createController(injector, controllerStr);
		
		Singleton singleton = controllerInst.getClass().getAnnotation(Singleton.class);
		if(singleton == null)
			throw new IllegalArgumentException("EVERY controller must be marked with @javax.inject.Singleton. bad controller="+controllerInst.getClass().getName());
		
		return loader.loadInstIntoMeta(controllerInst, methodStr);
	}

	protected abstract Object createController(Injector injector, String controllerStr);

	protected Service<MethodMeta, Action> createServiceFromFiltersImpl(ServiceCreationInfo meta) {
		Injector injector = meta.getInjector();
		List<RouteFilter<?>> filters = createFilters(injector, meta.getFilterInfos());
		Service<MethodMeta, Action> svcWithFilters = loader.loadFilters(meta.getService(), filters);
		return svcWithFilters;
	}
	
	protected List<RouteFilter<?>> createFilters(Injector injector, List<FilterInfo<?>> filterInfos) {
		List<RouteFilter<?>> filters = new ArrayList<>();
		for(FilterInfo<?> info : filterInfos) {
			RouteFilter<?> filter = createFilter2(injector, info);
			filters.add(filter);
		}
		return filters;
	}
	
	protected <T> RouteFilter<T> createFilter2(Injector injector, FilterInfo<T> info) {
		//This is a bit crazy in Development Server(production server is straightforward!!!).  In Dev Server
		//If the Filter java file changed, this filterClass was reloaded from a different classloader to
		//blow away the previous filter.  That was done upstream in the DevRoutingService
		Class<? extends RouteFilter<T>> filterClass = info.getFilter();
		
		RouteFilter<T> f = injector.getInstance(filterClass);
		f.initialize(info.getInitialConfig());
		return f;
	}
}

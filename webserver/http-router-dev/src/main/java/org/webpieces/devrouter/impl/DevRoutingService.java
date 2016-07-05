package org.webpieces.devrouter.impl;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.router.api.HttpRouterConfig;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.RoutingService;
import org.webpieces.router.api.dto.RouterRequest;
import org.webpieces.router.api.exceptions.NotFoundException;
import org.webpieces.router.api.routing.WebAppMeta;
import org.webpieces.router.impl.AbstractRouterService;
import org.webpieces.router.impl.MatchResult;
import org.webpieces.router.impl.RouteLoader;
import org.webpieces.router.impl.RouteMeta;
import org.webpieces.util.file.VirtualFile;

public class DevRoutingService extends AbstractRouterService implements RoutingService {

	private static final Logger log = LoggerFactory.getLogger(DevRoutingService.class);
	private long lastFileTimestamp;
	private RouteLoader routeLoader;
	private DevClassForName classLoader;
	private VirtualFile metaTextFile;
	private WebAppMeta routerModule;

	@Inject
	public DevRoutingService(RouteLoader routeConfig, HttpRouterConfig config, DevClassForName loader) {
		metaTextFile = config.getMetaFile();
		this.routeLoader = routeConfig;
		this.classLoader = loader;
		this.lastFileTimestamp = metaTextFile.lastModified();
	}

	@Override
	public void start() {
		log.info("Starting DEVELOPMENT server with CompilingClassLoader and HotSwap");
		loadOrReload();
		started = true;
	}

	@Override
	public void stop() {
		started = false;
	}
	
	@Override
	public void processHttpRequestsImpl(RouterRequest req, ResponseStreamer responseCb) {
		//In DevRouter, check if we need to reload the text file as it points to a new RouterModules.java implementation file
		boolean reloaded = reloadIfTextFileChanged();
		
		if(!reloaded)
			reloadIfClassFilesChanged();
		
		MatchResult result = routeLoader.fetchRoute(req);
		
		RouteMeta meta = result.getMeta();
		if(meta.getControllerInstance() == null) {
			routeLoader.loadControllerIntoMetaObject(meta, false);
		}
		
		routeLoader.invokeRoute(result, req, responseCb, (e) -> fetchNotFoundRoute(e, req));
	}
	
	public MatchResult fetchNotFoundRoute(NotFoundException e, RouterRequest req) {
		//There is no exception when it is purely no match was found...
		if(e == null)
			e = new NotFoundException("No route found for path="+req.relativePath);
		//else if a match was found and we could not convert certain params to their correct types, it is an exception of
		//NotfoundException as well since we found a route but it didn't 'really' match
		//This makes it easier to find type conversion mistakes since we blatantly tell you about them (FAIL FAST pattern)

		log.error("Route not found!!! Either you(developer) typed the wrong url OR you have a bad route.  Either way, something needs a'fixin", e);
		
		MatchResult result = routeLoader.fetchNotFoundRoute(e);
		
		RouteMeta meta = result.getMeta();
		if(meta.getControllerInstance() == null) {
			routeLoader.loadControllerIntoMetaObject(meta, false);
		}
		
		return result;
	}
	
	/**
	 * Only used with DevRouterConfig which is not on classpath in prod mode
	 * 
	 * @return
	 */
	private boolean reloadIfTextFileChanged() {
		//if timestamp the same, no changes
		if(lastFileTimestamp == metaTextFile.lastModified())
			return false;

		log.info("text file changed so need to reload RouterModules.java implementation");

		routerModule = routeLoader.load(classLoader);
		lastFileTimestamp = metaTextFile.lastModified();
		return true;
	}

	private void reloadIfClassFilesChanged() {
		String routerModuleClassName = routerModule.getClass().getName();
		ClassLoader previousCl = routerModule.getClass().getClassLoader();
		
		Class<?> newClazz = classLoader.clazzForName(routerModuleClassName);
		ClassLoader newClassLoader = newClazz.getClassLoader();
		if(previousCl == newClassLoader)
			return;
		
		log.info("classloader change so we need to reload all router classes");
		loadOrReload();
	}

	private void loadOrReload() {
		routerModule = routeLoader.load(classLoader);		
	}
}
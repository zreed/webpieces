package org.webpieces.router.api;

import java.util.Map;

import com.webpieces.http2engine.api.ResponseHandler;

public interface RouterResponseHandler extends ResponseHandler {

    /**
     * returns FrontendSocket for webpieces or whatever socket for other implementations wire
     * into this router
     *
     * The socket has a session for storing data common among all requests of the socket
     */
    Object getSocket();

    Map<String, Object> getSession();

    boolean requestCameFromHttpsSocket();
    
    boolean requestCameFromBackendSocket();

    /**
     * Temporary for refactoring only
     * @return
     * @deprecated
     */
    @Deprecated
    Void closeIfNeeded();
    
}
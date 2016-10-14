package org.webpieces.httpcommon.impl;

import com.webpieces.http2parser.api.dto.HasPriorityDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.httpcommon.api.*;
import org.webpieces.httpparser.api.dto.HttpRequest;
import org.webpieces.httpparser.api.dto.HttpResponse;


import static org.webpieces.httpcommon.impl.Stream.StreamStatus.IDLE;

class Stream {
    private static final Logger log = LoggerFactory.getLogger(Stream.class);

    enum StreamStatus {
        IDLE,
        RESERVED_LOCAL,
        RESERVED_REMOTE,
        OPEN,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED
    }



    private HttpRequest request;
    private HttpResponse response;

    // Only used for client
    private ResponseListener responseListener;

    private int streamId;

    int getStreamId() {
        return streamId;
    }

    RequestId getRequestId() { return new RequestId(streamId); }
    ResponseId getResponseId() { return new ResponseId(streamId); }

    void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public void setResponse(HttpResponse response) {
        this.response = response;
    }

    void setResponseListener(ResponseListener responseListener) {
        this.responseListener = responseListener;
    }

    private StreamStatus status = IDLE;
    private HasPriorityDetails.PriorityDetails priorityDetails;

    public HasPriorityDetails.PriorityDetails getPriorityDetails() {
        return priorityDetails;
    }

    void setPriorityDetails(HasPriorityDetails.PriorityDetails priorityDetails) {
        this.priorityDetails = priorityDetails;
    }

    public boolean isStreamDependencyIsExclusive() {
        return priorityDetails.isStreamDependencyIsExclusive();
    }

    public int getStreamDependency() {
        return priorityDetails.getStreamDependency();
    }

    public short getWeight() {
        return priorityDetails.getWeight();
    }

    StreamStatus getStatus() {
        return status;
    }

    void setStatus(StreamStatus status) {
        log.info("{}: {} -> {}", streamId, this.status, status);
        this.status = status;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    ResponseListener getResponseListener() {
        return responseListener;
    }
}

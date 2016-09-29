package com.webpieces.http2parser.impl;

import com.webpieces.http2parser.api.Http2FrameType;
import com.webpieces.http2parser.api.Http2Padded;
import com.webpieces.http2parser.api.Http2PushPromise;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.impl.ByteBufferDataWrapper;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class Http2PushPromiseImpl extends Http2FrameImpl implements Http2PushPromise {

    public Http2FrameType getFrameType() {
        return Http2FrameType.PUSH_PROMISE;
    }

    /* flags */
    private boolean endHeaders = false; /* 0x4 */
    private boolean padded = false; /* 0x8 */

    public boolean isEndHeaders() {
        return endHeaders;
    }

    public void setEndHeaders() {
        this.endHeaders = true;
    }

    public byte getFlagsByte() {
        byte value = 0x0;
        if (endHeaders) value |= 0x4;
        if (padded) value |= 0x8;
        return value;
    }

    public void setFlags(byte flags) {
        endHeaders = (flags & 0x4) == 0x4;
        padded = (flags & 0x8) == 0x8;
    }

    /* payload */
    // reserved - 1bit
    private int promisedStreamId = 0x0; //31bits
    private Http2HeaderBlockImpl headerBlock = new Http2HeaderBlockImpl();
    private byte[] padding = null;

    public void setPadding(byte[] padding) {
        this.padding = padding;
        padded = true;
    }

    public int getPromisedStreamId() {
        return promisedStreamId;
    }

    public void setPromisedStreamId(int promisedStreamId) {
        this.promisedStreamId = promisedStreamId & 0x7FFFFFFF;
    }

    // Should reuse code in Http2HeadersImpl but multiple-inheritance is not possible?
    public void setHeaders(Map<String, String> headers) {
        headerBlock.setFromMap(headers);
    }

    public Map<String, String> getHeaders() {
        return headerBlock.getMap();
    }

    public DataWrapper getPayloadDataWrapper() {
        ByteBuffer prelude = ByteBuffer.allocate(4);
        prelude.putInt(promisedStreamId);
        prelude.flip();

        DataWrapper headersDW = headerBlock.getDataWrapper();
        DataWrapper finalDW = dataGen.chainDataWrappers(
                new ByteBufferDataWrapper(prelude),
                headersDW);
        if (!padded)
            return finalDW;
        else
            return Http2Padded.pad(padding, finalDW);
    }

    public void setPayloadFromDataWrapper(DataWrapper payload) {
        List<? extends DataWrapper> split = dataGen.split(payload, 4);
        ByteBuffer prelude = ByteBuffer.wrap(split.get(0).createByteArray());
        setPromisedStreamId(prelude.getInt());

        if (padded) {
            List<? extends DataWrapper> split2 = Http2Padded.getPayloadAndPadding(split.get(1));
            padding = split2.get(1).createByteArray();
            headerBlock.setFromDataWrapper(split2.get(0));
        } else {
            headerBlock.setFromDataWrapper(split.get(1));
        }
    }
}

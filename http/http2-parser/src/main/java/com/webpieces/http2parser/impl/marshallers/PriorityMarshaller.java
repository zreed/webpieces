package com.webpieces.http2parser.impl.marshallers;

import java.nio.ByteBuffer;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;

import com.webpieces.http2parser.api.Http2ParseException;
import com.webpieces.http2parser.api.dto.PriorityFrame;
import com.webpieces.http2parser.api.dto.lib.AbstractHttp2Frame;
import com.webpieces.http2parser.api.dto.lib.Http2ErrorCode;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;
import com.webpieces.http2parser.api.dto.lib.PriorityDetails;
import com.webpieces.http2parser.impl.FrameHeaderData;
import com.webpieces.http2parser.impl.Http2MementoImpl;

public class PriorityMarshaller extends AbstractFrameMarshaller implements FrameMarshaller {

    public PriorityMarshaller(BufferPool bufferPool, DataWrapperGenerator dataGen) {
    	super(bufferPool);
    }

	@Override
	public DataWrapper marshal(Http2Frame frame) {
		PriorityFrame castFrame = (PriorityFrame) frame;
		
		PriorityDetails priorityDetails = castFrame.getPriorityDetails();
		
        DataWrapper payload = marshalPriorityDetails(bufferPool, priorityDetails, frame);
        
		return super.marshalFrame(frame, (byte)0, payload);
	}

	public static DataWrapper marshalPriorityDetails(BufferPool bufferPool, PriorityDetails priorityDetails, Http2Frame frame) {
		
		int streamDependency = priorityDetails.getStreamDependency();
		int newVal = streamDependency & 0x7FFFFFFF;
		if(newVal != streamDependency)
			throw new IllegalArgumentException("frame.priorityDetails.streamDependency "
					+ "is too large an id value per http/2 spec.  frame="+frame);
		
		ByteBuffer payload = bufferPool.nextBuffer(5);
        payload.putInt(priorityDetails.getStreamDependency());
        if (priorityDetails.isStreamDependencyIsExclusive()) 
        	payload.put(0, (byte) (payload.get(0) | 0x80));
        
        payload.put((byte) (priorityDetails.getWeight() & 0xFF));
        payload.flip();
        DataWrapper dataPayload = dataGen.wrapByteBuffer(payload);

		return dataPayload;
	}

	@Override
	public AbstractHttp2Frame unmarshal(Http2MementoImpl state, DataWrapper framePayloadData) {
		FrameHeaderData frameHeaderData = state.getFrameHeaderData();
		int streamId = frameHeaderData.getStreamId();
		if(state.getFrameHeaderData().getPayloadLength() > 5)
			throw new Http2ParseException(Http2ErrorCode.FRAME_SIZE_ERROR, streamId, false);
		//TODO: Verify this, previous code looks like connectionlevel = false but shouldn't this be true
		
        PriorityFrame frame = new PriorityFrame();
        PriorityDetails priorityDetails = frame.getPriorityDetails();
		super.unmarshalFrame(state, frame);

        ByteBuffer payloadByteBuffer = bufferPool.createWithDataWrapper(framePayloadData);

        int firstInt = payloadByteBuffer.getInt();
        priorityDetails.setStreamDependencyIsExclusive((firstInt >>> 31)== 0x1);
        int streamDependency = firstInt & 0x7FFFFFFF;
        if(streamDependency == frame.getStreamId()) {
            // Can't depend on self
            throw new Http2ParseException(Http2ErrorCode.PROTOCOL_ERROR, streamDependency, true);
        }
        priorityDetails.setStreamDependency(streamDependency);
        priorityDetails.setWeight((short) (payloadByteBuffer.get() & 0xFF));

        bufferPool.releaseBuffer(payloadByteBuffer);

        return frame;
	}

}
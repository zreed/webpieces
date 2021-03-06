package com.webpieces.http2parser.impl.marshallers;

import java.nio.ByteBuffer;

import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;

import com.webpieces.http2.api.dto.error.CancelReasonCode;
import com.webpieces.http2.api.dto.error.ConnectionException;
import com.webpieces.http2.api.dto.lowlevel.RstStreamFrame;
import com.webpieces.http2.api.dto.lowlevel.lib.AbstractHttp2Frame;
import com.webpieces.http2.api.dto.lowlevel.lib.Http2Frame;
import com.webpieces.http2parser.impl.FrameHeaderData;
import com.webpieces.http2parser.impl.Http2MementoImpl;
import com.webpieces.http2parser.impl.UnsignedData;

public class RstStreamMarshaller extends AbstractFrameMarshaller implements FrameMarshaller {
	public RstStreamMarshaller(BufferPool bufferPool, DataWrapperGenerator dataGen) {
		super(bufferPool);
	}

	@Override
	public DataWrapper marshal(Http2Frame frame) {
		RstStreamFrame castFrame = (RstStreamFrame) frame;

		ByteBuffer payload = bufferPool.nextBuffer(4);
		UnsignedData.putUnsignedInt(payload, castFrame.getErrorCode());
		payload.flip();

		DataWrapper dataPayload = DATA_GEN.wrapByteBuffer(payload);
		return super.marshalFrame(frame, (byte) 0, dataPayload);
	}

	@Override
	public AbstractHttp2Frame unmarshal(Http2MementoImpl state, DataWrapper framePayloadData) {
		FrameHeaderData frameHeaderData = state.getFrameHeaderData();
		int streamId = frameHeaderData.getStreamId();
		if(state.getFrameHeaderData().getPayloadLength() != 4)
            throw new ConnectionException(CancelReasonCode.FRAME_SIZE_INCORRECT, streamId, 
            		"rststream size not 4 and instead is="+state.getFrameHeaderData().getPayloadLength());
		else if(frameHeaderData.getStreamId() == 0)
            throw new ConnectionException(CancelReasonCode.INVALID_STREAM_ID, frameHeaderData.getStreamId(), 
            		"rst stream cannot be streamid 0 and was="+frameHeaderData.getStreamId());
            
		RstStreamFrame frame = new RstStreamFrame();
		super.unmarshalFrame(state, frame);

		ByteBuffer payloadByteBuffer = bufferPool.createWithDataWrapper(framePayloadData);
		
		long errorCode = UnsignedData.getUnsignedInt(payloadByteBuffer);
		
		frame.setErrorCode(errorCode);

		bufferPool.releaseBuffer(payloadByteBuffer);

		return frame;
	}

}

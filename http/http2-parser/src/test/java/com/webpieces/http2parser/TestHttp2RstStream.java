package com.webpieces.http2parser;

import org.junit.Assert;
import org.junit.Test;

import com.webpieces.http2parser.api.dto.Http2ErrorCode;
import com.webpieces.http2parser.api.dto.Http2Frame;
import com.webpieces.http2parser.api.dto.Http2RstStream;

public class TestHttp2RstStream {
    private static String connectError =
            "00 00 04" + // length
            "03" + // type
            "00" + // flags
            "00 00 00 04" + // streamid
            "00 00 00 0A"; // payload
    @Test
    public void testCreateRstStream() {
        Http2RstStream frame = new Http2RstStream();
        frame.setStreamId(0x4);
        frame.setErrorCode(Http2ErrorCode.CONNECT_ERROR);
        String hexFrame = UtilsForTest.frameToHex(frame);

        UtilsForTest.testBidiFromBytes(hexFrame);
    }

    @Test public void testParseRstStream() {
        Http2Frame frame = UtilsForTest.frameFromHex(connectError);
        Assert.assertTrue(Http2RstStream.class.isInstance(frame));
        Http2RstStream castFrame = (Http2RstStream) frame;

        Assert.assertEquals(castFrame.getStreamId(), 4);
        Assert.assertEquals(castFrame.getErrorCode(), Http2ErrorCode.CONNECT_ERROR);
    }
}

package org.webpieces.data.api;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.webpieces.data.impl.AbstractDataWrapper;


public class TestDataWrappers {

	private DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();

	@Test
	public void testSplitZero() {
		DataWrapper wrapper1 = dataGen.wrapByteArray("0123456789".getBytes());
		DataWrapper wrapper2 = dataGen.wrapByteArray("9876543210".getBytes());
		
		DataWrapper chainDataWrappers = dataGen.chainDataWrappers(wrapper1, wrapper2);

		List<? extends DataWrapper> split = dataGen.split(chainDataWrappers, 0);
		DataWrapper split1 = split.get(0);
		DataWrapper split2 = split.get(1);
		
		String str1 = split1.createStringFrom(0, split1.getReadableSize(), Charset.defaultCharset());
		String str2 = split2.createStringFrom(0, split2.getReadableSize(), Charset.defaultCharset());
		
		Assert.assertEquals("", str1);
		Assert.assertEquals("01234567899876543210", str2);
	}
	
	@Test
	public void testSplitAtLength() {
		DataWrapper wrapper1 = dataGen.wrapByteArray("0123456789".getBytes());
		DataWrapper wrapper2 = dataGen.wrapByteArray("9876543210".getBytes());
		
		DataWrapper chainDataWrappers = dataGen.chainDataWrappers(wrapper1, wrapper2);

		List<? extends DataWrapper> split = dataGen.split(chainDataWrappers, chainDataWrappers.getReadableSize());
		DataWrapper split1 = split.get(0);
		DataWrapper split2 = split.get(1);
		
		String str1 = split1.createStringFrom(0, split1.getReadableSize(), Charset.defaultCharset());
		String str2 = split2.createStringFrom(0, split2.getReadableSize(), Charset.defaultCharset());
		
		Assert.assertEquals("01234567899876543210", str1);
		Assert.assertEquals("", str2);
	}
	
	@Test
	public void testEvenSplit() {
		DataWrapper wrapper1 = dataGen.wrapByteArray("0123456789".getBytes());
		DataWrapper wrapper2 = dataGen.wrapByteArray("9876543210".getBytes());
		
		DataWrapper chainDataWrappers = dataGen.chainDataWrappers(wrapper1, wrapper2);

		List<? extends DataWrapper> split = dataGen.split(chainDataWrappers, 10);
		DataWrapper split1 = split.get(0);
		DataWrapper split2 = split.get(1);
		
		String str1 = split1.createStringFrom(0, split1.getReadableSize(), Charset.defaultCharset());
		String str2 = split2.createStringFrom(0, split2.getReadableSize(), Charset.defaultCharset());
		
		Assert.assertEquals("0123456789", str1);
		Assert.assertEquals("9876543210", str2);
	}
	
	@Test
	public void testBasic() {
		
		DataWrapper wrapper1 = dataGen.wrapByteArray("0123456789".getBytes());
		DataWrapper wrapper2 = dataGen.wrapByteArray("9876543210".getBytes());
		
		DataWrapper chainDataWrappers = dataGen.chainDataWrappers(wrapper1, wrapper2);

		List<? extends DataWrapper> split = dataGen.split(chainDataWrappers, 12);
		DataWrapper split1 = split.get(0);
		DataWrapper split2 = split.get(1);
		
		Assert.assertEquals("012345678998", split1.createStringFrom(0, split1.getReadableSize(), Charset.defaultCharset()));
		Assert.assertEquals("76543210", split2.createStringFrom(0, split2.getReadableSize(), Charset.defaultCharset()));
		
		DataWrapper wrapper3 = dataGen.wrapByteArray("abcdefghij".getBytes());
		
		DataWrapper parent = dataGen.chainDataWrappers(split2, wrapper3);
		
		List<? extends DataWrapper> splitList2 = dataGen.split(parent, 12);
		DataWrapper firstPart = splitList2.get(0);
		AbstractDataWrapper rightSide = (AbstractDataWrapper) splitList2.get(1);
	
		Assert.assertEquals(3, rightSide.getNumLayers());

		String str1 = split1.createStringFrom(0, split1.getReadableSize(), Charset.defaultCharset());
		String str2 = firstPart.createStringFrom(0, firstPart.getReadableSize(), Charset.defaultCharset());
		String str3 = rightSide.createStringFrom(0, rightSide.getReadableSize(), Charset.defaultCharset());
		
		Assert.assertEquals("01234567899876543210abcdefghij", str1+str2+str3);
		
		List<ByteBuffer> buffers = new ArrayList<>();
		split1.addUnderlyingBuffersToList(buffers);
		
		Assert.assertEquals(2, buffers.size());
		ByteBuffer buffer1 = buffers.get(0);
		ByteBuffer buffer2 = buffers.get(1);
		
		byte[] data = new byte[split1.getReadableSize()];
		int length = buffer1.remaining();
		buffer1.get(data, 0, length);
		buffer2.get(data, length, buffer2.remaining());
		
		String result = new String(data);
		
		Assert.assertEquals("012345678998", result);
	}

}

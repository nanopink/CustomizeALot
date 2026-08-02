package com.customizealot;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CustomizeALotNpcOverheadHeightTest
{
	@Test
	public void decodesOverrideAfterCommonNpcFields()
	{
		byte[] data = {
			1, 2, 0, 1, 0, 2,
			2, 'G', 'i', 'a', 'n', 't', 0,
			12, 3,
			13, 0, 1,
			14, 0, 2,
			15, 0, 3,
			16, 0, 4,
			17, 0, 5, 0, 6, 0, 7, 0, 8,
			18, 0, 9,
			30, 'A', 0,
			34, 'B', 0,
			40, 1, 0, 1, 0, 2,
			41, 1, 0, 3, 0, 4,
			60, 1, 0, 5,
			61, 1, 0, 0, 0, 6,
			62, 1, 0, 0, 0, 7,
			74, 0, 1,
			75, 0, 2,
			76, 0, 3,
			77, 0, 4,
			78, 0, 5,
			79, 0, 6,
			93,
			95, 0, 10,
			97, 0, 100,
			98, 0, 100,
			99,
			100, 1,
			101, 2,
			103, 0, 32,
			107,
			109,
			111,
			114, 0, 11,
			115, 0, 12, 0, 13, 0, 14, 0, 15,
			116, 0, 16,
			117, 0, 17, 0, 18, 0, 19, 0, 20,
			122,
			123,
			124, 1, 44,
			126, 0, 64,
			(byte) 129,
			(byte) 130,
			(byte) 145,
			(byte) 146, 0, 21,
			(byte) 147,
			0
		};

		assertEquals(300, CustomizeALotNpcOverheadHeight.decode(data));
	}

	@Test
	public void skipsVariableHeadIconTransformParamAndOperationFields()
	{
		byte[] data = {
			102, 5,
			0, 1, 1,
			(byte) 0x80, 0, 0, 2, (byte) 0x80, 1,
			106,
			0, 1, 0, 2,
			1,
			0, 3, 0, 4,
			118,
			0, 1, 0, 2, 0, 3,
			0,
			0, 4,
			(byte) 249, 3,
			1, 0, 0, 1, 'x', 0,
			2, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 1,
			0, 0, 0, 3, 0, 0, 0, 2,
			(byte) 251, 1, 2, 's', 0,
			(byte) 252,
			1, 0, 2, 0, 3,
			0, 0, 0, 4,
			0, 0, 0, 5,
			't', 0,
			(byte) 253,
			1, 0, 2, 0, 3, 0, 4,
			0, 0, 0, 5,
			0, 0, 0, 6,
			'u', 0,
			124, 0, 72,
			0
		};

		assertEquals(72, CustomizeALotNpcOverheadHeight.decode(data));
	}

	@Test
	public void preservesUnsignedOverrideValue()
	{
		assertEquals(
			65535,
			CustomizeALotNpcOverheadHeight.decode(new byte[]{
				124, (byte) 0xFF, (byte) 0xFF, 0
			}));
	}

	@Test
	public void missingOverrideIsAValidCachedResult()
	{
		assertEquals(
			CustomizeALotNpcOverheadHeight.NO_OVERRIDE,
			CustomizeALotNpcOverheadHeight.decode(new byte[]{2, 'N', 0, 0}));
	}

	@Test
	public void unavailableMalformedOrUnknownDefinitionsFailClosed()
	{
		assertEquals(
			CustomizeALotNpcOverheadHeight.LOAD_FAILED,
			CustomizeALotNpcOverheadHeight.decode(null));
		assertEquals(
			CustomizeALotNpcOverheadHeight.DECODE_FAILED,
			CustomizeALotNpcOverheadHeight.decode(new byte[]{124, 0}));
		assertEquals(
			CustomizeALotNpcOverheadHeight.DECODE_FAILED,
			CustomizeALotNpcOverheadHeight.decode(new byte[]{127, 124, 0, 72, 0}));
	}
}

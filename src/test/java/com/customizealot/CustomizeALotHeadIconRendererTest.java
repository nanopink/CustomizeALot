package com.customizealot;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CustomizeALotHeadIconRendererTest
{
	@Test
	public void scalesIconsWithinSupportedRange()
	{
		assertEquals(12, CustomizeALotHeadIconRenderer.scaled(24, 10));
		assertEquals(24, CustomizeALotHeadIconRenderer.scaled(24, 100));
		assertEquals(48, CustomizeALotHeadIconRenderer.scaled(24, 500));
	}

	@Test
	public void stacksAboveEarlierActorUiBeforeApplyingUserOffset()
	{
		assertEquals(
			118,
			CustomizeALotHeadIconRenderer.stackBottomY(
				120,
				CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP,
				0,
				2));
		assertEquals(98, CustomizeALotHeadIconRenderer.stackBottomY(120, 100, 0, 2));
		assertEquals(108, CustomizeALotHeadIconRenderer.stackBottomY(120, 100, -10, 2));
	}

	@Test
	public void configuredOffsetsStayConstantAcrossProjectedActorPositions()
	{
		assertHeadIconOffset(210, 380);
		assertHeadIconOffset(470, 125);
	}

	@Test
	public void spacingMovesTheFirstIconAwayFromContentBelowIt()
	{
		assertEquals(
			100,
			CustomizeALotHeadIconRenderer.stackBottomY(
				120,
				100,
				0,
				0));
		assertEquals(
			92,
			CustomizeALotHeadIconRenderer.stackBottomY(
				120,
				100,
				0,
				8));
	}

	@Test
	public void fractionalScalingKeepsCroppedSpriteInsideItsCanvas()
	{
		int canvasWidth = CustomizeALotHeadIconRenderer.scaled(3, 150);
		int imageX = CustomizeALotHeadIconRenderer.scaledEdge(1, 150);
		int imageWidth = CustomizeALotHeadIconRenderer.scaledSpan(1, 2, 150);

		assertEquals(4, canvasWidth);
		assertEquals(1, imageX);
		assertEquals(3, imageWidth);
		assertEquals(canvasWidth, imageX + imageWidth);
	}

	@Test
	public void spacingSeparatesEachFollowingIcon()
	{
		assertEquals(80, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 0));
		assertEquals(72, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 8));
		assertEquals(60, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 99));
	}

	private static void assertHeadIconOffset(int anchorX, int anchorY)
	{
		assertEquals(
			10,
			CustomizeALotHeadIconRenderer.iconLeft(anchorX, 24, 10)
				- CustomizeALotHeadIconRenderer.iconLeft(anchorX, 24, 0));
		assertEquals(
			-10,
			CustomizeALotHeadIconRenderer.stackBottomY(
				anchorY,
				CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP,
				10,
				2)
				- CustomizeALotHeadIconRenderer.stackBottomY(
					anchorY,
					CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP,
					0,
					2));
		assertEquals(
			-10,
			CustomizeALotHeadIconRenderer.stackBottomY(
				anchorY,
				anchorY - 20,
				10,
				2)
				- CustomizeALotHeadIconRenderer.stackBottomY(
					anchorY,
					anchorY - 20,
					0,
					2));
	}
}

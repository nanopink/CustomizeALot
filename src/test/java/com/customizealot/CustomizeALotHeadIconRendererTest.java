package com.customizealot;

import static org.junit.Assert.assertEquals;

import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class CustomizeALotHeadIconRendererTest
{
	@Test
	public void scalesIconsAcrossDynamicRenderRange()
	{
		assertEquals(2, CustomizeALotHeadIconRenderer.scaled(24, 10));
		assertEquals(24, CustomizeALotHeadIconRenderer.scaled(24, 100));
		assertEquals(96, CustomizeALotHeadIconRenderer.scaled(24, 500));
	}

	@Test
	public void perspectiveMagnificationShrinksAndEnlargesConfiguredScale()
	{
		assertEquals(50, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 384, 2048.0f));
		assertEquals(100, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 384, 1024.0f));
		assertEquals(200, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 384, 512.0f));
		assertEquals(150, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			150, true, 384, 1024.0f));
	}

	@Test
	public void extendedOuterZoomCanShrinkBelowConfiguredScaleFloor()
	{
		assertEquals(25, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 384, 4096.0f));
	}

	@Test
	public void disabledZoomScalingPreservesConfiguredPixelScale()
	{
		assertEquals(50, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			10, false, 384, 512.0f));
		assertEquals(125, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			125, false, 384, 4096.0f));
		assertEquals(200, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			500, false, 384, 512.0f));
	}

	@Test
	public void invalidProjectionFallsBackToConfiguredPixelScale()
	{
		assertEquals(120, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			120, true, 0, 1024.0f));
		assertEquals(120, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			120, true, 384, Float.NaN));
		assertEquals(120, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			120, true, 384, Float.POSITIVE_INFINITY));
		assertEquals(120, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			120, true, 384, 49.0f));
	}

	@Test
	public void zoomScalingUsesSafeRenderBounds()
	{
		assertEquals(10, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 1, Float.MAX_VALUE));
		assertEquals(400, CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 10_000, 50.0f));
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
	public void zoomedOutScalingKeepsCroppedSpriteInsideItsCanvas()
	{
		int percent = CustomizeALotHeadIconRenderer.effectiveScalePercent(
			100, true, 384, 4096.0f);
		int canvasWidth = CustomizeALotHeadIconRenderer.scaled(3, percent);
		int imageX = CustomizeALotHeadIconRenderer.scaledEdge(1, percent);
		int imageWidth = CustomizeALotHeadIconRenderer.scaledSpan(1, 2, percent);

		assertEquals(25, percent);
		assertEquals(1, canvasWidth);
		assertEquals(0, imageX);
		assertEquals(1, imageWidth);
		assertEquals(canvasWidth, imageX + imageWidth);
	}

	@Test
	public void spacingSeparatesEachFollowingIcon()
	{
		assertEquals(80, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 0));
		assertEquals(72, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 8));
		assertEquals(60, CustomizeALotHeadIconRenderer.nextIconBottomY(80, 99));
	}

	@Test
	public void mapsStandardOverheadPrayersToActiveResourcePackSprites()
	{
		assertEquals(SpriteID.Prayeron.PROTECT_FROM_MELEE,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(0));
		assertEquals(SpriteID.Prayeron.PROTECT_FROM_MISSILES,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(1));
		assertEquals(SpriteID.Prayeron.PROTECT_FROM_MAGIC,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(2));
		assertEquals(SpriteID.Prayeron.RETRIBUTION,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(3));
		assertEquals(SpriteID.Prayeron.SMITE,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(4));
		assertEquals(SpriteID.Prayeron.REDEMPTION,
			CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(5));
		assertEquals(-1, CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(-1));
		assertEquals(-1, CustomizeALotHeadIconRenderer.resourcePackPrayerSpriteId(6));
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

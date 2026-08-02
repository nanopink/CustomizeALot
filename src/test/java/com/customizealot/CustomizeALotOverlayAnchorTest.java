package com.customizealot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Point;
import org.junit.Test;

public class CustomizeALotOverlayAnchorTest
{
	@Test
	public void sharedAnchorUsesNativeUpperUiHeightPaddingForEveryActorSize()
	{
		assertEquals(47, CustomizeALotOverlay.actorProjectionHeight(32));
		assertEquals(111, CustomizeALotOverlay.actorProjectionHeight(96));
		assertEquals(335, CustomizeALotOverlay.actorProjectionHeight(320));
	}

	@Test
	public void npcDefinitionHeightOverridesLogicalHeightWhenAvailable()
	{
		assertEquals(72, CustomizeALotOverlay.selectBaseOverheadHeight(240, 72));
		assertEquals(240, CustomizeALotOverlay.selectBaseOverheadHeight(
			240,
			CustomizeALotNpcOverheadHeight.NO_OVERRIDE));
		assertEquals(240, CustomizeALotOverlay.selectBaseOverheadHeight(
			240,
			CustomizeALotNpcOverheadHeight.LOAD_FAILED));
		assertEquals(240, CustomizeALotOverlay.selectBaseOverheadHeight(
			240,
			CustomizeALotNpcOverheadHeight.DECODE_FAILED));
	}

	@Test
	public void projectedNativeOriginReceivesOnlyTheNativeCanvasLayoutCorrection()
	{
		Point anchor = CustomizeALotOverlay.nativeStackAnchor(new Point(210, 380));

		assertEquals(new Point(210, 384), anchor);
		assertEquals(377, CustomizeALotHealthBarRenderer.barTop(anchor.getY(), 5, 0));
		assertEquals(
			382,
			CustomizeALotHeadIconRenderer.stackBottomY(
				anchor.getY(),
				CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP,
				0,
				2));
		assertNull(CustomizeALotOverlay.nativeStackAnchor(null));
	}

	@Test
	public void projectsOnlyActorsWithVisibleOverheadUi()
	{
		assertTrue(CustomizeALotOverlay.needsActorTop(true, false, false));
		assertTrue(CustomizeALotOverlay.needsActorTop(false, true, false));
		assertTrue(CustomizeALotOverlay.needsActorTop(false, false, true));
		assertTrue(CustomizeALotOverlay.needsActorTop(true, true, true));
		assertFalse(CustomizeALotOverlay.needsActorTop(false, false, false));
	}

	@Test
	public void overheadStackIsTranslationInvariantAcrossActorTopPositions()
	{
		assertArrayEquals(relativeStack(380), relativeStack(125));

		int actorTopY = 380;
		int chatTop = CustomizeALotOverheadChatRenderer.chatLaneTop(
			actorTopY,
			12,
			CustomizeALotOverheadChatEffect.STATIC);
		int healthTop = CustomizeALotHealthBarRenderer.barTop(chatTop, 5, 0);
		int iconBottom = CustomizeALotHeadIconRenderer.stackBottomY(
			chatTop,
			healthTop,
			0,
			2);
		assertTrue(healthTop + 5 <= chatTop - 2);
		assertTrue(iconBottom <= healthTop - 2);
	}

	private static int[] relativeStack(int actorTopY)
	{
		int chatTop = CustomizeALotOverheadChatRenderer.chatLaneTop(
			actorTopY,
			12,
			CustomizeALotOverheadChatEffect.STATIC);
		int chatBaseline = CustomizeALotOverheadChatRenderer.chatBaseline(
			actorTopY,
			0);
		int healthTop = CustomizeALotHealthBarRenderer.barTop(chatTop, 5, 0);
		int iconBottom = CustomizeALotHeadIconRenderer.stackBottomY(
			chatTop,
			healthTop,
			0,
			2);
		return new int[]{
			chatTop - actorTopY,
			chatBaseline - actorTopY,
			healthTop - actorTopY,
			iconBottom - actorTopY
		};
	}
}

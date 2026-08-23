package com.customizealot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class CustomizeALotActorUiVisibilityTrackerTest
{
	@Test
	public void requiresAcceptanceDuringTheCurrentGameCycle()
	{
		assertTrue(CustomizeALotActorUiVisibilityTracker.acceptedOn(100, 100));
		assertFalse(CustomizeALotActorUiVisibilityTracker.acceptedOn(99, 100));
		assertFalse(CustomizeALotActorUiVisibilityTracker.acceptedOn(Integer.MIN_VALUE, 100));
	}

	@Test
	public void suppressesOnlyActorUiWhileEnabled()
	{
		assertTrue(CustomizeALotActorUiVisibilityTracker.shouldSuppressActorUi(true, true, true, false));
		assertFalse(CustomizeALotActorUiVisibilityTracker.shouldSuppressActorUi(false, true, true, false));
		assertFalse(CustomizeALotActorUiVisibilityTracker.shouldSuppressActorUi(true, false, true, false));
		assertFalse(CustomizeALotActorUiVisibilityTracker.shouldSuppressActorUi(true, true, false, false));
	}

	@Test
	public void preservesCompleteNativeUiWhenAnActorNeedsASecondaryGameplayBar()
	{
		assertFalse(CustomizeALotActorUiVisibilityTracker.shouldSuppressActorUi(true, true, true, true));
	}

	@Test
	public void recognizesReportedSecondaryGameplayBarActors()
	{
		assertTrue(CustomizeALotActorUiVisibilityTracker.isSpecialBarCompatibilityNpc(NpcID.DOM_BOSS));
		assertTrue(CustomizeALotActorUiVisibilityTracker.isSpecialBarCompatibilityNpc(NpcID.DOM_BOSS_SHIELDED));
		assertTrue(CustomizeALotActorUiVisibilityTracker.isSpecialBarCompatibilityNpc(NpcID.DOM_BOSS_BURROWED));
		assertTrue(CustomizeALotActorUiVisibilityTracker.isSpecialBarCompatibilityNpc(NpcID.MM_LAB_TABLE_BAR));
		assertFalse(CustomizeALotActorUiVisibilityTracker.isSpecialBarCompatibilityNpc(NpcID.GOBLIN));
	}

	@Test
	public void keepsReplacementHealthOnlyForDoomNativeUiActors()
	{
		assertTrue(CustomizeALotActorUiVisibilityTracker.usesDoomChargeBar(NpcID.DOM_BOSS));
		assertTrue(CustomizeALotActorUiVisibilityTracker.usesDoomChargeBar(NpcID.DOM_BOSS_SHIELDED));
		assertTrue(CustomizeALotActorUiVisibilityTracker.usesDoomChargeBar(NpcID.DOM_BOSS_BURROWED));
		assertFalse(CustomizeALotActorUiVisibilityTracker.usesDoomChargeBar(NpcID.MM_LAB_TABLE_BAR));
		assertFalse(CustomizeALotActorUiVisibilityTracker.usesDoomChargeBar(NpcID.GOBLIN));
	}
}

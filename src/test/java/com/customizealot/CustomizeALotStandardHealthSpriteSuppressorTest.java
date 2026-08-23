package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class CustomizeALotStandardHealthSpriteSuppressorTest
{
	@Test
	public void neverSuppressesTheDoomChargeSprites()
	{
		for (int spriteId : CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS)
		{
			assertTrue(spriteId != SpriteID.StandardCharge100.FRONT);
			assertTrue(spriteId != SpriteID.StandardCharge100.BACK);
		}
	}

	@Test
	public void waitsForTheClientAndInstallsEveryStandardSpriteAtomically()
	{
		AtomicBoolean ready = new AtomicBoolean();
		AtomicInteger loads = new AtomicInteger();
		AtomicInteger creations = new AtomicInteger();
		AtomicInteger cacheResets = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		TestSpritePixels source = new TestSpritePixels(30, 5);
		CustomizeALotStandardHealthSpriteSuppressor suppressor =
			new CustomizeALotStandardHealthSpriteSuppressor(
				ready::get,
				() -> overrides,
				spriteId ->
				{
					loads.incrementAndGet();
					return source;
				},
				sprite ->
				{
					creations.incrementAndGet();
					return new TestSpritePixels(sprite.getWidth(), sprite.getHeight());
				},
				cacheResets::incrementAndGet);

		suppressor.requestEnable();
		assertFalse(suppressor.isApplied());
		assertFalse(suppressor.applyRequested());
		assertFalse(suppressor.isApplied());
		assertTrue(overrides.isEmpty());
		assertEquals(0, cacheResets.get());

		ready.set(true);
		assertTrue(suppressor.applyRequested());
		assertTrue(suppressor.isApplied());
		assertEquals(CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS.length,
			overrides.size());
		assertEquals(overrides.size(), loads.get());
		assertEquals(overrides.size(), creations.get());
		assertEquals(1, cacheResets.get());
		for (int spriteId : CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS)
		{
			assertNotSame(source, overrides.get(spriteId));
		}

		assertTrue(suppressor.applyRequested());
		assertEquals(overrides.size(), loads.get());
		assertEquals(overrides.size(), creations.get());
		assertEquals(1, cacheResets.get());
	}

	@Test
	public void failedPreparationDoesNotPartiallyInstallOverrides()
	{
		AtomicInteger creations = new AtomicInteger();
		AtomicInteger cacheResets = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		CustomizeALotStandardHealthSpriteSuppressor suppressor =
			new CustomizeALotStandardHealthSpriteSuppressor(
				() -> true,
				() -> overrides,
				spriteId -> new TestSpritePixels(30, 5),
				sprite -> creations.incrementAndGet() == 2
					? null
					: new TestSpritePixels(30, 5),
				cacheResets::incrementAndGet);

		suppressor.requestEnable();
		assertFalse(suppressor.applyRequested());
		assertFalse(suppressor.isApplied());
		assertTrue(overrides.isEmpty());
		assertEquals(0, cacheResets.get());
	}

	@Test
	public void retriesAFailedInstallCacheResetWithoutRebuildingSprites()
	{
		AtomicInteger creations = new AtomicInteger();
		AtomicInteger cacheResetAttempts = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		CustomizeALotStandardHealthSpriteSuppressor suppressor =
			new CustomizeALotStandardHealthSpriteSuppressor(
				() -> true,
				() -> overrides,
				spriteId -> new TestSpritePixels(30, 5),
				sprite ->
				{
					creations.incrementAndGet();
					return new TestSpritePixels(30, 5);
				},
				() ->
				{
					if (cacheResetAttempts.incrementAndGet() == 1)
					{
						throw new IllegalStateException("retry");
					}
				});

		suppressor.requestEnable();
		assertFalse(suppressor.applyRequested());
		assertFalse(suppressor.isApplied());
		assertEquals(CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS.length,
			overrides.size());

		assertTrue(suppressor.applyRequested());
		assertTrue(suppressor.isApplied());
		assertEquals(overrides.size(), creations.get());
		assertEquals(2, cacheResetAttempts.get());
	}

	@Test
	public void restoresThePreviousOverrideWhenItStillOwnsTheEntry()
	{
		int spriteId = CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS[0];
		TestSpritePixels previous = new TestSpritePixels(40, 7);
		AtomicInteger cacheResets = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, previous);
		CustomizeALotStandardHealthSpriteSuppressor suppressor = suppressor(
			overrides,
			cacheResets);

		suppressor.requestEnable();
		assertTrue(suppressor.applyRequested());
		assertNotSame(previous, overrides.get(spriteId));
		assertEquals(40, overrides.get(spriteId).getWidth());
		assertEquals(7, overrides.get(spriteId).getHeight());

		suppressor.requestDisable();
		assertFalse(suppressor.isApplied());
		assertTrue(suppressor.removeOwnedOverrides());
		assertSame(previous, overrides.get(spriteId));
		assertEquals(1, overrides.size());
		assertEquals(2, cacheResets.get());
	}

	@Test
	public void removalDoesNotClobberANewerForeignOverride()
	{
		int spriteId = CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS[0];
		TestSpritePixels previous = new TestSpritePixels(40, 7);
		TestSpritePixels foreign = new TestSpritePixels(50, 8);
		AtomicInteger cacheResets = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, previous);
		CustomizeALotStandardHealthSpriteSuppressor suppressor = suppressor(
			overrides,
			cacheResets);

		suppressor.requestEnable();
		assertTrue(suppressor.applyRequested());
		overrides.put(spriteId, foreign);

		suppressor.requestDisable();
		assertTrue(suppressor.removeOwnedOverrides());
		assertSame(foreign, overrides.get(spriteId));
		assertEquals(1, overrides.size());
		assertEquals(2, cacheResets.get());
	}

	@Test
	public void reapplyRestoresTheLatestForeignOverrideOnRemoval()
	{
		int spriteId = CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS[0];
		TestSpritePixels foreign = new TestSpritePixels(50, 8);
		AtomicInteger cacheResets = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		CustomizeALotStandardHealthSpriteSuppressor suppressor = suppressor(
			overrides,
			cacheResets);

		suppressor.requestEnable();
		assertTrue(suppressor.applyRequested());
		overrides.put(spriteId, foreign);
		assertTrue(suppressor.reapplyRequested());
		assertNotSame(foreign, overrides.get(spriteId));
		assertEquals(50, overrides.get(spriteId).getWidth());
		assertEquals(8, overrides.get(spriteId).getHeight());

		suppressor.requestDisable();
		assertTrue(suppressor.removeOwnedOverrides());
		assertSame(foreign, overrides.get(spriteId));
		assertEquals(1, overrides.size());
		assertEquals(3, cacheResets.get());
	}

	@Test
	public void retriesARemovalCacheResetBeforeForgettingOwnership()
	{
		int spriteId = CustomizeALotStandardHealthSpriteSuppressor.STANDARD_HEALTH_SPRITE_IDS[0];
		TestSpritePixels previous = new TestSpritePixels(40, 7);
		AtomicInteger cacheResetAttempts = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, previous);
		CustomizeALotStandardHealthSpriteSuppressor suppressor =
			new CustomizeALotStandardHealthSpriteSuppressor(
				() -> true,
				() -> overrides,
				ignored -> new TestSpritePixels(30, 5),
				source -> new TestSpritePixels(source.getWidth(), source.getHeight()),
				() ->
				{
					if (cacheResetAttempts.incrementAndGet() == 2)
					{
						throw new IllegalStateException("retry");
					}
				});

		suppressor.requestEnable();
		assertTrue(suppressor.applyRequested());
		suppressor.requestDisable();
		assertFalse(suppressor.removeOwnedOverrides());
		assertSame(previous, overrides.get(spriteId));

		assertTrue(suppressor.removeOwnedOverrides());
		assertSame(previous, overrides.get(spriteId));
		assertEquals(1, overrides.size());
		assertEquals(3, cacheResetAttempts.get());
	}

	@Test
	public void copiesTheCompleteSpriteGeometry()
	{
		TestSpritePixels source = new TestSpritePixels(30, 5);
		source.setMaxWidth(44);
		source.setMaxHeight(11);
		source.setOffsetX(3);
		source.setOffsetY(4);
		TestSpritePixels destination = new TestSpritePixels(30, 5);

		CustomizeALotStandardHealthSpriteSuppressor.copyGeometry(source, destination);

		assertEquals(44, destination.getMaxWidth());
		assertEquals(11, destination.getMaxHeight());
		assertEquals(3, destination.getOffsetX());
		assertEquals(4, destination.getOffsetY());
	}

	private static CustomizeALotStandardHealthSpriteSuppressor suppressor(
		Map<Integer, SpritePixels> overrides,
		AtomicInteger cacheResets)
	{
		return new CustomizeALotStandardHealthSpriteSuppressor(
			() -> true,
			() -> overrides,
			spriteId -> new TestSpritePixels(30, 5),
			source -> new TestSpritePixels(source.getWidth(), source.getHeight()),
			cacheResets::incrementAndGet);
	}

	private static final class TestSpritePixels implements SpritePixels
	{
		private final int width;
		private final int height;
		private int maxWidth;
		private int maxHeight;
		private int offsetX;
		private int offsetY;

		private TestSpritePixels(int width, int height)
		{
			this.width = width;
			this.height = height;
			maxWidth = width;
			maxHeight = height;
		}

		@Override
		public void drawAt(int x, int y)
		{
		}

		@Override
		public int getWidth()
		{
			return width;
		}

		@Override
		public int getHeight()
		{
			return height;
		}

		@Override
		public int getMaxWidth()
		{
			return maxWidth;
		}

		@Override
		public int getMaxHeight()
		{
			return maxHeight;
		}

		@Override
		public int getOffsetX()
		{
			return offsetX;
		}

		@Override
		public int getOffsetY()
		{
			return offsetY;
		}

		@Override
		public void setMaxWidth(int maxWidth)
		{
			this.maxWidth = maxWidth;
		}

		@Override
		public void setMaxHeight(int maxHeight)
		{
			this.maxHeight = maxHeight;
		}

		@Override
		public void setOffsetX(int offsetX)
		{
			this.offsetX = offsetX;
		}

		@Override
		public void setOffsetY(int offsetY)
		{
			this.offsetY = offsetY;
		}

		@Override
		public int[] getPixels()
		{
			return new int[width * height];
		}

		@Override
		public BufferedImage toBufferedImage()
		{
			return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		}

		@Override
		public void toBufferedImage(BufferedImage target)
		{
		}

		@Override
		public BufferedImage toBufferedOutline(Color color)
		{
			return toBufferedImage();
		}

		@Override
		public void toBufferedOutline(BufferedImage target, int color)
		{
		}
	}
}

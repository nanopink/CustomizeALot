package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class CustomizeALotActorUiSpriteCacheTest
{
	private static final int ARCHIVE_ID = SpriteID.HEADICONS_PRAYER;

	@Test
	public void convertsOnlyTheRequestedSpriteAndReusesTheLoadedGroup()
	{
		AtomicInteger gameCycle = new AtomicInteger(100);
		AtomicInteger groupLoads = new AtomicInteger();
		AtomicInteger firstConversions = new AtomicInteger();
		AtomicInteger secondConversions = new AtomicInteger();
		CustomizeALotActorUiSpriteCache.SpriteSource first = source(firstConversions, 2, 3, 4, 5, 1, 2);
		CustomizeALotActorUiSpriteCache.SpriteSource second = source(secondConversions, 6, 7, 8, 9, 3, 4);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			gameCycle::get,
			archiveId ->
			{
				groupLoads.incrementAndGet();
				return new CustomizeALotActorUiSpriteCache.SpriteSource[]{first, second};
			});

		CustomizeALotSprite loadedSecond = cache.get(ARCHIVE_ID, 1);

		assertNotNull(loadedSecond);
		assertEquals(6, loadedSecond.getWidth());
		assertEquals(7, loadedSecond.getHeight());
		assertEquals(8, loadedSecond.getMaxWidth());
		assertEquals(9, loadedSecond.getMaxHeight());
		assertEquals(3, loadedSecond.getOffsetX());
		assertEquals(4, loadedSecond.getOffsetY());
		assertEquals(0, firstConversions.get());
		assertEquals(1, secondConversions.get());

		assertSame(loadedSecond, cache.get(ARCHIVE_ID, 1));
		assertNotNull(cache.get(ARCHIVE_ID, 0));
		assertEquals(1, groupLoads.get());
		assertEquals(1, firstConversions.get());
		assertEquals(1, secondConversions.get());
	}

	@Test
	public void missingSpriteEntryReloadsTheGroupAfterTheRetryWindow()
	{
		AtomicInteger gameCycle = new AtomicInteger();
		AtomicInteger groupLoads = new AtomicInteger();
		AtomicInteger conversions = new AtomicInteger();
		CustomizeALotActorUiSpriteCache.SpriteSource recovered = source(
			conversions, 3, 4, 5, 6, 1, 2);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			gameCycle::get,
			archiveId -> groupLoads.getAndIncrement() == 0
				? new CustomizeALotActorUiSpriteCache.SpriteSource[]{null}
				: new CustomizeALotActorUiSpriteCache.SpriteSource[]{recovered});

		assertNull(cache.get(ARCHIVE_ID, 0));
		gameCycle.set(9);
		assertNull(cache.get(ARCHIVE_ID, 0));
		gameCycle.set(10);
		CustomizeALotSprite loaded = cache.get(ARCHIVE_ID, 0);

		assertNotNull(loaded);
		assertEquals(3, loaded.getWidth());
		assertEquals(2, groupLoads.get());
		assertEquals(1, conversions.get());
	}

	@Test
	public void failedWholeGroupLoadIsRetriedWithoutDoubleLoadingAtTheBoundary()
	{
		AtomicInteger gameCycle = new AtomicInteger();
		AtomicInteger groupLoads = new AtomicInteger();
		AtomicInteger conversions = new AtomicInteger();
		CustomizeALotActorUiSpriteCache.SpriteSource recovered = source(
			conversions, 1, 1, 1, 1, 0, 0);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			gameCycle::get,
			archiveId -> groupLoads.getAndIncrement() == 0
				? null
				: new CustomizeALotActorUiSpriteCache.SpriteSource[]{recovered});

		assertNull(cache.get(ARCHIVE_ID, 0));
		gameCycle.set(9);
		assertNull(cache.get(ARCHIVE_ID, 0));
		gameCycle.set(10);
		assertNotNull(cache.get(ARCHIVE_ID, 0));

		assertEquals(2, groupLoads.get());
		assertEquals(1, conversions.get());
	}

	@Test
	public void resourcePackOverrideRefreshesWhenTheLiveSpriteChangesOrIsRemoved()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		TestSpritePixels firstSource = new TestSpritePixels(image(2, 3, Color.RED));
		overrides.put(spriteId, firstSource);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			archiveId -> null,
			() -> overrides);

		CustomizeALotSprite first = cache.getResourcePackOverride(spriteId);

		assertNotNull(first);
		assertEquals(2, first.getWidth());
		assertEquals(3, first.getHeight());
		assertSame(first, cache.getResourcePackOverride(spriteId));
		assertEquals(1, firstSource.conversions.get());

		TestSpritePixels secondSource = new TestSpritePixels(image(4, 5, Color.BLUE));
		overrides.put(spriteId, secondSource);
		CustomizeALotSprite second = cache.getResourcePackOverride(spriteId);

		assertNotNull(second);
		assertNotSame(first, second);
		assertEquals(4, second.getWidth());
		assertEquals(5, second.getHeight());
		assertEquals(1, secondSource.conversions.get());

		overrides.remove(spriteId);
		assertNull(cache.getResourcePackOverride(spriteId));
	}

	@Test
	public void clearingCacheDropsConvertedResourcePackSprites()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		TestSpritePixels source = new TestSpritePixels(image(1, 1, Color.RED));
		overrides.put(spriteId, source);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			archiveId -> null,
			() -> overrides);

		CustomizeALotSprite first = cache.getResourcePackOverride(spriteId);
		cache.clear();
		CustomizeALotSprite second = cache.getResourcePackOverride(spriteId);

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(first, second);
		assertEquals(2, source.conversions.get());
	}

	@Test
	public void resourcePackPrayerBackgroundPreservesSourceResolutionAndTogglesLive()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		CustomizeALotSprite nativePrayer = new CustomizeALotSprite(
			image(25, 25, Color.MAGENTA),
			25,
			25,
			0,
			0);
		CustomizeALotSprite background = new CustomizeALotSprite(
			image(34, 34, Color.GREEN),
			0,
			0);
		BufferedImage foregroundImage = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
		foregroundImage.setRGB(0, 0, Color.RED.getRGB());
		foregroundImage.setRGB(14, 14, Color.BLUE.getRGB());
		foregroundImage.setRGB(29, 29, Color.RED.getRGB());

		Map<Integer, CustomizeALotActorUiSpriteCache.SpriteSource[]> groups = new HashMap<>();
		groups.put(
			SpriteID.HEADICONS_PRAYER,
			new CustomizeALotActorUiSpriteCache.SpriteSource[]{source(nativePrayer)});
		groups.put(
			SpriteID.Prayerglow.ACTIVATED,
			new CustomizeALotActorUiSpriteCache.SpriteSource[]{source(background)});
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, new TestSpritePixels(foregroundImage));
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			groups::get,
			() -> overrides);

		CustomizeALotSprite composed = cache.getPrayerIcon(0, spriteId, true);

		assertNotNull(composed);
		assertNotSame(nativePrayer, composed);
		assertEquals(34, composed.getWidth());
		assertEquals(34, composed.getHeight());
		assertEquals(34, composed.getMaxWidth());
		assertEquals(34, composed.getMaxHeight());
		assertEquals(0, composed.getOffsetX());
		assertEquals(0, composed.getOffsetY());
		assertEquals(Color.GREEN.getRGB(), composed.getImage().getRGB(0, 0));
		assertEquals(Color.RED.getRGB(), composed.getImage().getRGB(2, 2));
		assertEquals(Color.BLUE.getRGB(), composed.getImage().getRGB(16, 16));
		assertEquals(Color.RED.getRGB(), composed.getImage().getRGB(31, 31));
		assertEquals(Color.GREEN.getRGB(), composed.getImage().getRGB(32, 32));
		for (int y = 0; y < composed.getHeight(); y++)
		{
			for (int x = 0; x < composed.getWidth(); x++)
			{
				assertNotEquals(Color.MAGENTA.getRGB(), composed.getImage().getRGB(x, y));
			}
		}

		CustomizeALotSprite foreground = cache.getPrayerIcon(0, spriteId, false);
		assertSame(cache.getResourcePackOverride(spriteId), foreground);
		assertEquals(30, foreground.getWidth());
		assertEquals(30, foreground.getHeight());
		assertEquals(Color.RED.getRGB(), foreground.getImage().getRGB(29, 29));
		assertSame(composed, cache.getPrayerIcon(0, spriteId, true));
	}

	@Test
	public void resourcePackPrayerWithoutBackgroundDoesNotLoadNativeSprites()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		AtomicInteger groupLoads = new AtomicInteger();
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		TestSpritePixels source = new TestSpritePixels(image(30, 30, Color.RED));
		source.setMaxWidth(32);
		source.setMaxHeight(33);
		source.setOffsetX(1);
		source.setOffsetY(2);
		overrides.put(spriteId, source);
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			archiveId ->
			{
				groupLoads.incrementAndGet();
				return null;
			},
			() -> overrides);

		CustomizeALotSprite foreground = cache.getPrayerIcon(0, spriteId, false);

		assertNotNull(foreground);
		assertEquals(30, foreground.getWidth());
		assertEquals(32, foreground.getMaxWidth());
		assertEquals(33, foreground.getMaxHeight());
		assertEquals(1, foreground.getOffsetX());
		assertEquals(2, foreground.getOffsetY());
		assertEquals(0, groupLoads.get());
	}

	@Test
	public void resourcePackPrayerCompositeTracksLiveReplacementAndRemoval()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		CustomizeALotSprite nativePrayer = new CustomizeALotSprite(
			image(25, 25, Color.MAGENTA),
			0,
			0);
		CustomizeALotSprite background = new CustomizeALotSprite(
			image(34, 34, Color.GREEN),
			0,
			0);
		Map<Integer, CustomizeALotActorUiSpriteCache.SpriteSource[]> groups = new HashMap<>();
		groups.put(
			SpriteID.HEADICONS_PRAYER,
			new CustomizeALotActorUiSpriteCache.SpriteSource[]{source(nativePrayer)});
		groups.put(
			SpriteID.Prayerglow.ACTIVATED,
			new CustomizeALotActorUiSpriteCache.SpriteSource[]{source(background)});
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, new TestSpritePixels(image(30, 30, Color.RED)));
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			groups::get,
			() -> overrides);

		CustomizeALotSprite first = cache.getPrayerIcon(0, spriteId, true);
		assertSame(first, cache.getPrayerIcon(0, spriteId, true));

		overrides.put(spriteId, new TestSpritePixels(image(30, 30, Color.BLUE)));
		CustomizeALotSprite second = cache.getPrayerIcon(0, spriteId, true);
		assertNotSame(first, second);
		assertEquals(Color.BLUE.getRGB(), second.getImage().getRGB(12, 12));

		overrides.remove(spriteId);
		assertSame(nativePrayer, cache.getPrayerIcon(0, spriteId, true));
	}

	@Test
	public void resourcePackPrayerKeepsTheForegroundWhenTheBackgroundIsUnavailable()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, new TestSpritePixels(image(30, 30, Color.RED)));
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			archiveId -> null,
			() -> overrides);

		CustomizeALotSprite foreground = cache.getPrayerIcon(0, spriteId, true);

		assertSame(cache.getResourcePackOverride(spriteId), foreground);
		assertEquals(30, foreground.getWidth());
	}

	@Test
	public void resourcePackPrayerCompositeTracksLiveBackgroundReplacement()
	{
		final int spriteId = SpriteID.Prayeron.PROTECT_FROM_MELEE;
		CustomizeALotSprite nativeBackground = new CustomizeALotSprite(
			image(34, 34, Color.GREEN),
			0,
			0);
		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(spriteId, new TestSpritePixels(image(30, 30, Color.RED)));
		overrides.put(
			SpriteID.Prayerglow.ACTIVATED,
			new TestSpritePixels(image(34, 34, Color.BLUE)));
		CustomizeALotActorUiSpriteCache cache = new CustomizeALotActorUiSpriteCache(
			() -> 0,
			archiveId -> archiveId == SpriteID.Prayerglow.ACTIVATED
				? new CustomizeALotActorUiSpriteCache.SpriteSource[]{source(nativeBackground)}
				: null,
			() -> overrides);

		CustomizeALotSprite first = cache.getPrayerIcon(0, spriteId, true);
		assertEquals(Color.BLUE.getRGB(), first.getImage().getRGB(0, 0));

		overrides.put(
			SpriteID.Prayerglow.ACTIVATED,
			new TestSpritePixels(image(34, 34, Color.YELLOW)));
		CustomizeALotSprite second = cache.getPrayerIcon(0, spriteId, true);
		assertNotSame(first, second);
		assertEquals(Color.YELLOW.getRGB(), second.getImage().getRGB(0, 0));

		overrides.remove(SpriteID.Prayerglow.ACTIVATED);
		CustomizeALotSprite third = cache.getPrayerIcon(0, spriteId, true);
		assertNotSame(second, third);
		assertEquals(Color.GREEN.getRGB(), third.getImage().getRGB(0, 0));
	}

	private static CustomizeALotActorUiSpriteCache.SpriteSource source(
		AtomicInteger conversions,
		int width,
		int height,
		int maxWidth,
		int maxHeight,
		int offsetX,
		int offsetY)
	{
		return new CustomizeALotActorUiSpriteCache.SpriteSource(() ->
		{
			conversions.incrementAndGet();
			return new CustomizeALotSprite(
				new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB),
				maxWidth,
				maxHeight,
				offsetX,
				offsetY);
		});
	}

	private static CustomizeALotActorUiSpriteCache.SpriteSource source(CustomizeALotSprite sprite)
	{
		return new CustomizeALotActorUiSpriteCache.SpriteSource(() -> sprite);
	}

	private static BufferedImage image(int width, int height, Color color)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(color);
			graphics.fillRect(0, 0, width, height);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private static final class TestSpritePixels implements SpritePixels
	{
		private final BufferedImage image;
		private final AtomicInteger conversions = new AtomicInteger();
		private int maxWidth;
		private int maxHeight;
		private int offsetX;
		private int offsetY;

		private TestSpritePixels(BufferedImage image)
		{
			this.image = image;
			maxWidth = image.getWidth();
			maxHeight = image.getHeight();
		}

		@Override
		public void drawAt(int x, int y)
		{
		}

		@Override
		public int getWidth()
		{
			return image.getWidth();
		}

		@Override
		public int getHeight()
		{
			return image.getHeight();
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
			return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		}

		@Override
		public BufferedImage toBufferedImage()
		{
			conversions.incrementAndGet();
			return image;
		}

		@Override
		public void toBufferedImage(BufferedImage target)
		{
			Graphics2D graphics = target.createGraphics();
			try
			{
				graphics.drawImage(image, 0, 0, null);
			}
			finally
			{
				graphics.dispose();
			}
		}

		@Override
		public BufferedImage toBufferedOutline(Color color)
		{
			return image;
		}

		@Override
		public void toBufferedOutline(BufferedImage target, int color)
		{
			toBufferedImage(target);
		}
	}
}

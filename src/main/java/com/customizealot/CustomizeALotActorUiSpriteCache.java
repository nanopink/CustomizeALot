package com.customizealot;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;

@Singleton
final class CustomizeALotActorUiSpriteCache
{
	private static final int CACHE_FAILURE_RETRY_CYCLES = 10;

	private final IntSupplier gameCycleSupplier;
	private final IntFunction<SpriteSource[]> groupLoader;
	private final Supplier<Map<Integer, SpritePixels>> spriteOverrides;
	private final Map<Integer, SpriteGroup> groups = new HashMap<>();
	private final Map<Integer, ResourcePackSprite> resourcePackSprites = new HashMap<>();
	private final Map<Integer, ResourcePackPrayerSprite> resourcePackPrayerSprites = new HashMap<>();

	@Inject
	CustomizeALotActorUiSpriteCache(Client client)
	{
		this(
			() -> safeGameCycle(client),
			archiveId -> loadGroup(client, archiveId),
			client::getSpriteOverrides);
	}

	CustomizeALotActorUiSpriteCache(
		IntSupplier gameCycleSupplier,
		IntFunction<SpriteSource[]> groupLoader)
	{
		this(gameCycleSupplier, groupLoader, () -> null);
	}

	CustomizeALotActorUiSpriteCache(
		IntSupplier gameCycleSupplier,
		IntFunction<SpriteSource[]> groupLoader,
		Supplier<Map<Integer, SpritePixels>> spriteOverrides)
	{
		this.gameCycleSupplier = gameCycleSupplier;
		this.groupLoader = groupLoader;
		this.spriteOverrides = spriteOverrides;
	}

	CustomizeALotSprite get(int archiveId, int spriteIndex)
	{
		if (archiveId < 0 || spriteIndex < 0)
		{
			return null;
		}

		int gameCycle = gameCycleSupplier.getAsInt();
		SpriteGroup group = groups.get(archiveId);
		if (group == null)
		{
			group = new SpriteGroup(groupLoader.apply(archiveId), retryOnGameCycle(gameCycle));
			groups.put(archiveId, group);
		}
		else if (group.shouldRetryGroup(gameCycle))
		{
			group.replaceSources(groupLoader.apply(archiveId), retryOnGameCycle(gameCycle));
		}

		return group.get(
			spriteIndex,
			gameCycle,
			() -> groupLoader.apply(archiveId));
	}

	CustomizeALotSprite getResourcePackOverride(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}

		SpritePixels source;
		try
		{
			Map<Integer, SpritePixels> overrides = spriteOverrides.get();
			source = overrides == null ? null : overrides.get(spriteId);
		}
		catch (RuntimeException ex)
		{
			return null;
		}

		if (source == null)
		{
			resourcePackSprites.remove(spriteId);
			return null;
		}

		ResourcePackSprite cached = resourcePackSprites.get(spriteId);
		if (cached != null && cached.source == source)
		{
			return cached.sprite;
		}

		CustomizeALotSprite converted = new SpriteSource(source).load();
		if (converted == null)
		{
			resourcePackSprites.remove(spriteId);
			return null;
		}

		resourcePackSprites.put(spriteId, new ResourcePackSprite(source, converted));
		return converted;
	}

	CustomizeALotSprite getPrayerIcon(
		int prayerIndex,
		int resourcePackSpriteId,
		boolean showBackground)
	{
		CustomizeALotSprite foreground = getResourcePackOverride(resourcePackSpriteId);
		if (foreground == null)
		{
			resourcePackPrayerSprites.remove(prayerIndex);
			return get(SpriteID.HEADICONS_PRAYER, prayerIndex);
		}
		if (!showBackground)
		{
			return foreground;
		}

		CustomizeALotSprite background = getResourcePackOverride(SpriteID.Prayerglow.ACTIVATED);
		if (background == null)
		{
			background = get(SpriteID.Prayerglow.ACTIVATED, 0);
		}
		if (background == null)
		{
			resourcePackPrayerSprites.remove(prayerIndex);
			return foreground;
		}

		ResourcePackPrayerSprite cached = resourcePackPrayerSprites.get(prayerIndex);
		if (cached != null
			&& cached.background == background
			&& cached.foreground == foreground)
		{
			return cached.sprite;
		}

		CustomizeALotSprite composed = composePrayerIcon(background, foreground);
		if (composed == null)
		{
			resourcePackPrayerSprites.remove(prayerIndex);
			return foreground;
		}

		resourcePackPrayerSprites.put(
			prayerIndex,
			new ResourcePackPrayerSprite(background, foreground, composed));
		return composed;
	}

	void clear()
	{
		groups.clear();
		resourcePackSprites.clear();
		resourcePackPrayerSprites.clear();
	}

	private static CustomizeALotSprite composePrayerIcon(
		CustomizeALotSprite background,
		CustomizeALotSprite foreground)
	{
		int backgroundWidth = logicalWidth(background);
		int backgroundHeight = logicalHeight(background);
		int foregroundWidth = logicalWidth(foreground);
		int foregroundHeight = logicalHeight(foreground);
		int canvasWidth = Math.max(backgroundWidth, foregroundWidth);
		int canvasHeight = Math.max(backgroundHeight, foregroundHeight);
		if (canvasWidth <= 0 || canvasHeight <= 0)
		{
			return null;
		}

		try
		{
			BufferedImage canvas = new BufferedImage(
				canvasWidth,
				canvasHeight,
				BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = canvas.createGraphics();
			try
			{
				drawCentered(graphics, background, canvasWidth, canvasHeight);
				drawCentered(graphics, foreground, canvasWidth, canvasHeight);
			}
			finally
			{
				graphics.dispose();
			}

			return new CustomizeALotSprite(canvas, 0, 0);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static int logicalWidth(CustomizeALotSprite sprite)
	{
		return Math.max(sprite.getMaxWidth(), sprite.getOffsetX() + sprite.getWidth());
	}

	private static int logicalHeight(CustomizeALotSprite sprite)
	{
		return Math.max(sprite.getMaxHeight(), sprite.getOffsetY() + sprite.getHeight());
	}

	private static void drawCentered(
		Graphics2D graphics,
		CustomizeALotSprite sprite,
		int canvasWidth,
		int canvasHeight)
	{
		int logicalWidth = logicalWidth(sprite);
		int logicalHeight = logicalHeight(sprite);
		int x = (canvasWidth - logicalWidth) / 2 + sprite.getOffsetX();
		int y = (canvasHeight - logicalHeight) / 2 + sprite.getOffsetY();
		graphics.drawImage(sprite.getImage(), x, y, null);
	}

	private static SpriteSource[] loadGroup(Client client, int archiveId)
	{
		try
		{
			SpritePixels[] pixels = client.getSprites(client.getIndexSprites(), archiveId, 0);
			if (pixels == null || pixels.length == 0)
			{
				return null;
			}

			SpriteSource[] sources = new SpriteSource[pixels.length];
			for (int i = 0; i < pixels.length; i++)
			{
				if (pixels[i] != null)
				{
					sources[i] = new SpriteSource(pixels[i]);
				}
			}
			return sources;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static int retryOnGameCycle(int gameCycle)
	{
		return gameCycle + CACHE_FAILURE_RETRY_CYCLES;
	}

	private static int safeGameCycle(Client client)
	{
		try
		{
			return client.getGameCycle();
		}
		catch (RuntimeException ex)
		{
			return 0;
		}
	}

	static final class SpriteSource
	{
		private final Supplier<CustomizeALotSprite> loader;

		SpriteSource(SpritePixels spritePixels)
		{
			this(() -> convert(spritePixels));
		}

		SpriteSource(Supplier<CustomizeALotSprite> loader)
		{
			this.loader = loader;
		}

		CustomizeALotSprite load()
		{
			try
			{
				return loader.get();
			}
			catch (RuntimeException ex)
			{
				return null;
			}
		}

		private static CustomizeALotSprite convert(SpritePixels spritePixels)
		{
			BufferedImage image = spritePixels.toBufferedImage();
			return image == null
				? null
				: new CustomizeALotSprite(
					image,
					spritePixels.getMaxWidth(),
					spritePixels.getMaxHeight(),
					spritePixels.getOffsetX(),
					spritePixels.getOffsetY());
		}
	}

	private static final class ResourcePackSprite
	{
		private final SpritePixels source;
		private final CustomizeALotSprite sprite;

		private ResourcePackSprite(SpritePixels source, CustomizeALotSprite sprite)
		{
			this.source = source;
			this.sprite = sprite;
		}
	}

	private static final class ResourcePackPrayerSprite
	{
		private final CustomizeALotSprite background;
		private final CustomizeALotSprite foreground;
		private final CustomizeALotSprite sprite;

		private ResourcePackPrayerSprite(
			CustomizeALotSprite background,
			CustomizeALotSprite foreground,
			CustomizeALotSprite sprite)
		{
			this.background = background;
			this.foreground = foreground;
			this.sprite = sprite;
		}
	}

	private static final class SpriteGroup
	{
		private final Map<Integer, CustomizeALotSprite> convertedSprites = new HashMap<>();
		private final Map<Integer, Integer> failedSpriteRetries = new HashMap<>();
		private SpriteSource[] sources;
		private int retryGroupOnGameCycle;

		private SpriteGroup(SpriteSource[] sources, int retryGroupOnGameCycle)
		{
			this.sources = sources;
			this.retryGroupOnGameCycle = retryGroupOnGameCycle;
		}

		private boolean shouldRetryGroup(int gameCycle)
		{
			return sources == null && gameCycle >= retryGroupOnGameCycle;
		}

		private void replaceSources(SpriteSource[] replacement, int nextRetryGameCycle)
		{
			sources = replacement;
			retryGroupOnGameCycle = nextRetryGameCycle;
			if (replacement == null)
			{
				failedSpriteRetries.replaceAll((index, ignored) -> nextRetryGameCycle);
			}
			else
			{
				failedSpriteRetries.clear();
				releaseConvertedSources();
			}
		}

		private CustomizeALotSprite get(
			int spriteIndex,
			int gameCycle,
			Supplier<SpriteSource[]> groupReloader)
		{
			CustomizeALotSprite converted = convertedSprites.get(spriteIndex);
			if (converted != null)
			{
				return converted;
			}

			Integer retryOnGameCycle = failedSpriteRetries.get(spriteIndex);
			if (retryOnGameCycle != null)
			{
				if (gameCycle < retryOnGameCycle)
				{
					return null;
				}

				SpriteSource[] replacement = groupReloader.get();
				if (replacement != null)
				{
					sources = replacement;
					failedSpriteRetries.clear();
					releaseConvertedSources();
				}
				else
				{
					failedSpriteRetries.replaceAll(
						(index, ignored) -> retryOnGameCycle(gameCycle));
				}
			}

			converted = convert(spriteIndex);
			if (converted == null)
			{
				failedSpriteRetries.put(spriteIndex, retryOnGameCycle(gameCycle));
				return null;
			}

			failedSpriteRetries.remove(spriteIndex);
			// The converted image is now authoritative; release the cache-backed pixels.
			sources[spriteIndex] = null;
			convertedSprites.put(spriteIndex, converted);
			return converted;
		}

		private void releaseConvertedSources()
		{
			if (sources == null)
			{
				return;
			}

			for (Integer spriteIndex : convertedSprites.keySet())
			{
				if (spriteIndex >= 0 && spriteIndex < sources.length)
				{
					sources[spriteIndex] = null;
				}
			}
		}

		private CustomizeALotSprite convert(int spriteIndex)
		{
			if (sources == null || spriteIndex >= sources.length)
			{
				return null;
			}

			SpriteSource source = sources[spriteIndex];
			if (source == null)
			{
				return null;
			}
			return source.load();
		}
	}
}

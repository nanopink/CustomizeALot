package com.customizealot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;

/**
 * Hides only ordinary native health-bar sprites while leaving charge, shield,
 * heat, and other gameplay bars available to the native actor UI pass.
 */
@Singleton
final class CustomizeALotStandardHealthSpriteSuppressor
{
	static final int[] STANDARD_HEALTH_SPRITE_IDS = {
		SpriteID.StandardHealth30.FRONT,
		SpriteID.StandardHealth30.BACK,
		SpriteID.StandardHealth40.FRONT,
		SpriteID.StandardHealth40.BACK,
		SpriteID.StandardHealth50.FRONT,
		SpriteID.StandardHealth50.BACK,
		SpriteID.StandardHealth60.FRONT,
		SpriteID.StandardHealth60.BACK,
		SpriteID.StandardHealth70.FRONT,
		SpriteID.StandardHealth70.BACK,
		SpriteID.StandardHealth80.FRONT,
		SpriteID.StandardHealth80.BACK,
		SpriteID.StandardHealth90.FRONT,
		SpriteID.StandardHealth90.BACK,
		SpriteID.StandardHealth100.FRONT,
		SpriteID.StandardHealth100.BACK,
		SpriteID.StandardHealth120.FRONT,
		SpriteID.StandardHealth120.BACK,
		SpriteID.StandardHealth140.FRONT,
		SpriteID.StandardHealth140.BACK,
		SpriteID.StandardHealth160.FRONT,
		SpriteID.StandardHealth160.BACK
	};

	private final BooleanSupplier clientReady;
	private final Supplier<Map<Integer, SpritePixels>> spriteOverrides;
	private final IntFunction<SpritePixels> originalSpriteLoader;
	private final Function<SpritePixels, SpritePixels> transparentSpriteFactory;
	private final Runnable healthBarCacheReset;
	private final Map<Integer, SpritePixels> previousOverrides = new HashMap<>();
	private final Map<Integer, SpritePixels> installedOverrides = new HashMap<>();

	private volatile boolean requestedEnabled;
	private volatile boolean applied;
	private boolean cacheResetRequired;

	@Inject
	CustomizeALotStandardHealthSpriteSuppressor(Client client)
	{
		this(
			() -> client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState(),
			client::getSpriteOverrides,
			spriteId -> loadOriginalSprite(client, spriteId),
			source -> createTransparentSprite(client, source),
			client::resetHealthBarCaches);
	}

	CustomizeALotStandardHealthSpriteSuppressor(
		BooleanSupplier clientReady,
		Supplier<Map<Integer, SpritePixels>> spriteOverrides,
		IntFunction<SpritePixels> originalSpriteLoader,
		Function<SpritePixels, SpritePixels> transparentSpriteFactory,
		Runnable healthBarCacheReset)
	{
		this.clientReady = clientReady;
		this.spriteOverrides = spriteOverrides;
		this.originalSpriteLoader = originalSpriteLoader;
		this.transparentSpriteFactory = transparentSpriteFactory;
		this.healthBarCacheReset = healthBarCacheReset;
	}

	void requestEnable()
	{
		requestedEnabled = true;
	}

	void requestDisable()
	{
		requestedEnabled = false;
		applied = false;
	}

	boolean isApplied()
	{
		return applied;
	}

	boolean applyRequested()
	{
		return applyRequested(false);
	}

	boolean reapplyRequested()
	{
		return applyRequested(true);
	}

	private boolean applyRequested(boolean forceCacheReset)
	{
		if (!requestedEnabled)
		{
			return true;
		}
		applied = false;

		try
		{
			if (!clientReady.getAsBoolean())
			{
				return false;
			}

			Map<Integer, SpritePixels> overrides = spriteOverrides.get();
			if (overrides == null)
			{
				return false;
			}

			Map<Integer, PreparedOverride> prepared = new LinkedHashMap<>();
			for (int spriteId : STANDARD_HEALTH_SPRITE_IDS)
			{
				SpritePixels current = overrides.get(spriteId);
				SpritePixels installed = installedOverrides.get(spriteId);
				if (installed != null && current == installed)
				{
					continue;
				}

				SpritePixels source = current == null
					? originalSpriteLoader.apply(spriteId)
					: current;
				SpritePixels transparent = source == null
					? null
					: transparentSpriteFactory.apply(source);
				if (transparent == null)
				{
					return false;
				}
				prepared.put(spriteId, new PreparedOverride(current, transparent));
			}

			for (Map.Entry<Integer, PreparedOverride> entry : prepared.entrySet())
			{
				cacheResetRequired = true;
				int spriteId = entry.getKey();
				PreparedOverride replacement = entry.getValue();
				previousOverrides.put(spriteId, replacement.previous);
				installedOverrides.put(spriteId, replacement.transparent);
				overrides.put(spriteId, replacement.transparent);
			}

			cacheResetRequired |= forceCacheReset;
			if (cacheResetRequired)
			{
				healthBarCacheReset.run();
				cacheResetRequired = false;
			}
			applied = true;
			return true;
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}

	boolean removeOwnedOverrides()
	{
		if (requestedEnabled)
		{
			return true;
		}
		if (installedOverrides.isEmpty())
		{
			return true;
		}

		try
		{
			Map<Integer, SpritePixels> overrides = spriteOverrides.get();
			if (overrides == null)
			{
				return false;
			}

			cacheResetRequired = true;
			for (Map.Entry<Integer, SpritePixels> entry : installedOverrides.entrySet())
			{
				int spriteId = entry.getKey();
				if (overrides.get(spriteId) != entry.getValue())
				{
					continue;
				}

				SpritePixels previous = previousOverrides.get(spriteId);
				if (previous == null)
				{
					overrides.remove(spriteId);
				}
				else
				{
					overrides.put(spriteId, previous);
				}
			}

			if (cacheResetRequired)
			{
				healthBarCacheReset.run();
				cacheResetRequired = false;
			}
			installedOverrides.clear();
			previousOverrides.clear();
			return true;
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}

	private static SpritePixels loadOriginalSprite(Client client, int spriteId)
	{
		try
		{
			SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
			return sprites == null || sprites.length == 0 ? null : sprites[0];
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	private static SpritePixels createTransparentSprite(Client client, SpritePixels source)
	{
		int width = source.getWidth();
		int height = source.getHeight();
		long pixelCount = (long) width * height;
		if (width <= 0 || height <= 0 || pixelCount > Integer.MAX_VALUE)
		{
			return null;
		}

		SpritePixels transparent = client.createSpritePixels(new int[(int) pixelCount], width, height);
		if (transparent == null)
		{
			return null;
		}
		copyGeometry(source, transparent);
		return transparent;
	}

	static void copyGeometry(SpritePixels source, SpritePixels destination)
	{
		destination.setMaxWidth(source.getMaxWidth());
		destination.setMaxHeight(source.getMaxHeight());
		destination.setOffsetX(source.getOffsetX());
		destination.setOffsetY(source.getOffsetY());
	}

	private static final class PreparedOverride
	{
		private final SpritePixels previous;
		private final SpritePixels transparent;

		private PreparedOverride(SpritePixels previous, SpritePixels transparent)
		{
			this.previous = previous;
			this.transparent = transparent;
		}
	}
}

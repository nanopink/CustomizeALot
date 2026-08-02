package com.customizealot;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.config.FontType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class CustomizeALotOverlay extends Overlay
{
	private static final BufferedImage ANCHOR_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	private static final int SLOT_WIDTH = 30;
	private static final int SLOT_HEIGHT = 20;
	private static final int SLOT_EDGE_CUT = 7;
	private static final int CACHE_FAILURE_RETRY_CYCLES = 10;
	private static final int MAX_TRANSFORM_DEPTH = 8;
	private static final int NATIVE_UPPER_UI_HEIGHT_PADDING = 15;
	// Native starts its upper-UI accumulator at -2; our shared layout subtracts
	// a 2 px component gap, so +4 preserves the native zero-offset baseline.
	private static final int NATIVE_STACK_CANVAS_ADJUSTMENT = 4;
	private static final Comparator<Actor> ACTOR_RENDER_ORDER = Comparator
		.comparingInt(CustomizeALotOverlay::actorTypeOrder)
		.thenComparingInt(CustomizeALotOverlay::actorIndex)
		.thenComparingInt(System::identityHashCode);
	private static final Predicate<Integer> RETRY_FAILED_NPC_OVERHEAD_HEIGHT =
		height -> height == CustomizeALotNpcOverheadHeight.LOAD_FAILED;

	private final Client client;
	private final CustomizeALotPlugin plugin;
	private final CustomizeALotConfig config;
	private final CustomizeALotSettings settings;
	private final CustomizeALotActorUiVisibilityTracker actorUiVisibilityTracker;
	private final CustomizeALotHealthBarRenderer healthBarRenderer;
	private final CustomizeALotHeadIconRenderer headIconRenderer;
	private final CustomizeALotOverheadChatRenderer overheadChatRenderer;
	private final CustomizeALotLocalChatEffectTracker localChatEffectTracker;
	private final CustomizeALotRetryCache<Integer, CustomizeALotHitmarkDefinition> hitmarkDefinitions =
		new CustomizeALotRetryCache<>(CACHE_FAILURE_RETRY_CYCLES);
	private final CustomizeALotRetryCache<Integer, CustomizeALotSprite> sprites =
		new CustomizeALotRetryCache<>(CACHE_FAILURE_RETRY_CYCLES);
	private final CustomizeALotRetryCache<Integer, Integer> npcOverheadHeights =
		new CustomizeALotRetryCache<>(CACHE_FAILURE_RETRY_CYCLES);
	private final IntFunction<CustomizeALotHitmarkDefinition> hitmarkDefinitionLoader =
		this::loadDefinition;
	private final Function<Integer, Integer> npcOverheadHeightLoader =
		this::loadNpcOverheadHeight;
	private final Map<Actor, SlotSize> actorSlotSizes = new HashMap<>();

	@Inject
	CustomizeALotOverlay(
		Client client,
		CustomizeALotPlugin plugin,
		CustomizeALotConfig config,
		CustomizeALotSettings settings,
		CustomizeALotActorUiVisibilityTracker actorUiVisibilityTracker,
		CustomizeALotHealthBarRenderer healthBarRenderer,
		CustomizeALotHeadIconRenderer headIconRenderer,
		CustomizeALotOverheadChatRenderer overheadChatRenderer,
		CustomizeALotLocalChatEffectTracker localChatEffectTracker)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.settings = settings;
		this.actorUiVisibilityTracker = actorUiVisibilityTracker;
		this.healthBarRenderer = healthBarRenderer;
		this.headIconRenderer = headIconRenderer;
		this.overheadChatRenderer = overheadChatRenderer;
		this.localChatEffectTracker = localChatEffectTracker;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int gameCycle = client.getGameCycle();
		List<Actor> actors = new ArrayList<>();
		actorUiVisibilityTracker.copyAcceptedInto(actors, gameCycle);
		if (actors.isEmpty())
		{
			actorSlotSizes.clear();
			return null;
		}
		actors.sort(ACTOR_RENDER_ORDER);

		Graphics2D overlayGraphics = (Graphics2D) graphics.create();
		try
		{
			FontType hitsplatFontType = settings.hitsplatFont();
			Font hitsplatFont = hitsplatFontType == null
				? FontManager.getRunescapeSmallFont()
				: hitsplatFontType.getFont();
			int hitsplatScalePercent = CustomizeALotRenderLayout.clampScalePercent(
				settings.hitsplatScalePercent());
			overlayGraphics.setFont(scaleFont(hitsplatFont, hitsplatScalePercent));
			CustomizeALotOverheadChatRenderer.Style chatStyle =
				CustomizeALotOverheadChatRenderer.captureStyle(config);
			int chatAscent = overlayGraphics.getFontMetrics(chatStyle.getFont()).getAscent();
			ClanSettings groupIronSettings = chatStyle.shouldRender(false)
				&& chatStyle.usesRelationshipColors()
				? client.getClanSettings(ClanID.GROUP_IRONMAN)
				: null;
			Player localPlayer = client.getLocalPlayer();
			overlayGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			overlayGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			actorSlotSizes.keySet().removeIf(actor -> !plugin.getHitsplats().containsKey(actor));
			List<Rectangle> occupiedChatBounds = new ArrayList<>();
			for (Actor actor : actors)
			{
				if (!hasRenderableState(actor))
				{
					continue;
				}

				boolean chatEnabled = chatStyle.shouldRender(actor instanceof NPC);
				CustomizeALotLocalChatEffectTracker.MessageState messageState = null;
				CustomizeALotOverheadChatEffect chatEffect = null;
				Color chatColor = null;
				boolean renderChat = false;
				if (chatEnabled)
				{
					int chatLifetimeCycles = CustomizeALotOverheadChatRenderer.chatLifetimeCycles(actor);
					messageState = localChatEffectTracker.messageStateFor(
						actor,
						actor.getOverheadText(),
						actor.getOverheadCycle(),
						gameCycle,
						chatLifetimeCycles);
					chatEffect = localChatEffectTracker.effectFor(
						actor,
						messageState.getText(),
						chatStyle.getFallbackEffect(),
						gameCycle);
					Color fallbackChatColor = chatStyle.getColor();
					if (chatStyle.usesRelationshipColors()
						&& messageState.getOverheadCycle() > 0
						&& messageState.getText() != null
						&& !messageState.getText().isEmpty())
					{
						fallbackChatColor = relationshipChatColor(
							actor,
							localPlayer,
							groupIronSettings,
							chatStyle);
					}
					chatColor = localChatEffectTracker.colorFor(
						actor,
						messageState.getText(),
						fallbackChatColor,
						gameCycle);
					renderChat = CustomizeALotOverheadChatRenderer.hasRenderableMessage(
						actor,
						messageState,
						chatStyle,
						chatColor);
				}
				// Height offsets are 3D model units. Project once here so every
				// component gap and configured offset remains in canvas pixels.
				boolean renderHealthBar = healthBarRenderer.hasRenderableHealthBar(actor);
				boolean renderHeadIcons = headIconRenderer.prepare(actor);
				Point actorTop = needsActorTop(
					renderHealthBar,
					renderHeadIcons,
					renderChat)
					? actorTopAnchor(actor, gameCycle)
					: null;
				int upperStackAnchorY = actorTop == null
					? 0
					: actorTop.getY();
				if (actorTop != null && renderChat)
				{
					upperStackAnchorY = CustomizeALotOverheadChatRenderer.chatLaneTop(
						actorTop.getY(),
						chatAscent,
						chatEffect);
				}
				int healthBarTopY = renderHealthBar
					? healthBarRenderer.render(
						overlayGraphics,
						actor,
						actorTop,
						upperStackAnchorY)
					: CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP;
				int occupiedTopY = headIconRenderer.renderPrepared(
					overlayGraphics,
					actor,
					actorTop,
					upperStackAnchorY,
					healthBarTopY);
				if (renderChat)
				{
					overheadChatRenderer.render(
						overlayGraphics,
						actor,
						actorTop,
						messageState,
						chatStyle,
						chatColor,
						chatEffect,
						gameCycle,
						occupiedTopY,
						upperStackAnchorY,
						occupiedChatBounds);
				}

				List<CustomizeALotHitsplat> actorHitsplats = plugin.getHitsplats().get(actor);
				if (actorHitsplats != null && !actorHitsplats.isEmpty())
				{
					renderActorHitsplats(
						overlayGraphics,
						actor,
						actorHitsplats,
						gameCycle,
						hitsplatScalePercent);
				}
			}
		}
		finally
		{
			overlayGraphics.dispose();
		}

		return null;
	}

	static Color relationshipChatColor(
		Actor actor,
		Player localPlayer,
		ClanSettings groupIronSettings,
		CustomizeALotOverheadChatRenderer.Style style)
	{
		if (!(actor instanceof Player) || actor == localPlayer)
		{
			return style == null ? Color.YELLOW : style.getColor();
		}

		Player player = (Player) actor;
		String playerName = player.getName();
		boolean groupIronMember = groupIronSettings != null
			&& playerName != null
			&& groupIronSettings.findMember(playerName) != null;
		return CustomizeALotOverheadChatRenderer.relationshipColor(
			style,
			groupIronMember,
			player.isFriend(),
			player.isClanMember());
	}

	private static boolean hasRenderableState(Actor actor)
	{
		if (actor instanceof NPC)
		{
			return ((NPC) actor).getTransformedComposition() != null;
		}

		if (actor instanceof Player)
		{
			Player player = (Player) actor;
			return player.getName() != null && player.getPlayerComposition() != null;
		}

		return true;
	}

	Point actorTopAnchor(Actor actor, int gameCycle)
	{
		if (actor == null)
		{
			return null;
		}

		Point projected = actor.getCanvasImageLocation(
			ANCHOR_IMAGE,
			actorProjectionHeight(actorBaseOverheadHeight(actor, gameCycle)));
		return nativeStackAnchor(projected);
	}

	private int actorBaseOverheadHeight(Actor actor, int gameCycle)
	{
		int logicalHeight = actor.getLogicalHeight();
		if (!(actor instanceof NPC))
		{
			return logicalHeight;
		}

		NPCComposition composition = ((NPC) actor).getComposition();
		if (composition == null)
		{
			return logicalHeight;
		}

		int definitionHeight = npcOverheadHeights.get(
			composition.getId(),
			gameCycle,
			npcOverheadHeightLoader,
			RETRY_FAILED_NPC_OVERHEAD_HEIGHT);
		return selectBaseOverheadHeight(logicalHeight, definitionHeight);
	}

	static int selectBaseOverheadHeight(int logicalHeight, int definitionHeight)
	{
		return definitionHeight >= 0 ? definitionHeight : logicalHeight;
	}

	static int actorProjectionHeight(int baseOverheadHeight)
	{
		// Actor projection already applies the animation height offset.
		return baseOverheadHeight + NATIVE_UPPER_UI_HEIGHT_PADDING;
	}

	static Point nativeStackAnchor(Point projected)
	{
		return projected == null
			? null
			: new Point(
				projected.getX(),
				projected.getY() + NATIVE_STACK_CANVAS_ADJUSTMENT);
	}

	static boolean needsActorTop(
		boolean renderHealthBar,
		boolean renderHeadIcons,
		boolean renderChat)
	{
		return renderHealthBar || renderHeadIcons || renderChat;
	}

	private static int actorTypeOrder(Actor actor)
	{
		return actor instanceof Player ? 0 : actor instanceof NPC ? 1 : 2;
	}

	private static int actorIndex(Actor actor)
	{
		if (actor instanceof Player)
		{
			return ((Player) actor).getId();
		}
		if (actor instanceof NPC)
		{
			return ((NPC) actor).getIndex();
		}
		return 0;
	}

	private boolean renderActorHitsplats(
		Graphics2D graphics,
		Actor actor,
		List<CustomizeALotHitsplat> hitsplats,
		int gameCycle,
		int scalePercent)
	{
		if (plugin.shouldDisableHitsplatsForActor(actor))
		{
			return false;
		}

		List<CustomizeALotHitsplat> visibleHitsplats = new ArrayList<>(hitsplats.size());
		for (CustomizeALotHitsplat hitsplat : hitsplats)
		{
			if (hitsplat.isExpired(gameCycle)
				|| settings.hideZeroHitsplats() && hitsplat.getAmount() == 0
				|| settings.onlyDisplayMine() && !hitsplat.isMine())
			{
				continue;
			}

			visibleHitsplats.add(hitsplat);
		}

		if (visibleHitsplats.isEmpty())
		{
			return false;
		}

		Point anchor = actor.getCanvasImageLocation(ANCHOR_IMAGE, actor.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return true;
		}

		visibleHitsplats.sort(Comparator
			.comparing(CustomizeALotHitsplat::isMine)
			.thenComparingLong(CustomizeALotHitsplat::getSequence));
		List<PreparedHitsplat> preparedHitsplats = new ArrayList<>(visibleHitsplats.size());
		int slotWidth = CustomizeALotRenderLayout.scaleDimension(SLOT_WIDTH, scalePercent);
		int slotHeight = CustomizeALotRenderLayout.scaleDimension(SLOT_HEIGHT, scalePercent);
		int maxHitsplats = config.maxHitsplats();
		CustomizeALotSpriteScalingMode scalingMode = spriteScalingMode();
		boolean bossLike = plugin.isBossLikeTarget(actor);
		int minRadius = plugin.minRadiusFor(bossLike);
		int maxRadius = plugin.maxRadiusFor(bossLike);
		for (CustomizeALotHitsplat hitsplat : visibleHitsplats)
		{
			if (maxHitsplats > 0 && hitsplat.getPosition() >= maxHitsplats)
			{
				continue;
			}

			if (!CustomizeALotLayout.isPositionWithinRadiusLimit(
				hitsplat.getPosition(),
				minRadius,
				maxRadius,
				settings.layoutShape()))
			{
				continue;
			}

			PreparedHitsplat preparedHitsplat = prepareHitsplat(
				graphics,
				hitsplat,
				scalePercent,
				scalingMode);
			if (preparedHitsplat == null)
			{
				continue;
			}

			preparedHitsplats.add(preparedHitsplat);
			slotWidth = Math.max(slotWidth, preparedHitsplat.layout.getWidth());
			slotHeight = Math.max(slotHeight, preparedHitsplat.layout.getHeight());
		}

		SlotSize slotSize = actorSlotSizes.computeIfAbsent(actor, ignored -> new SlotSize());
		slotSize.growTo(slotWidth, slotHeight);
		for (PreparedHitsplat preparedHitsplat : preparedHitsplats)
		{
			CustomizeALotOffset offset = CustomizeALotLayout.offsetFor(
				preparedHitsplat.hitsplat.getPosition(),
				slotSize.width,
				slotSize.height,
				CustomizeALotRenderLayout.scaleDimension(SLOT_EDGE_CUT, scalePercent),
				settings.layoutShape(),
				settings.layoutDirection(),
				settings.layoutBehavior(),
				CustomizeALotRenderLayout.scaleCoordinate(settings.xSpacing(), scalePercent),
				CustomizeALotRenderLayout.scaleCoordinate(settings.ySpacing(), scalePercent),
				minRadius);
			drawHitsplat(
				graphics,
				preparedHitsplat,
				anchor.getX() + offset.getX() + xOffsetFor(bossLike),
				anchor.getY() + offset.getY() - yOffsetFor(bossLike),
				gameCycle);
		}

		return true;
	}

	private PreparedHitsplat prepareHitsplat(
		Graphics2D graphics,
		CustomizeALotHitsplat hitsplat,
		int scalePercent,
		CustomizeALotSpriteScalingMode scalingMode)
	{
		CustomizeALotHitmarkDefinition definition = getDefinition(hitsplat.getHitsplatType());
		if (definition == null || !definition.hasRenderableContent())
		{
			return null;
		}

		CustomizeALotSprite first = getSprite(definition.getFirstSpriteId());
		CustomizeALotSprite middle = getSprite(definition.getMiddleSpriteId());
		CustomizeALotSprite second = getSprite(definition.getSecondSpriteId());
		CustomizeALotSprite last = getSprite(definition.getLastSpriteId());

		String amountText = definition.formatAmount(hitsplat.getAmount());
		if (amountText.isEmpty() && first == null && middle == null && second == null && last == null)
		{
			return null;
		}

		CustomizeALotRenderLayout layout = CustomizeALotRenderLayout.create(
			first,
			middle,
			second,
			last,
			amountText,
			graphics.getFontMetrics(),
			definition.getTextYOffset(),
			scalePercent);
		return new PreparedHitsplat(
			hitsplat,
			definition,
			first,
			middle,
			second,
			last,
			amountText,
			layout,
			scalePercent,
			scalingMode);
	}

	private void drawHitsplat(Graphics2D graphics, PreparedHitsplat prepared, int centerX, int centerY, int gameCycle)
	{
		Graphics2D hitsplatGraphics = (Graphics2D) graphics.create();
		float alpha = prepared.hitsplat.getAlpha(gameCycle) * opacityMultiplier();
		try
		{
			hitsplatGraphics.setComposite(AlphaComposite.SrcOver.derive(alpha));
			hitsplatGraphics.setRenderingHint(
				RenderingHints.KEY_INTERPOLATION,
				prepared.scalingMode.getInterpolationHint());
			drawScaledSprite(
				hitsplatGraphics,
				prepared.first,
				centerX + prepared.layout.getFirstX(),
				centerY + prepared.layout.getSpriteY(),
				prepared.scalePercent,
				prepared.scalingMode);
			drawScaledSprite(
				hitsplatGraphics,
				prepared.second,
				centerX + prepared.layout.getSecondX(),
				centerY + prepared.layout.getSpriteY(),
				prepared.scalePercent,
				prepared.scalingMode);
			for (int i = 0; i < prepared.layout.getMiddleRepeatCount(); i++)
			{
				drawScaledSprite(
					hitsplatGraphics,
					prepared.middle,
					centerX + prepared.layout.getMiddleX()
						+ i * CustomizeALotRenderLayout.width(prepared.middle, prepared.scalePercent),
					centerY + prepared.layout.getSpriteY(),
					prepared.scalePercent,
					prepared.scalingMode);
			}
			drawScaledSprite(
				hitsplatGraphics,
				prepared.last,
				centerX + prepared.layout.getLastX(),
				centerY + prepared.layout.getSpriteY(),
				prepared.scalePercent,
				prepared.scalingMode);

			int shadowOffset = CustomizeALotRenderLayout.scaleDimension(1, prepared.scalePercent);
			hitsplatGraphics.setColor(Color.BLACK);
			hitsplatGraphics.drawString(
				prepared.amountText,
				centerX + prepared.layout.getTextX() + shadowOffset,
				centerY + prepared.layout.getTextBaseline() + shadowOffset);
			hitsplatGraphics.setColor(new Color(prepared.definition.getTextColor() | 0xFF000000, true));
			hitsplatGraphics.drawString(
				prepared.amountText,
				centerX + prepared.layout.getTextX(),
				centerY + prepared.layout.getTextBaseline());
		}
		finally
		{
			hitsplatGraphics.dispose();
		}
	}

	CustomizeALotHitmarkDefinition getDefinition(int hitsplatType)
	{
		return resolveDefinition(hitsplatType, client, hitmarkDefinitionLoader);
	}

	static CustomizeALotHitmarkDefinition resolveDefinition(
		int hitsplatType,
		Client client,
		IntFunction<CustomizeALotHitmarkDefinition> loader)
	{
		CustomizeALotHitmarkDefinition definition = loader.apply(hitsplatType);
		for (int depth = 0; definition != null && depth < MAX_TRANSFORM_DEPTH; depth++)
		{
			if (!definition.hasTransforms())
			{
				return definition;
			}

			int transformedType = definition.getTransformedType(client);
			if (transformedType == CustomizeALotHitmarkDefinition.TRANSFORM_UNAVAILABLE)
			{
				return definition;
			}
			if (transformedType < 0)
			{
				return null;
			}
			if (transformedType == hitsplatType)
			{
				return definition;
			}

			hitsplatType = transformedType;
			definition = loader.apply(hitsplatType);
		}

		return definition;
	}

	private CustomizeALotHitmarkDefinition loadDefinition(int hitsplatType)
	{
		return hitmarkDefinitions.get(
			hitsplatType,
			safeGameCycle(),
			id -> CustomizeALotHitmarkDefinition.load(client, id),
			CustomizeALotHitmarkDefinition::isFallbackDefinition);
	}

	private Integer loadNpcOverheadHeight(Integer npcDefinitionId)
	{
		return CustomizeALotNpcOverheadHeight.load(client, npcDefinitionId);
	}

	CustomizeALotSprite getSprite(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}

		return sprites.get(
			spriteId,
			safeGameCycle(),
			id -> CustomizeALotSprite.load(client, id),
			sprite -> sprite == null);
	}

	static int nativeAppearanceGameCycle(
		CustomizeALotHitmarkDefinition definition,
		int disappearsOnGameCycle,
		int eventGameCycle)
	{
		if (definition == null || definition.isFallbackDefinition())
		{
			// Native UI is suppressed, so a cache miss must not hide the hit until native expiry.
			return eventGameCycle;
		}

		return definition.appearanceGameCycle(disappearsOnGameCycle);
	}

	void clearCaches()
	{
		hitmarkDefinitions.clear();
		sprites.clear();
		npcOverheadHeights.clear();
		actorSlotSizes.clear();
		healthBarRenderer.clearCache();
		headIconRenderer.clearCache();
	}

	void removeActor(Actor actor)
	{
		actorSlotSizes.remove(actor);
		healthBarRenderer.remove(actor);
	}

	void recordHealthBarDamage(Actor actor, int hitsplatType, int amount)
	{
		healthBarRenderer.recordDamage(actor, hitsplatType, amount);
	}

	void clearHitsplatLayoutCache()
	{
		actorSlotSizes.clear();
	}

	private int safeGameCycle()
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

	static void drawSprite(Graphics2D graphics, CustomizeALotSprite sprite, int x, int y)
	{
		if (sprite != null)
		{
			graphics.drawImage(
				sprite.getImage(),
				x + sprite.getOffsetX(),
				y + sprite.getOffsetY(),
				null);
		}
	}

	static void drawScaledSprite(
		Graphics2D graphics,
		CustomizeALotSprite sprite,
		int x,
		int y,
		int scalePercent,
		CustomizeALotSpriteScalingMode scalingMode)
	{
		if (sprite == null)
		{
			return;
		}

		int targetWidth = CustomizeALotRenderLayout.scaleDimension(sprite.getWidth(), scalePercent);
		int targetHeight = CustomizeALotRenderLayout.scaleDimension(sprite.getHeight(), scalePercent);
		BufferedImage image = sprite.getImageForScaling(scalingMode, targetWidth, targetHeight);
		graphics.drawImage(
			image,
			x + CustomizeALotRenderLayout.scaleCoordinate(sprite.getOffsetX(), scalePercent),
			y + CustomizeALotRenderLayout.scaleCoordinate(sprite.getOffsetY(), scalePercent),
			targetWidth,
			targetHeight,
			null);
	}

	static Font scaleFont(Font font, int scalePercent)
	{
		float scaledSize = font.getSize2D()
			* CustomizeALotRenderLayout.clampScalePercent(scalePercent)
			/ 100.0f;
		return font.deriveFont(Math.max(1.0f, scaledSize));
	}

	private CustomizeALotSpriteScalingMode spriteScalingMode()
	{
		CustomizeALotSpriteScalingMode scalingMode = config.spriteScalingMode();
		return scalingMode == null ? CustomizeALotSpriteScalingMode.XBR : scalingMode;
	}

	private float opacityMultiplier()
	{
		return Math.max(0, Math.min(100, config.opacityPercent())) / 100.0f;
	}

	private int xOffsetFor(boolean bossLike)
	{
		return bossLike ? config.largeTargetXOffset() : 0;
	}

	private int yOffsetFor(boolean bossLike)
	{
		return bossLike ? config.largeTargetYOffset() : 0;
	}

	private static final class PreparedHitsplat
	{
		private final CustomizeALotHitsplat hitsplat;
		private final CustomizeALotHitmarkDefinition definition;
		private final CustomizeALotSprite first;
		private final CustomizeALotSprite middle;
		private final CustomizeALotSprite second;
		private final CustomizeALotSprite last;
		private final String amountText;
		private final CustomizeALotRenderLayout layout;
		private final int scalePercent;
		private final CustomizeALotSpriteScalingMode scalingMode;

		private PreparedHitsplat(
			CustomizeALotHitsplat hitsplat,
			CustomizeALotHitmarkDefinition definition,
			CustomizeALotSprite first,
			CustomizeALotSprite middle,
			CustomizeALotSprite second,
			CustomizeALotSprite last,
			String amountText,
			CustomizeALotRenderLayout layout,
			int scalePercent,
			CustomizeALotSpriteScalingMode scalingMode)
		{
			this.hitsplat = hitsplat;
			this.definition = definition;
			this.first = first;
			this.middle = middle;
			this.second = second;
			this.last = last;
			this.amountText = amountText;
			this.layout = layout;
			this.scalePercent = scalePercent;
			this.scalingMode = scalingMode;
		}
	}

	private static final class SlotSize
	{
		private int width;
		private int height;

		private void growTo(int width, int height)
		{
			this.width = Math.max(this.width, width);
			this.height = Math.max(this.height, height);
		}
	}
}

package com.customizealot;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.client.config.FontType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.Text;

final class CustomizeALotOverheadChatRenderer
{
	private static final int COMPONENT_GAP = 2;
	private static final int PLAYER_CHAT_LIFETIME_CYCLES = 150;
	private static final int NPC_CHAT_LIFETIME_CYCLES = 100;
	private static final int WAVE_AMPLITUDE = 5;
	private static final int SHAKE_AMPLITUDE = 7;
	private static final int SCROLL_WINDOW_WIDTH = 100;
	private static final int SLIDE_EDGE_CYCLES = 20;

	static Style captureStyle(CustomizeALotConfig config)
	{
		if (config == null)
		{
			return Style.defaults();
		}

		FontType fontType = config.overheadChatFont();
		return new Style(
			config.overheadChatEnabled(),
			config.showNpcOverheadChat(),
			fontFrom(fontType),
			config.overheadChatColor(),
			config.overheadChatRelationshipColors(),
			config.overheadChatFriendColor(),
			config.overheadChatClanColor(),
			config.overheadChatGroupIronColor(),
			config.overheadChatShadow(),
			config.overheadChatShadowColor(),
			config.overheadChatEffect(),
			config.overheadChatXOffset(),
			config.overheadChatYOffset());
	}

	private static Font fontFrom(FontType fontType)
	{
		if (fontType == null)
		{
			return FontManager.getRunescapeBoldFont();
		}

		String family = fontType.getFamily();
		if (family == null || family.isEmpty())
		{
			family = FontManager.getRunescapeBoldFont().getFamily();
		}
		int fontStyle = (fontType.isBold() ? Font.BOLD : Font.PLAIN)
			| (fontType.isItalic() ? Font.ITALIC : Font.PLAIN);
		return FontManager.getFallbackFont(family, fontStyle, Math.max(1, fontType.getSize()));
	}

	static boolean hasRenderableMessage(
		Actor actor,
		CustomizeALotLocalChatEffectTracker.MessageState messageState,
		Style style,
		Color textColor)
	{
		if (messageState == null || style == null || !style.shouldRender(actor instanceof NPC))
		{
			return false;
		}

		String overheadText = messageState.getText();
		if (messageState.getOverheadCycle() <= 0
			|| overheadText == null
			|| displayText(overheadText).isEmpty())
		{
			return false;
		}

		Color effectiveColor = textColor == null ? style.color : textColor;
		return effectiveColor.getAlpha() > 0
			|| style.shadow && style.shadowColor.getAlpha() > 0;
	}

	void render(
		Graphics2D graphics,
		Actor actor,
		Point actorTop,
		CustomizeALotLocalChatEffectTracker.MessageState messageState,
		Style style,
		Color textColor,
		CustomizeALotOverheadChatEffect effect,
		int gameCycle,
		int occupiedTopY,
		int occupiedBottomY,
		List<Rectangle> occupiedChatBounds)
	{
		if (actorTop == null
			|| !hasRenderableMessage(actor, messageState, style, textColor))
		{
			return;
		}

		String overheadText = messageState.getText();
		int overheadCycle = messageState.getOverheadCycle();
		int chatLifetimeCycles = chatLifetimeCycles(actor);
		String text = displayText(overheadText);
		Color effectiveColor = textColor == null ? style.color : textColor;

		CustomizeALotOverheadChatEffect effectiveEffect = effect == null
			? CustomizeALotOverheadChatEffect.STATIC
			: effect;
		Font oldFont = graphics.getFont();
		Color oldColor = graphics.getColor();
		try
		{
			graphics.setFont(style.font);
			FontMetrics fontMetrics = graphics.getFontMetrics();
			int textWidth = fontMetrics.stringWidth(text);
			int x = chatLeft(actorTop.getX(), textWidth, style.xOffset);
			GlyphVector animatedGlyphs = createAnimatedGlyphs(
				graphics,
				text,
				effectiveEffect,
				gameCycle,
				overheadCycle,
				chatLifetimeCycles);
			int y = chatBaseline(
				actorTop.getY(),
				style.yOffset);
			y = collisionFreeBaseline(
				x,
				y,
				textWidth,
				fontMetrics.getAscent(),
				fontMetrics.getDescent(),
				effectiveEffect,
				occupiedTopY,
				occupiedBottomY,
				occupiedChatBounds);
			if (occupiedChatBounds != null)
			{
				addOccupiedBounds(occupiedChatBounds, effectBounds(
					x,
					y,
					textWidth,
					fontMetrics.getAscent(),
					fontMetrics.getDescent(),
					effectiveEffect));
			}
			drawStyledEffect(
				graphics,
				text,
				x,
				y,
				textWidth,
				fontMetrics,
				effectiveEffect,
				animatedGlyphs,
				overheadCycle,
				chatLifetimeCycles,
				style,
				effectiveColor);
		}
		finally
		{
			graphics.setFont(oldFont);
			graphics.setColor(oldColor);
		}
	}

	static int chatLeft(int anchorX, int textWidth, int xOffset)
	{
		return anchorX - Math.max(0, textWidth) / 2 + xOffset;
	}

	static int chatLaneTop(
		int actorTopY,
		int ascent,
		CustomizeALotOverheadChatEffect effect)
	{
		return actorTopY
			+ COMPONENT_GAP
			- Math.max(0, ascent)
			- effectVerticalPadding(effect);
	}

	static int chatBaseline(int actorTopY, int yOffset)
	{
		return actorTopY + COMPONENT_GAP - yOffset;
	}

	static int chatLifetimeCycles(Actor actor)
	{
		return actor instanceof Player
			? PLAYER_CHAT_LIFETIME_CYCLES
			: NPC_CHAT_LIFETIME_CYCLES;
	}

	static String displayText(String overheadText)
	{
		if (overheadText == null)
		{
			return "";
		}

		return Text.removeFormattingTags(overheadText)
			.replace("<lt>", "<")
			.replace("<gt>", ">");
	}

	static Color relationshipColor(
		Style style,
		boolean groupIronMember,
		boolean friend,
		boolean clanMember)
	{
		Style effectiveStyle = style == null ? Style.defaults() : style;
		if (!effectiveStyle.relationshipColors)
		{
			return effectiveStyle.color;
		}
		if (groupIronMember)
		{
			return effectiveStyle.groupIronColor;
		}
		if (friend)
		{
			return effectiveStyle.friendColor;
		}
		if (clanMember)
		{
			return effectiveStyle.clanColor;
		}
		return effectiveStyle.color;
	}

	static void drawStyledEffect(
		Graphics2D graphics,
		String text,
		int x,
		int baseline,
		int textWidth,
		FontMetrics fontMetrics,
		CustomizeALotOverheadChatEffect effect,
		GlyphVector animatedGlyphs,
		int overheadCycle,
		int chatLifetimeCycles,
		Style style,
		Color textColor)
	{
		Color effectiveTextColor = textColor == null ? Color.YELLOW : textColor;
		Style effectiveStyle = style == null ? Style.defaults() : style;
		CustomizeALotOverheadChatEffect effectiveEffect = effect == null
			? CustomizeALotOverheadChatEffect.STATIC
			: effect;
		if (effectiveStyle.shadow && effectiveStyle.shadowColor.getAlpha() > 0)
		{
			drawEffect(
				graphics,
				text,
				x,
				baseline,
				textWidth,
				fontMetrics,
				effectiveEffect,
				animatedGlyphs,
				overheadCycle,
				chatLifetimeCycles,
				effectiveStyle.shadowColor,
				1,
				1);
		}

		if (effectiveTextColor.getAlpha() > 0)
		{
			drawEffect(
				graphics,
				text,
				x,
				baseline,
				textWidth,
				fontMetrics,
				effectiveEffect,
				animatedGlyphs,
				overheadCycle,
				chatLifetimeCycles,
				effectiveTextColor,
				0,
				0);
		}
	}

	static int collisionFreeBaseline(
		int x,
		int preferredY,
		int width,
		int ascent,
		int descent,
		List<Rectangle> occupiedBounds)
	{
		return collisionFreeBaseline(
			x,
			preferredY,
			width,
			ascent,
			descent,
			CustomizeALotOverheadChatEffect.STATIC,
			occupiedBounds);
	}

	static int collisionFreeBaseline(
		int x,
		int preferredY,
		int width,
		int ascent,
		int descent,
		CustomizeALotOverheadChatEffect effect,
		int occupiedTopY,
		int occupiedBottomY,
		List<Rectangle> occupiedBounds)
	{
		int baseline = collisionFreeBaseline(
			x,
			preferredY,
			width,
			ascent,
			descent,
			effect,
			occupiedBounds);
		if (baseline == preferredY
			|| occupiedTopY == CustomizeALotHealthBarRenderer.NO_OCCUPIED_TOP
			|| occupiedBottomY <= occupiedTopY)
		{
			return baseline;
		}

		Rectangle shiftedBounds = effectBounds(
			x,
			baseline,
			width,
			ascent,
			descent,
			effect);
		int shiftedBottom = shiftedBounds.y + shiftedBounds.height;
		if (shiftedBounds.y >= occupiedBottomY || shiftedBottom <= occupiedTopY)
		{
			return baseline;
		}

		int bottomOffset = shiftedBottom - baseline;
		int aboveOwnUi = occupiedTopY - COMPONENT_GAP - bottomOffset;
		return collisionFreeBaseline(
			x,
			aboveOwnUi,
			width,
			ascent,
			descent,
			effect,
			occupiedBounds);
	}

	static int collisionFreeBaseline(
		int x,
		int preferredY,
		int width,
		int ascent,
		int descent,
		CustomizeALotOverheadChatEffect effect,
		List<Rectangle> occupiedBounds)
	{
		if (occupiedBounds == null || occupiedBounds.isEmpty())
		{
			return preferredY;
		}

		int baseline = preferredY;
		List<Rectangle> orderedBounds = descendingTopOrder(occupiedBounds);
		Rectangle candidate = effectBounds(x, baseline, width, ascent, descent, effect);
		int candidateBottomOffset = candidate.y + candidate.height - baseline;
		for (Rectangle occupied : orderedBounds)
		{
			if (occupied != null && candidate.intersects(occupied))
			{
				baseline = occupied.y - COMPONENT_GAP - candidateBottomOffset;
				candidate = effectBounds(x, baseline, width, ascent, descent, effect);
			}
		}
		return baseline;
	}

	static void addOccupiedBounds(List<Rectangle> occupiedBounds, Rectangle bounds)
	{
		if (occupiedBounds == null || bounds == null)
		{
			return;
		}

		int low = 0;
		int high = occupiedBounds.size();
		while (low < high)
		{
			int middle = (low + high) >>> 1;
			Rectangle existing = occupiedBounds.get(middle);
			if (existing != null && existing.y >= bounds.y)
			{
				low = middle + 1;
			}
			else
			{
				high = middle;
			}
		}
		occupiedBounds.add(low, bounds);
	}

	private static List<Rectangle> descendingTopOrder(List<Rectangle> occupiedBounds)
	{
		int previousY = Integer.MAX_VALUE;
		boolean ordered = true;
		for (Rectangle bounds : occupiedBounds)
		{
			if (bounds == null)
			{
				continue;
			}
			if (bounds.y > previousY)
			{
				ordered = false;
				break;
			}
			previousY = bounds.y;
		}
		if (ordered)
		{
			return occupiedBounds;
		}

		List<Rectangle> sorted = new ArrayList<>(occupiedBounds);
		sorted.sort(Comparator.nullsLast(
			Comparator.comparingInt((Rectangle bounds) -> bounds.y).reversed()));
		return sorted;
	}

	static Rectangle effectBounds(
		int x,
		int baseline,
		int width,
		int ascent,
		int descent,
		CustomizeALotOverheadChatEffect effect)
	{
		CustomizeALotOverheadChatEffect effectiveEffect = effect == null
			? CustomizeALotOverheadChatEffect.STATIC
			: effect;
		int safeWidth = Math.max(0, width);
		int safeAscent = Math.max(0, ascent);
		int safeDescent = Math.max(0, descent);
		if (effectiveEffect == CustomizeALotOverheadChatEffect.SCROLL)
		{
			int centerX = x + safeWidth / 2;
			return new Rectangle(
				centerX - SCROLL_WINDOW_WIDTH / 2,
				baseline - safeAscent,
				SCROLL_WINDOW_WIDTH + 1,
				Math.max(1, safeAscent + safeDescent + 1));
		}

		int horizontalPadding = effectiveEffect == CustomizeALotOverheadChatEffect.WAVE_2
			? WAVE_AMPLITUDE
			: 0;
		int verticalPadding = effectVerticalPadding(effectiveEffect);

		return new Rectangle(
			x - horizontalPadding,
			baseline - safeAscent - verticalPadding,
			Math.max(1, safeWidth + horizontalPadding * 2 + 1),
			Math.max(1, safeAscent + safeDescent + verticalPadding * 2 + 1));
	}

	static int effectXOffset(
		CustomizeALotOverheadChatEffect effect,
		int characterIndex,
		int gameCycle)
	{
		if (effect != CustomizeALotOverheadChatEffect.WAVE_2)
		{
			return 0;
		}

		return (int) Math.round(Math.sin(
			characterIndex / 5.0 + gameCycle / 5.0) * WAVE_AMPLITUDE);
	}

	static int effectYOffset(
		CustomizeALotOverheadChatEffect effect,
		int characterIndex,
		int gameCycle,
		int overheadCycle)
	{
		return effectYOffset(
			effect,
			characterIndex,
			gameCycle,
			overheadCycle,
			NPC_CHAT_LIFETIME_CYCLES);
	}

	static int effectYOffset(
		CustomizeALotOverheadChatEffect effect,
		int characterIndex,
		int gameCycle,
		int overheadCycle,
		int chatLifetimeCycles)
	{
		double phaseCycle = gameCycle;
		if (effect == null)
		{
			return 0;
		}

		switch (effect)
		{
			case WAVE:
				return (int) Math.round(Math.sin(
					characterIndex / 2.0 + phaseCycle / 5.0) * WAVE_AMPLITUDE);
			case WAVE_2:
				return (int) Math.round(Math.sin(
					characterIndex / 3.0 + phaseCycle / 5.0) * WAVE_AMPLITUDE);
			case SHAKE:
				return (int) Math.round(Math.sin(
					characterIndex / 1.5 + phaseCycle)
					* shakeAmplitude(overheadCycle, chatLifetimeCycles));
			default:
				return 0;
		}
	}

	static int shakeAmplitude(int overheadCycle)
	{
		return shakeAmplitude(overheadCycle, NPC_CHAT_LIFETIME_CYCLES);
	}

	static int shakeAmplitude(int overheadCycle, int chatLifetimeCycles)
	{
		return Math.max(
			0,
			SHAKE_AMPLITUDE - effectAge(overheadCycle, chatLifetimeCycles) / 8);
	}

	static int scrollTextX(int textX, int textWidth, int overheadCycle)
	{
		return scrollTextX(
			textX,
			textWidth,
			overheadCycle,
			NPC_CHAT_LIFETIME_CYCLES);
	}

	static int scrollTextX(
		int textX,
		int textWidth,
		int overheadCycle,
		int chatLifetimeCycles)
	{
		int safeWidth = Math.max(0, textWidth);
		int centerX = textX + safeWidth / 2;
		int travel = safeWidth + SCROLL_WINDOW_WIDTH;
		int safeLifetime = Math.max(1, chatLifetimeCycles);
		int travelled = effectAge(overheadCycle, safeLifetime) * travel / safeLifetime;
		return centerX + SCROLL_WINDOW_WIDTH / 2 - travelled;
	}

	static int slideYOffset(int overheadCycle)
	{
		return slideYOffset(overheadCycle, NPC_CHAT_LIFETIME_CYCLES);
	}

	static int slideYOffset(int overheadCycle, int chatLifetimeCycles)
	{
		int safeLifetime = Math.max(1, chatLifetimeCycles);
		int age = effectAge(overheadCycle, safeLifetime);
		if (age < SLIDE_EDGE_CYCLES)
		{
			return age - SLIDE_EDGE_CYCLES;
		}
		if (age > safeLifetime - SLIDE_EDGE_CYCLES)
		{
			return age - (safeLifetime - SLIDE_EDGE_CYCLES);
		}
		return 0;
	}

	private static int effectAge(int overheadCycle, int chatLifetimeCycles)
	{
		int safeLifetime = Math.max(1, chatLifetimeCycles);
		return Math.max(0, Math.min(safeLifetime, safeLifetime - overheadCycle));
	}

	private static void drawEffect(
		Graphics2D graphics,
		String text,
		int x,
		int baseline,
		int textWidth,
		FontMetrics fontMetrics,
		CustomizeALotOverheadChatEffect effect,
		GlyphVector animatedGlyphs,
		int overheadCycle,
		int chatLifetimeCycles,
		Color color,
		int drawOffsetX,
		int drawOffsetY)
	{
		Graphics2D effectGraphics = (Graphics2D) graphics.create();
		try
		{
			CustomizeALotOverheadChatEffect effectiveEffect = effect == null
				? CustomizeALotOverheadChatEffect.STATIC
				: effect;
			if (color instanceof CustomizeALotLocalChatEffectTracker.PerGlyphColor)
			{
				drawPerGlyphEffect(
					effectGraphics,
					text,
					x,
					baseline,
					textWidth,
					fontMetrics,
					effectiveEffect,
					animatedGlyphs,
					overheadCycle,
					chatLifetimeCycles,
					(CustomizeALotLocalChatEffectTracker.PerGlyphColor) color,
					drawOffsetX,
					drawOffsetY);
				return;
			}

			effectGraphics.setColor(color);
			switch (effectiveEffect)
			{
				case WAVE:
				case WAVE_2:
				case SHAKE:
					if (animatedGlyphs != null)
					{
						effectGraphics.drawGlyphVector(
							animatedGlyphs,
							x + drawOffsetX,
							baseline + drawOffsetY);
					}
					break;
				case SCROLL:
					int scrollCenterX = x + textWidth / 2;
					effectGraphics.clip(new Rectangle(
						scrollCenterX - SCROLL_WINDOW_WIDTH / 2,
						baseline - fontMetrics.getAscent(),
						SCROLL_WINDOW_WIDTH,
						Math.max(1, fontMetrics.getAscent() + fontMetrics.getDescent() + 1)));
					effectGraphics.drawString(
						text,
						scrollTextX(
							x,
							textWidth,
							overheadCycle,
							chatLifetimeCycles) + drawOffsetX,
						baseline + drawOffsetY);
					break;
				case SLIDE:
					effectGraphics.clip(new Rectangle(
						x,
						baseline - fontMetrics.getAscent(),
						Math.max(1, textWidth + 1),
						Math.max(1, fontMetrics.getAscent() + fontMetrics.getDescent() + 1)));
					effectGraphics.drawString(
						text,
						x + drawOffsetX,
						baseline
							+ slideYOffset(overheadCycle, chatLifetimeCycles)
							+ drawOffsetY);
					break;
				case STATIC:
				default:
					effectGraphics.drawString(text, x + drawOffsetX, baseline + drawOffsetY);
			}
		}
		finally
		{
			effectGraphics.dispose();
		}
	}

	private static void drawPerGlyphEffect(
		Graphics2D graphics,
		String text,
		int x,
		int baseline,
		int textWidth,
		FontMetrics fontMetrics,
		CustomizeALotOverheadChatEffect effect,
		GlyphVector animatedGlyphs,
		int overheadCycle,
		int chatLifetimeCycles,
		CustomizeALotLocalChatEffectTracker.PerGlyphColor colors,
		int drawOffsetX,
		int drawOffsetY)
	{
		GlyphVector glyphs = animatedGlyphs == null
			? createGlyphs(graphics, text)
			: animatedGlyphs;
		int drawX = x + drawOffsetX;
		int drawY = baseline + drawOffsetY;
		if (effect == CustomizeALotOverheadChatEffect.SCROLL)
		{
			int scrollCenterX = x + textWidth / 2;
			graphics.clip(new Rectangle(
				scrollCenterX - SCROLL_WINDOW_WIDTH / 2,
				baseline - fontMetrics.getAscent(),
				SCROLL_WINDOW_WIDTH,
				Math.max(1, fontMetrics.getAscent() + fontMetrics.getDescent() + 1)));
			drawX = scrollTextX(
				x,
				textWidth,
				overheadCycle,
				chatLifetimeCycles) + drawOffsetX;
		}
		else if (effect == CustomizeALotOverheadChatEffect.SLIDE)
		{
			graphics.clip(new Rectangle(
				x,
				baseline - fontMetrics.getAscent(),
				Math.max(1, textWidth + 1),
				Math.max(1, fontMetrics.getAscent() + fontMetrics.getDescent() + 1)));
			drawY += slideYOffset(overheadCycle, chatLifetimeCycles);
		}

		int glyphCount = glyphs.getNumGlyphs();
		for (int glyphIndex = 0; glyphIndex < glyphCount; glyphIndex++)
		{
			graphics.setColor(colors.colorForGlyph(glyphIndex, glyphCount));
			graphics.fill(glyphs.getGlyphOutline(glyphIndex, drawX, drawY));
		}
	}

	static GlyphVector createAnimatedGlyphs(
		Graphics2D graphics,
		String text,
		CustomizeALotOverheadChatEffect effect,
		int gameCycle,
		int overheadCycle,
		int chatLifetimeCycles)
	{
		if (effect != CustomizeALotOverheadChatEffect.WAVE
			&& effect != CustomizeALotOverheadChatEffect.WAVE_2
			&& effect != CustomizeALotOverheadChatEffect.SHAKE)
		{
			return null;
		}

		GlyphVector glyphs = createGlyphs(graphics, text);
		int glyphCount = glyphs.getNumGlyphs();
		float[] positions = glyphs.getGlyphPositions(0, glyphCount + 1, null);
		Point2D.Float adjustedPosition = new Point2D.Float();
		for (int glyphIndex = 0; glyphIndex < glyphCount; glyphIndex++)
		{
			adjustedPosition.setLocation(
				positions[glyphIndex * 2] + effectXOffset(effect, glyphIndex, gameCycle),
				positions[glyphIndex * 2 + 1]
					+ effectYOffset(
						effect,
						glyphIndex,
						gameCycle,
						overheadCycle,
						chatLifetimeCycles));
			glyphs.setGlyphPosition(glyphIndex, adjustedPosition);
		}
		return glyphs;
	}

	private static GlyphVector createGlyphs(Graphics2D graphics, String text)
	{
		char[] characters = text.toCharArray();
		return graphics.getFont().layoutGlyphVector(
			graphics.getFontRenderContext(),
			characters,
			0,
			characters.length,
			Font.LAYOUT_LEFT_TO_RIGHT);
	}

	private static int effectVerticalPadding(CustomizeALotOverheadChatEffect effect)
	{
		if (effect == CustomizeALotOverheadChatEffect.WAVE
			|| effect == CustomizeALotOverheadChatEffect.WAVE_2)
		{
			return WAVE_AMPLITUDE;
		}
		if (effect == CustomizeALotOverheadChatEffect.SHAKE)
		{
			return SHAKE_AMPLITUDE;
		}
		return 0;
	}

	static final class Style
	{
		private final boolean enabled;
		private final boolean showNpcOverheadChat;
		private final Font font;
		private final Color color;
		private final boolean relationshipColors;
		private final Color friendColor;
		private final Color clanColor;
		private final Color groupIronColor;
		private final boolean shadow;
		private final Color shadowColor;
		private final CustomizeALotOverheadChatEffect fallbackEffect;
		private final int xOffset;
		private final int yOffset;

		private Style(
			boolean enabled,
			boolean showNpcOverheadChat,
			Font font,
			Color color,
			boolean relationshipColors,
			Color friendColor,
			Color clanColor,
			Color groupIronColor,
			boolean shadow,
			Color shadowColor,
			CustomizeALotOverheadChatEffect fallbackEffect,
			int xOffset,
			int yOffset)
		{
			this.enabled = enabled;
			this.showNpcOverheadChat = showNpcOverheadChat;
			this.font = font == null ? FontManager.getRunescapeBoldFont() : font;
			this.color = color == null ? Color.YELLOW : color;
			this.relationshipColors = relationshipColors;
			this.friendColor = friendColor == null ? this.color : friendColor;
			this.clanColor = clanColor == null ? this.color : clanColor;
			this.groupIronColor = groupIronColor == null ? this.color : groupIronColor;
			this.shadow = shadow;
			this.shadowColor = shadowColor == null ? Color.BLACK : shadowColor;
			this.fallbackEffect = fallbackEffect == null
				? CustomizeALotOverheadChatEffect.STATIC
				: fallbackEffect;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}

		private static Style defaults()
		{
			return new Style(
				true,
				true,
				FontManager.getRunescapeBoldFont(),
				Color.YELLOW,
				false,
				Color.YELLOW,
				Color.YELLOW,
				Color.YELLOW,
				true,
				Color.BLACK,
				CustomizeALotOverheadChatEffect.STATIC,
				0,
				0);
		}

		boolean shouldRender(boolean npc)
		{
			return enabled && (!npc || showNpcOverheadChat);
		}

		Font getFont()
		{
			return font;
		}

		Color getColor()
		{
			return color;
		}

		boolean usesRelationshipColors()
		{
			return relationshipColors;
		}

		boolean hasShadow()
		{
			return shadow;
		}

		Color getShadowColor()
		{
			return shadowColor;
		}

		CustomizeALotOverheadChatEffect getFallbackEffect()
		{
			return fallbackEffect;
		}

		int getXOffset()
		{
			return xOffset;
		}

		int getYOffset()
		{
			return yOffset;
		}
	}
}

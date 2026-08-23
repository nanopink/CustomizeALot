package com.customizealot;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.client.util.Text;

/**
 * Correlates the local player's raw public-chat input with the resulting
 * overhead text, and retains the local-only color/effect metadata which the
 * public Actor API does not expose. It also bridges the native text/lifetime
 * fields during their first-frame write-order gap.
 */
@Singleton
final class CustomizeALotLocalChatEffectTracker
{
	private static final int PUBLIC_CHAT_TYPE = 0;
	private static final int MAX_PENDING_AGE_CYCLES = 100;
	private static final int MAX_ACTIVE_AGE_CYCLES = 150;
	private static final int MAX_OBSERVED_AGE_CYCLES = 150;
	private static final int MAX_PENDING_MESSAGES = 8;
	private static final String PATTERN_PREFIX = "pattern";
	private static final Color FLASH_3_DARK = new Color(0, 176, 0);
	private static final Color FLASH_3_LIGHT = new Color(128, 255, 128);
	private static final int[] PATTERN_COLORS = {
		0xFFFFFF, 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E,
		0x732982, 0xFF218C, 0xB55690, 0x5049CC, 0xA3A3A3, 0xD52D00,
		0xEF7627, 0xFCF434, 0x078D70, 0x21B1FF, 0x9B4F96, 0xFFAFC7,
		0xD162A4, 0x7BADE3, 0xFF9A56, 0x26CEAA, 0x73D7EE, 0x9C59D1,
		0x98E8C1, 0xB57EDC, 0x2C2C2C, 0x940202, 0x613915, 0xD0C100,
		0x4A8123, 0x0038A8, 0x800080, 0xD60270, 0xA30262, 0x3D1A78
	};
	private static final Set<String> COLOR_PREFIXES = Set.of(
		"yellow",
		"red",
		"green",
		"cyan",
		"purple",
		"white",
		"flash1",
		"flash2",
		"flash3",
		"glow1",
		"glow2",
		"glow3",
		"rainbow");

	private final Deque<PendingEffect> pendingEffects = new ArrayDeque<>();
	private final Map<Object, ObservedOverhead> observedOverheads = new IdentityHashMap<>();
	private volatile ActiveEffect activeEffect;

	synchronized void recordOutgoing(
		String input,
		int chatType,
		boolean consumed,
		int gameCycle)
	{
		prunePending(gameCycle);
		if (consumed || chatType != PUBLIC_CHAT_TYPE)
		{
			return;
		}

		ParsedInput parsed = parseInput(input);
		if (parsed.correlationMessages.isEmpty())
		{
			return;
		}

		while (pendingEffects.size() >= MAX_PENDING_MESSAGES)
		{
			pendingEffects.removeFirst();
		}
		pendingEffects.addLast(new PendingEffect(
			parsed.effect,
			parsed.colorOverride,
			parsed.animatedColorPrefix,
			parsed.correlationMessages,
			gameCycle));
	}

	synchronized void recordOverhead(
		Object actor,
		Object localPlayer,
		String overheadText,
		int gameCycle)
	{
		prunePending(gameCycle);
		pruneObserved(gameCycle);
		rememberOverhead(actor, overheadText, gameCycle);
		if (actor == null || actor != localPlayer)
		{
			return;
		}

		// Every new local overhead message supersedes the previous correlation,
		// including a message sent without an effect prefix.
		activeEffect = null;
		String normalizedMessage = normalizeMessage(overheadText);
		if (normalizedMessage.isEmpty())
		{
			return;
		}

		Iterator<PendingEffect> iterator = pendingEffects.iterator();
		while (iterator.hasNext())
		{
			PendingEffect pending = iterator.next();
			if (pending.correlationMessages.contains(normalizedMessage))
			{
				iterator.remove();
				if (pending.effect != null
					|| pending.colorOverride != null
					|| pending.animatedColorPrefix != null)
				{
					activeEffect = new ActiveEffect(
						actor,
						normalizedMessage,
						pending.effect,
						pending.colorOverride,
						pending.animatedColorPrefix,
						gameCycle);
				}
				return;
			}
		}
	}

	/**
	 * Returns a coherent text/lifetime snapshot for rendering. The text-change
	 * hook and the native remaining-cycle field are separate writes, so the
	 * latter can still be zero or belong to the previous message during the
	 * first replacement frame. A matching event snapshot bridges only that
	 * short window; the live actor state remains authoritative afterwards.
	 */
	synchronized MessageState messageStateFor(
		Object actor,
		String liveText,
		int liveOverheadCycle,
		int gameCycle,
		int expectedLifetimeCycles)
	{
		pruneObserved(gameCycle);
		String effectiveText = liveText;
		int effectiveCycle = liveOverheadCycle;
		ObservedOverhead observed = observedOverheads.get(actor);
		if (observed == null)
		{
			return new MessageState(effectiveText, effectiveCycle, false);
		}

		String normalizedLiveText = normalizeMessage(liveText);
		if (!normalizedLiveText.isEmpty() && !observed.normalizedText.equals(normalizedLiveText))
		{
			observedOverheads.remove(actor);
			return new MessageState(effectiveText, effectiveCycle, false);
		}

		int age = gameCycle - observed.observedOnGameCycle;
		int lifetime = Math.max(1, expectedLifetimeCycles);
		int eventRemaining = Math.max(0, lifetime - Math.max(0, age));
		if (normalizedLiveText.isEmpty() && eventRemaining > 0)
		{
			effectiveText = observed.text;
		}
		if (eventRemaining > effectiveCycle)
		{
			effectiveCycle = eventRemaining;
		}
		return new MessageState(effectiveText, effectiveCycle, true);
	}

	CustomizeALotOverheadChatEffect effectFor(
		Object actor,
		String overheadText,
		CustomizeALotOverheadChatEffect fallback,
		int gameCycle)
	{
		CustomizeALotOverheadChatEffect effectiveFallback = fallback == null
			? CustomizeALotOverheadChatEffect.STATIC
			: fallback;
		ActiveEffect active = activeFor(actor, overheadText, gameCycle);
		if (active == null || active.effect == null)
		{
			return effectiveFallback;
		}

		return active.effect;
	}

	Color colorFor(
		Object actor,
		String overheadText,
		Color fallback,
		int gameCycle)
	{
		Color effectiveFallback = fallback == null ? Color.YELLOW : fallback;
		ActiveEffect active = activeFor(actor, overheadText, gameCycle);
		if (active == null)
		{
			return effectiveFallback;
		}
		Color resolved = active.colorOverride;
		if (resolved == null && active.animatedColorPrefix != null)
		{
			PerGlyphColor perGlyphColor = PerGlyphColor.forPrefix(
				active.animatedColorPrefix,
				effectiveFallback);
			if (perGlyphColor != null)
			{
				return perGlyphColor;
			}
			resolved = animatedColorForPrefix(
				active.animatedColorPrefix,
				gameCycle,
				gameCycle - active.matchedOnGameCycle);
		}
		if (resolved == null)
		{
			return effectiveFallback;
		}

		return new Color(
			(resolved.getRGB() & 0x00FFFFFF)
				| (effectiveFallback.getAlpha() << 24),
			true);
	}

	private ActiveEffect activeFor(Object actor, String overheadText, int gameCycle)
	{
		ActiveEffect active = activeEffect;
		return active != null
			&& actor == active.actor
			&& withinAge(gameCycle, active.matchedOnGameCycle, MAX_ACTIVE_AGE_CYCLES)
			&& active.message.equals(normalizeMessage(overheadText))
			? active
			: null;
	}

	synchronized void remove(Object actor)
	{
		observedOverheads.remove(actor);
		ActiveEffect active = activeEffect;
		if (active != null && active.actor == actor)
		{
			activeEffect = null;
		}
	}

	synchronized void clear()
	{
		pendingEffects.clear();
		observedOverheads.clear();
		activeEffect = null;
	}

	static ParsedInput parseInput(String input)
	{
		String remaining = input == null ? "" : input.trim();
		CustomizeALotOverheadChatEffect effect = null;
		Color colorOverride = null;
		String animatedColorPrefix = null;
		boolean colorSeen = false;
		boolean effectWasFirst = false;
		String trailingColorPrefix = null;

		for (int prefixCount = 0; prefixCount < 2; prefixCount++)
		{
			int separator = remaining.indexOf(':');
			if (separator <= 0)
			{
				break;
			}

			String prefix = remaining.substring(0, separator);
			CustomizeALotOverheadChatEffect parsedEffect = effectForPrefix(prefix);
			if (effect == null && parsedEffect != null)
			{
				effect = parsedEffect;
				effectWasFirst = prefixCount == 0;
				remaining = remaining.substring(separator + 1);
				continue;
			}

			if (!colorSeen && isColorPrefix(prefix))
			{
				colorSeen = true;
				colorOverride = fixedColorForPrefix(prefix);
				animatedColorPrefix = colorOverride == null
					? supportedAnimatedColorPrefix(prefix)
					: null;
				if (effectWasFirst)
				{
					trailingColorPrefix = prefix;
				}
				remaining = remaining.substring(separator + 1);
				continue;
			}

			break;
		}

		List<String> correlationMessages = new ArrayList<>(2);
		addOutgoingNormalized(correlationMessages, remaining);
		if (effectWasFirst && trailingColorPrefix != null)
		{
			// The game traditionally requires color before effect. If it accepts
			// the effect first but leaves the following color token as text, this
			// alternate still correlates only the exact outgoing message.
			addOutgoingNormalized(correlationMessages, trailingColorPrefix + ':' + remaining);
		}
		return new ParsedInput(
			effect,
			colorOverride,
			animatedColorPrefix,
			remaining,
			correlationMessages);
	}

	private synchronized void prunePending(int gameCycle)
	{
		pendingEffects.removeIf(pending -> !withinAge(
			gameCycle,
			pending.recordedOnGameCycle,
			MAX_PENDING_AGE_CYCLES));
	}

	private void rememberOverhead(Object actor, String overheadText, int gameCycle)
	{
		if (actor == null)
		{
			return;
		}

		String normalizedText = normalizeMessage(overheadText);
		if (normalizedText.isEmpty())
		{
			observedOverheads.remove(actor);
			return;
		}
		observedOverheads.put(actor, new ObservedOverhead(
			overheadText,
			normalizedText,
			gameCycle));
	}

	private void pruneObserved(int gameCycle)
	{
		observedOverheads.entrySet().removeIf(entry -> !withinAge(
			gameCycle,
			entry.getValue().observedOnGameCycle,
			MAX_OBSERVED_AGE_CYCLES));
	}

	private static boolean withinAge(int currentCycle, int earlierCycle, int maximumAge)
	{
		int age = currentCycle - earlierCycle;
		return age >= 0 && age <= maximumAge;
	}

	private static CustomizeALotOverheadChatEffect effectForPrefix(String prefix)
	{
		if (prefix == null)
		{
			return null;
		}

		switch (prefix.toLowerCase(Locale.ROOT))
		{
			case "wave":
				return CustomizeALotOverheadChatEffect.WAVE;
			case "wave2":
				return CustomizeALotOverheadChatEffect.WAVE_2;
			case "shake":
				return CustomizeALotOverheadChatEffect.SHAKE;
			case "scroll":
				return CustomizeALotOverheadChatEffect.SCROLL;
			case "slide":
				return CustomizeALotOverheadChatEffect.SLIDE;
			default:
				return null;
		}
	}

	private static boolean isColorPrefix(String prefix)
	{
		if (prefix == null)
		{
			return false;
		}

		String normalized = prefix.toLowerCase(Locale.ROOT);
		if (COLOR_PREFIXES.contains(normalized))
		{
			return true;
		}
		if (!normalized.startsWith(PATTERN_PREFIX))
		{
			return false;
		}

		String pattern = normalized.substring(PATTERN_PREFIX.length());
		if (pattern.isEmpty() || pattern.length() > 8)
		{
			return false;
		}
		for (int i = 0; i < pattern.length(); i++)
		{
			char character = pattern.charAt(i);
			if (character > 127 || !Character.isLetterOrDigit(character))
			{
				return false;
			}
		}
		return true;
	}

	private static Color fixedColorForPrefix(String prefix)
	{
		if (prefix == null)
		{
			return null;
		}

		switch (prefix.toLowerCase(Locale.ROOT))
		{
			case "yellow":
				return Color.YELLOW;
			case "red":
				return Color.RED;
			case "green":
				return Color.GREEN;
			case "cyan":
				return Color.CYAN;
			case "purple":
				return Color.MAGENTA;
			case "white":
				return Color.WHITE;
			default:
				return null;
		}
	}

	private static String supportedAnimatedColorPrefix(String prefix)
	{
		if (prefix == null)
		{
			return null;
		}

		String normalized = prefix.toLowerCase(Locale.ROOT);
		return normalized.startsWith("flash")
			|| normalized.startsWith("glow")
			|| normalized.equals("rainbow")
			|| normalized.startsWith(PATTERN_PREFIX)
			? normalized
			: null;
	}

	static Color animatedColorForPrefix(String prefix, int gameCycle, int ageCycles)
	{
		if (prefix == null)
		{
			return null;
		}

		boolean firstPhase = Math.floorMod(gameCycle, 20) < 10;
		int age = Math.max(0, Math.min(MAX_ACTIVE_AGE_CYCLES - 1, ageCycles));
		switch (prefix.toLowerCase(Locale.ROOT))
		{
			case "flash1":
				return firstPhase ? Color.RED : Color.YELLOW;
			case "flash2":
				return firstPhase ? Color.BLUE : Color.CYAN;
			case "flash3":
				return firstPhase ? FLASH_3_DARK : FLASH_3_LIGHT;
			case "glow1":
				if (age < 50)
				{
					return rgb(0xFF0000 + age * 0x000500);
				}
				if (age < 100)
				{
					return rgb(0xFFFF00 - (age - 50) * 0x050000);
				}
				return rgb(0x00FF00 + (age - 100) * 0x000005);
			case "glow2":
				if (age < 50)
				{
					return rgb(0xFF0000 + age * 0x000005);
				}
				if (age < 100)
				{
					return rgb(0xFF00FF - (age - 50) * 0x050000);
				}
				return rgb(0x0000FF
					+ (age - 100) * 0x050000
					- (age - 100) * 0x000005);
			case "glow3":
				if (age < 50)
				{
					return rgb(0xFFFFFF - age * 0x050005);
				}
				if (age < 100)
				{
					return rgb(0x00FF00 + (age - 50) * 0x050005);
				}
				return rgb(0xFFFFFF - (age - 100) * 0x050000);
			default:
				return null;
		}
	}

	private static Color rgb(int rgb)
	{
		return new Color(rgb & 0x00FFFFFF);
	}

	private static int patternColorIndex(char character)
	{
		char normalized = Character.toLowerCase(character);
		if (normalized >= '0' && normalized <= '9')
		{
			return normalized - '0';
		}
		if (normalized >= 'a' && normalized <= 'z')
		{
			return normalized - 'a' + 10;
		}
		return -1;
	}

	static int rainbowRgbForStep(int hueStep)
	{
		// This is the current client's fixed 16-bit HSL chat palette lookup
		// for (hueStep << 10) | (7 << 7) | 64. Keep the same hue-bin
		// centers, saturation/lightness, and 0.95 palette exponent.
		double hue = Math.floorMod(hueStep, 64) / 64.0 + 0.0078125;
		double saturation = 0.9375;
		double lightness = 0.5;
		double high = saturation + lightness - saturation * lightness;
		double low = 2.0 * lightness - high;
		int red = paletteComponent(hue + 1.0 / 3.0, low, high);
		int green = paletteComponent(hue, low, high);
		int blue = paletteComponent(hue - 1.0 / 3.0, low, high);
		return red << 16 | green << 8 | blue;
	}

	private static int paletteComponent(double hue, double low, double high)
	{
		if (hue < 0.0)
		{
			hue += 1.0;
		}
		if (hue > 1.0)
		{
			hue -= 1.0;
		}

		double component;
		if (6.0 * hue < 1.0)
		{
			component = low + (high - low) * 6.0 * hue;
		}
		else if (2.0 * hue < 1.0)
		{
			component = high;
		}
		else if (3.0 * hue < 2.0)
		{
			component = low + (high - low) * (2.0 / 3.0 - hue) * 6.0;
		}
		else
		{
			component = low;
		}
		return (int) (Math.pow(component, 0.949999988079071) * 256.0);
	}

	private static String normalizeMessage(String message)
	{
		if (message == null)
		{
			return "";
		}
		return Text.unescapeJagex(Text.removeFormattingTags(message))
			.replace('\u00A0', ' ')
			.trim()
			.toLowerCase(Locale.ROOT);
	}

	private static void addOutgoingNormalized(List<String> messages, String message)
	{
		String normalized = normalizeMessage(Text.escapeJagex(message == null ? "" : message));
		if (!normalized.isEmpty() && !messages.contains(normalized))
		{
			messages.add(normalized);
		}
	}

	static final class ParsedInput
	{
		private final CustomizeALotOverheadChatEffect effect;
		private final Color colorOverride;
		private final String animatedColorPrefix;
		private final String message;
		private final List<String> correlationMessages;

		private ParsedInput(
			CustomizeALotOverheadChatEffect effect,
			Color colorOverride,
			String animatedColorPrefix,
			String message,
			List<String> correlationMessages)
		{
			this.effect = effect;
			this.colorOverride = colorOverride;
			this.animatedColorPrefix = animatedColorPrefix;
			this.message = message;
			this.correlationMessages = List.copyOf(correlationMessages);
		}

		CustomizeALotOverheadChatEffect getEffect()
		{
			return effect;
		}

		Color getColorOverride()
		{
			return colorOverride;
		}

		String getAnimatedColorPrefix()
		{
			return animatedColorPrefix;
		}

		String getMessage()
		{
			return message;
		}
	}

	static final class MessageState
	{
		private final String text;
		private final int overheadCycle;
		private final boolean eventBacked;

		private MessageState(String text, int overheadCycle, boolean eventBacked)
		{
			this.text = text;
			this.overheadCycle = overheadCycle;
			this.eventBacked = eventBacked;
		}

		String getText()
		{
			return text;
		}

		int getOverheadCycle()
		{
			return overheadCycle;
		}

		boolean isEventBacked()
		{
			return eventBacked;
		}
	}

	/**
	 * A Color subtype used as a small transport object between the tracker and
	 * renderer. The ordinary Color value remains the configured fallback so
	 * existing visibility/alpha handling stays intact; the renderer recognizes
	 * this subtype and applies its local per-glyph colors.
	 */
	static final class PerGlyphColor extends Color
	{
		private final boolean rainbow;
		private final int[] patternColors;
		private final int alpha;

		private PerGlyphColor(
			Color fallback,
			boolean rainbow,
			int[] patternColors)
		{
			super(fallback.getRGB(), true);
			this.rainbow = rainbow;
			this.patternColors = patternColors;
			this.alpha = fallback.getAlpha();
		}

		private static PerGlyphColor forPrefix(String prefix, Color fallback)
		{
			if (prefix == null || fallback == null)
			{
				return null;
			}

			String normalized = prefix.toLowerCase(Locale.ROOT);
			if (normalized.equals("rainbow"))
			{
				return new PerGlyphColor(fallback, true, null);
			}
			if (!normalized.startsWith(PATTERN_PREFIX))
			{
				return null;
			}

			String pattern = normalized.substring(PATTERN_PREFIX.length());
			if (pattern.isEmpty() || pattern.length() > 8)
			{
				return null;
			}
			int[] colors = new int[pattern.length()];
			for (int i = 0; i < pattern.length(); i++)
			{
				int colorIndex = patternColorIndex(pattern.charAt(i));
				if (colorIndex < 0)
				{
					return null;
				}
				colors[i] = PATTERN_COLORS[colorIndex];
			}
			return new PerGlyphColor(fallback, false, colors);
		}

		Color colorForGlyph(int glyphIndex, int glyphCount)
		{
			if (glyphCount <= 0)
			{
				return this;
			}

			int safeGlyphIndex = Math.max(0, Math.min(glyphCount - 1, glyphIndex));
			int rgb;
			if (rainbow)
			{
				// The native renderer quantizes the message-wide hue ramp to
				// 64 steps before looking it up in its fixed chat palette.
				int hueStep = (int) (64.0f * safeGlyphIndex / glyphCount);
				rgb = rainbowRgbForStep(hueStep);
			}
			else
			{
				int patternIndex = (int) ((long) safeGlyphIndex
					* patternColors.length / glyphCount);
				rgb = patternColors[Math.min(patternColors.length - 1, patternIndex)];
			}
			return new Color(
				(rgb & 0x00FFFFFF) | (alpha << 24),
				true);
		}

		boolean isRainbow()
		{
			return rainbow;
		}

		int patternLength()
		{
			return patternColors == null ? 0 : patternColors.length;
		}
	}

	private static final class PendingEffect
	{
		private final CustomizeALotOverheadChatEffect effect;
		private final Color colorOverride;
		private final String animatedColorPrefix;
		private final List<String> correlationMessages;
		private final int recordedOnGameCycle;

		private PendingEffect(
			CustomizeALotOverheadChatEffect effect,
			Color colorOverride,
			String animatedColorPrefix,
			List<String> correlationMessages,
			int recordedOnGameCycle)
		{
			this.effect = effect;
			this.colorOverride = colorOverride;
			this.animatedColorPrefix = animatedColorPrefix;
			this.correlationMessages = correlationMessages;
			this.recordedOnGameCycle = recordedOnGameCycle;
		}
	}

	private static final class ObservedOverhead
	{
		private final String text;
		private final String normalizedText;
		private final int observedOnGameCycle;

		private ObservedOverhead(
			String text,
			String normalizedText,
			int observedOnGameCycle)
		{
			this.text = text;
			this.normalizedText = normalizedText;
			this.observedOnGameCycle = observedOnGameCycle;
		}
	}

	private static final class ActiveEffect
	{
		private final Object actor;
		private final String message;
		private final CustomizeALotOverheadChatEffect effect;
		private final Color colorOverride;
		private final String animatedColorPrefix;
		private final int matchedOnGameCycle;

		private ActiveEffect(
			Object actor,
			String message,
			CustomizeALotOverheadChatEffect effect,
			Color colorOverride,
			String animatedColorPrefix,
			int matchedOnGameCycle)
		{
			this.actor = actor;
			this.message = message;
			this.effect = effect;
			this.colorOverride = colorOverride;
			this.animatedColorPrefix = animatedColorPrefix;
			this.matchedOnGameCycle = matchedOnGameCycle;
		}
	}
}

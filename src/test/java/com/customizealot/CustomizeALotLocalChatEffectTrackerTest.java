package com.customizealot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CustomizeALotLocalChatEffectTrackerTest
{
	@Test
	public void parsesEverySupportedEffectPrefix()
	{
		Map<String, CustomizeALotOverheadChatEffect> effects = Map.of(
			"wave", CustomizeALotOverheadChatEffect.WAVE,
			"wave2", CustomizeALotOverheadChatEffect.WAVE_2,
			"shake", CustomizeALotOverheadChatEffect.SHAKE,
			"scroll", CustomizeALotOverheadChatEffect.SCROLL,
			"slide", CustomizeALotOverheadChatEffect.SLIDE);

		for (Map.Entry<String, CustomizeALotOverheadChatEffect> entry : effects.entrySet())
		{
			CustomizeALotLocalChatEffectTracker.ParsedInput parsed =
				CustomizeALotLocalChatEffectTracker.parseInput(
					entry.getKey().toUpperCase() + ":Hello there");
			assertEquals(entry.getValue(), parsed.getEffect());
			assertEquals("Hello there", parsed.getMessage());
		}
	}

	@Test
	public void acceptsRuneScapeColorBeforeOrAfterEffect()
	{
		List<String> colors = List.of(
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
			"rainbow",
			"patternq3q3");

		for (String color : colors)
		{
			CustomizeALotLocalChatEffectTracker.ParsedInput colorFirst =
				CustomizeALotLocalChatEffectTracker.parseInput(
					color + ":wave2:Message");
			CustomizeALotLocalChatEffectTracker.ParsedInput effectFirst =
				CustomizeALotLocalChatEffectTracker.parseInput(
					"wave2:" + color + ":Message");
			assertEquals(CustomizeALotOverheadChatEffect.WAVE_2, colorFirst.getEffect());
			assertEquals("Message", colorFirst.getMessage());
			assertEquals(CustomizeALotOverheadChatEffect.WAVE_2, effectFirst.getEffect());
			assertEquals("Message", effectFirst.getMessage());
		}
	}

	@Test
	public void preservesFixedLocalColorPrefixesAndConfiguredAlpha()
	{
		Map<String, Color> colors = Map.of(
			"yellow", Color.YELLOW,
			"red", Color.RED,
			"green", Color.GREEN,
			"cyan", Color.CYAN,
			"purple", Color.MAGENTA,
			"white", Color.WHITE);
		for (Map.Entry<String, Color> entry : colors.entrySet())
		{
			assertEquals(
				entry.getValue(),
				CustomizeALotLocalChatEffectTracker.parseInput(
					entry.getKey() + ":wave:Hello").getColorOverride());
		}

		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();
		tracker.recordOutgoing("red:wave:Hello", 0, false, 100);
		tracker.recordOverhead(local, local, "Hello", 101);

		Color resolved = tracker.colorFor(local, "Hello", new Color(1, 2, 3, 77), 101);
		assertEquals(new Color(255, 0, 0, 77), resolved);
		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(local, "Hello", CustomizeALotOverheadChatEffect.STATIC, 101));
	}

	@Test
	public void flashAndGlowPrefixesAreReconstructedWithConfiguredAlpha()
	{
		for (String prefix : List.of("flash1", "flash2", "flash3", "glow1", "glow2", "glow3"))
		{
			CustomizeALotLocalChatEffectTracker.ParsedInput parsed =
				CustomizeALotLocalChatEffectTracker.parseInput(prefix + ":wave:Hello");
			assertNull(parsed.getColorOverride());
			assertEquals(prefix, parsed.getAnimatedColorPrefix());
		}

		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();
		Color fallback = new Color(12, 34, 56, 78);
		tracker.recordOutgoing("flash1:wave:Hello", 0, false, 100);
		tracker.recordOverhead(local, local, "Hello", 101);
		assertEquals(new Color(255, 0, 0, 78),
			tracker.colorFor(local, "Hello", fallback, 101));
		assertEquals(new Color(255, 255, 0, 78),
			tracker.colorFor(local, "Hello", fallback, 110));

		assertGlowPhases("glow1", 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFF5);
		assertGlowPhases("glow2", 0xFF0000, 0xFF00FF, 0x0000FF, 0xF5000A);
		assertGlowPhases("glow3", 0xFFFFFF, 0x00FF00, 0xFFFFFF, 0x0AFFFF);
		assertEquals(new Color(0, 176, 0),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix("flash3", 0, 0));
		assertEquals(new Color(128, 255, 128),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix("flash3", 10, 0));
	}

	@Test
	public void rainbowAndPatternPrefixesAreReconstructedPerGlyph()
	{
		for (String prefix : List.of("rainbow", "patternq3q3"))
		{
			CustomizeALotLocalChatEffectTracker.ParsedInput parsed =
				CustomizeALotLocalChatEffectTracker.parseInput(prefix + ":wave:Hello");
			assertNull(parsed.getColorOverride());
			assertEquals(prefix, parsed.getAnimatedColorPrefix());
		}

		CustomizeALotLocalChatEffectTracker patternTracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();
		Color fallback = new Color(12, 34, 56, 78);
		patternTracker.recordOutgoing("patternq3:wave:Pattern", 0, false, 100);
		patternTracker.recordOverhead(local, local, "Pattern", 101);
		Color patternColor = patternTracker.colorFor(local, "Pattern", fallback, 101);
		assertTrue(patternColor
			instanceof CustomizeALotLocalChatEffectTracker.PerGlyphColor);
		CustomizeALotLocalChatEffectTracker.PerGlyphColor pattern =
			(CustomizeALotLocalChatEffectTracker.PerGlyphColor) patternColor;
		assertFalse(pattern.isRainbow());
		assertEquals(2, pattern.patternLength());
		// The native font stretches the two pattern segments over all 8 glyphs.
		assertEquals(new Color(0x2C, 0x2C, 0x2C, 78), pattern.colorForGlyph(0, 8));
		assertEquals(new Color(0x2C, 0x2C, 0x2C, 78), pattern.colorForGlyph(3, 8));
		assertEquals(new Color(0xFF, 0xED, 0x00, 78), pattern.colorForGlyph(4, 8));
		assertEquals(new Color(0xFF, 0xED, 0x00, 78), pattern.colorForGlyph(7, 8));

		CustomizeALotLocalChatEffectTracker rainbowTracker =
			new CustomizeALotLocalChatEffectTracker();
		rainbowTracker.recordOutgoing("rainbow:wave:Rainbow!", 0, false, 200);
		rainbowTracker.recordOverhead(local, local, "Rainbow!", 201);
		Color rainbowColor = rainbowTracker.colorFor(local, "Rainbow!", fallback, 201);
		assertTrue(rainbowColor
			instanceof CustomizeALotLocalChatEffectTracker.PerGlyphColor);
		CustomizeALotLocalChatEffectTracker.PerGlyphColor rainbow =
			(CustomizeALotLocalChatEffectTracker.PerGlyphColor) rainbowColor;
		assertTrue(rainbow.isRainbow());
		assertEquals(new Color(0xF8, 0x15, 0x09, 78), rainbow.colorForGlyph(0, 8));
		assertEquals(new Color(0x79, 0xF8, 0x09, 78), rainbow.colorForGlyph(2, 8));
		assertEquals(new Color(0x09, 0xED, 0xF8, 78), rainbow.colorForGlyph(4, 8));
		assertEquals(new Color(0xF8, 0x09, 0xB4, 78), rainbow.colorForGlyph(7, 8));
		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			rainbowTracker.effectFor(
				local,
				"Rainbow!",
				CustomizeALotOverheadChatEffect.STATIC,
				201));
	}

	@Test
	public void textEventBridgesTheFirstFrameBeforeNativeLifetimeIsWritten()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object actor = new Object();

		tracker.recordOverhead(actor, new Object(), "Fresh message", 500);
		CustomizeALotLocalChatEffectTracker.MessageState firstFrame =
			tracker.messageStateFor(actor, null, 0, 500, 150);
		assertEquals("Fresh message", firstFrame.getText());
		assertEquals(150, firstFrame.getOverheadCycle());
		assertTrue(firstFrame.isEventBacked());

		CustomizeALotLocalChatEffectTracker.MessageState stalePriorLifetime =
			tracker.messageStateFor(actor, "Fresh message", 3, 500, 150);
		assertEquals(150, stalePriorLifetime.getOverheadCycle());

		CustomizeALotLocalChatEffectTracker.MessageState nextFrame =
			tracker.messageStateFor(actor, "Fresh message", 149, 501, 150);
		assertEquals(149, nextFrame.getOverheadCycle());
	}

	@Test
	public void eventBridgeDoesNotOverrideDifferentOrExpiredLiveState()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object actor = new Object();
		tracker.recordOverhead(actor, new Object(), "Old", 100);

		CustomizeALotLocalChatEffectTracker.MessageState different =
			tracker.messageStateFor(actor, "New", 42, 101, 150);
		assertEquals("New", different.getText());
		assertEquals(42, different.getOverheadCycle());
		assertFalse(different.isEventBacked());

		tracker.recordOverhead(actor, new Object(), "Old", 200);
		CustomizeALotLocalChatEffectTracker.MessageState expired =
			tracker.messageStateFor(actor, null, 0, 351, 150);
		assertNull(expired.getText());
		assertEquals(0, expired.getOverheadCycle());
		assertFalse(expired.isEventBacked());
	}

	@Test
	public void correlatesOnlyTheLocalActorsMatchingMessage()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();
		Object remote = new Object();

		tracker.recordOutgoing("red:wave:hello THERE", 0, false, 100);
		tracker.recordOverhead(local, local, "Hello there", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(
				local,
				"Hello there",
				CustomizeALotOverheadChatEffect.SHAKE,
				101));
		assertEquals(
			CustomizeALotOverheadChatEffect.SHAKE,
			tracker.effectFor(
				remote,
				"Hello there",
				CustomizeALotOverheadChatEffect.SHAKE,
				101));
		assertEquals(
			CustomizeALotOverheadChatEffect.SHAKE,
			tracker.effectFor(
				local,
				"Different text",
				CustomizeALotOverheadChatEffect.SHAKE,
				101));
	}

	@Test
	public void correlatesReverseOrderWhenGameLeavesColorAsText()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("slide:red:hello", 0, false, 100);
		tracker.recordOverhead(local, local, "red:Hello", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.SLIDE,
			tracker.effectFor(
				local,
				"red:Hello",
				CustomizeALotOverheadChatEffect.STATIC,
				101));
	}

	@Test
	public void consumedNonPublicMismatchedAndExpiredInputsDoNotCorrelate()
	{
		Object local = new Object();

		assertFallsBackAfterInput(local, "wave:hello", 0, true, "hello", 101);
		assertFallsBackAfterInput(local, "wave:hello", 2, false, "hello", 101);
		assertFallsBackAfterInput(local, "wave:hello", 0, false, "different", 101);
		assertFallsBackAfterInput(local, "wave:hello", 0, false, "hello", 201);
	}

	@Test
	public void newUnmatchedLocalMessageClearsPreviousOverride()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("wave:hello", 0, false, 100);
		tracker.recordOverhead(local, local, "Hello", 101);
		tracker.recordOverhead(local, local, "No prefix", 102);

		assertEquals(
			CustomizeALotOverheadChatEffect.STATIC,
			tracker.effectFor(
				local,
				"Hello",
				CustomizeALotOverheadChatEffect.STATIC,
				102));
	}

	@Test
	public void earlierPlainDuplicateCannotStealLaterEffectOverride()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("hello", 0, false, 100);
		tracker.recordOutgoing("wave:hello", 0, false, 101);
		tracker.recordOverhead(local, local, "Hello", 102);
		assertEquals(
			CustomizeALotOverheadChatEffect.STATIC,
			tracker.effectFor(
				local,
				"Hello",
				CustomizeALotOverheadChatEffect.STATIC,
				102));

		tracker.recordOverhead(local, local, "Hello", 103);
		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(
				local,
				"Hello",
				CustomizeALotOverheadChatEffect.STATIC,
				103));
	}

	@Test
	public void activeOverrideExpiresAndNullFallbackIsStatic()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("shake:hello", 0, false, 100);
		tracker.recordOverhead(local, local, "Hello", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.SHAKE,
			tracker.effectFor(local, "Hello", null, 251));
		assertEquals(
			CustomizeALotOverheadChatEffect.STATIC,
			tracker.effectFor(local, "Hello", null, 252));
	}

	@Test
	public void formattingTagsAreNormalizedWithoutLosingLiteralBrackets()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("scroll:<3", 0, false, 100);
		tracker.recordOverhead(local, local, "<lt>3", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.SCROLL,
			tracker.effectFor(
				local,
				"<lt>3",
				CustomizeALotOverheadChatEffect.STATIC,
				101));
	}

	@Test
	public void pairedLiteralBracketsStillCorrelateWithLocalEffects()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("wave:<hello>", 0, false, 100);
		tracker.recordOverhead(local, local, "<lt>hello<gt>", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(
				local,
				"<lt>hello<gt>",
				CustomizeALotOverheadChatEffect.STATIC,
				101));
	}

	@Test
	public void atEscapesCorrelateWithLocalEffectsAndColors()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("red:wave:name@example.com", 0, false, 100);
		tracker.recordOverhead(local, local, "name<at>example.com", 101);

		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(
				local,
				"name<at>example.com",
				CustomizeALotOverheadChatEffect.STATIC,
				101));
		assertEquals(
			new Color(255, 0, 0, 77),
			tracker.colorFor(
				local,
				"name<at>example.com",
				new Color(1, 2, 3, 77),
				101));
	}

	@Test
	public void literalAtTagDoesNotCollideWithTheAtCharacter()
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		Object local = new Object();

		tracker.recordOutgoing("wave:<at>", 0, false, 100);
		tracker.recordOverhead(local, local, "<at>", 101);
		assertEquals(
			CustomizeALotOverheadChatEffect.STATIC,
			tracker.effectFor(
				local,
				"<at>",
				CustomizeALotOverheadChatEffect.STATIC,
				101));

		tracker.recordOverhead(local, local, "<lt>at<gt>", 102);
		assertEquals(
			CustomizeALotOverheadChatEffect.WAVE,
			tracker.effectFor(
				local,
				"<lt>at<gt>",
				CustomizeALotOverheadChatEffect.STATIC,
				102));
	}

	@Test
	public void invalidOrMissingEffectDoesNotCreateAnOverride()
	{
		assertNull(CustomizeALotLocalChatEffectTracker.parseInput("red:hello").getEffect());
		assertNull(CustomizeALotLocalChatEffectTracker.parseInput("pattern@:wave:hello").getEffect());
		assertNull(CustomizeALotLocalChatEffectTracker.parseInput(
			"pattern123456789:wave:hello").getEffect());
		assertNull(CustomizeALotLocalChatEffectTracker.parseInput(null).getEffect());
	}

	private static void assertGlowPhases(
		String prefix,
		int startRgb,
		int secondRgb,
		int thirdRgb,
		int finalRgb)
	{
		assertEquals(new Color(startRgb),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix(prefix, 0, 0));
		assertEquals(new Color(secondRgb),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix(prefix, 0, 50));
		assertEquals(new Color(thirdRgb),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix(prefix, 0, 100));
		assertEquals(new Color(finalRgb),
			CustomizeALotLocalChatEffectTracker.animatedColorForPrefix(prefix, 0, 149));
	}

	private static void assertFallsBackAfterInput(
		Object local,
		String input,
		int chatType,
		boolean consumed,
		String overhead,
		int overheadCycle)
	{
		CustomizeALotLocalChatEffectTracker tracker =
			new CustomizeALotLocalChatEffectTracker();
		tracker.recordOutgoing(input, chatType, consumed, 100);
		tracker.recordOverhead(local, local, overhead, overheadCycle);
		assertEquals(
			CustomizeALotOverheadChatEffect.STATIC,
			tracker.effectFor(
				local,
				overhead,
				CustomizeALotOverheadChatEffect.STATIC,
				overheadCycle));
	}

}

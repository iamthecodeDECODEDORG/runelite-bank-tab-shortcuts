package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BankTabNavigatorTest
{
	private static final Canvas SOURCE = new Canvas();

	@Test
	public void zeroSelectsAllFromEitherNumberRow()
	{
		assertNotEquals(0, BankTabNavigator.NONE);
		assertEquals(0, BankTabNavigator.requestFor(key(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK)));
		assertEquals(0, BankTabNavigator.requestFor(key(KeyEvent.VK_NUMPAD0, InputEvent.CTRL_DOWN_MASK)));
		assertEquals(0, BankTabNavigator.targetFor(0, 4, counts()));
	}

	@Test
	public void mapsEveryNumberedShortcut()
	{
		for (int tab = 1; tab <= 9; tab++)
		{
			assertEquals(tab, BankTabNavigator.requestFor(key(KeyEvent.VK_0 + tab, InputEvent.CTRL_DOWN_MASK)));
		}
	}

	@Test
	public void mapsEveryNumpadShortcut()
	{
		for (int tab = 1; tab <= 9; tab++)
		{
			assertEquals(tab, BankTabNavigator.requestFor(key(KeyEvent.VK_NUMPAD0 + tab, InputEvent.CTRL_DOWN_MASK)));
		}
	}

	@Test
	public void recognizesOriginalNumberAfterAnotherListenerRemapsIt()
	{
		KeyEvent event = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 1L,
			InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_2, KeyEvent.CHAR_UNDEFINED)
		{
			@Override
			public int getExtendedKeyCode()
			{
				return KeyEvent.VK_2;
			}
		};
		// Key Remapping changes keyCode; AWT retains the native extended code.
		event.setKeyCode(KeyEvent.VK_F2);
		assertEquals(2, BankTabNavigator.requestFor(event));
	}

	@Test
	public void doesNotTreatPhysicalFunctionKeyAsNumberedShortcut()
	{
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.requestFor(key(KeyEvent.VK_F2, InputEvent.CTRL_DOWN_MASK)));
	}

	@Test
	public void mapsForwardAndBackwardCycling()
	{
		assertEquals(BankTabNavigator.NEXT,
			BankTabNavigator.requestFor(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)));
		assertEquals(BankTabNavigator.PREVIOUS,
			BankTabNavigator.requestFor(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	public void rejectsMissingOrExtraModifiersAndNonNumericNavigation()
	{
		assertEquals(BankTabNavigator.NONE, BankTabNavigator.requestFor(key(KeyEvent.VK_1, 0)));
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.requestFor(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.requestFor(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK)));
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.requestFor(key(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK)));
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.requestFor(key(KeyEvent.VK_NUMPAD1, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
	}

	@Test
	public void directNavigationRequiresTheRequestedTab()
	{
		assertEquals(3, BankTabNavigator.targetFor(3, 1, counts(4, 2, 1)));
		assertEquals(BankTabNavigator.NONE, BankTabNavigator.targetFor(4, 1, counts(4, 2, 1)));
	}

	@Test
	public void cyclesForwardAndWrapsAcrossExistingTabs()
	{
		int[] tabs = counts(5, 3, 2);
		assertEquals(2, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 1, tabs));
		assertEquals(3, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 2, tabs));
		assertEquals(1, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 3, tabs));
	}

	@Test
	public void cyclesBackwardAndWrapsAcrossExistingTabs()
	{
		int[] tabs = counts(5, 3, 2);
		assertEquals(2, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 3, tabs));
		assertEquals(1, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 2, tabs));
		assertEquals(3, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 1, tabs));
	}

	@Test
	public void cyclingFromAllOrSpecialTabsUsesTheNearestEnd()
	{
		int[] tabs = counts(5, 3, 2);
		assertEquals(1, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 0, tabs));
		assertEquals(3, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 0, tabs));
		assertEquals(1, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 15, tabs));
		assertEquals(3, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 15, tabs));
	}

	@Test
	public void sparseAndEmptyTabSetsAreHandled()
	{
		int[] sparse = counts(7, 0, 4, 0, 1);
		assertEquals(3, BankTabNavigator.targetFor(BankTabNavigator.NEXT, 1, sparse));
		assertEquals(5, BankTabNavigator.targetFor(BankTabNavigator.PREVIOUS, 1, sparse));
		assertEquals(BankTabNavigator.NONE,
			BankTabNavigator.targetFor(BankTabNavigator.NEXT, 0, counts()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMalformedTabSnapshots()
	{
		BankTabNavigator.targetFor(BankTabNavigator.NEXT, 1, new int[8]);
	}

	private static KeyEvent key(int keyCode, int modifiers)
	{
		return new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 1L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
	}

	private static int[] counts(int... existingCounts)
	{
		int[] counts = new int[BankTabNavigator.TAB_COUNT];
		System.arraycopy(existingCounts, 0, counts, 0, existingCounts.length);
		return counts;
	}
}

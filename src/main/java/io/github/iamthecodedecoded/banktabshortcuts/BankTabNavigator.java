package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.event.KeyEvent;

final class BankTabNavigator
{
	static final int NONE = 0;
	static final int NEXT = 10;
	static final int PREVIOUS = -10;
	static final int TAB_COUNT = 9;

	private BankTabNavigator()
	{
	}

	static int requestFor(KeyEvent event)
	{
		if (!event.isControlDown() || event.isAltDown() || event.isAltGraphDown() || event.isMetaDown())
		{
			return NONE;
		}

		if (event.getKeyCode() == KeyEvent.VK_TAB)
		{
			return event.isShiftDown() ? PREVIOUS : NEXT;
		}

		if (!event.isShiftDown()
			&& event.getKeyCode() >= KeyEvent.VK_1
			&& event.getKeyCode() <= KeyEvent.VK_9)
		{
			return event.getKeyCode() - KeyEvent.VK_0;
		}

		return NONE;
	}

	static int targetFor(int request, int currentTab, int[] tabItemCounts)
	{
		if (tabItemCounts == null || tabItemCounts.length != TAB_COUNT)
		{
			throw new IllegalArgumentException("tabItemCounts must contain exactly nine entries");
		}

		if (request >= 1 && request <= TAB_COUNT)
		{
			return exists(request, tabItemCounts) ? request : NONE;
		}

		if (request != NEXT && request != PREVIOUS)
		{
			return NONE;
		}

		int step = request == NEXT ? 1 : -1;
		int startingTab = currentTab >= 1 && currentTab <= TAB_COUNT
			? currentTab
			: (step > 0 ? 0 : TAB_COUNT + 1);

		for (int offset = 1; offset <= TAB_COUNT; offset++)
		{
			int candidate = Math.floorMod(startingTab - 1 + step * offset, TAB_COUNT) + 1;
			if (exists(candidate, tabItemCounts))
			{
				return candidate;
			}
		}

		return NONE;
	}

	private static boolean exists(int tab, int[] tabItemCounts)
	{
		return tabItemCounts[tab - 1] > 0;
	}
}

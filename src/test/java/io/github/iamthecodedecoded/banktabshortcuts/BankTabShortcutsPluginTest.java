package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankTabShortcutsPluginTest
{
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final List<String> operations = new ArrayList<>();
	private final QueuedClientThread clientThread = new QueuedClientThread();
	private BankTabShortcutsPlugin plugin;
	private Object[] rebuildListener;
	private boolean bankItemsHidden;

	@Before
	public void setUp() throws Exception
	{
		for (int id = VarbitID.BANK_TAB_1; id <= VarbitID.BANK_TAB_9; id++)
		{
			varbits.put(id, 0);
		}
		varbits.put(VarbitID.BANK_CURRENTTAB, 0);
		rebuildListener = new Object[]{12345};
		bankItemsHidden = false;

		Widget widget = (Widget) Proxy.newProxyInstance(
			Widget.class.getClassLoader(),
			new Class<?>[]{Widget.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("isHidden"))
				{
					return bankItemsHidden;
				}
				if (method.getName().equals("getOnInvTransmitListener"))
				{
					return rebuildListener;
				}
				return defaultValue(method.getReturnType());
			});

		Client client = (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(),
			new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getWidget":
						return widget;
					case "getVarbitValue":
						return varbits.getOrDefault((Integer) args[0], 0);
					case "setVarbit":
						varbits.put((Integer) args[0], (Integer) args[1]);
						operations.add("tab:" + args[1]);
						return null;
					case "runScript":
						Object firstArgument = ((Object[]) args[0])[0];
						operations.add(firstArgument.equals(ScriptID.MESSAGE_LAYER_CLOSE) ? "close-search" : "rebuild");
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});

		plugin = new BankTabShortcutsPlugin();
		inject(plugin, "client", client);
		inject(plugin, "clientThread", clientThread);
		WidgetLoaded bankLoaded = new WidgetLoaded();
		bankLoaded.setGroupId(InterfaceID.BANKMAIN);
		plugin.onWidgetLoaded(bankLoaded);
	}

	@Test
	public void directShortcutConsumesAndRunsNativeSequence()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		KeyEvent event = key(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK);

		plugin.keyPressed(event);
		assertTrue(event.isConsumed());
		assertEquals(0, (int) varbits.get(VarbitID.BANK_CURRENTTAB));

		clientThread.drain();
		assertEquals(3, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertEquals(List.of("tab:3", "close-search", "rebuild"), operations);
	}

	@Test
	public void queuedCycleRequestsAdvanceIndependently()
	{
		varbits.put(VarbitID.BANK_TAB_1, 4);
		varbits.put(VarbitID.BANK_TAB_2, 3);
		varbits.put(VarbitID.BANK_TAB_3, 2);
		varbits.put(VarbitID.BANK_CURRENTTAB, 1);

		plugin.keyPressed(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		plugin.keyPressed(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();

		assertEquals(3, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertEquals(List.of("tab:2", "close-search", "rebuild", "tab:3", "close-search", "rebuild"), operations);
	}

	@Test
	public void unavailableTabAndHiddenBankDoNotConsume()
	{
		KeyEvent absent = key(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK);
		plugin.keyPressed(absent);
		assertFalse(absent.isConsumed());

		varbits.put(VarbitID.BANK_TAB_1, 1);
		bankItemsHidden = true;
		KeyEvent hidden = key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
		plugin.keyPressed(hidden);
		assertFalse(hidden.isConsumed());
		assertTrue(clientThread.isEmpty());
	}

	@Test
	public void missingRebuildListenerDoesNotMutateTab()
	{
		varbits.put(VarbitID.BANK_TAB_2, 1);
		rebuildListener = null;
		KeyEvent event = key(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK);

		plugin.keyPressed(event);
		clientThread.drain();

		assertTrue(event.isConsumed());
		assertEquals(0, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertTrue(operations.isEmpty());
	}

	private static KeyEvent key(int keyCode, int modifiers)
	{
		return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 1L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
	}

	private static void inject(Object target, String fieldName, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (!type.isPrimitive())
		{
			return null;
		}
		if (type == boolean.class)
		{
			return false;
		}
		if (type == char.class)
		{
			return '\0';
		}
		return 0;
	}

	private static final class QueuedClientThread extends ClientThread
	{
		private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

		@Override
		public void invoke(Runnable runnable)
		{
			queued.add(runnable);
		}

		void drain()
		{
			while (!queued.isEmpty())
			{
				queued.remove().run();
			}
		}

		boolean isEmpty()
		{
			return queued.isEmpty();
		}
	}
}

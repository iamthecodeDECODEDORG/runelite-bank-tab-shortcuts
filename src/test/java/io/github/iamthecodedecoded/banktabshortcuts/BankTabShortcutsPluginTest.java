package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class BankTabShortcutsPluginTest
{
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final List<String> operations = new ArrayList<>();
	private final QueuedClientThread clientThread = new QueuedClientThread();
	private final EventBus eventBus = new EventBus();
	private BankTabShortcutsPlugin plugin;
	private KeyManager keyManager;
	private OverlayManager overlayManager;
	private BankTabNumbersOverlay numbersOverlay;
	private Object[] rebuildListener;
	private boolean bankItemsHidden;
	private boolean bankItemsPresent = true;
	private int displayedTab;
	private boolean searching = true;

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
				assertTrue("widget access must be on client thread: " + method.getName(), clientThread.running);
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
				assertTrue("client access must be on client thread: " + method.getName(), clientThread.running);
				switch (method.getName())
				{
					case "getWidget":
						return bankItemsPresent ? widget : null;
					case "getVarbitValue":
						return varbits.getOrDefault((Integer) args[0], 0);
					case "setVarbit":
						varbits.put((Integer) args[0], (Integer) args[1]);
						operations.add("tab:" + args[1]);
						return null;
					case "runScript":
						Object firstArgument = ((Object[]) args[0])[0];
						if (firstArgument.equals(ScriptID.MESSAGE_LAYER_CLOSE))
						{
							searching = false;
							operations.add("close-search");
						}
						else
						{
							displayedTab = varbits.get(VarbitID.BANK_CURRENTTAB);
							operations.add("rebuild");
						}
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});

		plugin = new BankTabShortcutsPlugin();
		inject(plugin, "client", client);
		inject(plugin, "clientThread", clientThread);
		Constructor<KeyManager> constructor = KeyManager.class.getDeclaredConstructor(Client.class, EventBus.class);
		constructor.setAccessible(true);
		keyManager = constructor.newInstance(null, new EventBus());
		inject(plugin, "keyManager", keyManager);
		overlayManager = mock(OverlayManager.class);
		numbersOverlay = new BankTabNumbersOverlay(client, new BankTabShortcutsConfig() {});
		inject(plugin, "overlayManager", overlayManager);
		inject(plugin, "numbersOverlay", numbersOverlay);
		EventQueue.invokeAndWait(plugin::startUp);
		eventBus.register(plugin);
	}

	@Test
	public void cachedHiddenWidgetsBetweenCloseAndOpenDoNotEraseRememberedTab()
	{
		selectTabFive();
		closeBank();
		bankItemsHidden = true;
		varbits.put(VarbitID.BANK_CURRENTTAB, 3);
		refresh();
		bankItemsHidden = false;
		fireScript(ScriptID.BANKMAIN_INIT);
		assertEquals(5, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
	}

	@Test
	public void bankTagsDefaultPriorityRestorationStillWins()
	{
		selectTabFive();
		eventBus.register(new Object()
		{
			@Subscribe
			public void onScriptPreFired(ScriptPreFired event)
			{
				if (event.getScriptId() == ScriptID.BANKMAIN_INIT)
				{
					assertEquals(5, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
					// Mirrors Bank Tags' default-priority remembered-tag initialization.
					varbits.put(VarbitID.BANK_CURRENTTAB, 0);
				}
			}
		});
		reopenWithServerTab(3);
		assertEquals(0, displayedTab);
	}

	@Test
	public void queuedOldBankShortcutCannotReplaceRememberedTabAfterReopening()
	{
		selectTabFive();
		press(key(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK));
		reopenWithServerTab(3);
		assertEquals(5, displayedTab);
	}

	@Test
	public void anotherViewImmediatelyFollowedByCloseDoesNotRestoreStaleTab()
	{
		selectTabFive();
		varbits.put(VarbitID.BANK_CURRENTTAB, 0);
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
	}

	@Test
	public void failedShortcutDoesNotRememberItsRequestedTab()
	{
		varbits.put(VarbitID.BANK_TAB_5, 4);
		rebuildListener = null;
		refresh();
		press(key(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
	}

	@Test
	public void keyboardTabSurvivesServerResyncOnRepeatedBankReopens()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		varbits.put(VarbitID.BANK_TAB_5, 4);
		varbits.put(VarbitID.BANK_CURRENTTAB, 3);
		refresh();
		press(key(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		assertEquals(5, displayedTab);
		for (int i = 0; i < 3; i++)
		{
			reopenWithServerTab(3);
			assertEquals(5, displayedTab);
		}
	}

	@Test
	public void allAndBothCycleDirectionsAreRemembered()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		varbits.put(VarbitID.BANK_TAB_5, 4);
		varbits.put(VarbitID.BANK_CURRENTTAB, 3);
		refresh();
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		reopenWithServerTab(3);
		assertEquals(5, displayedTab);
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		clientThread.drain();
		reopenWithServerTab(5);
		assertEquals(3, displayedTab);
		press(key(KeyEvent.VK_NUMPAD0, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		reopenWithServerTab(3);
		assertEquals(0, displayedTab);
	}

	@Test
	public void laterNativeClickTakesPrecedenceEvenBeforeNextTick()
	{
		selectTabFive();
		// Tab icons, All, separator headings and potion storage share native script 504.
		fireScript(504);
		varbits.put(VarbitID.BANK_CURRENTTAB, 3);
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
	}

	@Test
	public void removedTabIsNotResurrected()
	{
		selectTabFive();
		varbits.put(VarbitID.BANK_TAB_5, 0);
		reopenWithServerTab(0);
		assertEquals(0, displayedTab);
		varbits.put(VarbitID.BANK_TAB_5, 2);
		reopenWithServerTab(0);
		assertEquals(0, displayedTab);
	}

	@Test
	public void otherViewSelectionAndOpenBankReinitializationAreNotOverridden()
	{
		selectTabFive();
		varbits.put(VarbitID.BANK_CURRENTTAB, 0);
		fireScript(ScriptID.BANKMAIN_INIT);
		assertEquals(0, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		refresh();
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
	}

	@Test
	public void logoutHopAndDisableDiscardRememberedTab() throws Exception
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING, GameState.CONNECTION_LOST})
		{
			selectTabFive();
			GameStateChanged event = new GameStateChanged();
			event.setGameState(state);
			clientThread.invoke(() -> eventBus.post(event));
			clientThread.drain();
			reopenWithServerTab(3);
			assertEquals(3, displayedTab);
		}
		selectTabFive();
		EventQueue.invokeAndWait(plugin::shutDown);
		EventQueue.invokeAndWait(plugin::startUp);
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
	}

	@Test
	public void noKeyboardSelectionLeavesNativeRememberingUntouched()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		reopenWithServerTab(3);
		assertEquals(3, displayedTab);
		assertTrue(operations.isEmpty());
	}

	private void selectTabFive()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		varbits.put(VarbitID.BANK_TAB_5, 4);
		refresh();
		press(key(KeyEvent.VK_5, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
	}

	private void fireScript(int id)
	{
		clientThread.invoke(() -> eventBus.post(new ScriptPreFired(id)));
		clientThread.drain();
	}

	private void reopenWithServerTab(int serverTab)
	{
		closeBank();
		// Native bank open resyncs server state before script 274 builds the view.
		varbits.put(VarbitID.BANK_CURRENTTAB, serverTab);
		fireScript(ScriptID.BANKMAIN_INIT);
		displayedTab = varbits.get(VarbitID.BANK_CURRENTTAB);
		refresh();
	}

	private void closeBank()
	{
		// A native close can happen before already-queued AWT work is drained.
		clientThread.running = true;
		try
		{
			eventBus.post(new WidgetClosed(InterfaceID.BANKMAIN, 0, false));
		}
		finally
		{
			clientThread.running = false;
		}
	}

	@Test
	public void productionClassesLoadFromExpectedArtifact()
	{
		boolean loadedFromJar = BankTabShortcutsPlugin.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath().endsWith(".jar");
		assertEquals(Boolean.getBoolean("bankTabShortcuts.packagedTest"), loadedFromJar);
	}

	@Test
	public void overlayRegistersAndUnregistersWithPlugin() throws Exception
	{
		verify(overlayManager).add(numbersOverlay);
		EventQueue.invokeAndWait(plugin::shutDown);
		verify(overlayManager).remove(numbersOverlay);
	}

	@Test
	public void zeroOpensAllEvenWhenThereAreNoNumberedTabs()
	{
		varbits.put(VarbitID.BANK_CURRENTTAB, 3);
		refresh();
		for (int keyCode : new int[]{KeyEvent.VK_0, KeyEvent.VK_NUMPAD0})
		{
			KeyEvent event = key(keyCode, InputEvent.CTRL_DOWN_MASK);
			press(event);
			assertTrue(event.isConsumed());
			clientThread.drain();
			assertEquals(0, displayedTab);
		}
		assertFalse(searching);
		assertEquals(List.of("tab:0", "close-search", "rebuild", "tab:0", "close-search", "rebuild"), operations);
	}

	@Test
	public void enablingWhileBankIsOpenDoesNotReadWidgetsOnLifecycleThread()
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		assertEquals(1, displayedTab);
	}

	@Test
	public void directShortcutConsumesAndRunsNativeSequence()
	{
		varbits.put(VarbitID.BANK_TAB_3, 4);
		KeyEvent event = key(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK);

		refresh();
		press(event);
		assertTrue(event.isConsumed());
		assertEquals(0, (int) varbits.get(VarbitID.BANK_CURRENTTAB));

		clientThread.drain();
		assertEquals(3, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertEquals(3, displayedTab);
		assertFalse(searching);
		assertEquals(List.of("tab:3", "close-search", "rebuild"), operations);
	}

	@Test
	public void queuedCycleRequestsAdvanceIndependently()
	{
		varbits.put(VarbitID.BANK_TAB_1, 4);
		varbits.put(VarbitID.BANK_TAB_2, 3);
		varbits.put(VarbitID.BANK_TAB_3, 2);
		varbits.put(VarbitID.BANK_CURRENTTAB, 1);

		refresh();
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();

		assertEquals(3, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertEquals(List.of("tab:2", "close-search", "rebuild", "tab:3", "close-search", "rebuild"), operations);
	}

	@Test
	public void unavailableTabAndHiddenBankDoNotConsume()
	{
		KeyEvent absent = key(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK);
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(absent);
		assertFalse(absent.isConsumed());

		varbits.put(VarbitID.BANK_TAB_1, 1);
		bankItemsHidden = true;
		refresh();
		KeyEvent hidden = key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
		press(hidden);
		assertFalse(hidden.isConsumed());
		assertTrue(clientThread.isEmpty());
	}

	@Test
	public void missingRebuildListenerDoesNotMutateTab()
	{
		varbits.put(VarbitID.BANK_TAB_2, 1);
		rebuildListener = null;
		KeyEvent event = key(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK);

		refresh();
		press(event);
		clientThread.drain();

		assertTrue(event.isConsumed());
		assertEquals(0, (int) varbits.get(VarbitID.BANK_CURRENTTAB));
		assertTrue(operations.isEmpty());
	}

	@Test
	public void everyNumberedShortcutRunsOnClientThread()
	{
		for (int tab = 1; tab <= 9; tab++)
		{
			varbits.put(VarbitID.BANK_TAB_1 + tab - 1, 2);
		}
		refresh();
		for (int tab = 1; tab <= 9; tab++)
		{
			for (int keyCode : new int[]{KeyEvent.VK_0 + tab, KeyEvent.VK_NUMPAD0 + tab})
			{
				KeyEvent event = key(keyCode, InputEvent.CTRL_DOWN_MASK);
				press(event);
				assertTrue(event.isConsumed());
				clientThread.drain();
				assertEquals(tab, displayedTab);
			}
		}
	}

	@Test
	public void numberedShortcutSurvivesEarlierFKeyRemappingListener()
	{
		varbits.put(VarbitID.BANK_TAB_2, 1);
		refresh();
		keyManager.unregisterKeyListener(plugin);
		keyManager.registerKeyListener(new KeyListener()
		{
			@Override
			public void keyPressed(KeyEvent event)
			{
				if (new ModifierlessKeybind(KeyEvent.VK_2, 0).matches(event))
				{
					event.setKeyCode(KeyEvent.VK_F2);
					event.setKeyChar(KeyEvent.CHAR_UNDEFINED);
				}
			}

			@Override
			public void keyTyped(KeyEvent event)
			{
			}

			@Override
			public void keyReleased(KeyEvent event)
			{
			}
		});
		keyManager.registerKeyListener(plugin);
		KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 1L,
			InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_2, KeyEvent.CHAR_UNDEFINED)
		{
			@Override
			public int getExtendedKeyCode()
			{
				return KeyEvent.VK_2;
			}
		};
		press(event);
		clientThread.drain();
		assertTrue(event.isConsumed());
		assertEquals(2, displayedTab);
	}

	@Test
	public void reverseCyclingWrapsAndSkipsMissingTabs()
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		varbits.put(VarbitID.BANK_TAB_3, 1);
		varbits.put(VarbitID.BANK_CURRENTTAB, 1);
		refresh();
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		clientThread.drain();
		assertEquals(3, displayedTab);
		press(key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		assertEquals(1, displayedTab);
	}

	@Test
	public void closedBankDoesNotConsumeAndReopeningRestoresShortcuts()
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		bankItemsPresent = false;
		refresh();
		KeyEvent closed = key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
		press(closed);
		assertFalse(closed.isConsumed());
		bankItemsPresent = true;
		refresh();
		KeyEvent reopened = key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
		press(reopened);
		clientThread.drain();
		assertTrue(reopened.isConsumed());
		assertEquals(1, displayedTab);
	}

	@Test
	public void closingBankCancelsQueuedNavigation()
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		bankItemsPresent = false;
		closeBank();
		clientThread.drain();
		assertTrue(operations.isEmpty());
	}

	@Test
	public void tabDisappearingOrBankHidingBeforeDispatchDoesNotNavigate()
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		varbits.put(VarbitID.BANK_TAB_1, 0);
		clientThread.drain();
		assertTrue(operations.isEmpty());
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		bankItemsHidden = true;
		clientThread.drain();
		assertTrue(operations.isEmpty());
	}

	@Test
	public void disablingAndReenablingDoesNotReplayOldKeypresses() throws Exception
	{
		varbits.put(VarbitID.BANK_TAB_1, 1);
		refresh();
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		EventQueue.invokeAndWait(plugin::shutDown);
		KeyEvent disabled = key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK);
		press(disabled);
		assertFalse(disabled.isConsumed());
		EventQueue.invokeAndWait(plugin::startUp);
		refresh();
		clientThread.drain();
		assertTrue(operations.isEmpty());
		press(key(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		clientThread.drain();
		assertEquals(1, displayedTab);
	}

	private void refresh()
	{
		clientThread.invoke(() -> plugin.onClientTick(new ClientTick()));
		clientThread.drain();
	}

	private void press(KeyEvent event)
	{
		try
		{
			EventQueue.invokeAndWait(() -> keyManager.processKeyPressed(event));
		}
		catch (Exception exception)
		{
			throw new AssertionError("AWT key dispatch failed", exception);
		}
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
		private boolean running;

		@Override
		public void invoke(Runnable runnable)
		{
			queued.add(runnable);
		}

		void drain()
		{
			running = true;
			try
			{
				while (!queued.isEmpty())
				{
					queued.remove().run();
				}
			}
			finally
			{
				running = false;
			}
		}

		boolean isEmpty()
		{
			return queued.isEmpty();
		}
	}
}

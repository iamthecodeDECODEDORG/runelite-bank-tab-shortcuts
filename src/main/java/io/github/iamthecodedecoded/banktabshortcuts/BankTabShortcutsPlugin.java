package io.github.iamthecodedecoded.banktabshortcuts;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Bank Tab Shortcuts",
	description = "Navigate native bank tabs with Ctrl+0 through Ctrl+9, Ctrl+Tab, and optional tab numbers",
	tags = {"bank", "tabs", "keyboard", "hotkeys", "shortcuts"}
)
public class BankTabShortcutsPlugin extends Plugin implements KeyListener
{
	private static final Logger LOG = LoggerFactory.getLogger(BankTabShortcutsPlugin.class);
	// Native tab icons, separator headings and potion storage use this script.
	// RuneLite cs2-scripts: [clientscript,bankmain_switchtab] (not exposed by ScriptID).
	private static final int BANKMAIN_SWITCHTAB = 504;

	private static final int[] BANK_TAB_COUNT_VARBITS =
	{
		VarbitID.BANK_TAB_1,
		VarbitID.BANK_TAB_2,
		VarbitID.BANK_TAB_3,
		VarbitID.BANK_TAB_4,
		VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6,
		VarbitID.BANK_TAB_7,
		VarbitID.BANK_TAB_8,
		VarbitID.BANK_TAB_9
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankTabNumbersOverlay numbersOverlay;

	@Provides
	BankTabShortcutsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankTabShortcutsConfig.class);
	}

	// Only immutable, client-thread-produced data crosses into the AWT key listener.
	private volatile int availableTabs;
	private volatile Object activation;
	private volatile int rememberedTab = BankTabNavigator.NONE;
	private volatile boolean bankOpen;

	@Override
	protected void startUp()
	{
		availableTabs = 0;
		rememberedTab = BankTabNavigator.NONE;
		bankOpen = false;
		activation = new Object();
		keyManager.registerKeyListener(this);
		overlayManager.add(numbersOverlay);
	}

	@Override
	protected void shutDown()
	{
		activation = null;
		availableTabs = 0;
		rememberedTab = BankTabNavigator.NONE;
		bankOpen = false;
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(numbersOverlay);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (activation == null)
		{
			return;
		}

		try
		{
			int tabs = 0;
			Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
			if (bankItems != null && bankOpen)
			{
				// Respect other views/plugins that change the selection without script 504.
				if (rememberedTab != BankTabNavigator.NONE
					&& client.getVarbitValue(VarbitID.BANK_CURRENTTAB) != rememberedTab)
				{
					rememberedTab = BankTabNavigator.NONE;
				}
			}
			if (bankItems != null && !bankItems.isHidden())
			{
				bankOpen = true;
				tabs = 1; // Bit zero is the all-items view.
				int[] counts = readTabItemCounts();
				for (int i = 0; i < counts.length; i++)
				{
					if (counts[i] > 0)
					{
						tabs |= 1 << (i + 1);
					}
				}
			}
			availableTabs = tabs;
		}
		catch (RuntimeException exception)
		{
			availableTabs = 0;
			LOG.error("BTS_BANK_STATE_FAILED", exception);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			try
			{
				if (bankOpen && rememberedTab != BankTabNavigator.NONE
					&& client.getVarbitValue(VarbitID.BANK_CURRENTTAB) != rememberedTab)
				{
					rememberedTab = BankTabNavigator.NONE;
				}
			}
			catch (RuntimeException exception)
			{
				rememberedTab = BankTabNavigator.NONE;
				LOG.error("BTS_TAB_CAPTURE_FAILED", exception);
			}
			availableTabs = 0;
			bankOpen = false;
			// A key queued for the closed bank must not run in a newly opened bank.
			if (activation != null)
			{
				activation = new Object();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.LOGGING_IN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			rememberedTab = BankTabNavigator.NONE;
			bankOpen = false;
			availableTabs = 0;
			if (activation != null)
			{
				activation = new Object();
			}
		}
	}

	// Run before Bank Tags' default-priority init so its remembered tag can still win.
	@Subscribe(priority = 1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (activation == null)
		{
			return;
		}
		if (event.getScriptId() == BANKMAIN_SWITCHTAB)
		{
			// A subsequent native click owns the server's remembered selection again.
			rememberedTab = BankTabNavigator.NONE;
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_INIT && !bankOpen)
		{
			bankOpen = true;
			if (rememberedTab == BankTabNavigator.NONE)
			{
				return;
			}
			try
			{
				int target = BankTabNavigator.targetFor(rememberedTab, 0, readTabItemCounts());
				if (target == BankTabNavigator.NONE)
				{
					rememberedTab = BankTabNavigator.NONE;
					return;
				}
				// Bank open resyncs the server's last clicked tab. Override only the local
				// view before native init builds it; never send a synthetic tab click.
				client.setVarbit(VarbitID.BANK_CURRENTTAB, target);
			}
			catch (RuntimeException exception)
			{
				rememberedTab = BankTabNavigator.NONE;
				LOG.error("BTS_TAB_RESTORE_FAILED", exception);
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		int request = BankTabNavigator.requestFor(event);
		Object currentActivation = activation;
		int tabs = availableTabs;
		if (event.isConsumed() || request == BankTabNavigator.NONE || currentActivation == null || tabs == 0)
		{
			return;
		}

		if (request >= 0 && request <= BankTabNavigator.TAB_COUNT && (tabs & (1 << request)) == 0)
		{
			return;
		}
		if ((request == BankTabNavigator.NEXT || request == BankTabNavigator.PREVIOUS) && (tabs & ~1) == 0)
		{
			return;
		}

		event.consume();
		clientThread.invoke(() ->
		{
			if (activation == currentActivation)
			{
				navigate(request);
			}
		});
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}

	private int[] readTabItemCounts()
	{
		int[] counts = new int[BANK_TAB_COUNT_VARBITS.length];
		for (int i = 0; i < BANK_TAB_COUNT_VARBITS.length; i++)
		{
			counts[i] = client.getVarbitValue(BANK_TAB_COUNT_VARBITS[i]);
		}
		return counts;
	}

	private void navigate(int request)
	{
		try
		{
			if (activation == null || availableTabs == 0)
			{
				return;
			}

			Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
			if (bankItems == null || bankItems.isHidden())
			{
				return;
			}

			int target = BankTabNavigator.targetFor(
				request,
				client.getVarbitValue(VarbitID.BANK_CURRENTTAB),
				readTabItemCounts());
			if (target == BankTabNavigator.NONE)
			{
				return;
			}

			Object[] bankBuildArgs = bankItems.getOnInvTransmitListener();
			if (bankBuildArgs == null)
			{
				LOG.warn("BTS_BANK_REBUILD_LISTENER_MISSING request={}", request);
				return;
			}

			client.setVarbit(VarbitID.BANK_CURRENTTAB, target);
			client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 0);
			client.runScript(bankBuildArgs);
			rememberedTab = target;
		}
		catch (RuntimeException exception)
		{
			rememberedTab = BankTabNavigator.NONE;
			LOG.error("BTS_NAVIGATION_FAILED request={}", request, exception);
		}
	}
}

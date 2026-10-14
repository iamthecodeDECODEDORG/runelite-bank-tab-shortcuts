package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayLayer;
import org.junit.Test;

import static org.junit.Assert.*;

public class BankTabNumbersOverlayTest
{
	@Test
	public void disabledByDefaultAndDoesNotReadGameState()
	{
		BankTabShortcutsConfig config = new BankTabShortcutsConfig() {};
		assertFalse(config.showTabNumbers());
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> { throw new AssertionError("disabled overlay accessed client"); });
		assertEquals(0, render(new BankTabNumbersOverlay(client, config), 0).getRGB(0, 0));
	}

	@Test
	public void labelsOnlyExistingNativeTabsAtTopLeftAndPreservesGraphics()
	{
		BufferedImage image = render(overlay(true, false, false, 10, Color.LIGHT_GRAY), 1);
		assertTrue(changedPixels(image, new Rectangle(12, 12, 15, 17)) > 0); // All
		assertTrue(changedPixels(image, new Rectangle(53, 12, 15, 17)) > 0); // Tab 1
		assertEquals(0, changedPixels(image, new Rectangle(92, 10, 41, 40))); // New tab
		assertEquals(0, changedPixels(image, new Rectangle(10, 30, 82, 20))); // Icons below labels
	}

	@Test
	public void hiddenBankOrTabsProduceNoPixels()
	{
		assertEquals(0, changedPixels(render(overlay(true, true, false, 10, Color.WHITE), 0), new Rectangle(0, 0, 150, 60)));
		assertEquals(0, changedPixels(render(overlay(true, false, true, 10, Color.WHITE), 0), new Rectangle(0, 0, 150, 60)));
	}

	@Test
	public void colorAndSizeChangeRenderedPixels()
	{
		BufferedImage small = render(overlay(true, false, false, 8, Color.RED), 0);
		BufferedImage large = render(overlay(true, false, false, 16, Color.BLUE), 0);
		assertTrue(changedPixels(large, new Rectangle(0, 0, 150, 60)) > changedPixels(small, new Rectangle(0, 0, 150, 60)));
		assertTrue(hasColor(small, Color.RED));
		assertTrue(hasColor(large, Color.BLUE));
	}

	static BankTabNumbersOverlay overlay(boolean enabled, boolean bankHidden, boolean tabsHidden, int size, Color color)
	{
		Widget[] children = new Widget[20];
		for (int tab = 0; tab <= 2; tab++)
		{
			final int index = tab;
			children[10 + tab] = widget(new Rectangle(10 + tab * 41, 10, 41, 40), false, null,
				new String[]{index == 0 ? "View all items" : index == 1 ? "View tab" : "New tab"});
		}
		Widget tabs = widget(new Rectangle(10, 10, 123, 40), tabsHidden, children, null);
		Widget items = widget(new Rectangle(), bankHidden, null, null);
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("getWidget"))
				{
					return (Integer) args[0] == InterfaceID.Bankmain.TABS ? tabs : items;
				}
				if (method.getName().equals("getVarbitValue"))
				{
					return 1;
				}
				throw new AssertionError("unexpected client access: " + method.getName());
			});
		return new BankTabNumbersOverlay(client, new BankTabShortcutsConfig()
		{
			@Override public boolean showTabNumbers() { return enabled; }
			@Override public int numberSize() { return size; }
			@Override public Color numberColor() { return color; }
		});
	}

	private static Widget widget(Rectangle bounds, boolean hidden, Widget[] children, String[] actions)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "isHidden": case "isSelfHidden": return hidden;
					case "getBounds": return bounds;
					case "getChild": return children[(Integer) args[0]];
					case "getActions": return actions;
					default: throw new AssertionError("unexpected widget access: " + method.getName());
				}
			});
	}

	private static BufferedImage render(BankTabNumbersOverlay overlay, int expectedHooks)
	{
		assertEquals(OverlayLayer.MANUAL, overlay.getLayer());
		assertTrue(overlay.getDrawHooks().contains(InterfaceID.Bankmain.TABS));
		BufferedImage image = new BufferedImage(150, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		Color original = graphics.getColor();
		java.awt.Font font = graphics.getFont();
		overlay.render(graphics);
		assertEquals(original, graphics.getColor());
		assertEquals(font, graphics.getFont());
		assertNull(graphics.getClip());
		graphics.dispose();
		return image;
	}

	private static int changedPixels(BufferedImage image, Rectangle area)
	{
		int count = 0;
		for (int y = area.y; y < area.y + area.height; y++)
		{
			for (int x = area.x; x < area.x + area.width; x++)
			{
				if (image.getRGB(x, y) != 0) { count++; }
			}
		}
		return count;
	}

	private static boolean hasColor(BufferedImage image, Color color)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == color.getRGB()) { return true; }
			}
		}
		return false;
	}
}

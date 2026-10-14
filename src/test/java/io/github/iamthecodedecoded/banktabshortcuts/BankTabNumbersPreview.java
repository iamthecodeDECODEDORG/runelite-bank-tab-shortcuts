package io.github.iamthecodedecoded.banktabshortcuts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Isolated visual fixture: renders the packaged overlay over a supplied bank screenshot, no game login/input. */
public final class BankTabNumbersPreview
{
	public static void main(String[] args) throws Exception
	{
		if (!BankTabNumbersOverlay.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"))
		{
			throw new IllegalStateException("Preview must load the packaged production JAR");
		}
		BufferedImage source = ImageIO.read(new File(args[0]));
		File output = new File(args[1]);
		if (!output.isDirectory() && !output.mkdirs()) { throw new IllegalStateException("Cannot create preview output"); }
		BufferedImage[] previews = new BufferedImage[3];
		BankTabShortcutsConfig defaults = new BankTabShortcutsConfig() {};
		for (int row = 0; row < previews.length; row++)
		{
			final int mode = row;
			previews[row] = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = previews[row].createGraphics();
			graphics.drawImage(source, 0, 0, null);
			new BankTabNumbersOverlay(client(), new BankTabShortcutsConfig()
			{
				@Override public boolean showTabNumbers() { return mode != 0; }
				@Override public int numberSize() { return mode == 2 ? 14 : defaults.numberSize(); }
				@Override public Color numberColor() { return mode == 2 ? new Color(160, 210, 180) : defaults.numberColor(); }
			}).render(graphics);
			graphics.dispose();
			ImageIO.write(previews[row], "png", new File(output, "bank-labels-" + row + ".png"));
		}
		SwingUtilities.invokeAndWait(() ->
		{
			JFrame frame = new JFrame("Bank Tab Shortcuts 1.1.0 - packaged overlay fixture");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setContentPane(new JPanel()
			{
				@Override protected void paintComponent(Graphics graphics)
				{
					super.paintComponent(graphics);
					String[] titles = {"Numbers off (default)", "Numbers on: size 10, muted color", "Custom: size 14, green"};
					for (int row = 0; row < previews.length; row++)
					{
						int y = 27 + row * 174;
						graphics.setColor(Color.LIGHT_GRAY);
						graphics.drawString(titles[row] + " | right: 3x tab detail", 15, y);
						graphics.drawImage(previews[row], 15, y + 10, null);
						graphics.drawImage(previews[row], 520, y + 10, 1012, y + 130, 64, 64, 228, 104, null);
					}
				}
			});
			frame.getContentPane().setBackground(new Color(32, 32, 32));
			frame.getContentPane().setPreferredSize(new Dimension(1025, 550));
			frame.pack();
			frame.setLocation(0, 0);
			frame.setVisible(true);
		});
	}

	private static Client client()
	{
		Widget[] children = new Widget[20];
		for (int tab = 0; tab <= 9; tab++)
		{
			children[10 + tab] = widget(new Rectangle(64 + tab * 41, 64, 41, 40), null);
		}
		Widget tabs = widget(new Rectangle(64, 64, 410, 40), children);
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				if (method.getName().equals("getWidget")) { return tabs; }
				if (method.getName().equals("getVarbitValue")) { return 1; }
				throw new AssertionError("Unexpected fixture client access: " + method.getName());
			});
	}

	private static Widget widget(Rectangle bounds, Widget[] children)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "isHidden": return false;
					case "getBounds": return bounds;
					case "getActions": return new String[]{"View tab"};
					case "getChild": return children[(Integer) args[0]];
					default: throw new AssertionError("Unexpected fixture widget access: " + method.getName());
				}
			});
	}
}

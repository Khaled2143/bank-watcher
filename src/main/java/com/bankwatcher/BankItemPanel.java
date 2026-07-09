/*
 * Copyright (c) 2025, Khaled Ahmed
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.bankwatcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

public class BankItemPanel extends JPanel
{
	private static final Color GAIN = new Color(0, 200, 0);
	private static final Color LOSS = new Color(230, 60, 60);
	private static final Color MUTED = new Color(190, 190, 190);

	public BankItemPanel(BankItem item, BufferedImage icon)
	{
		setLayout(new BorderLayout(8, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

		int delta = item.getDelta();

		// Left-edge accent: green up, red down, neutral if unchanged.
		Color edge = delta > 0 ? GAIN : (delta < 0 ? LOSS : ColorScheme.MEDIUM_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEmptyBorder(4, 4, 4, 4),
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
						BorderFactory.createMatteBorder(0, 3, 0, 0, edge))));

		JLabel iconLabel = (icon != null) ? new JLabel(new ImageIcon(icon)) : new JLabel("\uD83E\uDE99", SwingConstants.CENTER);
		iconLabel.setPreferredSize(new Dimension(45, 45));
		add(iconLabel, BorderLayout.WEST);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		// --- Name ---
		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// --- Stats: each on its own full-width line ---
		int qtyDelta = item.getQuantityDelta();
		String qtyText = qtyDelta != 0
				? String.format("Qty: %s (%s%s)", format(item.getQuantity()), qtyDelta > 0 ? "+" : "-", format(Math.abs(qtyDelta)))
				: String.format("Qty: %s", format(item.getQuantity()));

		textPanel.add(nameLabel);
		textPanel.add(Box.createVerticalStrut(3));
		textPanel.add(muted(qtyText));
		textPanel.add(muted(String.format("GE: %s", format(item.getGePrice()))));
		textPanel.add(muted(String.format("Alch: %s", format(item.getAlchValue()))));

		// --- Last / New scan totals ---
		if (delta != 0)
		{
			textPanel.add(Box.createVerticalStrut(2));
			textPanel.add(muted(String.format("Last scan: %s", format(item.getOldTotal()))));
			textPanel.add(muted(String.format("New scan: %s", format(item.getTotalPrice()))));
			textPanel.add(buildChangeLabel(item));
		}
		else
		{
			textPanel.add(muted(String.format("Total: %s", format(item.getTotalPrice()))));
		}

		add(textPanel, BorderLayout.CENTER);
	}

	private JLabel buildChangeLabel(BankItem item)
	{
		int delta = item.getDelta();
		int baseline = item.getOldTotal();

		String pctText = "";
		if (baseline != 0)
		{
			double pct = delta * 100.0 / baseline;
			pctText = String.format(" (%+.1f%%)", pct);
		}

		String prefix = delta > 0 ? "+" : "-";
		JLabel changeLabel = new JLabel(String.format("Change: %s%s%s", prefix, format(Math.abs(delta)), pctText));
		changeLabel.setFont(changeLabel.getFont().deriveFont(Font.BOLD, 14f));
		changeLabel.setForeground(delta > 0 ? GAIN : LOSS);
		changeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		changeLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		return changeLabel;
	}

	/**
	 * Full commas under 100K; K/M/B with one decimal above, so large values
	 * don't overflow the fixed panel width.
	 */
	private String format(long value)
	{
		long abs = Math.abs(value);
		if (abs < 100_000)
		{
			return String.format("%,d", value);
		}
		if (abs < 1_000_000)
		{
			return trim(value / 1_000.0) + "K";
		}
		if (abs < 1_000_000_000)
		{
			return trim(value / 1_000_000.0) + "M";
		}
		return trim(value / 1_000_000_000.0) + "B";
	}

	/**
	 * One decimal, but drop a trailing ".0" so 5M reads "5M" not "5.0M".
	 */
	private String trim(double v)
	{
		String s = String.format("%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private JLabel muted(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(14f));
		label.setForeground(MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}

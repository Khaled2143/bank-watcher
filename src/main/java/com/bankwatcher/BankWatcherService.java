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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BankWatcherService
{
	private static final String SCAN_COUNT_KEY = "scan_count";
	private static final String LAST_RESET_KEY = "scan_reset_day";
	private static final int MAX_SCANS_PER_DAY = 5;
	private static final String QUANTITY_SNAPSHOT_KEY = "bank_quantities";
	private static final String CONFIG_GROUP = "bankwatcher";
	private static final String SNAPSHOT_KEY = "bank_snapshot";
	private final Map<Integer, Integer> previousTotals = new HashMap<>();
	private final Map<Integer, Integer> previousQuantities = new HashMap<>();

	@Inject
	private Client client;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private OkHttpClient httpClient;
	@Inject
	private Gson gson;
	@Inject
	private ScheduledExecutorService executor;

	private boolean snapshotLoaded = false;

	/**
	 * Returns whether the user has scans remaining today. Does NOT increment.
	 * Handles the daily reset.
	 */
	public boolean canScan()
	{
		String today = LocalDate.now().toString();
		String lastResetDay = configManager.getConfiguration(CONFIG_GROUP, LAST_RESET_KEY);

		// New day -> reset stored count.
		if (lastResetDay == null || !lastResetDay.equals(today))
		{
			configManager.setConfiguration(CONFIG_GROUP, LAST_RESET_KEY, today);
			configManager.setConfiguration(CONFIG_GROUP, SCAN_COUNT_KEY, "0");
			configManager.sendConfig();
			return true;
		}

		return getScanCount() < MAX_SCANS_PER_DAY;
	}

	public String getScanStatusText()
	{
		String today = LocalDate.now().toString();
		String lastResetDay = configManager.getConfiguration(CONFIG_GROUP, LAST_RESET_KEY);
		int scanCount = 0;

		try
		{
			String countStr = configManager.getConfiguration(CONFIG_GROUP, SCAN_COUNT_KEY);
			if (countStr != null)
			{
				scanCount = Integer.parseInt(countStr);
			}
		}
		catch (Exception ignored)
		{
		}

		boolean newDay = (lastResetDay == null || !lastResetDay.equals(today));
		if (newDay)
		{
			return String.format("You've used 0/%d scans today. Daily reset active.", MAX_SCANS_PER_DAY);
		}

		return String.format("You have used %d/%d scans today. Next reset at midnight.", scanCount, MAX_SCANS_PER_DAY);
	}

	/**
	 * Records that a scan was used (increments the daily counter).
	 * Call this only when an actual scan is performed.
	 */
	public void recordScan()
	{
		int count = getScanCount() + 1;
		configManager.setConfiguration(CONFIG_GROUP, SCAN_COUNT_KEY, String.valueOf(count));
		configManager.sendConfig();
	}
	/**
	 * Whether the bank is currently open/readable. Must be called on the client thread.
	 */
	public boolean isBankOpen()
	{
		return client.getItemContainer(InventoryID.BANK) != null;
	}
	/**
	 * Returns how many scans have been used today WITHOUT incrementing.
	 * Accounts for daily reset.
	 */
	public int getScanCount()
	{
		String today = LocalDate.now().toString();
		String lastResetDay = configManager.getConfiguration(CONFIG_GROUP, LAST_RESET_KEY);

		// New day means the effective count is 0.
		if (lastResetDay == null || !lastResetDay.equals(today))
		{
			return 0;
		}

		try
		{
			String countStr = configManager.getConfiguration(CONFIG_GROUP, SCAN_COUNT_KEY);
			if (countStr != null)
			{
				return Integer.parseInt(countStr);
			}
		}
		catch (Exception ignored)
		{
		}

		return 0;
	}

	public int getMaxScansPerDay()
	{
		return MAX_SCANS_PER_DAY;
	}

	/**
	 * Reads bank item IDs on the client thread, then runs the network fetch and
	 * delta calculation off-thread. The result is delivered via callback.
	 * Must be called from the client thread.
	 */
	public void scanBankAsync(Consumer<List<BankItem>> callback)
	{
		loadSnapshot();

		ItemContainer bankItems = client.getItemContainer(InventoryID.BANK);
		if (bankItems == null)
		{
			log.info("No bank items found.");
			callback.accept(Collections.emptyList());
			return;
		}

		// Read everything that requires the client thread NOW, into plain data.
		List<int[]> rawItems = new ArrayList<>(); // [id, quantity]
		List<Integer> tradeableIds = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();

		for (Item item : bankItems.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}

			int itemId = item.getId();
			ItemComposition comp = itemManager.getItemComposition(itemId);
			if (!comp.isTradeable())
			{
				continue;
			}

			rawItems.add(new int[]{itemId, item.getQuantity()});
			tradeableIds.add(itemId);
			names.put(itemId, comp.getName());
		}

		// Fallback prices (itemManager) are safe to read here too.
		Map<Integer, Integer> fallbackPrices = new HashMap<>();
		for (int id : tradeableIds)
		{
			fallbackPrices.put(id, itemManager.getItemPrice(id));
		}

		// Fetch prices without blocking; assembly runs once all batches finish.
		fetchWikiPricesAsync(tradeableIds, livePrices ->
		{
			List<BankItem> trackedItems = new ArrayList<>();
			for (int[] raw : rawItems)
			{
				int itemId = raw[0];
				int quantity = raw[1];

				int gePrice = livePrices.getOrDefault(itemId, fallbackPrices.getOrDefault(itemId, 0));
				int totalPrice = gePrice * quantity;

				int oldTotal = previousTotals.getOrDefault(itemId, totalPrice);
				int delta = totalPrice - oldTotal;

				int oldQuantity = previousQuantities.getOrDefault(itemId, quantity);
				int quantityDelta = quantity - oldQuantity;

				previousTotals.put(itemId, totalPrice);
				previousQuantities.put(itemId, quantity);

				trackedItems.add(new BankItem(
						itemId,
						names.get(itemId),
						gePrice,
						totalPrice,
						quantity,
						delta,
						quantityDelta
				));
			}

			saveSnapshot();
			log.info("Tracked {} bank items.", trackedItems.size());

			callback.accept(trackedItems);
		});
	}


	/**
	 * Fetches prices from the OSRS Wiki API (WeirdGloop) without blocking any thread.
	 * Batches are staggered via executor.schedule; the last batch to complete
	 * invokes the callback with the assembled price map. No Thread.sleep, no latch.
	 */
	private void fetchWikiPricesAsync(List<Integer> itemIds, Consumer<Map<Integer, Integer>> onComplete)
	{
		Map<Integer, Integer> prices = new ConcurrentHashMap<>();
		String baseUrl = "https://api.weirdgloop.org/exchange/history/osrs/latest";

		int batchSize = 100;
		int delayMs = 200;

		// Split ids into batches up front.
		List<List<Integer>> batches = new ArrayList<>();
		for (int i = 0; i < itemIds.size(); i += batchSize)
		{
			batches.add(new ArrayList<>(itemIds.subList(i, Math.min(i + batchSize, itemIds.size()))));
		}

		if (batches.isEmpty())
		{
			onComplete.accept(prices);
			return;
		}

		AtomicInteger remaining = new AtomicInteger(batches.size());

		for (int b = 0; b < batches.size(); b++)
		{
			final List<Integer> batch = batches.get(b);
			final int index = b;
			long delay = (long) b * delayMs;

			executor.schedule(() ->
			{
				try
				{
					fetchSingleBatch(baseUrl, batch, index, prices);
				}
				finally
				{
					// Last batch to finish hands the results off.
					if (remaining.decrementAndGet() == 0)
					{
						log.info("Fetched {} total live prices from WeirdGloop API.", prices.size());
						onComplete.accept(prices);
					}
				}
			}, delay, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Performs a single batch HTTP request and merges results into the shared map.
	 */
	private void fetchSingleBatch(String baseUrl, List<Integer> batch, int index, Map<Integer, Integer> prices)
	{
		String joinedIds = String.join("|", batch.stream().map(String::valueOf).toArray(String[]::new));
		String url = baseUrl + "?id=" + joinedIds;

		Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", "bankwatcher-plugin (contact: https://github.com/Khaled2143)")
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.isSuccessful() && response.body() != null)
			{
				String body = response.body().string();
				com.google.gson.JsonObject json = gson.fromJson(body, com.google.gson.JsonObject.class);

				for (String key : json.keySet())
				{
					com.google.gson.JsonObject obj = json.getAsJsonObject(key);
					if (obj != null && obj.has("price"))
					{
						int price = obj.get("price").getAsInt();
						if (price > 0)
						{
							prices.put(Integer.parseInt(key), price);
						}
					}
				}
				log.info("Fetched {} items for batch starting at index {}.", batch.size(), index);
			}
			else
			{
				log.warn("Failed to fetch Wiki prices for batch {}. Code: {}", index, response.code());
			}
		}
		catch (Exception e)
		{
			log.warn("Error fetching Wiki price batch {}: ", index, e);
		}
	}


	/**
	 * Loads the previously saved snapshot from RuneLite config (runs only once).
	 */
	private void loadSnapshot()
	{
		if (snapshotLoaded)
		{
			return;
		}
		snapshotLoaded = true;

		try
		{
			// Load total values
			String totalsJson = configManager.getConfiguration(CONFIG_GROUP, SNAPSHOT_KEY);
			if (totalsJson != null && !totalsJson.isEmpty())
			{
				Type type = new TypeToken<Map<Integer, Integer>>()
				{
				}.getType();
				Map<Integer, Integer> loaded = gson.fromJson(totalsJson, type);
				if (loaded != null)
				{
					previousTotals.clear();
					previousTotals.putAll(loaded);
					log.info("Loaded {} saved totals from config.", loaded.size());
				}
			}

			// Load quantities
			String qtyJson = configManager.getConfiguration(CONFIG_GROUP, QUANTITY_SNAPSHOT_KEY);
			if (qtyJson != null && !qtyJson.isEmpty())
			{
				Type type = new TypeToken<Map<Integer, Integer>>()
				{
				}.getType();
				Map<Integer, Integer> loadedQty = gson.fromJson(qtyJson, type);
				if (loadedQty != null)
				{
					previousQuantities.clear();
					previousQuantities.putAll(loadedQty);
					log.info("Loaded {} saved quantities from config.", loadedQty.size());
				}
			}

			if (previousTotals.isEmpty() && previousQuantities.isEmpty())
			{
				log.info("No saved snapshot found -- starting fresh.");
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load snapshot from config.", e);
		}
	}

	/**
	 * Saves the latest snapshot of total values to RuneLite config.
	 */
	private void saveSnapshot()
	{
		try
		{
			String totalsJson = gson.toJson(previousTotals);
			configManager.setConfiguration(CONFIG_GROUP, SNAPSHOT_KEY, totalsJson);

			String qtyJson = gson.toJson(previousQuantities);
			configManager.setConfiguration(CONFIG_GROUP, QUANTITY_SNAPSHOT_KEY, qtyJson);

			log.info("Saved {} totals and {} quantities to config.", previousTotals.size(), previousQuantities.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to save snapshot to config.", e);
		}
	}
}
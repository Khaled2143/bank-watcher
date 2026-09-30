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

public class BankItem
{

	private final String name;
	private final long gePrice;
	private final long totalPrice;
	private final long oldTotal;
	private final long delta;
	private final int quantity;
	private final int quantityDelta;
	private final int id;
	private final int alchValue;

	public BankItem(int id, String name, long gePrice, long totalPrice, long oldTotal, int quantity, long delta, int quantityDelta, int alchValue)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.gePrice = gePrice;
		this.totalPrice = totalPrice;
		this.oldTotal = oldTotal;
		this.delta = delta;
		this.quantityDelta = quantityDelta;
		this.alchValue = alchValue;
	}

	public int getAlchValue()
	{
		return alchValue;
	}

	public int getQuantityDelta()
	{
		return quantityDelta;
	}

	public int getId()
	{
		return id;
	}

	public long getDelta()
	{
		return delta;
	}

	public long getOldTotal()
	{
		return oldTotal;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public String getName()
	{
		return name;
	}

	public long getGePrice()
	{
		return gePrice;
	}

	public long getTotalPrice()
	{
		return totalPrice;
	}

	@Override
	public String toString()
	{
		return String.format("%s | Price: %,d | Total: %,d | Quantity: %,d | Delta: %,d | Alch: %,d", name, gePrice, totalPrice, quantity, delta, alchValue);
	}
}
// -*- Mode: Java; indent-tabs-mode: t; tab-width: 4 -*-
// ---------------------------------------------------------------------------
// Multi-Phasic Applications: SquirrelJME
//     Copyright (C) Stephanie Gawroriski <xer@multiphasicapps.net>
// ---------------------------------------------------------------------------
// SquirrelJME is under the Mozilla Public License Version 2.0.
// See https://github.com/SquirrelJME/SquirrelJME/blob/trunk/LICENSE for licensing and copyright information.
// ---------------------------------------------------------------------------

package opendoja.audio.mld.ma3;

import opendoja.host.DoJaEncoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Rom Data Loader.
 *
 * @since 2025/05/03
 */
public enum MA3Rom
{
	/** MA-2 Instruments. */
	MA2_INSTRUMENTS("2i", 128),
	
	/** MA-2 Drums. */
	MA2_DRUMS("2d", 61),
	
	/** MA-3 Instruments 20P. */
	MA3_INSTRUMENTS_2OP("3i20p", 128),
	
	/** MA-3 Instruments 40P. */
	MA3_INSTRUMENTS_4OP("3i40p", 128),
	
	/** MA-3 Drums 20P. */
	MA3_DRUMS_2OP("3d20p", 61),
	
	/** MA-3 Drums 40P. */
	MA3_DRUMS_4OP("3d40p", 61),
	
	/** MA-3 Drum Waves. */
	MA3_DRUMS_WAVE("3d", 21),
	
	/** MA-3 WaveROM. */
	MA3_WAVEROM("3wr", 7),
	
	/* End. */
	;
	
	/** The data prefix. */
	protected final String prefix;
	
	/** The number of entries. */
	protected final int count;
	
	/**
	 * Initializes the ROM data load info.
	 *
	 * @param __prefix The prefix for the data.
	 * @param __count The number of data entries.
	 * @throws NullPointerException On null arguments.
	 * @since 2025/05/03
	 */
	MA3Rom(String __prefix, int __count)
		throws NullPointerException
	{
		if (__prefix == null)
			throw new NullPointerException("NARG");
		
		this.prefix = __prefix;
		this.count = __count;
	}
	
	/**
	 * Reads in the given resource.
	 *
	 * @param __id The resource ID.
	 * @return The data for the given resource.
	 * @throws IndexOutOfBoundsException If the index is not valid.
	 * @since 2025/05/03
	 */
	public final byte[] bytes(int __id)
		throws IndexOutOfBoundsException, RuntimeException
	{
		try
		{
			return this.decode(readAll(this.input(__id)));
		}
		catch (IOException __e)
		{
			throw new RuntimeException(__e.getMessage(), __e);
		}
	}
	
	/**
	 * Reads in the given resource.
	 *
	 * @param __id The resource ID.
	 * @return The data for the given resource.
	 * @throws IndexOutOfBoundsException If the index is not valid.
	 * @since 2025/05/03
	 */
	public final InputStream input(int __id)
		throws IndexOutOfBoundsException
	{
		if (__id < 0 || __id > 0xFF || __id >= this.count)
			throw new IndexOutOfBoundsException("IOOB");
		
		// Load in resource
		String baseName = String.format("%s.%02x", this.prefix, __id);
		InputStream result = MA3Rom.class.getResourceAsStream(baseName);
		if (result == null)
			result = MA3Rom.class.getResourceAsStream(baseName + ".__mime");
		if (result == null)
			throw new IndexOutOfBoundsException("IOOB");
		
		return result;
	}

	private byte[] decode(byte[] bytes)
	{
		String text = new String(bytes, StandardCharsets.US_ASCII);
		if (!text.startsWith("begin-base64 "))
			return bytes;
		int start = text.indexOf('\n');
		int end = text.indexOf("\n====");
		if (start < 0 || end < 0 || end <= start)
			throw new RuntimeException("Invalid MA3 ROM wrapper.");
		String payload = text.substring(start + 1, end).replace("\n", "");
		return Base64.getMimeDecoder().decode(payload);
	}

	private static byte[] readAll(InputStream input)
		throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) >= 0)
		{
			if (read > 0)
				output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}
}

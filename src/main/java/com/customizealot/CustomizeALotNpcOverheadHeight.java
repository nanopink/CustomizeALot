package com.customizealot;

import net.runelite.api.Client;

/**
 * Reads the NPC-definition height used by the native actor 2D renderer.
 *
 * <p>RuneLite exposes the config index but not NPC opcode 124 through
 * {@code NPCComposition}. Keep this decoder fail-closed so a cache-format
 * change falls back to the actor's public logical height.</p>
 */
final class CustomizeALotNpcOverheadHeight
{
	static final int NO_OVERRIDE = -1;
	static final int LOAD_FAILED = Integer.MIN_VALUE;
	static final int DECODE_FAILED = Integer.MIN_VALUE + 1;

	// NPC definitions are files in config archive 9. There is no public gameval
	// constant for config archive IDs.
	private static final int NPC_CONFIG_ARCHIVE = 9;

	private CustomizeALotNpcOverheadHeight()
	{
	}

	static int load(Client client, int npcDefinitionId)
	{
		if (client == null || npcDefinitionId < 0)
		{
			return LOAD_FAILED;
		}

		try
		{
			return decode(client.getIndexConfig().loadData(
				NPC_CONFIG_ARCHIVE,
				npcDefinitionId));
		}
		catch (RuntimeException ex)
		{
			return LOAD_FAILED;
		}
	}

	static int decode(byte[] data)
	{
		if (data == null)
		{
			return LOAD_FAILED;
		}

		try
		{
			Buffer buffer = new Buffer(data);
			int overheadHeight = NO_OVERRIDE;
			while (true)
			{
				int opcode = buffer.readUnsignedByte();
				if (opcode == 0)
				{
					return overheadHeight;
				}
				if (opcode == 124)
				{
					overheadHeight = buffer.readUnsignedShort();
					continue;
				}

				skipOpcode(buffer, opcode);
			}
		}
		catch (RuntimeException ex)
		{
			return DECODE_FAILED;
		}
	}

	private static void skipOpcode(Buffer buffer, int opcode)
	{
		switch (opcode)
		{
			case 1:
			case 60:
				buffer.skip(buffer.readUnsignedByte() * 2);
				break;
			case 2:
			case 30:
			case 31:
			case 32:
			case 33:
			case 34:
				buffer.skipString();
				break;
			case 12:
			case 100:
			case 101:
				buffer.skip(1);
				break;
			case 13:
			case 14:
			case 15:
			case 16:
			case 18:
			case 74:
			case 75:
			case 76:
			case 77:
			case 78:
			case 79:
			case 95:
			case 97:
			case 98:
			case 103:
			case 114:
			case 116:
			case 126:
			case 146:
				buffer.skip(2);
				break;
			case 17:
			case 115:
			case 117:
				buffer.skip(8);
				break;
			case 40:
			case 41:
				buffer.skip(buffer.readUnsignedByte() * 4);
				break;
			case 61:
			case 62:
				buffer.skip(buffer.readUnsignedByte() * 4);
				break;
			case 93:
			case 99:
			case 107:
			case 109:
			case 111:
			case 122:
			case 123:
			case 129:
			case 130:
			case 145:
			case 147:
				break;
			case 102:
				skipHeadIcons(buffer);
				break;
			case 106:
				skipTransforms(buffer, false);
				break;
			case 118:
				skipTransforms(buffer, true);
				break;
			case 249:
				skipParams(buffer);
				break;
			case 251:
				buffer.skip(2);
				buffer.skipString();
				break;
			case 252:
				buffer.skip(13);
				buffer.skipString();
				break;
			case 253:
				buffer.skip(15);
				buffer.skipString();
				break;
			default:
				throw new IllegalArgumentException("Unknown NPC opcode " + opcode);
		}
	}

	private static void skipHeadIcons(Buffer buffer)
	{
		int bitfield = buffer.readUnsignedByte();
		for (int bits = bitfield; bits != 0; bits >>>= 1)
		{
			if ((bits & 1) != 0)
			{
				buffer.skipNullableLargeSmart();
				buffer.skipUnsignedShortSmartMinusOne();
			}
		}
	}

	private static void skipTransforms(Buffer buffer, boolean hasFallback)
	{
		buffer.skip(hasFallback ? 6 : 4);
		int count = buffer.readUnsignedByte();
		buffer.skip((count + 1) * 2);
	}

	private static void skipParams(Buffer buffer)
	{
		int count = buffer.readUnsignedByte();
		for (int i = 0; i < count; i++)
		{
			int type = buffer.readUnsignedByte();
			buffer.skip(3);
			if (type == 1)
			{
				buffer.skipString();
			}
			else
			{
				buffer.skip(type == 2 ? 8 : 4);
			}
		}
	}

	private static final class Buffer
	{
		private final byte[] data;
		private int offset;

		private Buffer(byte[] data)
		{
			this.data = data;
		}

		private int readUnsignedByte()
		{
			require(1);
			return data[offset++] & 0xFF;
		}

		private int readUnsignedShort()
		{
			require(2);
			int value = ((data[offset] & 0xFF) << 8)
				| (data[offset + 1] & 0xFF);
			offset += 2;
			return value;
		}

		private void skip(int length)
		{
			require(length);
			offset += length;
		}

		private void skipString()
		{
			while (readUnsignedByte() != 0)
			{
				// Scan to the null terminator.
			}
		}

		private void skipNullableLargeSmart()
		{
			require(1);
			skip(data[offset] < 0 ? 4 : 2);
		}

		private void skipUnsignedShortSmartMinusOne()
		{
			require(1);
			skip((data[offset] & 0xFF) < 128 ? 1 : 2);
		}

		private void require(int length)
		{
			if (length < 0 || offset > data.length - length)
			{
				throw new IllegalArgumentException("Truncated NPC definition");
			}
		}
	}
}

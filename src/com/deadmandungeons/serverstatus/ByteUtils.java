package com.deadmandungeons.serverstatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Contains various utility methods for manipulating bytes, byte arrays, and byte streams
 */
public class ByteUtils {

    /**
     * Creates and returns a new array with the values of the original from index <code>a</code> to index <code>b</code>
     * and of size <code>(b-a)</code>.
     * @param in input array
     * @param a first index
     * @param b last index
     * @return a new array based on the desired range of the input
     */
    public static byte[] subarray(byte[] in, int a, int b) {
        if (b - a > in.length) {
            return in;
        }

        byte[] out = new byte[(b - a) + 1];
        System.arraycopy(in, a, out, 0, (b + 1) - a);
        return out;
    }

    /**
     * Functions similarly to the standard java <code>String.trim()</code> method (except that null bytes (0x00),
     * instead of whitespace, are stripped from the beginning and end). If the input array already has no leading/trailing null bytes,
     * is returned unmodified.
     * @param arr the input array
     * @return an array without any leading or trailing null bytes
     */
    public static byte[] trim(byte[] arr) {
        if (arr[0] != 0 && arr[arr.length - 1] != 0) {
            return arr; // return the input if it has no leading/trailing null bytes
        }

        int begin = 0, end = arr.length;
        // find the first non-null byte
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != (byte) 0x00) {
                begin = i;
                break;
            }
        }
        // find the last non-null byte
        for (int i = arr.length - 1; i >= 0; i--) {
            if (arr[i] != (byte) 0x00) {
                end = i;
                break;
            }
        }

        return subarray(arr, begin, end);
    }

    /**
     * Spits the input array into separate byte arrays. Works similarly to <code>String.split()</code>, but always splits on a null byte (0x00).
     * @param input the input array
     * @return a new array of byte arrays
     */
    public static byte[][] split(byte[] input) {
        ArrayList<byte[]> temp = new ArrayList<>();

        int index_cache = 0;
        for (int i = 0; i < input.length; i++) {
            if (input[i] == 0x00) {
                // output[out_index++] = subarray(input, index_cache, i-1);
                // store the array from the last null byte to the current one
                byte[] b = subarray(input, index_cache, i - 1);
                temp.add(b);
                index_cache = i + 1; // note, this is the index *after* the null byte
            }
        }
        // get the remaining part
        // prevent duplication if there are no null bytes
        if (index_cache != 0) {
            // output[out_index] = subarray(input, index_cache, input.length-1);
            byte[] b = subarray(input, index_cache, input.length - 1);
            temp.add(b);
        }

        byte[][] output = new byte[temp.size()][input.length];
        for (int i = 0; i < temp.size(); i++) {
            output[i] = temp.get(i);
        }

        return output;
    }

    /**
     * Creates an new array of length <code>arr+amount</code>, identical to the original, <code>arr</code>,
     * except with <code>amount</code> null bytes (0x00) padding the end.
     * @param arr the input array
     * @param amount the amount of byte to pad
     * @return a new array, identical to the original, with the desired padding
     */
    public static byte[] padArrayEnd(byte[] arr, int amount) {
        byte[] arr2 = new byte[arr.length + amount];
        System.arraycopy(arr, 0, arr2, 0, arr.length);
        for (int i = arr.length; i < arr2.length; i++) {
            arr2[i] = 0;
        }
        return arr2;
    }

    public static short bytesToShort(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b, 0, 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort();
    }

    // Big endian !!
    public static byte[] intToBytes(int in) {
        byte[] b;
        b = new byte[]{(byte) (in >>> 24 & 0xFF), (byte) (in >>> 16 & 0xFF), (byte) (in >>> 8 & 0xFF), (byte) (in & 0xFF)};
        return b;
    }

    public static int bytesToInt(byte[] in) {
        return ByteBuffer.wrap(in).getInt(); // note: big-endian by default
    }


    public static int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();

            i |= (k & 0x7F) << j++ * 7;

            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
            if ((k & 0x80) != 128) {
                break;
            }
        }
        return i;
    }

    public static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
        while (true) {
            if ((paramInt & 0xFFFFFF80) == 0) {
                out.writeByte(paramInt);
                return;
            }

            out.writeByte(paramInt & 0x7F | 0x80);
            paramInt >>>= 7;
        }
    }

}
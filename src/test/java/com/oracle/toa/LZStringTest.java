package com.oracle.toa;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;


/**
 * Compression Utilities test.
 */
public class LZStringTest {
    public static void main(String args[]) throws Exception {

        // Normal Compression and Decompression
        String test = "Lets see how much we can compress this string!";
        String output = LZString.compress(test);

        String decompressed = LZString.decompress(output);
        System.out.println("Decompressed: " + decompressed);
        assertThat("Compression And Decompression Work Using LZ Compression", decompressed, equalTo(test));

        //UTF-16 Compression and Decompression
        String testUTF16 = "Lets see how much we can compress this string!";
        String outputUTF16 = LZString.compressToUTF16(testUTF16);

        String decompressedUTF16 = LZString.decompressFromUTF16(outputUTF16);
        System.out.println("Decompressed: " + decompressedUTF16);
        assertThat("Compression And Decompression Using UTF16 Works", decompressedUTF16, equalTo(testUTF16));

        String testBase64 = "Lets see how much we can compress this string!";
        String outputBase64 = LZString.compressToBase64(testBase64);
        System.out.println("Compressed: " + outputBase64);
        String decompressedBase64 = LZString.decompressFromBase64(outputBase64);
        System.out.println("Decompressed: " + decompressedBase64);
        assertThat("Base64 Compression/Decompression Works", testBase64, equalTo(decompressedBase64));
    }
}

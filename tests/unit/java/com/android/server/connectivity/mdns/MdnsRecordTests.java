/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.util.Log;

import com.android.net.module.util.HexDump;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.List;

// The record test data does not use compressed names (label pointers), since that would require
// additional data to populate the label dictionary accordingly.
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsRecordTests {
    private static final String TAG = "MdnsRecordTests";
    private static final int MAX_PACKET_SIZE = 4096;
    private static final InetSocketAddress MULTICAST_IPV4_ADDRESS =
            new InetSocketAddress(MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT);
    private static final InetSocketAddress MULTICAST_IPV6_ADDRESS =
            new InetSocketAddress(MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT);

    @Test
    public void testInet4AddressRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "0474657374000001" + "0001000011940004" + "0A010203");
        assertNotNull(dataIn);
        String dataInText = HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        assertEquals("test", name[0]);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_A, type);

        MdnsInetAddressRecord record = new MdnsInetAddressRecord(name, MdnsRecord.TYPE_A, reader);
        Inet4Address addr = record.getInet4Address();
        assertEquals("/10.1.2.3", addr.toString());

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV4_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        assertEquals(dataInText, dataOutText);
    }

    @Test
    public void testTypeAAAInet6AddressRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "047465737400001C"
                        + "0001000011940010"
                        + "AABBCCDD11223344"
                        + "A0B0C0D010203040");
        assertNotNull(dataIn);
        String dataInText = HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT));
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_AAAA, type);

        MdnsInetAddressRecord record = new MdnsInetAddressRecord(name, MdnsRecord.TYPE_AAAA,
                reader);
        assertNull(record.getInet4Address());
        Inet6Address addr = record.getInet6Address();
        assertEquals("/aabb:ccdd:1122:3344:a0b0:c0d0:1020:3040", addr.toString());

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV6_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        assertEquals(dataInText, dataOutText);
    }

    @Test
    public void testTypeAAAInet4AddressRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "047465737400001C"
                        + "0001000011940010"
                        + "0000000000000000"
                        + "0000FFFF10203040");
        assertNotNull(dataIn);
        HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT));
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_AAAA, type);

        MdnsInetAddressRecord record = new MdnsInetAddressRecord(name, MdnsRecord.TYPE_AAAA,
                reader);
        assertNull(record.getInet6Address());
        Inet4Address addr = record.getInet4Address();
        assertEquals("/16.32.48.64", addr.toString());

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV4_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        final byte[] expectedDataIn =
                HexDump.hexStringToByteArray("047465737400001C000100001194000410203040");
        assertNotNull(expectedDataIn);
        String expectedDataInText = HexDump.dumpHexString(expectedDataIn, 0, expectedDataIn.length);

        assertEquals(expectedDataInText, dataOutText);
    }

    @Test
    public void testPointerRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "047465737400000C"
                        + "000100001194000E"
                        + "03666F6F03626172"
                        + "047175787800");
        assertNotNull(dataIn);
        String dataInText = HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_PTR, type);

        MdnsPointerRecord record = new MdnsPointerRecord(name, reader);
        String[] pointer = record.getPointer();
        assertEquals("foo.bar.quxx", MdnsRecord.labelsToString(pointer));

        assertFalse(record.hasSubtype());
        assertNull(record.getSubtype());

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV4_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        assertEquals(dataInText, dataOutText);
    }

    @Test
    public void testServiceRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "0474657374000021"
                        + "0001000011940014"
                        + "000100FF1F480366"
                        + "6F6F036261720471"
                        + "75787800");
        assertNotNull(dataIn);
        String dataInText = HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_SRV, type);

        MdnsServiceRecord record = new MdnsServiceRecord(name, reader);

        int servicePort = record.getServicePort();
        assertEquals(8008, servicePort);

        String serviceHost = MdnsRecord.labelsToString(record.getServiceHost());
        assertEquals("foo.bar.quxx", serviceHost);

        assertEquals(1, record.getServicePriority());
        assertEquals(255, record.getServiceWeight());

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV4_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        assertEquals(dataInText, dataOutText);
    }

    @Test
    public void testTextRecord() throws IOException {
        final byte[] dataIn = HexDump.hexStringToByteArray(
                "0474657374000010"
                        + "0001000011940024"
                        + "0D613D68656C6C6F"
                        + "2074686572650C62"
                        + "3D31323334353637"
                        + "3839300878797A3D"
                        + "21402324");
        assertNotNull(dataIn);
        String dataInText = HexDump.dumpHexString(dataIn, 0, dataIn.length);

        // Decode
        DatagramPacket packet = new DatagramPacket(dataIn, dataIn.length);
        MdnsPacketReader reader = new MdnsPacketReader(packet);

        String[] name = reader.readLabels();
        assertNotNull(name);
        assertEquals(1, name.length);
        String fqdn = MdnsRecord.labelsToString(name);
        assertEquals("test", fqdn);

        int type = reader.readUInt16();
        assertEquals(MdnsRecord.TYPE_TXT, type);

        MdnsTextRecord record = new MdnsTextRecord(name, reader);

        List<String> strings = record.getStrings();
        assertNotNull(strings);
        assertEquals(3, strings.size());

        assertEquals("a=hello there", strings.get(0));
        assertEquals("b=1234567890", strings.get(1));
        assertEquals("xyz=!@#$", strings.get(2));

        // Encode
        MdnsPacketWriter writer = new MdnsPacketWriter(MAX_PACKET_SIZE);
        record.write(writer, record.getReceiptTime());

        packet = writer.getPacket(MULTICAST_IPV4_ADDRESS);
        byte[] dataOut = packet.getData();

        String dataOutText = HexDump.dumpHexString(dataOut, 0, packet.getLength());
        Log.d(TAG, dataOutText);

        assertEquals(dataInText, dataOutText);
    }
}

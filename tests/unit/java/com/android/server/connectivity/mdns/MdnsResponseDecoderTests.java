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

import static com.android.server.connectivity.mdns.MdnsResponseDecoder.Clock;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.android.net.module.util.HexDump;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsResponseDecoderTests {
    private static final byte[] data = HexDump.hexStringToByteArray(
            "0000840000000004"
            + "00000003134A6F68"
            + "6E6E792773204368"
            + "726F6D6563617374"
            + "0B5F676F6F676C65"
            + "63617374045F7463"
            + "70056C6F63616C00"
            + "0010800100001194"
            + "006C2369643D3937"
            + "3062663534376237"
            + "3533666336336332"
            + "6432613336626238"
            + "3936616261380576"
            + "653D30320D6D643D"
            + "4368726F6D656361"
            + "73741269633D2F73"
            + "657475702F69636F"
            + "6E2E706E6716666E"
            + "3D4A6F686E6E7927"
            + "73204368726F6D65"
            + "636173740463613D"
            + "350473743D30095F"
            + "7365727669636573"
            + "075F646E732D7364"
            + "045F756470C03100"
            + "0C00010000119400"
            + "02C020C020000C00"
            + "01000011940002C0"
            + "0CC00C0021800100"
            + "000078001C000000"
            + "001F49134A6F686E"
            + "6E79277320436872"
            + "6F6D6563617374C0"
            + "31C0F30001800100"
            + "0000780004C0A864"
            + "68C0F3002F800100"
            + "0000780005C0F300"
            + "0140C00C002F8001"
            + "000011940009C00C"
            + "00050000800040");

    private static final byte[] data6 = HexDump.hexStringToByteArray(
            "0000840000000001000000030B5F676F6F676C656361737404"
            + "5F746370056C6F63616C00000C000100000078003330476F6F676C"
            + "652D486F6D652D4D61782D61363836666331323961366638636265"
            + "31643636353139343065336164353766C00CC02E00108001000011"
            + "9400C02369643D6136383666633132396136663863626531643636"
            + "3531393430653361643537662363643D4133304233303032363546"
            + "36384341313233353532434639344141353742314613726D3D4335"
            + "35393134383530383841313638330576653D3035126D643D476F6F"
            + "676C6520486F6D65204D61781269633D2F73657475702F69636F6E"
            + "2E706E6710666E3D417474696320737065616B65720863613D3130"
            + "3234340473743D320F62733D464138464341363734453537046E66"
            + "3D320372733DC02E0021800100000078002D000000001F49246136"
            + "3836666331322D396136662D386362652D316436362D3531393430"
            + "65336164353766C01DC13F001C8001000000780010200033330000"
            + "0000DA6C63FFFE7C74830109018001000000780004C0A801026C6F"
            + "63616C0000018001000000780004C0A8010A000001800100000078"
            + "0004C0A8010A00000000000000");

    private static final String DUMMY_CAST_SERVICE_NAME = "_googlecast";
    private static final String[] DUMMY_CAST_SERVICE_TYPE =
            new String[] {DUMMY_CAST_SERVICE_NAME, "_tcp", "local"};

    private final List<MdnsResponse> responses = new LinkedList<>();

    private final Clock mClock = mock(Clock.class);

    @Before
    public void setUp() {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, DUMMY_CAST_SERVICE_TYPE);
        assertNotNull(data);
        DatagramPacket packet = new DatagramPacket(data, data.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT));
        responses.clear();
        int errorCode = decoder.decode(packet, responses);
        assertEquals(MdnsResponseDecoder.SUCCESS, errorCode);
        assertEquals(1, responses.size());
    }

    @Test
    public void testDecodeWithNullServiceType() {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        assertNotNull(data);
        DatagramPacket packet = new DatagramPacket(data, data.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT));
        responses.clear();
        int errorCode = decoder.decode(packet, responses);
        assertEquals(MdnsResponseDecoder.SUCCESS, errorCode);
        assertEquals(2, responses.size());
    }

    @Test
    public void testDecodeMultipleAnswerPacket() throws IOException {
        MdnsResponse response = responses.get(0);
        assertTrue(response.isComplete());

        MdnsInetAddressRecord inet4AddressRecord = response.getInet4AddressRecord();
        Inet4Address inet4Addr = inet4AddressRecord.getInet4Address();

        assertNotNull(inet4Addr);
        assertEquals("/192.168.100.104", inet4Addr.toString());

        MdnsServiceRecord serviceRecord = response.getServiceRecord();
        String serviceName = serviceRecord.getServiceName();
        assertEquals(DUMMY_CAST_SERVICE_NAME, serviceName);

        String serviceInstanceName = serviceRecord.getServiceInstanceName();
        assertEquals("Johnny's Chromecast", serviceInstanceName);

        String serviceHost = MdnsRecord.labelsToString(serviceRecord.getServiceHost());
        assertEquals("Johnny's Chromecast.local", serviceHost);

        int serviceProto = serviceRecord.getServiceProtocol();
        assertEquals(MdnsServiceRecord.PROTO_TCP, serviceProto);

        int servicePort = serviceRecord.getServicePort();
        assertEquals(8009, servicePort);

        int servicePriority = serviceRecord.getServicePriority();
        assertEquals(0, servicePriority);

        int serviceWeight = serviceRecord.getServiceWeight();
        assertEquals(0, serviceWeight);

        MdnsTextRecord textRecord = response.getTextRecord();
        List<String> textStrings = textRecord.getStrings();
        assertEquals(7, textStrings.size());
        assertEquals("id=970bf547b753fc63c2d2a36bb896aba8", textStrings.get(0));
        assertEquals("ve=02", textStrings.get(1));
        assertEquals("md=Chromecast", textStrings.get(2));
        assertEquals("ic=/setup/icon.png", textStrings.get(3));
        assertEquals("fn=Johnny's Chromecast", textStrings.get(4));
        assertEquals("ca=5", textStrings.get(5));
        assertEquals("st=0", textStrings.get(6));
    }

    @Test
    public void testDecodeIPv6AnswerPacket() throws IOException {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, DUMMY_CAST_SERVICE_TYPE);
        assertNotNull(data6);
        DatagramPacket packet = new DatagramPacket(data6, data6.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT));

        responses.clear();
        int errorCode = decoder.decode(packet, responses);
        assertEquals(MdnsResponseDecoder.SUCCESS, errorCode);

        MdnsResponse response = responses.get(0);
        assertTrue(response.isComplete());

        MdnsInetAddressRecord inet6AddressRecord = response.getInet6AddressRecord();
        assertNotNull(inet6AddressRecord);
        Inet4Address inet4Addr = inet6AddressRecord.getInet4Address();
        assertNull(inet4Addr);

        Inet6Address inet6Addr = inet6AddressRecord.getInet6Address();
        assertNotNull(inet6Addr);
        assertEquals(inet6Addr.getHostAddress(), "2000:3333::da6c:63ff:fe7c:7483");
    }

    @Test
    public void testIsComplete() {
        MdnsResponse response = responses.get(0);
        assertTrue(response.isComplete());

        response.clearPointerRecords();
        assertFalse(response.isComplete());

        response = responses.get(0);
        response.setInet4AddressRecord(null);
        assertFalse(response.isComplete());

        response = responses.get(0);
        response.setInet6AddressRecord(null);
        assertFalse(response.isComplete());

        response = responses.get(0);
        response.setServiceRecord(null);
        assertFalse(response.isComplete());

        response = responses.get(0);
        response.setTextRecord(null);
        assertFalse(response.isComplete());
    }
}
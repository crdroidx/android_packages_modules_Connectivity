/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.server.nearby.provider;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.hardware.location.NanoAppMessage;
import android.nearby.DataElement;
import android.nearby.NearbyDeviceParcelable;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.presence.PresenceDiscoveryResult;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import service.proto.Blefilter;

public class ChreDiscoveryProviderTest {
    @Mock AbstractDiscoveryProvider.Listener mListener;
    @Mock ChreCommunication mChreCommunication;

    @Captor ArgumentCaptor<ChreCommunication.ContextHubCommsCallback> mChreCallbackCaptor;
    @Captor ArgumentCaptor<NearbyDeviceParcelable> mNearbyDevice;

    private static final int DATA_TYPE_CONNECTION_STATUS_KEY = 10;
    private static final int DATA_TYPE_BATTERY_KEY = 11;

    private ChreDiscoveryProvider mChreDiscoveryProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mChreDiscoveryProvider =
                new ChreDiscoveryProvider(context, mChreCommunication, new InLineExecutor());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testInit() {
        mChreDiscoveryProvider.init();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().started(true);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testOnNearbyDeviceDiscovered() {
        Blefilter.PublicCredential credential =
                Blefilter.PublicCredential.newBuilder()
                        .setSecretId(ByteString.copyFrom(new byte[] {1}))
                        .setAuthenticityKey(ByteString.copyFrom(new byte[2]))
                        .setPublicKey(ByteString.copyFrom(new byte[3]))
                        .setEncryptedMetadata(ByteString.copyFrom(new byte[4]))
                        .setEncryptedMetadataTag(ByteString.copyFrom(new byte[5]))
                        .build();
        Blefilter.BleFilterResult result =
                Blefilter.BleFilterResult.newBuilder()
                        .setTxPower(2)
                        .setRssi(1)
                        .setPublicCredential(credential)
                        .build();
        Blefilter.BleFilterResults results =
                Blefilter.BleFilterResults.newBuilder().addResult(result).build();
        NanoAppMessage chre_message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        results.toByteArray());
        mChreDiscoveryProvider.getController().setListener(mListener);
        mChreDiscoveryProvider.init();
        mChreDiscoveryProvider.onStart();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().onMessageFromNanoApp(chre_message);
        verify(mListener).onNearbyDeviceDiscovered(any());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testOnNearbyDeviceDiscoveredWithDataElements() {
        final byte [] connectionStatus = new byte[] {1, 2, 3};
        final byte [] batteryStatus = new byte[] {4, 5, 6};
        final List<DataElement> expectedExtendedProperties = new ArrayList<>();
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_CONNECTION_STATUS_KEY,
                connectionStatus));
        expectedExtendedProperties.add(new DataElement(DATA_TYPE_BATTERY_KEY, batteryStatus));

        Blefilter.PublicCredential credential =
                Blefilter.PublicCredential.newBuilder()
                        .setSecretId(ByteString.copyFrom(new byte[] {1}))
                        .setAuthenticityKey(ByteString.copyFrom(new byte[2]))
                        .setPublicKey(ByteString.copyFrom(new byte[3]))
                        .setEncryptedMetadata(ByteString.copyFrom(new byte[4]))
                        .setEncryptedMetadataTag(ByteString.copyFrom(new byte[5]))
                        .build();
        Blefilter.BleFilterResult result =
                Blefilter.BleFilterResult.newBuilder()
                        .setTxPower(2)
                        .setRssi(1)
                        .setPublicCredential(credential)
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(
                                        Blefilter.DataElement.ElementType
                                                .DE_CONNECTION_STATUS)
                                .setValue(ByteString.copyFrom(connectionStatus))
                                .setValueLength(connectionStatus.length)
                        )
                        .addDataElement(Blefilter.DataElement.newBuilder()
                                .setKey(
                                        Blefilter.DataElement.ElementType
                                                .DE_BATTERY_STATUS)
                                .setValue(ByteString.copyFrom(batteryStatus))
                                .setValueLength(batteryStatus.length)
                        )
                        .build();
        Blefilter.BleFilterResults results =
                Blefilter.BleFilterResults.newBuilder().addResult(result).build();
        NanoAppMessage chre_message =
                NanoAppMessage.createMessageToNanoApp(
                        ChreDiscoveryProvider.NANOAPP_ID,
                        ChreDiscoveryProvider.NANOAPP_MESSAGE_TYPE_FILTER_RESULT,
                        results.toByteArray());
        mChreDiscoveryProvider.getController().setListener(mListener);
        mChreDiscoveryProvider.init();
        mChreDiscoveryProvider.onStart();
        verify(mChreCommunication).start(mChreCallbackCaptor.capture(), any());
        mChreCallbackCaptor.getValue().onMessageFromNanoApp(chre_message);
        verify(mListener).onNearbyDeviceDiscovered(mNearbyDevice.capture());

        List<DataElement> extendedProperties = PresenceDiscoveryResult
                .fromDevice(mNearbyDevice.getValue()).getExtendedProperties();
        assertThat(expectedExtendedProperties.equals(extendedProperties)).isTrue();
    }

    private static class InLineExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}

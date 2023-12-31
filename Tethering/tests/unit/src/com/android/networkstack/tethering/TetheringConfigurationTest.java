/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.networkstack.tethering;

import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL;
import static android.telephony.CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.networkstack.apishim.ConstantsShim.KEY_CARRIER_SUPPORTS_TETHERING_BOOL;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_FORCE_USB_FUNCTIONS;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_USB_NCM_FUNCTION;
import static com.android.networkstack.tethering.TetheringConfiguration.TETHER_USB_RNDIS_FUNCTION;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.SharedLog;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Iterator;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringConfigurationTest {
    private final SharedLog mLog = new SharedLog("TetheringConfigurationTest");

    @Rule public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private static final String[] PROVISIONING_APP_NAME = {"some", "app"};
    private static final String PROVISIONING_NO_UI_APP_NAME = "no_ui_app";
    private static final String PROVISIONING_APP_RESPONSE = "app_response";
    private static final String TEST_PACKAGE_NAME = "com.android.tethering.test";
    private static final String APEX_NAME = "com.android.tethering";
    private static final long TEST_PACKAGE_VERSION = 1234L;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private Resources mResources;
    @Mock private Resources mResourcesForSubId;
    @Mock private PackageManager mPackageManager;
    @Mock private ModuleInfo mMi;
    private Context mMockContext;
    private boolean mHasTelephonyManager;
    private MockitoSession mMockingSession;
    private MockContentResolver mContentResolver;
    private final PersistableBundle mCarrierConfig = new PersistableBundle();

    private class MockTetheringConfiguration extends TetheringConfiguration {
        MockTetheringConfiguration(Context ctx, SharedLog log, int id) {
            super(ctx, log, id);
        }

        @Override
        protected Resources getResourcesForSubIdWrapper(Context ctx, int subId) {
            return mResourcesForSubId;
        }
    }

    private class MockContext extends BroadcastInterceptingContext {
        MockContext(Context base) {
            super(base);
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            return mApplicationInfo;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.TELEPHONY_SERVICE.equals(name)) {
                return mHasTelephonyManager ? mTelephonyManager : null;
            }
            return super.getSystemService(name);
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public String getPackageName() {
            return TEST_PACKAGE_NAME;
        }
    }

    @Before
    public void setUp() throws Exception {
        // TODO: use a dependencies class instead of mock statics.
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(DeviceConfig.class)
                .strictness(Strictness.WARN)
                .startMocking();
        DeviceConfigUtils.resetPackageVersionCacheForTest();
        doReturn(null).when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(TetheringConfiguration.TETHER_ENABLE_LEGACY_DHCP_SERVER)));
        setTetherForceUpstreamAutomaticFlagVersion(null);

        final PackageInfo pi = new PackageInfo();
        pi.setLongVersionCode(TEST_PACKAGE_VERSION);
        doReturn(pi).when(mPackageManager).getPackageInfo(eq(TEST_PACKAGE_NAME), anyInt());
        doReturn(mMi).when(mPackageManager).getModuleInfo(eq(APEX_NAME), anyInt());
        doReturn(TEST_PACKAGE_NAME).when(mMi).getPackageName();

        when(mResources.getStringArray(R.array.config_tether_dhcp_range)).thenReturn(
                new String[0]);
        when(mResources.getInteger(R.integer.config_tether_offload_poll_interval)).thenReturn(
                TetheringConfiguration.DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS);
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
                .thenReturn(new String[]{ "test_usb\\d" });
        when(mResources.getStringArray(R.array.config_tether_wifi_regexs))
                .thenReturn(new String[]{ "test_wlan\\d" });
        when(mResources.getStringArray(R.array.config_tether_bluetooth_regexs)).thenReturn(
                new String[0]);
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(new int[0]);
        when(mResources.getStringArray(R.array.config_mobile_hotspot_provision_app))
                .thenReturn(new String[0]);
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                false);
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_wifi_p2p_dedicated_ip))
                .thenReturn(false);
        initializeBpfOffloadConfiguration(true, null /* unset */);

        mHasTelephonyManager = true;
        mMockContext = new MockContext(mContext);

        mContentResolver = new MockContentResolver(mMockContext);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        // Call {@link #clearSettingsProvider()} before and after using FakeSettingsProvider.
        FakeSettingsProvider.clearSettingsProvider();
    }

    @After
    public void tearDown() throws Exception {
        mMockingSession.finishMocking();
        DeviceConfigUtils.resetPackageVersionCacheForTest();
        // Call {@link #clearSettingsProvider()} before and after using FakeSettingsProvider.
        FakeSettingsProvider.clearSettingsProvider();
    }

    private TetheringConfiguration getTetheringConfiguration(int... legacyTetherUpstreamTypes) {
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(
                legacyTetherUpstreamTypes);
        return new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
    }

    @Test
    public void testNoTelephonyManagerMeansNoDun() {
        mHasTelephonyManager = false;
        final TetheringConfiguration cfg = getTetheringConfiguration(
                new int[]{TYPE_MOBILE_DUN, TYPE_WIFI});
        assertFalse(cfg.isDunRequired);
        assertFalse(cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_DUN));
        // Just to prove we haven't clobbered Wi-Fi:
        assertTrue(cfg.preferredUpstreamIfaceTypes.contains(TYPE_WIFI));
    }

    @Test
    public void testDunFromTelephonyManagerMeansDun() {
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(true);

        final TetheringConfiguration cfgWifi = getTetheringConfiguration(TYPE_WIFI);
        final TetheringConfiguration cfgMobileWifiHipri = getTetheringConfiguration(
                TYPE_MOBILE, TYPE_WIFI, TYPE_MOBILE_HIPRI);
        final TetheringConfiguration cfgWifiDun = getTetheringConfiguration(
                TYPE_WIFI, TYPE_MOBILE_DUN);
        final TetheringConfiguration cfgMobileWifiHipriDun = getTetheringConfiguration(
                TYPE_MOBILE, TYPE_WIFI, TYPE_MOBILE_HIPRI, TYPE_MOBILE_DUN);

        for (TetheringConfiguration cfg : Arrays.asList(cfgWifi, cfgMobileWifiHipri,
                cfgWifiDun, cfgMobileWifiHipriDun)) {
            String msg = "config=" + cfg.toString();
            assertTrue(msg, cfg.isDunRequired);
            assertTrue(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_DUN));
            assertFalse(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE));
            assertFalse(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_HIPRI));
            // Just to prove we haven't clobbered Wi-Fi:
            assertTrue(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_WIFI));
        }
    }

    @Test
    public void testDunNotRequiredFromTelephonyManagerMeansNoDun() {
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(false);

        final TetheringConfiguration cfgWifi = getTetheringConfiguration(TYPE_WIFI);
        final TetheringConfiguration cfgMobileWifiHipri = getTetheringConfiguration(
                TYPE_MOBILE, TYPE_WIFI, TYPE_MOBILE_HIPRI);
        final TetheringConfiguration cfgWifiDun = getTetheringConfiguration(
                TYPE_WIFI, TYPE_MOBILE_DUN);
        final TetheringConfiguration cfgWifiMobile = getTetheringConfiguration(
                TYPE_WIFI, TYPE_MOBILE);
        final TetheringConfiguration cfgWifiHipri = getTetheringConfiguration(
                TYPE_WIFI, TYPE_MOBILE_HIPRI);
        final TetheringConfiguration cfgMobileWifiHipriDun = getTetheringConfiguration(
                TYPE_MOBILE, TYPE_WIFI, TYPE_MOBILE_HIPRI, TYPE_MOBILE_DUN);

        String msg;
        // TYPE_MOBILE_DUN should be present in none of the combinations.
        // TYPE_WIFI should not be affected.
        for (TetheringConfiguration cfg : Arrays.asList(cfgWifi, cfgMobileWifiHipri, cfgWifiDun,
                cfgWifiMobile, cfgWifiHipri, cfgMobileWifiHipriDun)) {
            msg = "config=" + cfg.toString();
            assertFalse(msg, cfg.isDunRequired);
            assertFalse(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_DUN));
            assertTrue(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_WIFI));
        }

        for (TetheringConfiguration cfg : Arrays.asList(cfgWifi, cfgMobileWifiHipri, cfgWifiDun,
                cfgMobileWifiHipriDun)) {
            msg = "config=" + cfg.toString();
            assertTrue(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE));
            assertTrue(msg, cfg.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_HIPRI));
        }
        msg = "config=" + cfgWifiMobile.toString();
        assertTrue(msg, cfgWifiMobile.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE));
        assertFalse(msg, cfgWifiMobile.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_HIPRI));
        msg = "config=" + cfgWifiHipri.toString();
        assertFalse(msg, cfgWifiHipri.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE));
        assertTrue(msg, cfgWifiHipri.preferredUpstreamIfaceTypes.contains(TYPE_MOBILE_HIPRI));

    }

    @Test
    public void testNoDefinedUpstreamTypesAddsEthernet() {
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(new int[]{});
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(false);

        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        final Iterator<Integer> upstreamIterator = cfg.preferredUpstreamIfaceTypes.iterator();
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_ETHERNET, upstreamIterator.next().intValue());
        // The following is because the code always adds some kind of mobile
        // upstream, be it DUN or, in this case where DUN is NOT required,
        // make sure there is at least one of MOBILE or HIPRI. With the empty
        // list of the configuration in this test, it will always add both
        // MOBILE and HIPRI, in that order.
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_MOBILE, upstreamIterator.next().intValue());
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_MOBILE_HIPRI, upstreamIterator.next().intValue());
        assertFalse(upstreamIterator.hasNext());
    }

    @Test
    public void testDefinedUpstreamTypesSansEthernetAddsEthernet() {
        when(mResources.getIntArray(R.array.config_tether_upstream_types)).thenReturn(
                new int[]{TYPE_WIFI, TYPE_MOBILE_HIPRI});
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(false);

        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        final Iterator<Integer> upstreamIterator = cfg.preferredUpstreamIfaceTypes.iterator();
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_ETHERNET, upstreamIterator.next().intValue());
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_WIFI, upstreamIterator.next().intValue());
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_MOBILE_HIPRI, upstreamIterator.next().intValue());
        assertFalse(upstreamIterator.hasNext());
    }

    @Test
    public void testDefinedUpstreamTypesWithEthernetDoesNotAddEthernet() {
        when(mResources.getIntArray(R.array.config_tether_upstream_types))
                .thenReturn(new int[]{TYPE_WIFI, TYPE_ETHERNET, TYPE_MOBILE_HIPRI});
        when(mTelephonyManager.isTetheringApnRequired()).thenReturn(false);

        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        final Iterator<Integer> upstreamIterator = cfg.preferredUpstreamIfaceTypes.iterator();
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_WIFI, upstreamIterator.next().intValue());
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_ETHERNET, upstreamIterator.next().intValue());
        assertTrue(upstreamIterator.hasNext());
        assertEquals(TYPE_MOBILE_HIPRI, upstreamIterator.next().intValue());
        assertFalse(upstreamIterator.hasNext());
    }

    private void initializeBpfOffloadConfiguration(
            final boolean fromRes, final String fromDevConfig) {
        when(mResources.getBoolean(R.bool.config_tether_enable_bpf_offload)).thenReturn(fromRes);
        doReturn(fromDevConfig).when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(TetheringConfiguration.OVERRIDE_TETHER_ENABLE_BPF_OFFLOAD)));
    }

    @Test
    public void testBpfOffloadEnabledByResource() {
        initializeBpfOffloadConfiguration(true, null /* unset */);
        final TetheringConfiguration enableByRes =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertTrue(enableByRes.isBpfOffloadEnabled());
    }

    @Test
    public void testBpfOffloadEnabledByDeviceConfigOverride() {
        for (boolean res : new boolean[]{true, false}) {
            initializeBpfOffloadConfiguration(res, "true");
            final TetheringConfiguration enableByDevConOverride =
                    new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
            assertTrue(enableByDevConOverride.isBpfOffloadEnabled());
        }
    }

    @Test
    public void testBpfOffloadDisabledByResource() {
        initializeBpfOffloadConfiguration(false, null /* unset */);
        final TetheringConfiguration disableByRes =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertFalse(disableByRes.isBpfOffloadEnabled());
    }

    @Test
    public void testBpfOffloadDisabledByDeviceConfigOverride() {
        for (boolean res : new boolean[]{true, false}) {
            initializeBpfOffloadConfiguration(res, "false");
            final TetheringConfiguration disableByDevConOverride =
                    new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
            assertFalse(disableByDevConOverride.isBpfOffloadEnabled());
        }
    }

    @Test
    public void testNewDhcpServerDisabled() {
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                true);
        doReturn("false").when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(TetheringConfiguration.TETHER_ENABLE_LEGACY_DHCP_SERVER)));

        final TetheringConfiguration enableByRes =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertTrue(enableByRes.useLegacyDhcpServer());

        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                false);
        doReturn("true").when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(TetheringConfiguration.TETHER_ENABLE_LEGACY_DHCP_SERVER)));

        final TetheringConfiguration enableByDevConfig =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertTrue(enableByDevConfig.useLegacyDhcpServer());
    }

    @Test
    public void testNewDhcpServerEnabled() {
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_dhcp_server)).thenReturn(
                false);
        doReturn("false").when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                eq(TetheringConfiguration.TETHER_ENABLE_LEGACY_DHCP_SERVER)));

        final TetheringConfiguration cfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);

        assertFalse(cfg.useLegacyDhcpServer());
    }

    @Test
    public void testOffloadIntervalByResource() {
        final TetheringConfiguration intervalByDefault =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(TetheringConfiguration.DEFAULT_TETHER_OFFLOAD_POLL_INTERVAL_MS,
                intervalByDefault.getOffloadPollInterval());

        final int[] testOverrides = {0, 3000, -1};
        for (final int override : testOverrides) {
            when(mResources.getInteger(R.integer.config_tether_offload_poll_interval)).thenReturn(
                    override);
            final TetheringConfiguration overrideByRes =
                    new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
            assertEquals(override, overrideByRes.getOffloadPollInterval());
        }
    }

    @Test
    public void testGetResourcesBySubId() {
        setUpResourceForSubId();
        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertTrue(cfg.provisioningApp.length == 0);
        final int anyValidSubId = 1;
        final MockTetheringConfiguration mockCfg =
                new MockTetheringConfiguration(mMockContext, mLog, anyValidSubId);
        assertEquals(mockCfg.provisioningApp[0], PROVISIONING_APP_NAME[0]);
        assertEquals(mockCfg.provisioningApp[1], PROVISIONING_APP_NAME[1]);
        assertEquals(mockCfg.provisioningAppNoUi, PROVISIONING_NO_UI_APP_NAME);
        assertEquals(mockCfg.provisioningResponse, PROVISIONING_APP_RESPONSE);
    }

    private void setUpResourceForSubId() {
        when(mResourcesForSubId.getStringArray(
                R.array.config_tether_dhcp_range)).thenReturn(new String[0]);
        when(mResourcesForSubId.getStringArray(
                R.array.config_tether_usb_regexs)).thenReturn(new String[0]);
        when(mResourcesForSubId.getStringArray(
                R.array.config_tether_wifi_regexs)).thenReturn(new String[]{ "test_wlan\\d" });
        when(mResourcesForSubId.getStringArray(
                R.array.config_tether_bluetooth_regexs)).thenReturn(new String[0]);
        when(mResourcesForSubId.getIntArray(R.array.config_tether_upstream_types)).thenReturn(
                new int[0]);
        when(mResourcesForSubId.getStringArray(
                R.array.config_mobile_hotspot_provision_app)).thenReturn(PROVISIONING_APP_NAME);
        when(mResourcesForSubId.getString(R.string.config_mobile_hotspot_provision_app_no_ui))
                .thenReturn(PROVISIONING_NO_UI_APP_NAME);
        when(mResourcesForSubId.getString(
                R.string.config_mobile_hotspot_provision_response)).thenReturn(
                PROVISIONING_APP_RESPONSE);
    }

    private <T> void mockService(String serviceName, Class<T> serviceClass, T service) {
        when(mMockContext.getSystemServiceName(serviceClass)).thenReturn(serviceName);
        when(mMockContext.getSystemService(serviceName)).thenReturn(service);
    }

    @Test
    public void testGetCarrierConfigBySubId_noCarrierConfigManager_configsAreDefault() {
        // Act like the CarrierConfigManager is present and ready unless told otherwise.
        mockService(Context.CARRIER_CONFIG_SERVICE,
                CarrierConfigManager.class, null);
        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);

        assertTrue(cfg.isCarrierSupportTethering);
        assertTrue(cfg.isCarrierConfigAffirmsEntitlementCheckRequired);
    }

    @Test
    public void testGetCarrierConfigBySubId_carrierConfigMissing_configsAreDefault() {
        // Act like the CarrierConfigManager is present and ready unless told otherwise.
        mockService(Context.CARRIER_CONFIG_SERVICE,
                CarrierConfigManager.class, mCarrierConfigManager);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(null);
        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);

        assertTrue(cfg.isCarrierSupportTethering);
        assertTrue(cfg.isCarrierConfigAffirmsEntitlementCheckRequired);
    }

    @Test
    public void testGetCarrierConfigBySubId_hasConfigs_carrierUnsupportAndCheckNotRequired() {
        mockService(Context.CARRIER_CONFIG_SERVICE,
                CarrierConfigManager.class, mCarrierConfigManager);
        mCarrierConfig.putBoolean(KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mCarrierConfig.putBoolean(KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, false);
        mCarrierConfig.putBoolean(KEY_CARRIER_SUPPORTS_TETHERING_BOOL, false);
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mCarrierConfig);
        final TetheringConfiguration cfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);

        if (SdkLevel.isAtLeastT()) {
            assertFalse(cfg.isCarrierSupportTethering);
        } else {
            assertTrue(cfg.isCarrierSupportTethering);
        }
        assertFalse(cfg.isCarrierConfigAffirmsEntitlementCheckRequired);

    }

    @Test
    public void testEnableLegacyWifiP2PAddress() throws Exception {
        final TetheringConfiguration defaultCfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertFalse(defaultCfg.shouldEnableWifiP2pDedicatedIp());

        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_wifi_p2p_dedicated_ip))
                .thenReturn(true);
        final TetheringConfiguration testCfg = new TetheringConfiguration(
                mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertTrue(testCfg.shouldEnableWifiP2pDedicatedIp());
    }

    @Test
    public void testChooseUpstreamAutomatically() throws Exception {
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic))
                .thenReturn(true);
        assertChooseUpstreamAutomaticallyIs(true);

        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic))
                .thenReturn(false);
        assertChooseUpstreamAutomaticallyIs(false);
    }

    // The flag override only works on R-
    @Test @IgnoreAfter(Build.VERSION_CODES.R)
    public void testChooseUpstreamAutomatically_FlagOverride() throws Exception {
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic))
                .thenReturn(false);
        setTetherForceUpstreamAutomaticFlagVersion(TEST_PACKAGE_VERSION - 1);
        assertTrue(DeviceConfigUtils.isFeatureEnabled(mMockContext, NAMESPACE_CONNECTIVITY,
                TetheringConfiguration.TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION, APEX_NAME, false));

        assertChooseUpstreamAutomaticallyIs(true);

        setTetherForceUpstreamAutomaticFlagVersion(0L);
        assertChooseUpstreamAutomaticallyIs(false);

        setTetherForceUpstreamAutomaticFlagVersion(Long.MAX_VALUE);
        assertChooseUpstreamAutomaticallyIs(false);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testChooseUpstreamAutomatically_FlagOverrideAfterR() throws Exception {
        when(mResources.getBoolean(R.bool.config_tether_upstream_automatic))
                .thenReturn(false);
        setTetherForceUpstreamAutomaticFlagVersion(TEST_PACKAGE_VERSION - 1);
        assertChooseUpstreamAutomaticallyIs(false);
    }

    private void setTetherForceUpstreamAutomaticFlagVersion(Long version) {
        doReturn(version == null ? null : Long.toString(version)).when(
                () -> DeviceConfig.getProperty(eq(NAMESPACE_CONNECTIVITY),
                        eq(TetheringConfiguration.TETHER_FORCE_UPSTREAM_AUTOMATIC_VERSION)));
    }

    private void assertChooseUpstreamAutomaticallyIs(boolean value) {
        assertEquals(value, new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID)
                .chooseUpstreamAutomatically);
    }

    @Test
    public void testUsbTetheringFunctions() throws Exception {
        // Test default value. If both resource and settings is not configured, usingNcm is false.
        assertIsUsingNcm(false /* usingNcm */);

        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TETHER_USB_NCM_FUNCTION);
        assertIsUsingNcm(true /* usingNcm */);

        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TETHER_USB_RNDIS_FUNCTION);
        assertIsUsingNcm(false /* usingNcm */);

        setTetherForceUsbFunctions(TETHER_USB_RNDIS_FUNCTION);
        assertIsUsingNcm(false /* usingNcm */);

        setTetherForceUsbFunctions(TETHER_USB_NCM_FUNCTION);
        assertIsUsingNcm(true /* usingNcm */);

        // Test throws NumberFormatException.
        setTetherForceUsbFunctions("WrongNumberFormat");
        assertIsUsingNcm(false /* usingNcm */);
    }

    private void assertIsUsingNcm(boolean expected) {
        final TetheringConfiguration cfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(expected, cfg.isUsingNcm());
    }

    private void setTetherForceUsbFunctions(final String value) {
        Settings.Global.putString(mContentResolver, TETHER_FORCE_USB_FUNCTIONS, value);
    }

    private void setTetherForceUsbFunctions(final int value) {
        setTetherForceUsbFunctions(Integer.toString(value));
    }

    @Test
    public void testNcmRegexs() throws Exception {
        final String[] rndisRegexs = {"test_rndis\\d"};
        final String[] ncmRegexs   = {"test_ncm\\d"};
        final String[] rndisNcmRegexs   = {"test_rndis\\d", "test_ncm\\d"};

        // cfg.isUsingNcm = false.
        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TETHER_USB_RNDIS_FUNCTION);
        setUsbAndNcmRegexs(rndisRegexs, ncmRegexs);
        assertUsbAndNcmRegexs(rndisRegexs, ncmRegexs);

        setUsbAndNcmRegexs(rndisNcmRegexs, new String[0]);
        assertUsbAndNcmRegexs(rndisNcmRegexs, new String[0]);

        // cfg.isUsingNcm = true.
        when(mResources.getInteger(R.integer.config_tether_usb_functions)).thenReturn(
                TETHER_USB_NCM_FUNCTION);
        setUsbAndNcmRegexs(rndisRegexs, ncmRegexs);
        assertUsbAndNcmRegexs(ncmRegexs, new String[0]);

        setUsbAndNcmRegexs(rndisNcmRegexs, new String[0]);
        assertUsbAndNcmRegexs(rndisNcmRegexs, new String[0]);

        // Check USB regex is not overwritten by the NCM regex after force to use rndis from
        // Settings.
        setUsbAndNcmRegexs(rndisRegexs, ncmRegexs);
        setTetherForceUsbFunctions(TETHER_USB_RNDIS_FUNCTION);
        assertUsbAndNcmRegexs(rndisRegexs, ncmRegexs);
    }

    private void setUsbAndNcmRegexs(final String[] usbRegexs, final String[] ncmRegexs) {
        when(mResources.getStringArray(R.array.config_tether_usb_regexs)).thenReturn(usbRegexs);
        when(mResources.getStringArray(R.array.config_tether_ncm_regexs)).thenReturn(ncmRegexs);
    }

    private void assertUsbAndNcmRegexs(final String[] usbRegexs, final String[] ncmRegexs) {
        final TetheringConfiguration cfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertArrayEquals(usbRegexs, cfg.tetherableUsbRegexs);
        assertArrayEquals(ncmRegexs, cfg.tetherableNcmRegexs);
    }

    @Test
    public void testP2pLeasesSubnetPrefixLength() throws Exception {
        when(mResources.getBoolean(R.bool.config_tether_enable_legacy_wifi_p2p_dedicated_ip))
                .thenReturn(true);

        final int defaultSubnetPrefixLength = 0;
        final TetheringConfiguration defaultCfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(defaultSubnetPrefixLength, defaultCfg.getP2pLeasesSubnetPrefixLength());

        final int prefixLengthTooSmall = -1;
        when(mResources.getInteger(R.integer.config_p2p_leases_subnet_prefix_length)).thenReturn(
                prefixLengthTooSmall);
        final TetheringConfiguration tooSmallCfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(defaultSubnetPrefixLength, tooSmallCfg.getP2pLeasesSubnetPrefixLength());

        final int prefixLengthTooLarge = 31;
        when(mResources.getInteger(R.integer.config_p2p_leases_subnet_prefix_length)).thenReturn(
                prefixLengthTooLarge);
        final TetheringConfiguration tooLargeCfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(defaultSubnetPrefixLength, tooLargeCfg.getP2pLeasesSubnetPrefixLength());

        final int p2pLeasesSubnetPrefixLength = 27;
        when(mResources.getInteger(R.integer.config_p2p_leases_subnet_prefix_length)).thenReturn(
                p2pLeasesSubnetPrefixLength);
        final TetheringConfiguration p2pCfg =
                new TetheringConfiguration(mMockContext, mLog, INVALID_SUBSCRIPTION_ID);
        assertEquals(p2pLeasesSubnetPrefixLength, p2pCfg.getP2pLeasesSubnetPrefixLength());
    }
}

package at.favre.lib.hood.defaults;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import at.favre.lib.hood.page.PageEntry;
import at.favre.lib.hood.page.entries.HeaderEntry;
import at.favre.lib.hood.page.entries.KeyValueEntry;
import at.favre.lib.hood.page.values.DynamicValue;
import at.favre.lib.hood.util.DeviceStatusUtil;
import at.favre.lib.hood.util.HoodUtil;
import at.favre.lib.hood.util.TypeTranslators;

/**
 * A set of methods that returns default {@link KeyValueEntry} type page entries.
 */
public class DefaultProperties {

    private static final String TAG = DefaultProperties.class.getName();

    /**
     * Returns entries of some basic device data like model number and sdk version.
     *
     * @param includeHeader adds a title entry if true
     * @return list of entries
     */
    public static List<PageEntry<?>> createBasicDeviceInfo(boolean includeHeader) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (includeHeader) {
            entries.add(new HeaderEntry("Device"));
        }
        entries.add(new KeyValueEntry("model", Build.MODEL));
        entries.add(new KeyValueEntry("name", Build.DEVICE));
        entries.add(new KeyValueEntry("brand", Build.MANUFACTURER));
        entries.add(new KeyValueEntry("version", Build.VERSION.RELEASE));
        entries.add(new KeyValueEntry("version_minor", Build.VERSION.INCREMENTAL));
        entries.add(new KeyValueEntry("build-id", Build.ID));
        entries.add(new KeyValueEntry("sdk-int", String.valueOf(Build.VERSION.SDK_INT)));
        entries.add(new KeyValueEntry("serial", Build.SERIAL));

        return entries;
    }

    /**
     * Additional device info
     *
     * @param activity can be null, but will just return an empty list
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createDetailedDeviceInfo(@Nullable final Activity activity) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (activity != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            entries.add(new KeyValueEntry("dpi", "x" + metrics.density + "/" + metrics.densityDpi + "dpi"));
            entries.add(new KeyValueEntry("resolution", metrics.heightPixels + "x" + metrics.widthPixels));
            entries.add(new KeyValueEntry("locale/timezone", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return HoodUtil.getCurrentLocale(activity).toString() + "/" + TimeZone.getDefault().getDisplayName();
                }
            }));
        }
        return entries;
    }

    public static List<PageEntry<?>> createInternalProcessDebugInfo(@Nullable final Context context, boolean includeHeader) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (context != null) {
            if (includeHeader) {
                entries.add(new HeaderEntry("Process Debug Info"));
            }

            entries.add(new KeyValueEntry("heap-native", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return "used " + HoodUtil.humanReadableByteCount(Debug.getNativeHeapAllocatedSize(), false) + " of " + HoodUtil.humanReadableByteCount(Debug.getNativeHeapSize(), false);
                }
            }));

            entries.add(new KeyValueEntry("memory", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return "used " + HoodUtil.humanReadableByteCount(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(), false) + " (max:" + HoodUtil.humanReadableByteCount(Runtime.getRuntime().maxMemory(), false) + ")";
                }
            }));
            entries.add(new KeyValueEntry("pss", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return HoodUtil.humanReadableByteCount(Debug.getPss(), false);
                }
            }));
            entries.add(new KeyValueEntry("loaded-classes", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return String.valueOf(Debug.getLoadedClassCount());
                }
            }));
            entries.add(new KeyValueEntry("local/death/proxy objs", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return String.valueOf(Debug.getBinderLocalObjectCount()) + "/" + String.valueOf(Debug.getBinderDeathObjectCount()) + "/" + String.valueOf(Debug.getBinderProxyObjectCount());
                }
            }));
            entries.add(new KeyValueEntry("debugger-connected", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return String.valueOf(Debug.isDebuggerConnected());
                }
            }));
        }
        return entries;
    }

    /**
     * Creates page-entries for all the apk's signature sha256-hashes
     *
     * @param context can be null, but will just return an empty list
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createSignatureHashInfo(@Nullable Context context) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (context != null) {
            try {
                final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                for (Signature signature : info.signatures) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    md.update(signature.toByteArray());
                    entries.add(new KeyValueEntry("apk-signature-sha256", HoodUtil.byteToHex(md.digest()), true));
                }
            } catch (Exception e) {
                Log.e(TAG, "could not read apk signature", e);
            }
        }
        return entries;
    }

    /**
     * Traverses the static fields of given arbitrary class. Will create an entry for each non-null
     * static public field. Ignores "serialVersionUID".
     *
     * @param clazz the BuildConfig.java class you want the info
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createStaticFieldsInfo(Class<?> clazz) {
        List<PageEntry<?>> entries = new ArrayList<>();

        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers()))
                try {
                    String key = field.getName();
                    String value = String.valueOf(field.get(null));

                    if (key != null && !key.equals("serialVersionUID") &&
                            value != null && !value.equals("null") && !value.trim().isEmpty()) {
                        entries.add(new KeyValueEntry(key, String.valueOf(field.get(null))));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "could not get field from class (" + field + ")");
                }
        }

        return entries;
    }

    /**
     * Traverses the static fields of given class (which must be of Type BuildConfig) and parses the main
     * fields that is create by the android gradle plugin (ie. version, app_id, etc.)
     *
     * @param buildConfig   the BuildConfig.java class you want the info
     * @param includeHeader adds a title entry if true
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createAppVersionInfo(Class<?> buildConfig, boolean includeHeader) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (includeHeader) {
            entries.add(new HeaderEntry("App Version"));
        }

        Field[] declaredFields = buildConfig.getDeclaredFields();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    String key = null;
                    String value = String.valueOf(field.get(null));
                    switch (field.getName().toLowerCase()) {
                        case "version_name":
                            key = "version";
                            break;
                        case "version_code":
                            key = "version-code";
                            break;
                        case "application_id":
                            key = "app-id";
                            break;
                        case "build_type":
                            key = "build-type";
                            break;
                        case "flavor":
                            key = "flavor";
                            break;
                        case "debug":
                            key = "debug";
                            break;
                    }

                    if (key != null && value != null && !value.trim().isEmpty()) {
                        entries.add(new KeyValueEntry(key, String.valueOf(field.get(null))));
                    }

                } catch (Exception e) {
                    Log.w(TAG, "could not get field from BuildConfig (" + field + ")");
                }
            }
        }

        return entries;
    }

    /**
     * Returns pageentry for each defined permission in the app (the passed activity belongs to).
     * <p>
     * See {@link #createRuntimePermissionInfo(Activity, boolean, String, String...)} for more details
     *
     * @param activity      can be null, but will just return an empty list
     * @param includeHeader adds a title entry if true
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createRuntimePermissionInfo(@Nullable final Activity activity, boolean includeHeader) {
        if (activity != null) {
            try {
                PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
                if (info.requestedPermissions != null && info.requestedPermissions.length > 0) {
                    List<String> permissionsList = new ArrayList<>(info.requestedPermissions.length);
                    Collections.addAll(permissionsList, info.requestedPermissions);
                    Collections.sort(permissionsList);
                    return createRuntimePermissionInfo(activity, includeHeader, permissionsList.get(0), permissionsList.subList(1, permissionsList.size()).toArray(new String[permissionsList.size() - 1]));
                }
            } catch (PackageManager.NameNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Returns for each provided permission a pageentry containing the current dynamic state (granted, denied, etc.) including click
     * actions to request the permission.
     *
     * @param activity          can be null, but will just return an empty list
     * @param includeHeader     adds a title entry if true
     * @param androidPermission see {@link android.Manifest.permission}
     * @param morePermissions   more permissions
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createRuntimePermissionInfo(@Nullable final Activity activity, boolean includeHeader, String androidPermission, String... morePermissions) {
        final List<String> permissions = new ArrayList<>();
        List<PageEntry<?>> entries = new ArrayList<>();

        if (activity != null) {
            permissions.add(androidPermission);
            Collections.addAll(permissions, morePermissions);

            if (includeHeader) {
                entries.add(new HeaderEntry("Permissions"));
            }

            for (final String perm : permissions) {
                entries.add(new KeyValueEntry(perm.replace("android.permission.", ""), new DynamicValue<String>() {
                    @Override
                    public String getValue() {
                        return TypeTranslators.translatePermissionState(HoodUtil.getPermissionStatus(activity, perm));
                    }
                }, new KeyValueEntry.AskPermissionClickAction(perm, activity), false));
            }
        }
        return entries;
    }

    /**
     * Get current state of various connectivity adapters (network, wifi, bt, nfc,...). Requires to have the specific
     * permission to be able to show the actual state (see eg. {@link DeviceStatusUtil#getNetworkConnectivityState(Context)}.
     * <p>
     * Click action is opening the system settings for each adapter.
     *
     * @param context       can be null, but will just return an empty list
     * @param includeHeader adds a title entry if true
     * @return the list of entries
     */
    public static List<PageEntry<?>> createConnectivityStatusInfo(@Nullable final Context context, boolean includeHeader) {
        return createConnectivityStatusInfo(context, includeHeader, true, true, true, true);
    }

    /**
     * See {@link #createConnectivityStatusInfo(Context, boolean)}
     *
     * @param context             can be null, but will just return an empty list
     * @param includeHeader       adds a title entry if true
     * @param includeNetworkState if network state should be included
     * @param includeWifiState    if wifi state should be included
     * @param includeBtState      if bluetooth state should be included
     * @param includeNfcState     if nfc state should be included
     * @return the list of entries
     */
    public static List<PageEntry<?>> createConnectivityStatusInfo(@Nullable final Context context, boolean includeHeader, boolean includeNetworkState, boolean includeWifiState, boolean includeBtState, boolean includeNfcState) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (context != null) {
            if (includeHeader) {
                entries.add(new HeaderEntry("Connectivity Status"));
            }

            if (includeNetworkState) {
                entries.add(new KeyValueEntry("internet", new DynamicValue<String>() {
                    @Override
                    public String getValue() {
                        return String.valueOf(DeviceStatusUtil.getNetworkConnectivityState(context));
                    }
                }, new KeyValueEntry.StartIntentAction(new Intent(Settings.ACTION_SETTINGS)), false));
            }


            if (includeWifiState) {
                final DeviceStatusUtil.Status wifiState = DeviceStatusUtil.getWifiStatus(context);
                entries.add(new KeyValueEntry("wifi", new DynamicValue<String>() {
                    @Override
                    public String getValue() {
                        return String.valueOf(wifiState);
                    }
                }, wifiState == DeviceStatusUtil.Status.UNSUPPORTED ?
                        new KeyValueEntry.ToastClickAction() :
                        new KeyValueEntry.StartIntentAction(new Intent(Settings.ACTION_WIFI_SETTINGS)), false));
            }

            if (includeBtState) {
                final DeviceStatusUtil.Status btState = DeviceStatusUtil.getBluetoothStatus(context);
                entries.add(new KeyValueEntry("bluetooth", new DynamicValue<String>() {
                    @Override
                    public String getValue() {
                        return String.valueOf(btState);
                    }
                }, btState == DeviceStatusUtil.Status.UNSUPPORTED ?
                        new KeyValueEntry.ToastClickAction() :
                        new KeyValueEntry.StartIntentAction(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)), false));
            }

            if (includeNfcState) {
                final DeviceStatusUtil.Status nfcState = DeviceStatusUtil.getNfcState(context);
                entries.add(new KeyValueEntry("nfc", new DynamicValue<String>() {
                    @Override
                    public String getValue() {
                        return String.valueOf(nfcState);
                    }
                }, nfcState == DeviceStatusUtil.Status.UNSUPPORTED ?
                        new KeyValueEntry.ToastClickAction() :
                        new KeyValueEntry.StartIntentAction(new Intent(Settings.ACTION_NFC_SETTINGS)), false));
            }
        }
        return entries;
    }

    /**
     * Convince feature to add state of all declared system features (see Manifest uses-feature tags)
     * Uses {@link #createSystemFeatureInfo(Context, Map)} call.
     *
     * @param context       can be null, but will just return an empty list
     * @param includeHeader adds a title entry if true
     * @return list of all declared uses-feature tags in AndroidManifest as page entries
     */
    public static List<PageEntry<?>> createDeclaredSystemFeatureInfo(@Nullable Context context, boolean includeHeader) {
        if (context != null) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_CONFIGURATIONS);
                Map<String, String> featureMap = new TreeMap<>();
                if (info.reqFeatures != null && info.reqFeatures.length > 0) {
                    for (FeatureInfo reqFeature : info.reqFeatures) {
                        boolean required = reqFeature.flags == FeatureInfo.FLAG_REQUIRED;
                        featureMap.put(reqFeature.name.replace("android.hardware.", "") + (required ? " (req)" : ""), reqFeature.name);
                    }
                }

                List<PageEntry<?>> entries = new ArrayList<>();
                if (includeHeader) {
                    entries.add(new HeaderEntry("System Features"));
                }
                entries.addAll(createSystemFeatureInfo(context, featureMap));
                if (entries.size() > 1) {
                    return entries;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Convince feature to add state of multiple system features.
     * Uses {@link PackageManager#hasSystemFeature(String)} call.
     * <p>
     * See https://developer.android.com/guide/topics/manifest/uses-feature-element.html#features-reference for
     * available system features.
     *
     * @param context               can be null, but will just return an empty list
     * @param labelSystemFeatureMap a map which has ui labels as key and android system feature string
     *                              (as returned as name by {@link PackageManager#getSystemAvailableFeatures()}) as value
     * @return list of page-entries (one for each map entry)
     */
    public static List<PageEntry<?>> createSystemFeatureInfo(@Nullable Context context, Map<String, String> labelSystemFeatureMap) {
        List<PageEntry<?>> entries = new ArrayList<>();
        if (context != null) {
            for (Map.Entry<String, String> entry : labelSystemFeatureMap.entrySet()) {
                entries.add(new KeyValueEntry(entry.getKey(), String.valueOf(context.getPackageManager().hasSystemFeature(entry.getValue()))));
            }
        }
        return entries;
    }

    /**
     * Returns a list of the most important {@link TelephonyManager} states and ids. Will only
     * work if the app has the {@link Manifest.permission#READ_PHONE_STATE} permission or just
     * returns an empty list.
     *
     * @param context       can be null, but will just return an empty list
     * @param includeHeader adds a title entry if true
     * @return the list of entries
     */
    //@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public static List<PageEntry<?>> createTelephonyMangerInfo(@Nullable Context context, boolean includeHeader) {
        List<PageEntry<?>> entries = new ArrayList<>();

        if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (includeHeader) {
                entries.add(new HeaderEntry("Telephony Status"));
            }

            final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            entries.add(new KeyValueEntry("sim-serial", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return telephonyManager.getSimSerialNumber();
                }
            }));
            entries.add(new KeyValueEntry("sim-state", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return TypeTranslators.translateSimState(telephonyManager.getSimState());
                }
            }));
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                entries.add(new KeyValueEntry("sim-slots", new DynamicValue<String>() {
                    @TargetApi(Build.VERSION_CODES.M)
                    @Override
                    public String getValue() {
                        return String.valueOf(telephonyManager.getPhoneCount());
                    }
                }));
            }
            entries.add(new KeyValueEntry("subscriber-id", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return telephonyManager.getSubscriberId();
                }
            }));
            entries.add(new KeyValueEntry("operator", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return telephonyManager.getNetworkOperatorName();
                }
            }));
            entries.add(new KeyValueEntry("connection-type", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return TypeTranslators.translateTelephonyNetworkType(telephonyManager.getNetworkType());
                }
            }));
            entries.add(new KeyValueEntry("MSISDN", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return telephonyManager.getLine1Number();
                }
            }));
            entries.add(new KeyValueEntry("IMEI", new DynamicValue<String>() {
                @Override
                public String getValue() {
                    return telephonyManager.getDeviceId();
                }
            }));

        }
        return entries;
    }

    /**
     * Converts a {@link Properties} objekt to page-entries
     *
     * @param properties
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createPropertiesEntries(@NonNull Properties properties) {
        return createFromMap(properties);
    }

    /**
     * Converts an arbitrary map to page-entries. Uses {@link #toString()} on the contained objects.
     *
     * @param hashMap
     * @return list of page-entries
     */
    public static List<PageEntry<?>> createFromMap(Map<?, ?> hashMap) {
        return createFromEntrySet(hashMap.entrySet());
    }

    private static List<PageEntry<?>> createFromEntrySet(Set<? extends Map.Entry<?, ?>> entrySet) {
        List<PageEntry<?>> entries = new ArrayList<>();
        for (Map.Entry propEntry : entrySet) {
            if (propEntry != null && propEntry.getKey() != null && propEntry.getValue() != null) {
                entries.add(new KeyValueEntry(propEntry.getKey().toString(), propEntry.getValue().toString()));
            }
        }
        return entries;
    }
}
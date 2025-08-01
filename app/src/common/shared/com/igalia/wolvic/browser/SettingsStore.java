package com.igalia.wolvic.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.browser.api.WContentBlocking;
import com.igalia.wolvic.browser.api.WSessionSettings;
import com.igalia.wolvic.browser.engine.EngineProvider;
import com.igalia.wolvic.speech.SpeechServices;
import com.igalia.wolvic.telemetry.TelemetryService;
import com.igalia.wolvic.ui.viewmodel.SettingsViewModel;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.ui.widgets.menus.library.SortingContextMenuWidget;
import com.igalia.wolvic.utils.DeviceType;
import com.igalia.wolvic.utils.RemoteProperties;
import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import mozilla.components.concept.fetch.Request;
import mozilla.components.concept.fetch.Response;

public class SettingsStore {

    private static final String LOGTAG = SystemUtils.createLogtag(SettingsStore.class);

    private static SettingsStore mSettingsInstance;

    public static synchronized @NonNull
    SettingsStore getInstance(final @NonNull Context aContext) {
        if (mSettingsInstance == null) {
            mSettingsInstance = new SettingsStore(aContext);
        }

        return mSettingsInstance;
    }

    @IntDef(value = { INTERNAL, EXTERNAL})
    public @interface Storage {}
    public static final int INTERNAL = 0;
    public static final int EXTERNAL = 1;

    @IntDef(value = { TABS_LOCATION_TRAY, TABS_LOCATION_HORIZONTAL, TABS_LOCATION_VERTICAL})
    public @interface TabsLocation {}
    public static final int TABS_LOCATION_TRAY = 0;
    public static final int TABS_LOCATION_HORIZONTAL = 1;
    public static final int TABS_LOCATION_VERTICAL = 2;

    @IntDef(value = {WINDOW_SELECTION_METHOD_CLICK, WINDOW_SELECTION_METHOD_HOVER})
    public @interface WindowSelectionMethod {}
    public static final int WINDOW_SELECTION_METHOD_CLICK = 0;
    public static final int WINDOW_SELECTION_METHOD_HOVER = 1;

    private Context mContext;
    private SharedPreferences mPrefs;
    private SettingsViewModel mSettingsViewModel;

    // Developer options default values
    public final static boolean REMOTE_DEBUGGING_DEFAULT = false;
    public final static boolean ENV_OVERRIDE_DEFAULT = false;
    public final static boolean SYSTEM_ROOT_CA_DEFAULT = false;
    public final static boolean UI_HARDWARE_ACCELERATION_DEFAULT = true;
    public final static boolean UI_HARDWARE_ACCELERATION_DEFAULT_MAGIC_LEAP_2 = false;
    public final static boolean PERFORMANCE_MONITOR_DEFAULT = true;
    public final static boolean DRM_PLAYBACK_DEFAULT = false;
    public final static int TRACKING_DEFAULT = WContentBlocking.EtpLevel.DEFAULT;
    public final static boolean NOTIFICATIONS_DEFAULT = true;
    public final static boolean SPEECH_DATA_COLLECTION_DEFAULT = false;
    public final static boolean SPEECH_DATA_COLLECTION_REVIEWED_DEFAULT = false;
    public final static float WINDOW_DISTANCE_DEFAULT = BuildConfig.DEFAULT_WINDOW_DISTANCE;
    public final static int UA_MODE_DEFAULT = WSessionSettings.USER_AGENT_MODE_VR;
    public final static int INPUT_MODE_DEFAULT = 1;

    // The default density is defined at build time.
    public final static float DISPLAY_DENSITY_DEFAULT = BuildConfig.DEFAULT_DENSITY;
    // The DPI is calculated so the default window has a logical width of 1024 CSS pixels.
    // For a density of 1.0, the DPI is 128 and the texture matches the logical size of the webpage.
    // For a density of 1.5, the DPI of 192 and the resolution of the texture is twice the world size of the window.
    public final static int DISPLAY_DPI_BASE = 40;
    public final static int DISPLAY_DPI_DEFAULT = (int) (DISPLAY_DENSITY_DEFAULT * DISPLAY_DPI_BASE);
    public final static int DISPLAY_DPI_MIN = 70;
    public final static int DISPLAY_DPI_MAX = 300;
    // World size: multiply by density to get the available resolution for the Web engine.
    public final static int WINDOW_WIDTH_DEFAULT = 900;
    public final static int WINDOW_HEIGHT_DEFAULT = 600;
    // The maximum size is computed so the resulting texture fits within 2560x2560.
    public final static int MAX_WINDOW_WIDTH_DEFAULT = 1200;
    public final static int MAX_WINDOW_HEIGHT_DEFAULT = 800;
    public enum WindowSizePreset {
        PRESET_0(WINDOW_WIDTH_DEFAULT, WINDOW_HEIGHT_DEFAULT),
        PRESET_1(750, 500),
        PRESET_2(825, 550),
        PRESET_3(900, 600);

        public final int width;
        public final int height;

        WindowSizePreset(int width, int height) {
            this.width = width;
            this.height = height;
        }
        public static WindowSizePreset fromId(int id) {
            if (id >= 0 && id < values().length) {
                return values()[id];
            } else {
                return WINDOW_SIZE_PRESET_DEFAULT;
            }
        }
        public static WindowSizePreset fromValues(int width, int height) {
            for (WindowSizePreset preset : values()) {
                if (preset.width == width && preset.height == height) {
                    return preset;
                }
            }
            return WINDOW_SIZE_PRESET_DEFAULT;
        }
    }
    public final static WindowSizePreset WINDOW_SIZE_PRESET_DEFAULT = WindowSizePreset.PRESET_3;

    public final static @WindowSelectionMethod int WINDOW_SELECTION_METHOD_DEFAULT = WINDOW_SELECTION_METHOD_HOVER;
    public final static int POINTER_COLOR_DEFAULT_DEFAULT = Color.parseColor("#FFFFFF");
    public final static int SCROLL_DIRECTION_DEFAULT = 0;
    public final static String ENV_DEFAULT = "cyberpunk";
    public final static int MSAA_DEFAULT_LEVEL = 1;
    public final static boolean AUDIO_ENABLED = BuildConfig.FLAVOR_backend == "chromium";
    public final static boolean LATIN_AUTO_COMPLETE_ENABLED = false;
    public final static boolean WINDOW_MOVEMENT_DEFAULT = false;
    public final static @TabsLocation int TABS_LOCATION_DEFAULT = TABS_LOCATION_TRAY;
    public final static float CYLINDER_DENSITY_ENABLED_DEFAULT = 4680.0f;
    public final static float HAPTIC_PULSE_DURATION_DEFAULT = 10.0f;
    public final static float HAPTIC_PULSE_INTENSITY_DEFAULT = 1.0f;
    public final static boolean HAPTIC_FEEDBACK_ENABLED = false;
    public final static boolean CENTER_WINDOWS_DEFAULT = false;
    private final static long CRASH_RESTART_DELTA = 2000;
    public final static boolean AUTOPLAY_ENABLED = false;
    public final static boolean HEAD_LOCK_DEFAULT = false;
    public final static boolean OPEN_TABS_IN_BACKGROUND_DEFAULT = true;
    public final static boolean DEBUG_LOGGING_DEFAULT = BuildConfig.DEBUG;
    public final static boolean POP_UPS_BLOCKING_DEFAULT = true;
    public final static boolean WEBXR_ENABLED_DEFAULT = true;
    public final static boolean TELEMETRY_STATUS_UPDATE_SENT_DEFAULT = false;
    public final static boolean BOOKMARKS_SYNC_DEFAULT = true;
    public final static boolean LOGIN_SYNC_DEFAULT = true;
    public final static boolean HISTORY_SYNC_DEFAULT = true;
    public final static boolean WHATS_NEW_DISPLAYED = false;
    public final static long FXA_LAST_SYNC_NEVER = 0;
    public final static boolean RESTORE_TABS_ENABLED = true;
    public final static boolean BYPASS_CACHE_ON_RELOAD = false;
    public final static int DOWNLOADS_SORTING_ORDER_DEFAULT = SortingContextMenuWidget.SORT_DATE_DESC;
    public final static boolean AUTOCOMPLETE_ENABLED = true;
    public final static boolean WEBGL_OUT_OF_PROCESS = false;
    public final static boolean LOCAL_ADDON_ALLOWED = false;
    public final static int PREFS_LAST_RESET_VERSION_CODE = 0;
    public final static boolean PASSWORDS_ENCRYPTION_KEY_GENERATED = false;
    public final static boolean AUTOFILL_ENABLED = true;
    public final static boolean LOGIN_AUTOCOMPLETE_ENABLED = true;
    public final static String SEARCH_ENGINE_DEFAULT = "";
    public final static @WidgetManagerDelegate.PointerMode int POINTER_MODE_DEFAULT = WidgetManagerDelegate.TRACKED_POINTER;

    // Enable telemetry by default (opt-out).
    public final static boolean CRASH_REPORTING_DEFAULT = false;
    public final static boolean TELEMETRY_DEFAULT = true;

    private int mCachedScrollDirection = -1;

    private boolean mDisableLayers = false;
    public void setDisableLayers(final boolean aDisableLayers) {
        mDisableLayers = aDisableLayers;
    }

    public SettingsStore(Context aContext) {
        mContext = aContext;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(aContext);
    }

    public void initModel(@NonNull Context context) {
        mSettingsViewModel = new ViewModelProvider(
                (VRBrowserActivity)context,
                ViewModelProvider.AndroidViewModelFactory.getInstance(((VRBrowserActivity) context).getApplication()))
                .get(SettingsViewModel.class);

        // Setup the stored properties until we get updated ones
        String json = mPrefs.getString(mContext.getString(R.string.settings_key_remote_props), null);
        mSettingsViewModel.setProps(json);

        String announcementsJson = mPrefs.getString(mContext.getString(R.string.settings_key_remote_announcements), null);
        mSettingsViewModel.setAnnouncements(announcementsJson);

        String experiencesJson = mPrefs.getString(mContext.getString(R.string.settings_key_remote_experiences), null);
        mSettingsViewModel.setExperiences(experiencesJson);

        String heyVRJson = mPrefs.getString(mContext.getString(R.string.settings_key_heyvr_experiences), null);
        mSettingsViewModel.setHeyVRExperiences(heyVRJson);

        mSettingsViewModel.refresh();

        updateRemoteContent(BuildConfig.PROPS_ENDPOINT,
                R.string.settings_key_remote_props,
                mSettingsViewModel::setProps);
        updateRemoteContent(BuildConfig.ANNOUNCEMENTS_ENDPOINT,
                R.string.settings_key_remote_announcements,
                mSettingsViewModel::setAnnouncements);
        updateRemoteContent(BuildConfig.EXPERIENCES_ENDPOINT,
                R.string.settings_key_remote_experiences,
                mSettingsViewModel::setExperiences);
        updateRemoteContent(BuildConfig.HEYVR_ENDPOINT,
                R.string.settings_key_heyvr_experiences,
                mSettingsViewModel::setHeyVRExperiences);
    }

    /**
     * Synchronizes the remote properties with the settings storage and notifies the model.
     * Any consumer listening to the SettingsViewModel will get notified of the properties updates.
     */
    private void updateRemoteContent(String endpoint, int prefsKey, Consumer<String> onContentAvailable) {
        ((VRBrowserApplication) mContext.getApplicationContext()).getExecutors().backgroundThread().post(() -> {
            Request request = new Request(
                    endpoint,
                    Request.Method.GET,
                    null,
                    null,
                    null,
                    null,
                    Request.Redirect.FOLLOW,
                    Request.CookiePolicy.INCLUDE,
                    false,
                    false
            );

            try {
                Response response = EngineProvider.INSTANCE.getDefaultClient(mContext).fetch(request);
                if (response.getStatus() == 200) {
                    String json = response.getBody().string(StandardCharsets.UTF_8);
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString(mContext.getString(prefsKey), json);
                    editor.apply();
                    // Once the JSON content has been received, execute the callback.
                    onContentAvailable.accept(json);
                }
            } catch (IOException e) {
                Log.d(LOGTAG, "Remote data fetch error for " + endpoint + ": " + e.getLocalizedMessage());
            }
        });
    }

    public Set<String> getDismissedAnnouncementIds() {
        Set<String> dismissedIds = new HashSet<>();
        String json = mPrefs.getString(mContext.getString(R.string.settings_key_dismissed_remote_announcements), "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                dismissedIds.add(jsonArray.getString(i));
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Error loading dismissed announcements: " + e.getMessage());
        }
        return dismissedIds;
    }

    public void addDismissedAnnouncementId(String announcementId) {
        Set<String> dismissedIds = getDismissedAnnouncementIds();
        if (dismissedIds.contains(announcementId)) {
            return;
        }

        dismissedIds.add(announcementId);

        JSONArray jsonArray = new JSONArray();
        for (String id : dismissedIds) {
            jsonArray.put(id);
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_dismissed_remote_announcements), jsonArray.toString());
        editor.apply();
    }

    public boolean isCrashReportingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_crash), CRASH_REPORTING_DEFAULT);
    }

    public void setCrashReportingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_crash), isEnabled);
        editor.apply();
    }

    public boolean isTelemetryEnabled() {
        // The first access to shared preferences will require a disk read.
        final StrictMode.ThreadPolicy threadPolicy = StrictMode.allowThreadDiskReads();
        try {
            return mPrefs.getBoolean(
                    mContext.getString(R.string.settings_key_telemetry), TELEMETRY_DEFAULT);
        } finally {
            StrictMode.setThreadPolicy(threadPolicy);
        }
    }

    public void setTelemetryEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_telemetry), isEnabled);
        editor.apply();

        // We send after enabling in case of opting-in
        if (isEnabled) {
            TelemetryService.start();
        } else {
            TelemetryService.stop();
        }

        // Update the status sent flag
        setTelemetryPingUpdateSent(true);
    }

    public boolean telemetryStatusSaved() {
        return mPrefs.contains(mContext.getString(R.string.settings_key_telemetry));
    }

    public boolean isTelemetryPingUpdateSent() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_telemetry_status_update_sent), TELEMETRY_STATUS_UPDATE_SENT_DEFAULT);
    }

    public void setTelemetryPingUpdateSent(boolean isSent) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_telemetry_status_update_sent), isSent);
        editor.apply();
    }

    public void setGeolocationData(String aGeolocationData) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_geolocation_data), aGeolocationData);
        editor.apply();
    }

    public String getGeolocationData() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_geolocation_data), "");
    }

    public boolean isRemoteDebuggingEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_remote_debugging), REMOTE_DEBUGGING_DEFAULT);
    }

    public void setRemoteDebuggingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_remote_debugging), isEnabled);
        editor.apply();
    }


    public boolean isDrmContentPlaybackEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_drm_playback), DRM_PLAYBACK_DEFAULT);
    }

    public boolean isDrmContentPlaybackSet() {
        return mPrefs.contains(mContext.getString(R.string.settings_key_drm_playback));
    }

    public void setDrmContentPlaybackEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_drm_playback), isEnabled);
        editor.apply();

        mSettingsViewModel.setIsDrmEnabled(isEnabled);
    }

    public int getTrackingProtectionLevel() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_tracking_protection_level), TRACKING_DEFAULT);
    }

    public void setTrackingProtectionLevel(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_tracking_protection_level), level);
        editor.apply();

        mSettingsViewModel.setIsTrackingProtectionEnabled(level != WContentBlocking.EtpLevel.NONE);
    }

    public boolean isSystemRootCAEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_system_root_ca), SYSTEM_ROOT_CA_DEFAULT);
    }

    public void setSystemRootCAEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_system_root_ca), isEnabled);
        editor.apply();
    }

    public static boolean shouldStartWithPassthrougEnabled() {
        return DeviceType.getType() == DeviceType.LenovoA3 || DeviceType.getType() == DeviceType.VisionGlass;
    }

    public boolean isStartWithPassthroughEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_start_with_passthrough), shouldStartWithPassthrougEnabled());
    }

    public void setStartWithPassthroughEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_start_with_passthrough), isEnabled);
        editor.apply();
    }

    public boolean isLatinAutoCompleteEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_latin_auto_complete), LATIN_AUTO_COMPLETE_ENABLED);
    }

    public void setLatinAutoComplete(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_latin_auto_complete), isEnabled);
        editor.apply();
    }

    public boolean isHeadLockEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_head_lock), HEAD_LOCK_DEFAULT);
    }

    public void setHeadLockEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_head_lock), isEnabled);
        editor.apply();
    }

    public boolean isOpenTabsInBackgroundEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_open_tabs_in_background), OPEN_TABS_IN_BACKGROUND_DEFAULT);
    }

    public void setOpenTabsInBackgroundEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_open_tabs_in_background), isEnabled);
        editor.apply();
    }

    @TabsLocation
    public int getTabsLocation() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_tabs_location), TABS_LOCATION_DEFAULT);
    }

    public void setTabsLocation(@TabsLocation int tabsLocation) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_tabs_location), tabsLocation);
        editor.commit();
    }

    public boolean isEnvironmentOverrideEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_environment_override), ENV_OVERRIDE_DEFAULT);
    }

    public void setEnvironmentOverrideEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_environment_override), isEnabled);
        editor.apply();
    }

    public boolean isUIHardwareAccelerationEnabled() {
        boolean defaultValue = UI_HARDWARE_ACCELERATION_DEFAULT;
        if (DeviceType.getType() == DeviceType.MagicLeap2) {
            // Hardware acceleration causes several UI glitches when rendering widgets and
            // also locks un the UI thread for several seconds.
            defaultValue = UI_HARDWARE_ACCELERATION_DEFAULT_MAGIC_LEAP_2;
        }
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_ui_hardware_acceleration), defaultValue);
    }

    public void setUIHardwareAccelerationEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_ui_hardware_acceleration), isEnabled);
        editor.apply();
    }

    public boolean isPerformanceMonitorEnabled() {
        // Disabling Performance Monitor until it can properly handle multi-window
        return false; // mPrefs.getBoolean(mContext.getString(R.string.settings_key_performance_monitor), PERFORMANCE_MONITOR_DEFAULT);
    }

    public void setPerformanceMonitorEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_performance_monitor), isEnabled);
        editor.apply();
    }

    @FloatRange(from = 0, to = 1)
    public float getWindowDistance() {
        return mPrefs.getFloat(mContext.getString(R.string.settings_key_window_distance), WINDOW_DISTANCE_DEFAULT);
    }

    public void setWindowDistance(@FloatRange(from = 0, to = 1) float distance) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_window_distance), distance);
        editor.apply();
    }

    public int getUaMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_user_agent_version), UA_MODE_DEFAULT);
    }

    public void setUaMode(int mode) {
        int checkedMode = mode;
        if ((mode != WSessionSettings.USER_AGENT_MODE_VR) && (mode != WSessionSettings.USER_AGENT_MODE_MOBILE)) {
            Log.e(LOGTAG, "User agent mode: " + mode + " is not supported.");
            checkedMode = UA_MODE_DEFAULT;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_user_agent_version), checkedMode);
        editor.apply();
    }

    public int getInputMode() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_input_mode), INPUT_MODE_DEFAULT);
    }

    public void setInputMode(int aTouchMode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_input_mode), aTouchMode);
        editor.apply();
    }

    public String getHomepage() {
        return mPrefs.getString(
                mContext.getString(R.string.settings_key_homepage),
                mContext.getString(R.string.HOMEPAGE_URL));
    }

    public void setHomepage(String aHomepage) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_homepage), aHomepage);
        editor.apply();
    }

    public float getDisplayDensity() {
        return mPrefs.getFloat(
                mContext.getString(R.string.settings_key_display_density), DISPLAY_DENSITY_DEFAULT);
    }

    public void setDisplayDensity(float aDensity) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_display_density), aDensity);
        editor.apply();
    }

    public int getWindowWidth() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_window_width), WINDOW_WIDTH_DEFAULT);
    }

    public int getWindowHeight() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_window_height), WINDOW_HEIGHT_DEFAULT);
    }

    public void setWindowSizePreset(int presetId) {
        WindowSizePreset preset = WindowSizePreset.fromId(presetId);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_window_width), preset.width);
        editor.putInt(mContext.getString(R.string.settings_key_window_height), preset.height);
        editor.commit();
    }

    public float getWindowAspect() {
        return (float)getWindowWidth() / (float)getWindowHeight();
    }

    public String getDeviceName() {
        return mPrefs.getString(
                mContext.getString(R.string.settings_key_device_name), DeviceType.getDeviceName(mContext));
    }

    public void setDeviceName(String aDeviceName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_device_name), aDeviceName);
        editor.apply();
    }

    public int getDisplayDpi() {
        return Math.max(Math.min(mPrefs.getInt(
                mContext.getString(R.string.settings_key_display_dpi), DISPLAY_DPI_DEFAULT), DISPLAY_DPI_MAX), DISPLAY_DPI_MIN);
    }

    public void setDisplayDpi(int aDpi) {
        // Reject non-valid value
        if (aDpi > DISPLAY_DPI_MAX || aDpi < DISPLAY_DPI_MIN) {
            return;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_display_dpi), aDpi);
        editor.apply();
    }

    public int getMaxWindowWidth() {
        return MAX_WINDOW_WIDTH_DEFAULT;
    }

    public int getMaxWindowHeight() {
        return MAX_WINDOW_HEIGHT_DEFAULT;
    }

    public String getEnvironment() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_env), ENV_DEFAULT);
    }

    public void setEnvironment(String aEnv) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_env), aEnv);
        editor.apply();
    }

    @WindowSelectionMethod
    public int getWindowSelectionMethod() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_window_selection_method), WINDOW_SELECTION_METHOD_DEFAULT);
    }

    public void setWindowSelectionMethod(int method) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_window_selection_method), method);
        editor.apply();
    }

    public int getPointerColor() {
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_pointer_color), POINTER_COLOR_DEFAULT_DEFAULT);
    }

    public void setPointerColor(int color) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_pointer_color), color);
        editor.apply();
    }

    public int getScrollDirection() {
        if (mCachedScrollDirection < 0) {
            mCachedScrollDirection = mPrefs.getInt(mContext.getString(R.string.settings_key_scroll_direction), SCROLL_DIRECTION_DEFAULT);
        }
        return mCachedScrollDirection;
    }

    public void setScrollDirection(int aScrollDirection) {
        mCachedScrollDirection = aScrollDirection;
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_scroll_direction), aScrollDirection);
        editor.apply();
    }


    public int getMSAALevel() {
        // We could get the exact HarmonyOS version using the Huawei's ohos package but there is little
        // point in adding a dependency just for that. We can do an alternate check.
        boolean isHarmonyOS2 = DeviceType.isHVRBuild() && Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
        return mPrefs.getInt(
                mContext.getString(R.string.settings_key_msaa), isHarmonyOS2 ? 0 : MSAA_DEFAULT_LEVEL);
    }

    public void setMSAALevel(int level) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_msaa), level);
        editor.apply();
    }

    public boolean getLayersEnabled() {
        if ((DeviceType.isOculusBuild() || DeviceType.isPicoXR())  || DeviceType.isPfdmXR() && !mDisableLayers) {
            Log.i(LOGTAG, "Layers are enabled");
            return true;
        }
        Log.i(LOGTAG, "Layers are not supported");
        return false;
    }

    public int getTransparentBorderWidth() {
        return 1;
    }

    public boolean isAudioEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_audio), AUDIO_ENABLED);
    }

    public void setAudioEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_audio), isEnabled);
        editor.apply();
    }

    public String getVoiceSearchService() {
        return mPrefs.getString(
                mContext.getString(R.string.settings_key_voice_search_service), SpeechServices.DEFAULT);
    }

    public void setVoiceSearchService(String service) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_voice_search_service), service);
        editor.apply();
    }

    public String getVoiceSearchLocale() {
        String language = mPrefs.getString(
                mContext.getString(R.string.settings_key_voice_search_language), null);
        return language;
    }

    public void setVoiceSearchLocale(String language) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_voice_search_language), language);
        editor.apply();
    }

    public String getDisplayLocale() {
        String language = mPrefs.getString(
                mContext.getString(R.string.settings_key_display_language), null);
        return language;
    }

    public void setDisplayLocale(String language) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_display_language), language);
        editor.apply();
    }

    public ArrayList<String> getContentLocales() {
        ArrayList<String> result = new ArrayList<>();

        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_content_languages),
                null);

        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i=0; i<jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }

            return result;

        } catch (Exception e) {
            return null;
        }
    }

    public void setContentLocales(List<String> languages) {
        JSONArray json = new JSONArray(languages);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_content_languages), json.toString());
        editor.apply();
    }

    public float getCylinderDensity() {
        return mPrefs.getFloat(mContext.getString(R.string.settings_key_cylinder_density),  CYLINDER_DENSITY_ENABLED_DEFAULT);
    }

    public void setCylinderDensity(float aDensity) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_cylinder_density), aDensity);
        editor.apply();
    }

    public boolean isCurvedModeEnabled() {
        return getCylinderDensity() > 0;
    }

    public float getHapticPulseDuration() {
        return mPrefs.getFloat(mContext.getString(R.string.settings_key_haptic_pulse_duration), HAPTIC_PULSE_DURATION_DEFAULT);
    }

    public void setHapticPulseDuration(float aPulseDuration) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_haptic_pulse_duration), aPulseDuration);
        editor.apply();
    }

    public float getHapticPulseIntensity() {
        return mPrefs.getFloat(mContext.getString(R.string.settings_key_haptic_pulse_intensity), HAPTIC_PULSE_INTENSITY_DEFAULT);
    }

    public void setHapticPulseIntensity(float aPulseIntensity) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(mContext.getString(R.string.settings_key_haptic_pulse_intensity), aPulseIntensity);
        editor.apply();
    }

    public void setHapticFeedbackEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_haptic_feedback_enabled), isEnabled);
        editor.apply();
    }

    public boolean isHapticFeedbackEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_haptic_feedback_enabled), HAPTIC_FEEDBACK_ENABLED);
    }

    public void setPointerMode(@WidgetManagerDelegate.PointerMode int pointerMode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_pointer_mode), pointerMode);
        editor.apply();
    }

    public @WidgetManagerDelegate.PointerMode int getPointerMode() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_pointer_mode), POINTER_MODE_DEFAULT);
    }

    public boolean isCenterWindows() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_center_windows), CENTER_WINDOWS_DEFAULT);
    }

    public void setCenterWindows(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_center_windows), isEnabled);
        editor.apply();
    }

    public void setSelectedKeyboard(Locale aLocale) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_keyboard_locale), aLocale.toLanguageTag());
        editor.apply();
    }

    public Locale getKeyboardLocale() {
        String value = mPrefs.getString(mContext.getString(R.string.settings_key_keyboard_locale), null);
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return Locale.forLanguageTag(value);
    }

    public synchronized long getCrashRestartCount() {
        long count = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        if (count > 0) {
            final long timestamp = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), -1);
            if (System.currentTimeMillis() - timestamp > CRASH_RESTART_DELTA) {
                count = 0;
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), count);
                editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), -1);
                editor.apply();
            }
        }
        return count;
    }

    public synchronized void incrementCrashRestartCount() {
        SharedPreferences.Editor editor = mPrefs.edit();
        long count = mPrefs.getLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        count++;
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), count);
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count_timestamp), System.currentTimeMillis());
        editor.apply();
    }

    public synchronized void resetCrashRestartCount() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putLong(mContext.getString(R.string.settings_key_crash_restart_count), 0);
        editor.apply();
    }

    public boolean isSpeechDataCollectionEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_speech_data_collection), SPEECH_DATA_COLLECTION_DEFAULT);
    }

    public void setSpeechDataCollectionEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_speech_data_collection), isEnabled);
        editor.apply();
    }

    public boolean isNotificationsEnabled() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_notifications), NOTIFICATIONS_DEFAULT);
    }

    public void setNotificationsEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_notifications), isEnabled);
        editor.apply();
    }

    public boolean isSpeechDataCollectionReviewed() {
        return mPrefs.getBoolean(
                mContext.getString(R.string.settings_key_speech_data_collection_reviewed), SPEECH_DATA_COLLECTION_REVIEWED_DEFAULT);
    }

    public void setSpeechDataCollectionReviewed(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_speech_data_collection_reviewed), isEnabled);
        editor.apply();
    }

    public boolean isDebugLoggingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_debug_logging), DEBUG_LOGGING_DEFAULT);
    }

    public void setDebugLoggingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_debug_logging), isEnabled);
        editor.apply();
    }

    public boolean isAutoplayEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autoplay), AUTOPLAY_ENABLED);
    }

    public void setAutoplayEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autoplay), isEnabled);
        editor.apply();
    }

    public void setPid(int aPid) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_pid), aPid);
        editor.apply();
    }

    public int getPid() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_pid), 0);
    }

    public boolean isPopUpsBlockingEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_pop_up_blocking), POP_UPS_BLOCKING_DEFAULT);
    }

    public void setPopUpsBlockingEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_pop_up_blocking), isEnabled);
        editor.apply();

        mSettingsViewModel.setIsPopUpBlockingEnabled(isEnabled);
    }

    public boolean isWebXREnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_webxr), WEBXR_ENABLED_DEFAULT);
    }

    public void setWebXREnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_webxr), isEnabled);
        editor.apply();

        mSettingsViewModel.setIsWebXREnabled(isEnabled);
    }

    public void setWhatsNewDisplayed(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_whats_new_displayed), isEnabled);
        editor.apply();
    }

    public boolean isWhatsNewDisplayed() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_whats_new_displayed), WHATS_NEW_DISPLAYED);
    }

    public void setFxALastSync(@NonNull String email, long timestamp) {
        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_fxa_last_sync),
                new JSONObject().toString());

        try {
            JSONObject jsonObject = new JSONObject(json);
            jsonObject.put(email, timestamp);

            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putString(mContext.getString(R.string.settings_key_fxa_last_sync), jsonObject.toString());
            editor.apply();

        } catch (Exception e) {
            Log.d(LOGTAG, e.getMessage());
        }
    }

    public long getFxALastSync(@NonNull String email) {
        String json = mPrefs.getString(
                mContext.getString(R.string.settings_key_fxa_last_sync),
                null);

        try {
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key.equals(email)) {
                    return jsonObject.getLong(key);
                }
            }

            return FXA_LAST_SYNC_NEVER;

        } catch (Exception e) {
            return FXA_LAST_SYNC_NEVER;
        }
    }

    public void setRestoreTabsEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_restore_tabs), isEnabled);
        editor.apply();
    }

    public boolean isRestoreTabsEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_restore_tabs), RESTORE_TABS_ENABLED);
    }

    public void setBypassCacheOnReload(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_bypass_cache_on_reload), isEnabled);
        editor.apply();
    }

    public boolean isBypassCacheOnReloadEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_bypass_cache_on_reload), BYPASS_CACHE_ON_RELOAD);
    }

    public void setDownloadsSortingOrder(@SortingContextMenuWidget.Order int order) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_downloads_sorting_order), order);
        editor.apply();
    }

    public @Storage int getDownloadsSortingOrder() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_downloads_sorting_order), DOWNLOADS_SORTING_ORDER_DEFAULT);
    }

    public void setRemotePropsVersionName(String versionName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_remote_props_version_name), versionName);
        editor.apply();

        mSettingsViewModel.setPropsVersionName(versionName);
    }

    public String getRemotePropsVersionName() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_remote_props_version_name), "0");
    }

    public void setAutocompleteEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autocomplete), isEnabled);
        editor.apply();
    }

    public boolean isAutocompleteEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autocomplete), AUTOCOMPLETE_ENABLED);
    }

    public String getSearchEngineId() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_search_engine_id), SEARCH_ENGINE_DEFAULT);
    }

    public void setSearchEngineId(@Nullable String searchEngineId) {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (!StringUtils.isEmpty(searchEngineId)) {
            editor.putString(mContext.getString(R.string.settings_key_search_engine_id), searchEngineId);
        } else {
            editor.remove(mContext.getString(R.string.settings_key_search_engine_id));
        }
        editor.apply();
    }

    public void setWebGLOutOfProcess(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_webgl_out_of_process), isEnabled);
        editor.apply();
    }

    public boolean isWebGLOutOfProcess() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_webgl_out_of_process), WEBGL_OUT_OF_PROCESS);
    }

    public void setLocalAddonAllowed(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_local_addon_allowed), isEnabled);
        editor.apply();
    }

    public boolean isLocalAddonAllowed() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_local_addon_allowed), LOCAL_ADDON_ALLOWED);
    }

    public int getPrefsLastResetVersionCode() {
        return mPrefs.getInt(mContext.getString(R.string.settings_key_prefs_last_reset_version_code), PREFS_LAST_RESET_VERSION_CODE);
    }

    public void setPrefsLastResetVersionCode(int versionCode) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(mContext.getString(R.string.settings_key_prefs_last_reset_version_code), versionCode);
        editor.apply();
    }

    @Nullable
    public Map<String, RemoteProperties> getRemoteProperties() {
        String json = mPrefs.getString(mContext.getString(R.string.settings_key_remote_props), null);

        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<Map<String, RemoteProperties>>() {}.getType();

        Map<String, RemoteProperties> propertiesMap = null;
        try {
            propertiesMap = gson.fromJson(json, type);

        } catch (Exception ignored) { }

        return propertiesMap;
    }

    public void setRemoteProperties(@Nullable String json) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_remote_props), json);
        editor.apply();
    }

    public void recordPasswordsEncryptionKeyGenerated() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_passwords_encryption_key_generated), true);
        editor.apply();
    }

    public boolean isPasswordsEncryptionKeyGenerated() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_passwords_encryption_key_generated), PASSWORDS_ENCRYPTION_KEY_GENERATED);
    }

    public void setAutoFillEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_autofill_enabled), isEnabled);
        editor.apply();
    }

    public boolean isAutoFillEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_autofill_enabled), AUTOFILL_ENABLED);
    }

    public void setLoginAutocompleteEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_login_autocomplete_enabled), isEnabled);
        editor.apply();
    }

    public boolean isLoginAutocompleteEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_login_autocomplete_enabled), LOGIN_AUTOCOMPLETE_ENABLED);
    }

    public void setLoginSyncEnabled(boolean isEnabled) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_login_sync_enabled), isEnabled);
        editor.apply();
    }

    public boolean isLoginSyncEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_login_sync_enabled), LOGIN_SYNC_DEFAULT);
    }

    public void setTabAfterRestore(@Nullable String uri) {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (!StringUtils.isEmpty(uri)) {
            editor.putString(mContext.getString(R.string.settings_key_tab_after_restore), uri);
        } else {
            editor.remove(mContext.getString(R.string.settings_key_tab_after_restore));
        }
        editor.apply();
    }

    public String getTabAfterRestore() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_tab_after_restore), null);
    }

    public void setTermsServiceAccepted(boolean isAccepted) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_terms_service_accepted), isAccepted);
        editor.apply();
    }

    public boolean isTermsServiceAccepted() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_terms_service_accepted), false);
    }

    public void setPrivacyPolicyAccepted(boolean isAccepted) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_privacy_policy_accepted), isAccepted);
        editor.apply();
    }

    public boolean isPrivacyPolicyAccepted() {
        return mPrefs.getBoolean(mContext.getString(R.string.settings_key_privacy_policy_accepted), false);
    }

    public void setWebAppsData(String aWebAppsData) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(mContext.getString(R.string.settings_key_web_apps_data), aWebAppsData);
        editor.apply();
    }

    public String getWebAppsData() {
        return mPrefs.getString(mContext.getString(R.string.settings_key_web_apps_data), "");
    }
}

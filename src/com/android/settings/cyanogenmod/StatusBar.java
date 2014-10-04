/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.settings.cyanogenmod;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.MSimTelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class StatusBar extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String TAG = "StatusBar";

    private static final String STATUS_BAR_BATTERY = "status_bar_battery";
    private static final String STATUS_BAR_SIGNAL = "status_bar_signal";

    private static final String STATUS_BAR_BATTERY_SHOW_PERCENT = "status_bar_battery_show_percent";

    private static final String STATUS_BAR_STYLE_HIDDEN = "4";
    private static final String STATUS_BAR_STYLE_TEXT = "6";
    private static final String STATUS_BAR_STYLE_BAR = "7";

    private static final String BATTERY_BAR_HEIGHT = "battery_bar_height";
    private static final String BATTERY_BAR_LEFT_COLOR = "battery_bar_left_color";
    private static final String BATTERY_BAR_RIGHT_COLOR = "battery_bar_right_color";

    private static final String STATUS_BAR_GENERAL = "status_bar_general";

    private static final int MENU_RESET = Menu.FIRST;

    private ListPreference mStatusBarBattery;
    private SystemSettingCheckBoxPreference mStatusBarBatteryShowPercent;
    private ListPreference mStatusBarCmSignal;
    private CheckBoxPreference mStatusBarBrightnessControl;

    private PreferenceCategory mStatusBarGeneral;

    private SeekBarPreference mBatteryBarHeight;
    private ColorPickerPreference mBatteryBarLeftColor;
    private ColorPickerPreference mBatteryBarRightColor;

    private ContentObserver mSettingsObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.status_bar);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        mStatusBarBattery = (ListPreference) findPreference(STATUS_BAR_BATTERY);
        mStatusBarBatteryShowPercent =
                (SystemSettingCheckBoxPreference) findPreference(STATUS_BAR_BATTERY_SHOW_PERCENT);
        mStatusBarCmSignal = (ListPreference) prefSet.findPreference(STATUS_BAR_SIGNAL);

        mStatusBarBrightnessControl = (CheckBoxPreference)
                prefSet.findPreference(Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL);
        refreshBrightnessControl();

        int batteryStyle = Settings.System.getInt(resolver, Settings.System.STATUS_BAR_BATTERY, 0);
        mStatusBarBattery.setValue(String.valueOf(batteryStyle));
        mStatusBarBattery.setSummary(mStatusBarBattery.getEntry());
        mStatusBarBattery.setOnPreferenceChangeListener(this);

        int signalStyle = Settings.System.getInt(resolver, Settings.System.STATUS_BAR_SIGNAL_TEXT, 0);
        mStatusBarCmSignal.setValue(String.valueOf(signalStyle));
        mStatusBarCmSignal.setSummary(mStatusBarCmSignal.getEntry());
        mStatusBarCmSignal.setOnPreferenceChangeListener(this);

        if (Utils.isWifiOnly(getActivity())
                || (MSimTelephonyManager.getDefault().isMultiSimEnabled())) {
            prefSet.removePreference(mStatusBarCmSignal);
        }

        mSettingsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                refreshBrightnessControl();
            }

            @Override
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }
        };

    mStatusBarGeneral = (PreferenceCategory) findPreference(STATUS_BAR_GENERAL);

        mBatteryBarHeight = (SeekBarPreference) findPreference(BATTERY_BAR_HEIGHT);
        mBatteryBarHeight.setValue(Settings.System.getInt(resolver,
                Settings.System.BATTERY_BAR_HEIGHT, 3));
        mBatteryBarHeight.setOnPreferenceChangeListener(this);

        mBatteryBarLeftColor = (ColorPickerPreference) findPreference(BATTERY_BAR_LEFT_COLOR);
        int leftColor = Settings.System.getInt(resolver,
                Settings.System.BATTERY_BAR_LEFT_COLOR, 0xffff4444);
        String hexColor = String.format("#%08x", (0x00ffffff & leftColor));
        mBatteryBarLeftColor.setSummary(hexColor);
        mBatteryBarLeftColor.setNewPreviewColor(leftColor);
        mBatteryBarLeftColor.setOnPreferenceChangeListener(this);

        mBatteryBarRightColor = (ColorPickerPreference) findPreference(BATTERY_BAR_RIGHT_COLOR);
        int rightColor = Settings.System.getInt(resolver,
                Settings.System.BATTERY_BAR_RIGHT_COLOR, 0xff33b5e5);
        hexColor = String.format("#%08x", (0x00ffffff & rightColor));
        mBatteryBarRightColor.setSummary(hexColor);
        mBatteryBarRightColor.setNewPreviewColor(rightColor);
        mBatteryBarRightColor.setOnPreferenceChangeListener(this);

        enableStatusBarBatteryDependents(mStatusBarBattery.getValue());

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                true, mSettingsObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mStatusBarBattery) {
            int batteryStyle = Integer.valueOf((String) newValue);
            int index = mStatusBarBattery.findIndexOfValue((String) newValue);
            Settings.System.putInt(resolver, Settings.System.STATUS_BAR_BATTERY, batteryStyle);
            mStatusBarBattery.setSummary(mStatusBarBattery.getEntries()[index]);

            enableStatusBarBatteryDependents((String) newValue);
            return true;
        } else if (preference == mStatusBarCmSignal) {
            int signalStyle = Integer.valueOf((String) newValue);
            int index = mStatusBarCmSignal.findIndexOfValue((String) newValue);
            Settings.System.putInt(resolver, Settings.System.STATUS_BAR_SIGNAL_TEXT, signalStyle);
            mStatusBarCmSignal.setSummary(mStatusBarCmSignal.getEntries()[index]);
            return true;
        } else if (preference == mBatteryBarHeight) {
            int height = ((Integer) newValue).intValue();
            Settings.System.putInt(resolver, Settings.System.BATTERY_BAR_HEIGHT, height);
            return true;
        } else if (preference == mBatteryBarLeftColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(resolver, Settings.System.BATTERY_BAR_LEFT_COLOR, intHex);
            return true;
        } else if (preference == mBatteryBarRightColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(resolver, Settings.System.BATTERY_BAR_RIGHT_COLOR, intHex);
            return true;
        }

        return false;
    }

    private void refreshBrightnessControl() {
        try {
            if (Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                mStatusBarBrightnessControl.setSummary(R.string.status_bar_toggle_info);
            } else {
                mStatusBarBrightnessControl.setSummary(R.string.status_bar_toggle_brightness_summary);
            }
        } catch (SettingNotFoundException e) {
            // Do nothing
        }
    }

    private void enableStatusBarBatteryDependents(String value) {
        boolean enabled = !(value.equals(STATUS_BAR_STYLE_TEXT)
                || value.equals(STATUS_BAR_STYLE_HIDDEN)
                || value.equals(STATUS_BAR_STYLE_BAR));
        mStatusBarBatteryShowPercent.setEnabled(enabled);

        if (value.equals(STATUS_BAR_STYLE_BAR)) {
            mStatusBarGeneral.addPreference(mBatteryBarHeight);
            mStatusBarGeneral.addPreference(mBatteryBarLeftColor);
            mStatusBarGeneral.addPreference(mBatteryBarRightColor);
        } else {
            mStatusBarGeneral.removePreference(mBatteryBarHeight);
            mStatusBarGeneral.removePreference(mBatteryBarLeftColor);
            mStatusBarGeneral.removePreference(mBatteryBarRightColor);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.menu_restore)
                .setIcon(R.drawable.ic_settings_backup)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetToDefault();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void resetToDefault() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(R.string.shortcut_action_reset);
        alertDialog.setMessage(R.string.battery_bar_reset_message);
        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                resetValues();
            }
        });
        alertDialog.setNegativeButton(R.string.cancel, null);
        alertDialog.create().show();
    }

    private void resetValues() {
        Settings.System.putInt(getContentResolver(),
                Settings.System.BATTERY_BAR_HEIGHT, 3);
        mBatteryBarHeight.setValue(3);
        Settings.System.putInt(getContentResolver(),
                Settings.System.BATTERY_BAR_LEFT_COLOR, 0xffff4444);
        mBatteryBarLeftColor.setNewPreviewColor(0xffff4444);
        Settings.System.putInt(getContentResolver(),
                Settings.System.BATTERY_BAR_RIGHT_COLOR, 0xff33b5e5);
        mBatteryBarRightColor.setNewPreviewColor(0xff33b5e5);
    }
}

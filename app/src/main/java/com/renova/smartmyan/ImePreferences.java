package com.renova.smartmyan;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethodcommon.InputMethodSettingsFragment;

//Display the IME preferences inside the input method setting
public class ImePreferences extends PreferenceActivity {
    static InputMethodManager imeManager;
    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, Settings.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //overwriting the title of the activity
        setTitle(R.string.settings_name);

        imeManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return Settings.class.getName().equals(fragmentName);
    }

    public static class Settings extends InputMethodSettingsFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
            setSubtypeEnablerTitle(R.string.select_language);

            //loading the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);

            Preference myPref = (Preference) findPreference("go_to_ime");
            myPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
                    return true;
                }
            });

            Preference myPref2 = (Preference) findPreference("choose_keyboard");
            myPref2.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    imeManager.showInputMethodPicker();
                    return true;
                }
            });
        }
    }
}
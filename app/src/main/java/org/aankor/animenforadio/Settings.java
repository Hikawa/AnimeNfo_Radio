package org.aankor.animenforadio;

import android.os.Bundle;
import android.preference.ListPreference;
import android.support.v4.app.FragmentActivity;
import android.support.v4.preference.PreferenceFragment;

/**
 * Created by aankor on 20.09.14.
 */
public class Settings extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
    }

    private static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.prefs);
            ListPreference pauseLengthPref = (ListPreference) findPreference("pauseLength");
            if (pauseLengthPref.getValue() == null)
                pauseLengthPref.setValueIndex(3);
            ListPreference radioStreamPref = (ListPreference) findPreference("radioStream");
            if (radioStreamPref.getValue() == null)
                radioStreamPref.setValueIndex(0);
        }
    }
}

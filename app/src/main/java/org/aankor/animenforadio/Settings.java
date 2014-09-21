package org.aankor.animenforadio;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

/**
 * Created by aankor on 20.09.14.
 */
public class Settings extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
    }

    public static class PrefsFragment extends PreferenceFragment {
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

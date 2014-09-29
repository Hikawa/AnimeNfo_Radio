/*
 * This file is part of AnimeNfoRadio.
 * Copyright (C) 2014  aankor
 *
 * AnimeNfoRadio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AnimeNfoRadio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AnimeNfoRadio.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.aankor.animenforadio;

import android.os.Bundle;
import android.preference.ListPreference;
import android.support.v4.app.FragmentActivity;
import android.support.v4.preference.PreferenceFragment;

public class Settings extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
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

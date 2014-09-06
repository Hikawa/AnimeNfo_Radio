package org.aankor.animenforadio;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.aankor.animenforadio.api.RadioState;
import org.aankor.animenforadio.api.SongInfo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RadioPlayer.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {link RadioPlayer#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RadioPlayer extends Fragment {
    /*
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;
*/
    private boolean isPlaying = false;
    private RadioState radioState = new RadioState();
    private ImageView albumMiniArtView;
    private TextView songNameView;
    private ArrayList<OnSongChangeListener> onSongChangeListeners = new ArrayList<OnSongChangeListener>();

    public RadioPlayer() {
        // Required empty public constructor
    }

    public void addOnSongChangeListener(OnSongChangeListener listener) {
        onSongChangeListeners.add(listener);
    }

    public void removeOnSongChangeListener(OnSongChangeListener listener) {
        onSongChangeListeners.remove(listener);
    }

    /*
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                mParam1 = getArguments().getString(ARG_PARAM1);
                mParam2 = getArguments().getString(ARG_PARAM2);
            }
        }
    */
    private void refresh(final Bitmap bmp) {
        for (OnSongChangeListener listener : onSongChangeListeners)
            listener.onSongChange(radioState.currentSong, bmp);
        albumMiniArtView.setImageBitmap(bmp);
        songNameView.setText(radioState.currentSong.getArtist() + " - " + radioState.currentSong.getTitle());
    }

    private void update() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!radioState.fetch())
                    return;
                try {
                    URL url = new URL(radioState.currentSong.getArtUrl());
                    final Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());

                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (radioState.currentSong == null)
                                        return;
                                    refresh(bmp);
                                }
                            });

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View rootView = inflater.inflate(R.layout.radio_player, container, false);
        final ImageButton playStopButton = (ImageButton) rootView.findViewById(R.id.playStopButton);
        albumMiniArtView = (ImageView) rootView.findViewById(R.id.albumMiniArtView);
        songNameView = (TextView) rootView.findViewById(R.id.songNameView);
        playStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isPlaying = !isPlaying;
                playStopButton.setBackgroundResource(isPlaying ? R.drawable.player_stop : R.drawable.player_play);
                if (isPlaying) {
                    getActivity().startService(new Intent(getActivity(), RadioService.class));
                    update();
                } else {
                    getActivity().stopService(new Intent(getActivity(), RadioService.class));
                    RadioNotification.cancel(getActivity());
                }
            }
        });
        return rootView;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * <p/>
     * param param1 Parameter 1.
     * param param2 Parameter 2.
     * return A new instance of fragment RadioPlayer.
     */
  /*  // TODO: Rename and change types and number of parameters
    public static RadioPlayer newInstance(String param1, String param2) {
        RadioPlayer fragment = new RadioPlayer();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }*/

    public interface OnSongChangeListener {
        void onSongChange(final SongInfo songInfo, final Bitmap bmp);
    }
/*
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
*/

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

}

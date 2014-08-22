package de.ph1b.audiobook.fragment;


import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.TimeUnit;

import de.ph1b.audiobook.BuildConfig;
import de.ph1b.audiobook.R;
import de.ph1b.audiobook.activity.MediaView;
import de.ph1b.audiobook.adapter.MediaSpinnerAdapter;
import de.ph1b.audiobook.dialog.JumpToPosition;
import de.ph1b.audiobook.dialog.SleepDialog;
import de.ph1b.audiobook.utils.BookDetail;
import de.ph1b.audiobook.utils.MediaDetail;
import de.ph1b.audiobook.service.AudioPlayerService;
import de.ph1b.audiobook.service.PlaybackService;
import de.ph1b.audiobook.service.PlayerStates;
import de.ph1b.audiobook.service.StateManager;

public class BookPlay extends Fragment implements OnClickListener {

    private ImageButton play_button;
    private TextView playedTimeView;
    private ImageView coverView;
    private ImageButton previous_button;
    private ImageButton forward_button;
    private SeekBar seek_bar;
    private Spinner bookSpinner;
    private TextView maxTimeView;
    private int position;

    private LocalBroadcastManager bcm;

    private static int bookId;
    private int oldPosition = -1;
    public static final String TAG = "de.ph1b.audiobooks.fragment.MediaPlayFragment";

    private boolean seekBarIsUpdating = false;
    private MediaDetail[] allMedia;
    private MediaDetail media;

    private int duration;


    private final BroadcastReceiver updateGUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(PlaybackService.GUI)) {

                //update book
                final BookDetail b = intent.getParcelableExtra(PlaybackService.GUI_BOOK);
                int mediaId = b.getPosition();
                allMedia = (MediaDetail[]) intent.getParcelableArrayExtra(PlaybackService.GUI_ALL_MEDIA);

                for (MediaDetail m : allMedia) {
                    if (m.getId() == mediaId) {
                        media = m;
                        break;
                    }
                }

                //checks if file exists
                File testFile = new File(media.getPath());
                if (!testFile.exists()) {
                    makeToast(getString(R.string.file_not_found), Toast.LENGTH_LONG);
                    startActivity(new Intent(getActivity(), MediaView.class));
                }


                //setting book name
                String bookName = b.getName();
                ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(bookName);

                bookId = b.getId();

                //setting cover
                coverView.getViewTreeObserver().addOnPreDrawListener(
                        new ViewTreeObserver.OnPreDrawListener() {
                            public boolean onPreDraw() {
                                int height = coverView.getMeasuredHeight();
                                int width = coverView.getMeasuredWidth();
                                String imagePath = b.getCover();
                                Bitmap cover;
                                if (imagePath.equals("") || new File(imagePath).isDirectory()) {
                                    cover = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                                    Canvas c = new Canvas(cover);
                                    Paint textPaint = new Paint();
                                    textPaint.setTextSize(4 * width / 5);
                                    Resources r = getActivity().getResources();

                                    textPaint.setColor(r.getColor(android.R.color.white));
                                    textPaint.setAntiAlias(true);
                                    textPaint.setTextAlign(Paint.Align.CENTER);
                                    Paint backgroundPaint = new Paint();
                                    backgroundPaint.setColor(r.getColor(R.color.file_chooser_audio));
                                    c.drawRect(0, 0, width, height, backgroundPaint);
                                    int y = (int) ((c.getHeight() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
                                    c.drawText(b.getName().substring(0, 1).toUpperCase(), width / 2, y, textPaint);
                                    coverView.setImageBitmap(cover);
                                } else {
                                    coverView.setImageURI(Uri.parse(imagePath));
                                }
                                return true;
                            }
                        }
                );


                //hides control elements if there is only one media to play
                if (allMedia.length == 1) {
                    previous_button.setVisibility(View.GONE);
                    forward_button.setVisibility(View.GONE);
                    bookSpinner.setVisibility(View.GONE);
                } else {
                    previous_button.setVisibility(View.VISIBLE);
                    forward_button.setVisibility(View.VISIBLE);
                    bookSpinner.setVisibility(View.VISIBLE);
                    MediaSpinnerAdapter adapter = new MediaSpinnerAdapter(getActivity(), allMedia);
                    int currentPosition = b.getPosition();
                    bookSpinner.setSelection(adapter.getPositionByMediaDetailId(currentPosition));
                    bookSpinner.setAdapter(adapter);
                    //sets correct position in spinner
                    if (allMedia.length > 1) {
                        bookSpinner.setSelection(adapter.getPositionByMediaDetailId(mediaId));
                    }
                    bookSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position != oldPosition) {
                                int newMediaId = allMedia[position].getId();
                                Intent i = new Intent(AudioPlayerService.CONTROL_CHANGE_BOOK_POSITION);
                                i.putExtra(AudioPlayerService.CONTROL_CHANGE_BOOK_POSITION, newMediaId);
                                bcm.sendBroadcast(i);
                                oldPosition = position;
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {

                        }
                    });
                }


                //updates media
                int position = media.getPosition();
                playedTimeView.setText(formatTime(position));

                //sets duration of file
                duration = media.getDuration();
                maxTimeView.setText(formatTime(duration));


                //sets seekBar to current position and correct length
                seek_bar.setMax(duration);
                seek_bar.setProgress(position);

                //sets play-button logo depending on player playing
                int icon = intent.getExtras().getInt(PlaybackService.GUI_PLAY_ICON);
                play_button.setImageResource(icon);
            }

            //updates seekBar by frequent calls
            if (action.equals(PlaybackService.GUI_SEEK)) {
                if (!seekBarIsUpdating)
                    seek_bar.setProgress(intent.getExtras().getInt(PlaybackService.GUI_SEEK));
            }

            if (action.equals(PlaybackService.GUI_MAKE_TOAST)) {
                String text = intent.getStringExtra(PlaybackService.GUI_MAKE_TOAST);
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Received text for toast: " + text);
                makeToast(text, Toast.LENGTH_SHORT);
            }
        }
    };

    private void makeToast(String text, int duration) {
        if (text != null) {
            Toast toast = Toast.makeText(getActivity(), text, duration);
            toast.show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_book_play, container, false);
        super.onCreate(savedInstanceState);

        bcm = LocalBroadcastManager.getInstance(getActivity());

        //setup actionbar
        ActionBar actionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, false);

        //starting AudioPlayerService and give him bookId to play
        if (getArguments() != null)
            bookId = getArguments().getInt(MediaView.PLAY_BOOK);
        else
            bookId = 0;

        //starting the service
        Intent serviceIntent = new Intent(getActivity(), AudioPlayerService.class);
        serviceIntent.putExtra(AudioPlayerService.BOOK_ID, bookId);
        getActivity().startService(serviceIntent);


        //init buttons
        seek_bar = (SeekBar) v.findViewById(R.id.seekBar);
        play_button = (ImageButton) v.findViewById(R.id.play);
        ImageButton rewind_button = (ImageButton) v.findViewById(R.id.rewind);
        ImageButton fast_forward_button = (ImageButton) v.findViewById(R.id.fast_forward);
        playedTimeView = (TextView) v.findViewById(R.id.played);
        forward_button = (ImageButton) v.findViewById(R.id.next_song);
        previous_button = (ImageButton) v.findViewById(R.id.previous_song);
        coverView = (ImageView) v.findViewById(R.id.book_cover);
        maxTimeView = (TextView) v.findViewById(R.id.maxTime);
        bookSpinner = (Spinner) v.findViewById(R.id.book_spinner);


        //register bc to receive images from services immediately
        IntentFilter filter = new IntentFilter();
        filter.addAction(PlaybackService.GUI);
        filter.addAction(PlaybackService.GUI_SEEK);
        filter.addAction(PlaybackService.GUI_PLAY_ICON);
        filter.addAction(PlaybackService.GUI_MAKE_TOAST);
        bcm.registerReceiver(updateGUIReceiver, filter);


        //setup buttons
        forward_button.setOnClickListener(this);
        previous_button.setOnClickListener(this);
        rewind_button.setOnClickListener(this);
        fast_forward_button.setOnClickListener(this);
        play_button.setOnClickListener(this);

        seek_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                position = progress;
                playedTimeView.setText(formatTime(progress)); //sets text to adjust while using seekBar
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBarIsUpdating = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Intent i = new Intent(AudioPlayerService.CONTROL_CHANGE_MEDIA_POSITION);
                i.putExtra(AudioPlayerService.CONTROL_CHANGE_MEDIA_POSITION, position);
                bcm.sendBroadcast(i);

                playedTimeView.setText(formatTime(position));
                seekBarIsUpdating = false;
            }
        });
        return v;
    }

    private String formatTime(int ms) {
        String h = String.valueOf(TimeUnit.MILLISECONDS.toHours(ms));
        String m = String.format("%02d", (TimeUnit.MILLISECONDS.toMinutes(ms) % 60));
        String s = String.format("%02d", (TimeUnit.MILLISECONDS.toSeconds(ms) % 60));
        return h + ":" + m + ":" + s;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.play:
                bcm.sendBroadcast(new Intent(AudioPlayerService.CONTROL_PLAY_PAUSE));
                break;
            case R.id.rewind:
                bcm.sendBroadcast(new Intent(AudioPlayerService.CONTROL_REWIND));
                break;
            case R.id.fast_forward:
                bcm.sendBroadcast(new Intent(AudioPlayerService.CONTROL_FAST_FORWARD));
                break;
            case R.id.next_song:
                bcm.sendBroadcast(new Intent(AudioPlayerService.CONTROL_FORWARD));
                break;
            case R.id.previous_song:
                bcm.sendBroadcast(new Intent(AudioPlayerService.CONTROL_PREVIOUS));
                break;
            default:
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.action_media_play, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new Preferences())
                        .addToBackStack(Preferences.TAG)
                        .commit();
                return true;
            case R.id.action_time_change:
                if (duration > 0) {
                    JumpToPosition jumpToPosition = new JumpToPosition();
                    Bundle bundle = new Bundle();
                    bundle.putInt(JumpToPosition.DURATION, duration);
                    bundle.putInt(JumpToPosition.POSITION, position);
                    jumpToPosition.setArguments(bundle);
                    jumpToPosition.show(getFragmentManager(), "timePicker");
                }
                return true;
            case R.id.action_sleep:
                SleepDialog sleepDialog = new SleepDialog();
                sleepDialog.show(getFragmentManager(), "sleep_timer");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onDestroy() {
        bcm.unregisterReceiver(updateGUIReceiver);

        super.onDestroy();
    }


    @Override
    public void onResume() {
        super.onResume();

        //starting the service
        Intent serviceIntent = new Intent(getActivity(), AudioPlayerService.class);
        serviceIntent.putExtra(AudioPlayerService.BOOK_ID, bookId);
        getActivity().startService(serviceIntent);

        Intent pokeIntent = new Intent(AudioPlayerService.CONTROL_POKE_UPDATE);
        pokeIntent.setAction(AudioPlayerService.CONTROL_POKE_UPDATE);
        bcm.sendBroadcast(pokeIntent);

        if (StateManager.getState() == PlayerStates.STARTED) {
            play_button.setImageResource(R.drawable.av_pause);
        } else {
            play_button.setImageResource(R.drawable.av_play);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(PlaybackService.GUI);
        filter.addAction(PlaybackService.GUI_SEEK);
        filter.addAction(PlaybackService.GUI_PLAY_ICON);
        filter.addAction(PlaybackService.GUI_MAKE_TOAST);
        bcm.registerReceiver(updateGUIReceiver, filter);
    }


    @Override
    public void onPause() {
        bcm.unregisterReceiver(updateGUIReceiver);
        super.onPause();
    }
}
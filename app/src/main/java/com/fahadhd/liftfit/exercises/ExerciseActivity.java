package com.fahadhd.liftfit.exercises;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import com.fahadhd.liftfit.MainActivity;
import com.fahadhd.liftfit.R;
import com.fahadhd.liftfit.SettingsActivity;
import com.fahadhd.liftfit.TrackerApplication;
import com.fahadhd.liftfit.UserNotes;
import com.fahadhd.liftfit.data.TrackerDAO;
import com.fahadhd.liftfit.sessions.Session;
import com.fahadhd.liftfit.sessions.SessionsFragment;
import com.fahadhd.liftfit.utilities.Constants;
import com.fahadhd.liftfit.utilities.Utility;

import java.util.ArrayList;

public class ExerciseActivity extends AppCompatActivity implements WorkoutDialog.Communicator {
    ExercisesFragment exercisesFragment;
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String SESSION_ID = "session_id";
    public static final String SESSION_NOTES = "session_notes";
    public static final String RECENT_POSITION_KEY = "recent_position";
    private String sessionNotes;
    TrackerDAO dao;
    public static long sessionID;
    ArrayList<Session> sessions;
    TrackerApplication application;
    /***********Toolbar Variables****************/

    /******************************************/

    /***********Snackbar variables**************/
    TimerService mTimerService;
    Intent timerIntent;
    View mySnackView; TextView timerView, snackbarText;
    Snackbar mySnackBar;
    long currentTime = 0L;
    boolean mServiceBound = false, durationUpdated = false, snackBarOn = false;
    boolean isServiceOn = false;
    /******************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercises);

        Toolbar toolbar = (Toolbar)findViewById(R.id.exercise_toolbar);
        setSupportActionBar(toolbar);

        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        application  = (TrackerApplication)this.getApplication();
        dao = application.getDatabase();
        sessions = application.getSessions();
        exercisesFragment = (ExercisesFragment) getSupportFragmentManager().
                findFragmentById(R.id.exercises_fragment);
        sessionID = exercisesFragment.sessionID;
        mySnackView = getLayoutInflater().inflate(R.layout.my_snackbar, null);
        timerView = (TextView) mySnackView.findViewById(R.id.timer);

        /**Creates a new exercise loader if one doesn't exist or refreshes the data if one does exist.**/
        if (this.getSupportLoaderManager().getLoader(R.id.exercise_loader_id) == null) {
            this.getSupportLoaderManager().initLoader(R.id.exercise_loader_id, null, exercisesFragment);
        } else {
            this.getSupportLoaderManager().restartLoader(R.id.exercise_loader_id, null, exercisesFragment);
        }

        /**Button in charge of adding workouts to the list_view. First displays a dialog for input**/
        ImageButton addWorkout = (ImageButton) findViewById(R.id.btn_add_workout);
        if (addWorkout != null) {
            addWorkout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WorkoutDialog dialog = new WorkoutDialog();
                    dialog.show(getFragmentManager(), "WorkoutDialog");
                }
            });
        }
        new GetNotes().execute();
    }

    @Override
    protected void onStart() {
        super.onStart();
        timerIntent = new Intent(this, TimerService.class);
        currentTime = 0L;
        //Binds to an existing running service
        if (Utility.isMyServiceRunning(TimerService.class,this)) {
            isServiceOn = true;
            Log.v(TAG,"Bound to service");
            bindService(timerIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            registerTimerReceiver();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present
        getMenuInflater().inflate(R.menu.menu_exercises, menu);
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(isServiceOn && mServiceBound) {
            unBindTimerService();
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                if(sessions.size() <= 1){
                    startActivity(new Intent(this,MainActivity.class));
                }
                else {
                    setResult(SessionsFragment.RECENT_POSITION, new Intent().putExtra(RECENT_POSITION_KEY, exercisesFragment.position));
                    finish();
                }
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this,SettingsActivity.class));
                return true;
            case R.id.action_notes:
                startActivity(new Intent(this,UserNotes.class).
                        putExtra(SESSION_ID,sessionID).
                        putExtra(SESSION_NOTES,sessionNotes));
                return true;
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    public void onBackPressed() {
        setResult(SessionsFragment.RECENT_POSITION, new Intent().putExtra(RECENT_POSITION_KEY, exercisesFragment.position));
        super.onBackPressed();
    }

    /************************ Workout Tasks Add, Update, Delete, add get notes ********************/

    //Receives workout info from dialog fragment and adds it into the database
    @Override
    public void addWorkoutInfo(String name, int weight, int max_sets, int max_reps) {
        //TODO: Store new workout in cached data and use that to instantly update view while
        //concurrently performing background task
        deactivateTemplates();
        Workout workoutInfo = new Workout(sessionID,name,weight,max_sets,max_reps, Constants.WORKOUT_TASK.ADD_WORKOUT);
        exercisesFragment.startWorkoutTask(workoutInfo);
    }

    @Override
    public void updateWorkoutInfo(Workout workout, String name, int weight, int maxSet, int maxRep) {
        Workout updatedWorkout = new Workout(workout.getSessionID(),workout.getWorkoutID(),
                name,weight,maxSet,maxRep,new ArrayList<Set>());

        if(!workout.equals(updatedWorkout)){
            workout.updateTask(Constants.WORKOUT_TASK.UPDATE_WORKOUT);
            exercisesFragment.startWorkoutTask(workout, updatedWorkout);
        }
    }


    @Override
    public void deleteWorkoutInfo(Workout workout) {
        deactivateTemplates();
        workout.updateTask(Constants.WORKOUT_TASK.DELETE_WORKOUT);
        exercisesFragment.startWorkoutTask(workout);
    }

    public void deactivateTemplates(){
        sessions.get(exercisesFragment.position).updateTemplateName("None");
        exercisesFragment.deactivateTemplates();
    }

    public class GetNotes extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... params) {
            sessionNotes = dao.getNotes(sessionID);
            return null;
        }
    }



    /****************************** Timer Snackbar ****************************************/

    /****************** BroadCast Receiver in charge of snackbar counter *****************/

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(mTimerService != null) {

                if (intent.getAction().equals(Constants.TIMER.TIMER_OFF) ||
                        (snackBarOn && mySnackBar!= null && !mySnackBar.isShown())) {
                    stopTimerService();
                    return;
                }
                if (!snackBarOn) {
                    if (mySnackBar == null) {
                        mySnackBar = initCustomSnackbar(mTimerService.getMessage());
                    }
                    mySnackBar.setDuration(1260000);
                    mySnackBar.show();
                    snackBarOn = true;
                }
                updateTimerUI();
            }
        }
    };

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data){

        Toast.makeText(this,"can reload here",Toast.LENGTH_LONG).show();
    }


    /****************** Service to bind exercise activity with timer service ****************/
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.MyBinder myBinder = (TimerService.MyBinder) service;
            mTimerService = myBinder.getService();
            mServiceBound = true;
        }
    };

    /******** Returns a custom snackbar to be used for the timer between sets **************/
    public Snackbar initCustomSnackbar(String message){
        View exerciseView;
        exerciseView = findViewById(R.id.exercises_list_main);
        if(exerciseView == null) return  null;
        final Snackbar snackbar = Snackbar.make(exerciseView, message, Snackbar.LENGTH_LONG);

        /**** Customizing snackbar view with my own.*****/
        LayoutInflater inflater =  getLayoutInflater();
        mySnackView = inflater.inflate(R.layout.my_snackbar, null);
        timerView = (TextView) mySnackView.findViewById(R.id.timer);
        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
        layout.setBackgroundColor(Color.GRAY);
        snackbarText = (TextView) snackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
        snackbarText.setTextSize(12f);
        //layout basically works like a list where you can add views at the top and also remove them.
        layout.addView(mySnackView, 0);
        /*********************************************/

        /////Makes the action button width smaller.////
        snackbar.setActionTextColor(Color.WHITE);
        Button action = (Button) snackbar.getView().findViewById(android.support.design.R.id.snackbar_action);
        ViewGroup.LayoutParams params= action.getLayoutParams();
        params.width= 150;
        action.setLayoutParams(params);
        //////////////////////////////////////////////

        /**Dismisses snackbar and stop service when user presses the X button.**/
        snackbar.setAction(R.string.dismiss, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimerService();
            }
        });

        return snackbar;
    }


    /************************ Helper methods for snackbar timer start *********************************/
    //Receives the current timer from the timer service broadcast and updates the UI
    public boolean updateTimerUI(){
        if(!durationUpdated && mTimerService.isDurationReached() && mySnackBar != null){
            //settings snackbar to a different color when timer has reached its duration
            Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) mySnackBar.getView();
            layout.setBackgroundColor(ContextCompat.getColor(this,R.color.orange_a400));
            snackbarText.setText(mTimerService.getMessage());
            durationUpdated = true;

        }
        //Updating the timer in the snackbar
        this.currentTime = mTimerService.getTimer();
        int secs = (int) (currentTime / 1000);
        int minutes = secs / 60;
        timerView.setText(Integer.toString(minutes) + ":" + String.format("%02d", secs%60));
        return true;
    }

    public void startTimerService(String message){
        if(isServiceOn && mTimerService != null){
            mTimerService.resetTimer(message);
            durationUpdated = false;
            if(mySnackBar != null){
                mySnackBar.dismiss();
                snackBarOn = false;
            }
        }
        else {
            timerIntent.setAction(Constants.ACTION.START_FOREGROUND_ACTION);
            timerIntent.putExtra(Constants.GENERAL.SESSION_ID, sessionID);
            timerIntent.putExtra(Constants.TIMER.TIMER_MSG, message);
            startService(timerIntent);
            isServiceOn = true;
            Log.v(TAG, "Bound to service");
            bindService(timerIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            registerTimerReceiver();
        }
        mySnackBar = initCustomSnackbar(message);
    }

    public void stopTimerService(){
        unBindTimerService();
        if(isServiceOn) {
            timerIntent.setAction(Constants.ACTION.STOP_FOREGROUND_ACTION);
            startService(timerIntent);
            unregisterReceiver(broadcastReceiver);
            isServiceOn = false;
        }
        if(mySnackBar != null){mySnackBar.dismiss(); snackBarOn = false;}
        durationUpdated = false;
    }


    public void registerTimerReceiver(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.TIMER.TIMER_RUNNING);
        intentFilter.addAction(Constants.TIMER.TIMER_OFF);
        registerReceiver(broadcastReceiver, intentFilter);
    }


    public void unBindTimerService(){
        if(mServiceBound) {
            Log.v(TAG,"UnBounded from service");
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }



    /*********************** Helper methods for snackbar timer end ***************************/
}

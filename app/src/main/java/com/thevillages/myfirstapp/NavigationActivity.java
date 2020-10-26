package com.thevillages.myfirstapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.esri.arcgisruntime.UnitSystem;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.ImmutablePartCollection;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.ArcGISMapImageLayer;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.location.LocationDataSource;
import com.esri.arcgisruntime.location.LocationDataSource.Location;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.navigation.DestinationStatus;
import com.esri.arcgisruntime.navigation.RouteTracker;
import com.esri.arcgisruntime.navigation.TrackingStatus;
import com.esri.arcgisruntime.navigation.VoiceGuidance;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;
//import com.esri.arcgisruntime.toolkit.compass.Compass;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Long;

import static com.esri.arcgisruntime.navigation.RouteTracker.*;

public class NavigationActivity extends AppCompatActivity {
    private final String TAG = NavigationActivity.class.getSimpleName();
    private MapView mMapView;
    private LocationDisplay mLocationDisplay;
    private GraphicsOverlay mGraphicsOverlay;
    private RouteTask routeTask;
    private RouteTracker routeTracker;
    private RouteResult mRouteResult;
    private  TextToSpeech textToSpeech;
    private boolean isTextToSpeechInitialized = false;
    private VoiceGuidance mVoiceGuidance;
    private double mDirection = 0.0;
    private EditText infolbl;
    //private Compass mCompass;
    private int offRouteCount = 0;
    private NewVoiceGuidanceListener voiceGuidanceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        infolbl = findViewById(R.id.infoText);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Starting navication action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                startNavigation();
            }
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            // initialize text-to-speech to replay navigation voice guidance
            textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if(status != TextToSpeech.ERROR) {
                        textToSpeech.setLanguage(Locale.US);
                        isTextToSpeechInitialized = true;
                    }
                }
            });

            //set up mapview
            mMapView = findViewById(R.id.mapNavView1);
            String  mapService = "https://arc7.thevillages.com/arcgis/rest/services/PUBLICMAP26/MapServer/";

            String basemap = "http://services.arcgisonline.com/arcgis/rest/services/USA_Topo_Maps/MapServer";

            ArcGISTiledLayer layer = new ArcGISTiledLayer(basemap);
            ArcGISTiledLayer layer2 = new ArcGISTiledLayer(mapService);
            ArcGISMapImageLayer censusLayer = new ArcGISMapImageLayer("http://sampleserver6.arcgisonline.com/arcgis/rest/services/Census/MapServer");

            //Basemap map =  new Basemap(layer);
            ArcGISMap map = new ArcGISMap();
            map.getBasemap().getBaseLayers().add(layer2);
            //map.getBasemap().getBaseLayers().add(censusLayer);
            mMapView.setMap(map);
            mGraphicsOverlay = new  GraphicsOverlay();
            mMapView.getGraphicsOverlays().add(mGraphicsOverlay);

            // throws an error on rotation
            //mCompass = findViewById(R.id.compass); // new Compass(mMapView.getContext());
            //mCompass.setAutoHide(false);
            //mCompass.addToGeoView(mMapView); // use with new compass
            //mCompass.bindTo(mMapView);;

            // generate route with direction and stops for navigation
            String routeUrl = getResources().getString(R.string.car_route_service );
            routeTask = new RouteTask(this, routeUrl);

            setupLocationDisplay();

        } catch (Exception ex) {
            Toast.makeText(this, "Navigation initialization failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, ex.getMessage());
        }

       // add mapview navagation change handler

    } // end onCreate

    private void setupLocationDisplay() {
        mLocationDisplay = mMapView.getLocationDisplay();

        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {

            if (dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() == null) {
                LocationDataSource lds = mLocationDisplay.getLocationDataSource();
                lds.addHeadingChangedListener(headingChangedEvent -> {

                        double head = headingChangedEvent.getHeading();
                        Log.d(TAG, "my heading is " + head);

                        double course = mLocationDisplay.getLocation().getCourse();
                        String h = "My heading : " + Math.round(head) + " Course: " + course;
                        infolbl.setText(h);

                        //View contextView = findViewById(R.id.fab);
                        //Snackbar.make(contextView, "My heading : " + head, Snackbar.LENGTH_LONG)
                        //       .setAction("Action", null).show();

                });
                return;
            }

            int requestPermissionsCode = 2;
            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

            if (!(ContextCompat.checkSelfPermission(NavigationActivity.this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(NavigationActivity.this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(NavigationActivity.this, requestPermissions, requestPermissionsCode);
            } else {
                String message = String.format("Error in DataSourceStatusChangedListener: %s",
                        dataSourceStatusChangedEvent.getSource().getLocationDataSource().getError().getMessage());
                Toast.makeText(NavigationActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });

        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);

        mLocationDisplay.startAsync();

    }

    private void speakNavigationInstructions() {
        if (isTextToSpeechInitialized && !textToSpeech.isSpeaking()) {
            VoiceGuidance voiceGuidance = routeTracker.generateVoiceGuidance();

            if (voiceGuidance == null){
                return;
            }
            if (mVoiceGuidance != null)
                if ( voiceGuidance.getText().equals(mVoiceGuidance.getText())) {
                    return;
                }

            mVoiceGuidance = voiceGuidance;
            Log.d(TAG, voiceGuidance.getText());
            infolbl.setText(voiceGuidance.getText(), TextView.BufferType.NORMAL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null);
            }

        }
    }

    private List<Stop> getStops(){
        List<Stop> stops = new ArrayList<Stop>(2) ;
        // palmers country club   -81.993316, 28.910335, cane garden cc 28.893097, -81.994359 from google map
        Point palmers = new  Point (-81.993316, 28.910335, SpatialReferences.getWgs84());
        Point tsg = new Point(-81.974829, 28.908085, SpatialReferences.getWgs84());
        Point walmart = new Point(-82.029751, 28.931747, SpatialReferences.getWgs84());
        Point caneGarden = new Point(-81.992341, 28.892343, SpatialReferences.getWgs84());
        Point lakeshorepool = new Point(-81.971427, 28.910289, SpatialReferences.getWgs84());
        Point currentLocation = mLocationDisplay.getMapLocation();

        stops.add(new Stop(currentLocation));
        stops.add(new Stop(palmers));
        //stops.add(new Stop(caneGarden));
        return stops;
    }

    private void startNavigation(){

        // setup route navigation
        routeTask.loadAsync();

        routeTask.addDoneLoadingListener(() -> {
            if (routeTask.getLoadError() == null && routeTask.getLoadStatus() == LoadStatus.LOADED) {
                //final ListenableFuture<RouteParameters>
                try {
                    RouteParameters routeParameters = routeTask.createDefaultParametersAsync().get();

                    routeParameters.setStops(getStops());
                    routeParameters.setReturnDirections(true);
                    routeParameters.setReturnStops(true);
                    routeParameters.setDirectionsLanguage("en");
                    ;
                    mRouteResult = routeTask.solveRouteAsync(routeParameters).get();

                    Polyline routeGeometry = mRouteResult.getRoutes().get(0).getRouteGeometry();

                   if(mLocationDisplay.isStarted()){
                       mLocationDisplay.stop();
                       mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                       mLocationDisplay.setNavigationPointHeightFactor(0.2f);

                       Toast.makeText(this, "Stopped location service",Toast.LENGTH_SHORT).show();
                       // for demo testing
                       mLocationDisplay.setLocationDataSource(new RouteLocationDataSource(routeGeometry, this));
                    }

                    // show the route as a graphic in the map
                    Graphic routeGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f));
                    mGraphicsOverlay.getGraphics().clear();
                    mGraphicsOverlay.getGraphics().add(routeGraphic);
                    mMapView.setViewpointGeometryAsync(routeGeometry.getExtent());

                    // set routetracker
                    routeTracker = new RouteTracker(this, mRouteResult, 0);
                    routeTracker.setVoiceGuidanceUnitSystem(UnitSystem.IMPERIAL);
                    // not available to 100.7.0
                    //routeTracker.enableReroutingAsync(routeTask, routeParameters, RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, true).get();

                    // listen for location changes
                    mLocationDisplay.addLocationChangedListener(new LocationDisplay.LocationChangedListener() {
                        @Override
                        public void onLocationChanged(LocationDisplay.LocationChangedEvent locationChangedEvent) {

                            Location currentLocation = locationChangedEvent.getLocation();

                            mDirection = currentLocation.getCourse();
                            double speed = currentLocation.getVelocity();
                            Log.d(TAG, "Direction degrees: " + mDirection);
                            Log.d(TAG, "speed : " + speed);

                            // rotate the view
                            double courseDirection = currentLocation.getCourse();
                            if (courseDirection > 0.0d && courseDirection != mDirection){
                                mDirection = courseDirection;
                                Toast.makeText(NavigationActivity.this, "Set rotation " + mDirection, Toast.LENGTH_LONG ).show();

                                mMapView.setViewpointRotationAsync(mDirection);
                            }

                            routeTracker.trackLocationAsync(currentLocation)
                                    .addDoneListener(new Runnable() {
                                        @Override
                                        public void run() {
                                            //speakNavigationInstructions();
                                            View contextView = findViewById(R.id.fab);
                                            String msg = "";
                                            if (routeTracker.getTrackingStatus().isOnRoute()){
                                                msg = "On route, you're doing great";
                                            } else
                                            {
                                              msg = "You are NOT on route";
                                              offRouteCount++;
                                              if (offRouteCount > 3)
                                                  refreshRouteNavigation();
                                            }
                                            Snackbar.make(contextView, msg , Snackbar.LENGTH_LONG)
                                                    .setAction("Action", null).show();
                                        }
                                    });

                        }
                    });
                    voiceGuidanceListener = newVoiceGuidanceEvent -> {

                        VoiceGuidance voiceGuidance = newVoiceGuidanceEvent.getVoiceGuidance();
                        infolbl.setText(voiceGuidance.getText(), TextView.BufferType.NORMAL);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
                        } else {
                            textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null);
                        }
                    };
                    routeTracker.addNewVoiceGuidanceListener(voiceGuidanceListener);

                    routeTracker.addTrackingStatusChangedListener(new TrackingStatusChangedListener() {
                        @Override
                        public void onTrackingStatusChanged(TrackingStatusChangedEvent trackingStatusChangedEvent) {
                            TrackingStatus status = trackingStatusChangedEvent.getTrackingStatus();

                            if (status.getDestinationStatus() == DestinationStatus.REACHED){
                                routeTracker.switchToNextDestinationAsync()
                                        .addDoneListener(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (status.getDestinationStatus() == DestinationStatus.REACHED){
                                                    mLocationDisplay.stop();
                                                }
                                                Log.d(TAG, "Switch to next distination runnable");
                                            }
                                        });
                            }

                            Log.d(TAG, "status = " + status.getRouteProgress().getRemainingTime());
                        }
                    });

                    mLocationDisplay.startAsync();


                } catch (Exception et) {
                    Toast.makeText(this, "Navigation failed: " + et.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, et.getCause().getMessage());
                }

            } else {
                Toast.makeText(this, "Unable to load RouteTask " + routeTask.getLoadStatus().toString(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Unable to load RouteTask " + routeTask.getLoadStatus().toString());
            }

        });

    } // end start navigation
    // called when ouff route more than x times.
    private void refreshRouteNavigation(){
        offRouteCount = 0;
        routeTracker.removeNewVoiceGuidanceListener(voiceGuidanceListener);

        Toast.makeText(this, "Starting reroute ",Toast.LENGTH_LONG).show();

        //startNavigation();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
        textToSpeech.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
       // super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mLocationDisplay.startAsync();
        } else {
            Toast.makeText(NavigationActivity.this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * A LocationDataSource that simulates movement along the specified route. Upon start of the RouteLocationDataSource,
     * a timer is started, which updates the location along the route at fixed intervals.
     *
     * @since 100.6.0
     */
    private class RouteLocationDataSource extends LocationDataSource
    {
        private Point currentLocation;
        private Timer mTimer;
        private Long distance = 0L;
        private Long distanceInterval = 40L;
        private Polyline mRoute;
        private Context mContext;
        private ImmutablePartCollection parts;


        public RouteLocationDataSource ( Polyline route, Context context){
            mRoute = route;
            mContext = context;

        }

        @Override
        protected void onStart() {
            TimerTask tTask = new TimerTask(){
                public void run(){
                    currentLocation = GeometryEngine.createPointAlong(mRoute, distance);

                    updateLocation(new Location(currentLocation));
                    distance += distanceInterval;

                    // stop the LocationDataSource at the end of the route so no more location updates will occur,
                    // which will stop the voice guidance instructions.
                    if (GeometryEngine.createPointAlong(mRoute, distance) == parts.get(0).getEndPoint()) {
                        stop();
                    }
                }
            };

            Throwable error = null;
            try {
                // start route
                parts = mRoute.getParts();
                currentLocation = parts.get(0).getStartPoint();
                updateLocation( new Location(currentLocation, 1.0, 1.0, 10.0, 0.0, false, Calendar.getInstance()));

                mTimer = new Timer("RouteLocationDataSource Timer", false);
                mTimer.schedule(tTask,3000, 1000 ) ;


            } catch (Exception t){
                error = t;
            }
            onStartCompleted(error);
        }

        @Override
        protected void onStop() {
            mTimer.cancel();
        }
    }
}

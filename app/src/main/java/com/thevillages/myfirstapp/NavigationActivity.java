package com.thevillages.myfirstapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.ImmutablePartCollection;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.location.LocationDataSource;
import com.esri.arcgisruntime.location.LocationDataSource.Location;
import com.esri.arcgisruntime.location.RouteTrackerLocationDataSource;
import com.esri.arcgisruntime.location.SimulatedLocationDataSource;
import com.esri.arcgisruntime.location.SimulationParameters;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Long;
import java.util.concurrent.ExecutionException;

import static com.esri.arcgisruntime.navigation.RouteTracker.*;

public class NavigationActivity extends AppCompatActivity {
    private SimulatedLocationDataSource mSimulatedLocationDataSource;
    private TextToSpeech mTextToSpeech;
    private boolean isTextToSpeechInitialized = false;

    private MapView mMapView;
    private RouteTracker mRouteTracker;
    private Graphic mRouteAheadGraphic;
    private FloatingActionButton mRecenterButton;

    private NewVoiceGuidanceListener voiceGuidanceListener;

    public NavigationActivity(){
        ArcGISRuntimeEnvironment.setLicense("runtimebasic,1000,rud000252796,none,MJJ47AZ7G349NERL1216");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        mMapView = findViewById(R.id.mapView);
        ArcGISMap map = new ArcGISMap(Basemap.createOpenStreetMap());
        mMapView.setMap(map);

        // initialize text-to-speech to replay navigation voice guidance
        mTextToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                mTextToSpeech.setLanguage(Locale.US);
                isTextToSpeechInitialized = true;
            }
        });

        //RouteTask routeTask = new RouteTask(this, getString(R.string.routing_service_url));
        RouteTask routeTask = new RouteTask(this, getString(R.string.car_route_service));

        //routeTask.loadAsync();

        routeTask.addDoneLoadingListener(() -> {

            if (routeTask.getLoadError() == null && routeTask.getLoadStatus() == LoadStatus.LOADED) {

                final ListenableFuture<RouteParameters> routeParametersFuture = routeTask.createDefaultParametersAsync();
                routeParametersFuture.addDoneListener(() -> {

                    try {

                        RouteParameters routeParameters = routeParametersFuture.get();
                        routeParameters.setStops(getStopsFirstRoute());
                        routeParameters.setReturnDirections(true);
                        routeParameters.setReturnStops(true);
                        routeParameters.setReturnRoutes(true);
                        ListenableFuture<RouteResult> routeResultFuture = routeTask.solveRouteAsync(routeParameters);

                        routeParametersFuture.addDoneListener(() -> {

                            try {

                                RouteResult routeResult = routeResultFuture.get();
                                Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();
                                Graphic routeGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5f));

                                GraphicsOverlay mGraphicsOverlay = new GraphicsOverlay();
                                mGraphicsOverlay.setOpacity((float) 0.4);
                                mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
                                mGraphicsOverlay.getGraphics().add(routeGraphic);

                                mMapView.setViewpointAsync(new Viewpoint(routeGeometry.getExtent()));

                                RouteParameters routeParameters_ = routeParametersFuture.get();
                                routeParameters_.setStops(getStopsSecondRoute());
                                routeParameters_.setReturnDirections(true);
                                routeParameters_.setReturnStops(true);
                                routeParameters_.setReturnRoutes(true);
                                ListenableFuture<RouteResult> routeResultFuture_ = routeTask.solveRouteAsync(routeParameters_);

                                routeParametersFuture.addDoneListener(() -> {

                                    try {

                                        RouteResult routeResult_ = routeResultFuture_.get();
                                        Polyline routeGeometry_ = routeResult_.getRoutes().get(0).getRouteGeometry();
                                        Graphic routeGraphic_ = new Graphic(routeGeometry_, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 5f));

                                        mGraphicsOverlay.getGraphics().add(routeGraphic_);

                                        Envelope e = com.esri.arcgisruntime.geometry.GeometryEngine.combineExtents(routeGraphic.getGeometry(), routeGraphic_.getGeometry());
                                        mMapView.setViewpointGeometryAsync(e, 30);

                                        FloatingActionButton navigateRouteButton = findViewById(R.id.navigateRouteButton);
                                        navigateRouteButton.setOnClickListener(v -> startNavigation(routeTask, routeResult, routeParameters, routeResult_, routeParameters_));

                                    } catch (ExecutionException | InterruptedException e) {
                                        String error = "Error creating default route parameters: " + e.getMessage();
                                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                                        Log.e("case02665010", error);
                                    }

                                });

                            } catch (ExecutionException | InterruptedException e) {
                                String error = "Error creating default route parameters: " + e.getMessage();
                                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                                Log.e("case02665010", error);
                            }

                        });

                    } catch (ExecutionException | InterruptedException e) {
                        String error = "Error creating default route parameters: " + e.getMessage();
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        Log.e("case02665010", error);
                    }

                });


            }

        });

        mRecenterButton = findViewById(R.id.recenterButton);
        mRecenterButton.setEnabled(false);
        mRecenterButton.setOnClickListener(v -> {
            mMapView.getLocationDisplay().setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
            mRecenterButton.setEnabled(false);
        });


    } // end onCreate

//    private void setupLocationDisplay() {
//        mLocationDisplay = mMapView.getLocationDisplay();
//
//        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {
//
//            if (dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() == null) {
//                LocationDataSource lds = mLocationDisplay.getLocationDataSource();
//                lds.addHeadingChangedListener(headingChangedEvent -> {
//
//                        double head = headingChangedEvent.getHeading();
//                        Log.d(TAG, "my heading is " + head);
//
//                        double course = mLocationDisplay.getLocation().getCourse();
//                        String h = "My heading : " + Math.round(head) + " Course: " + course;
//                        infolbl.setText(h);
//
//                        //View contextView = findViewById(R.id.fab);
//                        //Snackbar.make(contextView, "My heading : " + head, Snackbar.LENGTH_LONG)
//                        //       .setAction("Action", null).show();
//
//                });
//                return;
//            }
//
//            int requestPermissionsCode = 2;
//            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
//
//            if (!(ContextCompat.checkSelfPermission(NavigationActivity.this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
//                    && ContextCompat.checkSelfPermission(NavigationActivity.this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
//                ActivityCompat.requestPermissions(NavigationActivity.this, requestPermissions, requestPermissionsCode);
//            } else {
//                String message = String.format("Error in DataSourceStatusChangedListener: %s",
//                        dataSourceStatusChangedEvent.getSource().getLocationDataSource().getError().getMessage());
//                Toast.makeText(NavigationActivity.this, message, Toast.LENGTH_LONG).show();
//            }
//        });
//
//        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);
//
//        mLocationDisplay.startAsync();
//
//    }

//    private void speakNavigationInstructions() {
//        if (isTextToSpeechInitialized && !textToSpeech.isSpeaking()) {
//            VoiceGuidance voiceGuidance = routeTracker.generateVoiceGuidance();
//
//            if (voiceGuidance == null){
//                return;
//            }
//            if (mVoiceGuidance != null)
//                if ( voiceGuidance.getText().equals(mVoiceGuidance.getText())) {
//                    return;
//                }
//
//            mVoiceGuidance = voiceGuidance;
//            Log.d(TAG, voiceGuidance.getText());
//            infolbl.setText(voiceGuidance.getText(), TextView.BufferType.NORMAL);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
//            } else {
//                textToSpeech.speak(voiceGuidance.getText(), TextToSpeech.QUEUE_FLUSH, null);
//            }
//
//        }
//    }


    private void startNavigation(RouteTask routeTask, RouteResult firstRouteResult, RouteParameters firstRouteParameters, RouteResult secondRouteResult, RouteParameters secondRouteParameters) {

        mRouteAheadGraphic = new Graphic(firstRouteResult.getRoutes().get(0).getRouteGeometry(),
                new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.YELLOW, 5f));

        GraphicsOverlay mGraphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
        mGraphicsOverlay.getGraphics().add(mRouteAheadGraphic);

        LocationDisplay locationDisplay = mMapView.getLocationDisplay();

        mSimulatedLocationDataSource = new SimulatedLocationDataSource();
        SimulationParameters simulationParameters_ = new SimulationParameters(Calendar.getInstance(), 35, 5, 5);
        mSimulatedLocationDataSource.setLocations(firstRouteResult.getRoutes().get(0).getRouteGeometry(), simulationParameters_);

        mRouteTracker = new RouteTracker(getApplicationContext(), secondRouteResult, 0, true);
        mRouteTracker.enableReroutingAsync(routeTask, secondRouteParameters, RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, true);
        RouteTrackerLocationDataSource routeTrackerLocationDataSource_ = new RouteTrackerLocationDataSource(mRouteTracker, mSimulatedLocationDataSource);
        locationDisplay.setLocationDataSource(routeTrackerLocationDataSource_);
        locationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        locationDisplay.addAutoPanModeChangedListener(autoPanModeChangedEvent -> mRecenterButton.setEnabled(true));

        locationDisplay.addLocationChangedListener(locationChangedEvent -> {
            TrackingStatus trackingStatus = mRouteTracker.getTrackingStatus();
            mRouteAheadGraphic.setGeometry(trackingStatus.getRouteProgress().getRemainingGeometry());
        });

        locationDisplay.startAsync();
        //Toast.makeText(this, "Navigating...", Toast.LENGTH_LONG).show();

    }

    private List<Stop> getStops(){
        List<Stop> stops = new ArrayList<Stop>(2) ;
        // palmers country club   -81.993316, 28.910335, cane garden cc 28.893097, -81.994359 from google map
        Point palmers = new  Point (-81.993316, 28.910335, SpatialReferences.getWgs84());
        Point tsg = new Point(-81.974829, 28.908085, SpatialReferences.getWgs84());
        Point walmart = new Point(-82.029751, 28.931747, SpatialReferences.getWgs84());
        Point caneGarden = new Point(-81.992341, 28.892343, SpatialReferences.getWgs84());
        Point lakeshorepool = new Point(-81.971427, 28.910289, SpatialReferences.getWgs84());
        //Point currentLocation = mLocationDisplay.getMapLocation();

        stops.add(new Stop(lakeshorepool));
        stops.add(new Stop(palmers));
        //stops.add(new Stop(caneGarden));
        return stops;
    }

    private static List<Stop> getStopsFirstRoute() {

        List<Stop> stops = new ArrayList<>(2);

        // National Air and Space Museum
        //Stop airAndSpaceMuseum  = new Stop(new Point(-77.0196932, 38.8875456, SpatialReferences.getWgs84()));
        Stop lakeShorePool = new Stop( new Point(-81.971427, 28.910289, SpatialReferences.getWgs84()));
        stops.add(lakeShorePool);

        // National Gallery of Art
        Stop  palmers = new Stop( new  Point (-81.993316, 28.910335, SpatialReferences.getWgs84()));
        stops.add(palmers);

        // Washington Union Station
        //Stop unionStation = new Stop(new Point(-77.0063628, 38.8969139, SpatialReferences.getWgs84()));
        Stop walmart = new Stop( new Point(-82.029751, 28.931747, SpatialReferences.getWgs84()));
        stops.add(walmart);

        return stops;

    }

    private static List<Stop> getStopsSecondRoute() {

        List<Stop> stops = new ArrayList<>(2);

        // National Air and Space Museum
        //Stop airAndSpaceMuseum  = new Stop(new Point(-77.0196932, 38.8875456, SpatialReferences.getWgs84()));
        Stop lakeShorePool = new Stop( new Point(-81.971427, 28.910289, SpatialReferences.getWgs84()));
        stops.add(lakeShorePool);

        // National Geographic Museum
        //Stop natGeoMuseum = new Stop(new Point(-77.0384956, 38.9044580, SpatialReferences.getWgs84()));
        Point caneGarden = new Point(-81.992341, 28.892343, SpatialReferences.getWgs84());
        stops.add(new Stop(caneGarden));

        return stops;
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

    }



    /**
     * A LocationDataSource that simulates movement along the specified route. Upon start of the RouteLocationDataSource,
     * a timer is started, which updates the location along the route at fixed intervals.
     *
     * @since 100.6.0
     */
    private static class RouteLocationDataSource extends LocationDataSource
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

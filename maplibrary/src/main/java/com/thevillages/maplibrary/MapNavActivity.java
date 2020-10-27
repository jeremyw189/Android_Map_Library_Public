package com.thevillages.maplibrary;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.UnitSystem;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
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
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.DirectionManeuver;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import info.androidhive.fontawesome.FontDrawable;


public class MapNavActivity extends AppCompatActivity {
    private final String TAG = MapNavActivity.class.getSimpleName();
    private MapView mMapView;
    private RouteTracker mRouteTracker;
    private RouteTask mRouteTask;
    private LocationDisplay mLocationDisplay;
    private Graphic mRouteAheadGraphic;
    private Graphic mRouteTraveledGraphic;
    private GraphicsOverlay mGraphicsOverlay;
    private MenuItem mRecenterButton;
    private RouteParameters mRouteParameters;
    private TextToSpeech mTextToSpeech;
    private boolean mIsTextToSpeechInitialized = false;
    private TextView infoLbl;
    private TextView directionLbl;
    private TextView distanceLbl;
    private Point mDestination;
    private Point mCurrentLocation;
    private RouteTracker.NewVoiceGuidanceListener myNewVoiceGuidanceListener;
    private RouteTracker.TrackingStatusChangedListener myTrackingStatusChangedListener;

    private List<DirectionManeuver> mDirections;
    private TravelType mTravelType;

    public MapNavActivity(){
        ArcGISRuntimeEnvironment.setLicense("runtimebasic,1000,rud000252796,none,MJJ47AZ7G349NERL1216");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_nav);
        initFontIcons();
        // setup toolbar
        Toolbar myToolBar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolBar);
        // show the back arrow.
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // get a reference to navigation text views
        directionLbl = findViewById(R.id.directionText);
        distanceLbl = findViewById(R.id.distanceText);
        infoLbl = findViewById(R.id.timeRemainingText);

        // values pass from the villages mobile app
        if (this.getIntent().getExtras() != null) {
            double GISLat = getIntent().getDoubleExtra("GISLAT",0 );
            double GISLong = getIntent().getDoubleExtra("GISLONG", 0);
            int travel = getIntent().getIntExtra("TRAVEL_TYPE", 0);
            String address = getIntent().getStringExtra("ADDRESS");
            if (address != null)
                infoLbl.setText("Destination: " + address );
            mTravelType = (travel == 0) ? TravelType.CAR : TravelType.GOLF_CART;
            mDestination = new Point( GISLong, GISLat, SpatialReferences.getWgs84());
        }


        mMapView = findViewById(R.id.myMapView);
        String  mapService = getResources().getString(R.string.map_service);
        ArcGISTiledLayer layer = new ArcGISTiledLayer(mapService);
        Basemap basemap =  new Basemap(layer);
        ArcGISMap map = new ArcGISMap(basemap);
        mMapView.setMap(map);

        map.addDoneLoadingListener(this::setLocationDisplay);
        map.loadAsync();

        // create a graphics overlay to hold our route graphics
        createGraphicsOverlay();

        // initialize text-to-speech to replay navigation voice guidance
        mTextToSpeech = new TextToSpeech(this, status -> {
            if(status != TextToSpeech.ERROR) {
                mTextToSpeech.setLanguage(Locale.US);
                mIsTextToSpeechInitialized = true;
            }
        });

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //LocationProvider gpsProvider =  lm.getProvider(LocationManager.GPS_PROVIDER);
        final boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled){
            //create intent and open activity
            enableLocationSettings();
        }

    } // end onCreate

    private void initFontIcons(){
        //Typeface fontAwesome = Typeface.createFromAsset(getAssets(), "fonts/fa-solid-900.ttf");
        ImageButton fab = findViewById(R.id.fab);
        FontDrawable mapIcon = new FontDrawable(this, R.string.fa_map_marked_alt_solid, true, false);
        mapIcon.setTextSize(18);
        mapIcon.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        fab.setImageDrawable(mapIcon);
        fab.setBackgroundColor(Color.RED);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.toolbar_route) {
            mMapView.getLocationDisplay().setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
            return true;
        }
        if (item.getItemId() == R.id.toolbar_directions) {
            View view = item.getActionView();
            if (view == null)
                return true;

            infoLbl.setText("TODO show directions dialog");
            return true;
        }
        if (item.getItemId() == R.id.toolbar_stop) {
            mLocationDisplay.stop();
            return true;
        }

        finish();

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.nav_menu, menu);
        mRecenterButton  = menu.findItem(R.id.toolbar_route);
        FontDrawable drawable = new FontDrawable(this, R.string.fa_route_solid, true, false);
        drawable.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        mRecenterButton.setIcon(drawable);

        MenuItem dirMenuItem = menu.findItem(R.id.toolbar_directions);
        drawable = new FontDrawable(this, R.string.fa_map_signs_solid, true, false);
        drawable.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        dirMenuItem.setIcon(drawable);

        MenuItem stopMenuItem = menu.findItem(R.id.toolbar_stop);
        drawable = new FontDrawable(this, R.string.fa_traffic_light_solid, true, false);
        drawable.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        stopMenuItem.setIcon(drawable);


        return true;
        //return super.onCreateOptionsMenu(menu);
    }

    private void enableLocationSettings(){
        Intent settingsIntent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
        startActivity(settingsIntent);
    }

    private void setLocationDisplay() {
        mLocationDisplay = mMapView.getLocationDisplay();

        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {
            if (dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() == null) {
                String routeUrl = (mTravelType == TravelType.CAR) ? getResources().getString(R.string.car_route_service )
                        : getResources().getString(R.string.golf_route_service);

                findRoute(routeUrl);
                return;
            }

            // requrst location permissions
            int requestPermissionsCode = 2;
            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

            if (!(ContextCompat.checkSelfPermission(this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, requestPermissions, requestPermissionsCode);
            } else {
                 String message = String.format("Error in DataSourceStatusChangedListener: %s",
                         dataSourceStatusChangedEvent.getSource().getLocationDataSource().getError().getMessage());
                 Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
        mMapView.getLocationDisplay().startAsync();
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.OFF);

    }

    private void findRoute(String routeService) {
        if (mMapView.getLocationDisplay().isStarted()) {
            mCurrentLocation = mLocationDisplay.getMapLocation();
        }

        mRouteTask =  new RouteTask(getApplicationContext(), routeService);
        mRouteTask.loadAsync();
        mRouteTask.addDoneLoadingListener(() -> {

            if (mRouteTask.getLoadError() == null && mRouteTask.getLoadStatus() == LoadStatus.LOADED) {
                // route task has loaded successfully

                final ListenableFuture<RouteParameters> routeParametersFuture = mRouteTask.createDefaultParametersAsync();
                routeParametersFuture.addDoneListener(() -> {

                    try {
                        RouteParameters routeParameters = routeParametersFuture.get();
                        routeParameters.setStops(getStops());
                        routeParameters.setReturnDirections(true);
                        routeParameters.setReturnStops(true);
                        routeParameters.setReturnRoutes(true);
                        mRouteParameters = routeParameters;
                        ListenableFuture<RouteResult> routeResultFuture = mRouteTask.solveRouteAsync(routeParameters);
                        routeParametersFuture.addDoneListener(() -> {
                            try {
                                // get the route geometry from the route result
                                RouteResult routeResult = routeResultFuture.get();
                                mDirections =  routeResult.getRoutes().get(0).getDirectionManeuvers();
                                Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();
                                // create a graphic for the route geometry
                                mRouteAheadGraphic = new Graphic(routeGeometry,
                                        new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.MAGENTA, 5f));

                                mGraphicsOverlay.getGraphics().add(mRouteAheadGraphic);

                                // add it to the graphics overlay
                                setEndMarker(mDestination);
                                setStartMarker(mCurrentLocation);
                                // set the map view view point to show the whole route
                                mMapView.setViewpointAsync(new Viewpoint(routeGeometry.getExtent()));

                                // create a button to start navigation with the given route
                                ImageButton navigateRouteButton = findViewById(R.id.fab);
                                navigateRouteButton.setOnClickListener(v -> startNavigation(routeParameters, routeResult));

                            } catch (ExecutionException | InterruptedException e) {
                                String error = "Error creating default route parameters: " + e.getMessage();
                                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                                Log.e(TAG, error);
                            }
                        });

                    } catch (InterruptedException | ExecutionException e) {
                        //showError("Cannot create RouteTask parameters " + e.getMessage());
                        infoLbl.setText(e.getMessage());
                        Toast.makeText(this, "Cannot create RouteTask parameters " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                if (mRouteTask.getLoadStatus() == LoadStatus.FAILED_TO_LOAD){
                    ArcGISRuntimeException error = mRouteTask.getLoadError();
                    infoLbl.setText(error.getMessage());
                    directionLbl.setText(error.getAdditionalMessage());
                    try {
                        assert error.getCause() != null;
                        throw error.getCause();
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
            }
        });
    }

    private void startNavigation(RouteParameters routeParameters, RouteResult routeResult) {
        //WindowManager wm = getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // set up a RouteTracker for navigation along the calculated route
        mRouteTracker = new RouteTracker(getApplicationContext(), routeResult, 0, true);

        // listen for new voice guidance events
        mRouteTracker.setVoiceGuidanceUnitSystem(UnitSystem.IMPERIAL);
        mRouteTracker.addNewVoiceGuidanceListener(getNewVoiceGuidanceListener());

        // create a graphic (solid) to represent the route that's been traveled (initially empty)
        createGraphicsOverlay();
        mRouteAheadGraphic = new Graphic(routeResult.getRoutes().get(0).getRouteGeometry(),
                new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.MAGENTA, 5f));
        mGraphicsOverlay.getGraphics().add(mRouteAheadGraphic);
        mRouteTraveledGraphic = new Graphic(routeResult.getRoutes().get(0).getRouteGeometry(),
                new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f));
        mGraphicsOverlay.getGraphics().add(mRouteTraveledGraphic);

        mRouteTracker.addTrackingStatusChangedListener(getTrackingStatusChangedListener());

        if (mRouteTask.getRouteTaskInfo().isSupportsRerouting()) {
             mRouteTracker.enableReroutingAsync(mRouteTask, routeParameters,
                    RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, false);
            // setup listener for rerouting
            infoLbl.setText(R.string.reroute_supported);
            mRouteTracker.addRerouteStartedListener(rerouteStartedEvent ->  {
                // remove graphics ?

                mRouteTracker.removeNewVoiceGuidanceListener(myNewVoiceGuidanceListener);
                mRouteTracker.removeTrackingStatusChangedListener(myTrackingStatusChangedListener);
            });
            mRouteTracker.addRerouteCompletedListener(rerouteCompletedEvent -> {
                mDirections = rerouteCompletedEvent.getTrackingStatus()
                                .getRouteResult().getRoutes().get(0).getDirectionManeuvers();
                mRouteTracker.addTrackingStatusChangedListener(getTrackingStatusChangedListener());
                mRouteTracker.addNewVoiceGuidanceListener(getNewVoiceGuidanceListener());
            });
        }

        // get the map view's location display
        mLocationDisplay = mMapView.getLocationDisplay();

        // get the route's geometry from the route result
        Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();
        // for demo
        // set up a simulated location data source which simulates movement along the route
        SimulatedLocationDataSource mSimulatedLocationDataSource = new SimulatedLocationDataSource();
        SimulationParameters simulationParameters = new SimulationParameters(Calendar.getInstance(), 35, 5, 5);
        mSimulatedLocationDataSource.setLocations(routeGeometry, simulationParameters);
        // create a route tracker location data source to snap the location display to the route
        RouteTrackerLocationDataSource routeTrackerLocationDataSource = new RouteTrackerLocationDataSource(mRouteTracker, mSimulatedLocationDataSource);

        // for production use device location data source
        //RouteTrackerLocationDataSource routeTrackerLocationDataSource = new RouteTrackerLocationDataSource(mRouteTracker, mLocationDisplay.getLocationDataSource());
        // set the route tracker location data source as the location data source for this app
        mLocationDisplay.setLocationDataSource(routeTrackerLocationDataSource);

        // if the user navigates the map view away from the location display, activate the recenter button
        mLocationDisplay.addAutoPanModeChangedListener(autoPanModeChangedEvent -> mRecenterButton.setEnabled(true));


        // start the LocationDisplay, which starts the RouteTrackerLocationDataSource and SimulatedLocationDataSource
        mLocationDisplay.startAsync();
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        Toast.makeText(getApplicationContext(), "Navigating to the first stop.", Toast.LENGTH_SHORT ).show();

    } // end start navigation

    private RouteTracker.TrackingStatusChangedListener  getTrackingStatusChangedListener() {
        myTrackingStatusChangedListener = new RouteTracker.TrackingStatusChangedListener() {
            @Override
            public void onTrackingStatusChanged(RouteTracker.TrackingStatusChangedEvent trackingStatusChangedEvent) {
                // get the route's tracking status
                TrackingStatus trackingStatus = trackingStatusChangedEvent.getTrackingStatus();

                // set geometries for the route ahead and the remaining route
                mRouteAheadGraphic.setGeometry(trackingStatus.getRouteProgress().getRemainingGeometry());
                mRouteTraveledGraphic.setGeometry(trackingStatus.getRouteProgress().getTraversedGeometry());

                // get remaining distance information
                TrackingStatus.Distance remainingDistance = trackingStatus.getDestinationProgress().getRemainingDistance();
                // covert remaining minutes to hours:minutes:seconds
                String remainingTimeString = DateUtils
                        .formatElapsedTime((long) (trackingStatus.getDestinationProgress().getRemainingTime() * 60));

                // update text views
                distanceLbl.setText(getString(R.string.distance_remaining, remainingDistance.getDisplayText(),
                        remainingDistance.getDisplayTextUnits().getPluralDisplayName()));
                infoLbl.setText(getString(R.string.time_remaining, remainingTimeString));

                // if a destination has been reached
                if (trackingStatus.getDestinationStatus() == DestinationStatus.REACHED) {
                    // if there are more destinations to visit. Greater than 1 because the start point is considered a "stop"
                    if (mRouteTracker.getTrackingStatus().getRemainingDestinationCount() > 1) {
                        // switch to the next destination
                        mRouteTracker.switchToNextDestinationAsync();
                        Toast.makeText(getApplicationContext(), "Navigating to the second stop.", Toast.LENGTH_LONG).show();
                    } else {
                        mMapView.getLocationDisplay().stop();
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        Toast.makeText(getApplicationContext(), "Arrived at the final destination.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };

        return myTrackingStatusChangedListener;
    }

    // listener
    private RouteTracker.NewVoiceGuidanceListener getNewVoiceGuidanceListener () {

        myNewVoiceGuidanceListener = new RouteTracker.NewVoiceGuidanceListener() {
            @Override
            public void onNewVoiceGuidance(RouteTracker.NewVoiceGuidanceEvent newVoiceGuidanceEvent) {
                // use Android's text to speech to speak the voice guidance
                String txt = newVoiceGuidanceEvent.getVoiceGuidance().getText();
                Log.d(TAG, "startNavigation: " + txt);
                speakVoiceGuidance(txt);
                directionLbl.setText(getString(R.string.next_direction, txt));
            }
        };
        return myNewVoiceGuidanceListener;
    }

    /**
     * Uses Android's text to speak to say the latest voice guidance from the RouteTracker out loud.
     */
    private void speakVoiceGuidance(String voiceGuidanceText) {
        if (mIsTextToSpeechInitialized && !mTextToSpeech.isSpeaking()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mTextToSpeech.speak(voiceGuidanceText, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                mTextToSpeech.speak(voiceGuidanceText, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private List<Stop> getStops() {
        List<Stop> stops =   new ArrayList<Stop>(2);
        if (mCurrentLocation == null || mCurrentLocation.getX() == 0.0){
            mMapView.getLocationDisplay().startAsync();
            mCurrentLocation = mLocationDisplay.getMapLocation();
        }
        stops.add(new Stop(mCurrentLocation));
        stops.add(new Stop(mDestination));

        return stops;
    }
    private void createGraphicsOverlay() {
        mGraphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().clear();
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
    }

    private void setMapMarker(Point location, SimpleMarkerSymbol.Style style, int markerColor, int outlineColor) {
        float markerSize = 8.0f;
        float markerOutlineThickness = 2.0f;
        SimpleMarkerSymbol pointSymbol = new SimpleMarkerSymbol(style, markerColor, markerSize);
        pointSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, outlineColor, markerOutlineThickness));
        Graphic pointGraphic = new Graphic(location, pointSymbol);
        mGraphicsOverlay.getGraphics().add(pointGraphic);
    }

    private void setStartMarker(Point location) {
        setMapMarker(location, SimpleMarkerSymbol.Style.DIAMOND, Color.rgb(226, 119, 40), Color.BLUE);
    }

    private void setEndMarker(Point location) {
        setMapMarker(location, SimpleMarkerSymbol.Style.SQUARE, Color.rgb(40, 119, 226), Color.RED);
    }

    @Override
    protected void onPause() {
        if(mMapView != null){
            mMapView.pause();
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mMapView != null) {
            mMapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (mMapView != null) {
            mMapView.dispose();
        }
        super.onDestroy();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
           //mLocationDisplay.startAsync();
        } else {
            Toast.makeText(this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
        }
    }
}



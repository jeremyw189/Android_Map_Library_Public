package com.thevillages.maplib;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.ActionBarContainer;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ActionMenuView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.UnitSystem;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.io.RequestConfiguration;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.location.AndroidLocationDataSource;
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
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.DirectionManeuver;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import info.androidhive.fontawesome.FontDrawable;


public class MapNavActivity extends AppCompatActivity {
    private static final String TAG = MapNavActivity.class.getSimpleName();
    private MapView mMapView;
    private RouteTracker mRouteTracker;
    private LocationDisplay mLocationDisplay;
    private Graphic mRouteAheadGraphic;
    private Graphic mRouteTraveledGraphic;
    private GraphicsOverlay mGraphicsOverlay;
    private ImageButton recenterButton;
    private TextToSpeech mTextToSpeech;
    private boolean mIsTextToSpeechInitialized = false;
    private TextView directionLbl;
    private MaterialTextView distanceLbl;
    private MaterialTextView timeLbl;
    private Stop mDestination;
    private Point mCurrentLocation;
    private RouteTracker.NewVoiceGuidanceListener myNewVoiceGuidanceListener;
    private RouteTracker.TrackingStatusChangedListener myTrackingStatusChangedListener;
    private List<DirectionManeuver> mDirections;
    private TravelType mTravelType;
    private boolean mImNavigating;

    public MapNavActivity(){
      //ArcGISRuntimeEnvironment.setLicense("runtimebasic,1000,rud000252796,none,MJJ47AZ7G349NERL1216");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ArcGISRuntimeEnvironment.setLicense(getString(R.string.runtime_license));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_nav);
        // keep device alive while navigating
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initFontIcons();
        // setup toolbar
        Toolbar myToolBar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolBar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(myToolBar);

        // show the back arrow.
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // get a reference to navigation text views
        directionLbl = findViewById(R.id.directionText);
        distanceLbl = findViewById(R.id.distanceLbl);
        timeLbl = findViewById(R.id.timeLbl);

        // values pass from the villages mobile app
        if (this.getIntent().getExtras() != null) {
            double GISLat = getIntent().getDoubleExtra("GISLAT",0 );
            double GISLong = getIntent().getDoubleExtra("GISLONG", 0);
            int travel = getIntent().getIntExtra("TRAVEL_TYPE", 0);
            String address = getIntent().getStringExtra("ADDRESS");
            if (address != null) {
                //getSupportActionBar().setTitle( Html.fromHtml("<font color=\"white\">" + address + "</font>"));
               getSupportActionBar().setTitle(address);
            }
            mTravelType = (travel == 0) ? TravelType.CAR : TravelType.GOLF_CART;
            mDestination = new Stop(new Point( GISLong, GISLat, SpatialReferences.getWgs84()));
            mDestination.setName(address);
        }

        // initialize map view
        mMapView = findViewById(R.id.myMapView);
        String  mapService = getResources().getString(R.string.map_service);
        ArcGISTiledLayer layer = new ArcGISTiledLayer(mapService);
        ArcGISMap map = new ArcGISMap(new Basemap(layer));
        mMapView.setMap(map);
        map.loadAsync();
        createGraphicsOverlay();

        map.addDoneLoadingListener(() -> {
            directionLbl.setText("Map finished loading");
            setLocationDisplay();
            findRoute();
        });

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

        // Button centerButton = findViewById(R.id.centerButton);
        // centerButton.setOnClickListener(v -> startNavigation(routeTask, routeParameters, routeResult));

        // Reference the center button
        recenterButton = findViewById(R.id.centerButton);

    } // end onCreate

    private void initFontIcons(){
        //Typeface fontAwesome = Typeface.createFromAsset(getAssets(), "fonts/fa-solid-900.ttf");
        // FloatingActionButton fab = findViewById(R.id.fab);
        FontDrawable mapIcon = new FontDrawable(this, R.string.fa_map_marked_alt_solid, true, false);
        if (mImNavigating) {
            mapIcon = new FontDrawable(this, R.string.fa_stop_circle_solid, true, false);
        }
        mapIcon.setTextSize(18);
        mapIcon.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        // fab.setImageDrawable(mapIcon);
        // fab.setBackgroundColor(Color.RED);
    }

    public void centerButtonClick(View view) {
        mMapView.getLocationDisplay().setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        recenterButton.setEnabled(false);
        // mRecenterButton.setEnabled(false);
    }

    public void directionsButtonClick(View view) {
        showSimpleAdapterAlertDialog();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        //if (item.getItemId() == R.id.toolbar_route) {
        //    mMapView.getLocationDisplay().setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        //    mRecenterButton.setEnabled(false);
        //    return true;
        //}
        //if (item.getItemId() == R.id.toolbar_directions) {
            //View view = item.getActionView();
            //if (view == null)
            //    return true;
            // display popup of directions

        //    showSimpleAdapterAlertDialog();
            /*
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            String[] directions = new String[mDirections.size()];
            for (int i=0; i<mDirections.size(); i++){

                directions[i] = mDirections.get(i).getDirectionText();
            }
            builder.setTitle("Directions")
                    .setItems(directions, null)
                    .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                        }
                    });
            AlertDialog directionModal =  builder.create();
            directionModal.show();
            */
        //    return true;
        //}

        finish();

        return super.onOptionsItemSelected(item);
    }

    // setup top menu bar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.nav_menu, menu);
        // mRecenterButton  = menu.findItem(R.id.toolbar_route);
        // FontDrawable drawable = new FontDrawable(this, R.string.fa_crosshairs_solid, true, false);
        // drawable.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        // mRecenterButton.setIcon(drawable);

        // MenuItem dirMenuItem = menu.findItem(R.id.toolbar_directions);
        // fa_map_signs_solid
        // drawable = new FontDrawable(this, R.string.fa_directions_solid, true, false);
        // drawable.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        // dirMenuItem.setIcon(drawable);

        return true;
        //return super.onCreateOptionsMenu(menu);
    }

    private void enableLocationSettings(){
        Intent settingsIntent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
        startActivity(settingsIntent);
    }

    // call after map done loading
    private void setLocationDisplay() {
        mLocationDisplay = mMapView.getLocationDisplay();

        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
        mLocationDisplay.startAsync();

        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {
            if (dataSourceStatusChangedEvent.isStarted() && dataSourceStatusChangedEvent.getError() == null) {
               // mCurrentLocation = dataSourceStatusChangedEvent.getSource().getLocation().getPosition();
                //findRoute(routeUrl);
                return;
            }

            // requrst location permissions
            int requestPermissionsCode = 2;
            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

            if (!(ContextCompat.checkSelfPermission(this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, requestPermissions, requestPermissionsCode);
            } else {
                if (dataSourceStatusChangedEvent.getError() != null){
                    String msg = "unknown";
                    msg = dataSourceStatusChangedEvent.getError().getMessage();
                    String message = String.format("Error in DataSourceStatusChangedListener: %s", msg );
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }
        });

        //mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.OFF);
    }

    private void findRoute() {
        String routeService = (mTravelType == TravelType.CAR) ? getResources().getString(R.string.car_route_service )
                : getResources().getString(R.string.golf_route_service);

        //mMapView.getGraphicsOverlays().get(0).getGraphics().clear();
        //mCurrentLocation = mLocationDisplay.getMapLocation();

        RouteTask routeTask =  new RouteTask(this, routeService);

        routeTask.loadAsync();
        routeTask.addDoneLoadingListener(() -> {
            if (routeTask.getLoadError() == null && routeTask.getLoadStatus() == LoadStatus.LOADED) {

                final ListenableFuture<RouteParameters> routeParametersFuture = routeTask.createDefaultParametersAsync();
                routeParametersFuture.addDoneListener(() -> {
                    try {
                        RouteParameters routeParameters = routeParametersFuture.get();
                        routeParameters.setStops(getStops());
                        routeParameters.setReturnDirections(true);
                        routeParameters.setReturnStops(true);
                        routeParameters.setReturnRoutes(true);
                        //mRouteParameters = routeParameters;

                        ListenableFuture<RouteResult> routeResultFuture = routeTask.solveRouteAsync(routeParameters);
                        routeResultFuture.addDoneListener(() -> {
                            try {
                                // get the route geometry from the route result
                                RouteResult routeResult = routeResultFuture.get();
                                mDirections = routeResult.getRoutes().get(0).getDirectionManeuvers();
                                Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();
                                // create a graphic for the route geometry
                                Graphic routeGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5f));

                                //GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
                                //graphicsOverlay.setOpacity((float)0.7);
                                //mMapView.getGraphicsOverlays().add(graphicsOverlay);
                                mGraphicsOverlay.getGraphics().add(routeGraphic);
                                //mGraphicsOverlay.getGraphics().add(mRouteAheadGraphic);

                                // add it to the graphics overlay
                                setStartMarker(mCurrentLocation);
                                setEndMarker(mDestination.getGeometry());
                                directionLbl.setText(mDirections.get(1).getDirectionText());

                                // PRODUCTION  set the map view view point to show the whole route
                                //mMapView.setViewpointAsync(new Viewpoint(routeGeometry.getExtent()));
                                // Button navigateRouteButton = findViewById(R.id.navigationButton);
                                // navigateRouteButton.setOnClickListener(v -> startNavigation(routeTask, routeParameters, routeResult, null ));

                                // TESTING SIMULATED ROUTE ONLY
                               getSimulatedRoute(routeTask, routeResult, routeParameters );

                            } catch (ExecutionException | InterruptedException e) {
                                String error =  "Error: " + e.getMessage() ;
                                directionLbl.setText(R.string.route_result_error);
                                Log.e(TAG, error);
                            }
                        });

                    } catch ( InterruptedException | ExecutionException e) {
                        //showError("Cannot create RouteTask parameters " + e.getMessage());
                        directionLbl.setText(R.string.route_error);
                        Toast.makeText(this, "Cannot create RouteTask parameters " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, e.getMessage());
                    }
                });
            }
        });

    }   //end find route

    private void stopNavigation(){
        if (mLocationDisplay.isStarted()) {
            mTextToSpeech.stop();
            mLocationDisplay.stop();
            //mDirections.clear();
        }
       // mGraphicsOverlay = new GraphicsOverlay();
       //int layers = mMapView.getGraphicsOverlays().size();
       //mMapView.getGraphicsOverlays().remove(layers - 1);
    }

    private void startNavigation(RouteTask routeTask, RouteParameters routeParameters, RouteResult routeResult, RouteResult simulatedRoute) {
        // Reference the navigate button so we can change it's color and text later
        Button navigateRouteButton = findViewById(R.id.navigationButton);

        if (mImNavigating) {
            stopNavigation();
            mImNavigating = false;
            initFontIcons();

            // Update navigate button color and text
            navigateRouteButton.setBackgroundColor(getResources().getColor(R.color.colorVillageGreen));
            navigateRouteButton.setText(getResources().getText(R.string.toolbar_start));
            return;
        }
        else {
            // Update navigate button color and text
            navigateRouteButton.setBackgroundColor(getResources().getColor(R.color.colorVillageBurgundy));
            navigateRouteButton.setText(getResources().getText(R.string.toolbar_stop));
        }

        int layers = mMapView.getGraphicsOverlays().size();
        //if (layers > 1 )
        //    mMapView.getGraphicsOverlays().get(layers - 1).getGraphics().clear();

        mImNavigating = true;
        initFontIcons();
        // create a graphic (solid) to represent the route that's been traveled (initially empty)
       mMapView.getGraphicsOverlays().get(0).getGraphics().clear();

        // get the route's geometry from the route result
        Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();

        mRouteAheadGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5f));
        mRouteTraveledGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 5f));

        GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        //graphicsOverlay.setOpacity((float)0.4);
        //mMapView.getGraphicsOverlays().add(graphicsOverlay);
        //int i = mMapView.getGraphicsOverlays().size();
        //mMapView.getGraphicsOverlays().get(i - 1).getGraphics().add(mRouteAheadGraphic);
       // mMapView.getGraphicsOverlays().get(i - 1).getGraphics().add(mRouteTraveledGraphic);
        mGraphicsOverlay.getGraphics().add(mRouteAheadGraphic);
        mGraphicsOverlay.getGraphics().add(mRouteTraveledGraphic);

        mLocationDisplay = mMapView.getLocationDisplay();

        // set up a RouteTracker for navigation along the calculated route
        mRouteTracker = new RouteTracker(this, routeResult, 0, true );

        // listen for new voice guidance events
        mRouteTracker.setVoiceGuidanceUnitSystem(UnitSystem.IMPERIAL);
        mRouteTracker.addNewVoiceGuidanceListener(getNewVoiceGuidanceListener());

        if (routeTask.getRouteTaskInfo().isSupportsRerouting()) {
             mRouteTracker.enableReroutingAsync(routeTask, routeParameters, RouteTracker.ReroutingStrategy.TO_NEXT_WAYPOINT, false);
            // setup listener for rerouting
            directionLbl.setText("Rerouting supported");
            Toast.makeText(this, R.string.reroute_supported, Toast.LENGTH_LONG ).show();
//            mRouteTracker.addRerouteStartedListener(rerouteStartedEvent ->  {
//                mRouteTracker.removeNewVoiceGuidanceListener(myNewVoiceGuidanceListener);
//                mRouteTracker.removeTrackingStatusChangedListener(myTrackingStatusChangedListener);
//            });
            mRouteTracker.addRerouteCompletedListener(rerouteCompletedEvent -> {
                mDirections = rerouteCompletedEvent.getTrackingStatus().getRouteResult().getRoutes().get(0).getDirectionManeuvers();
                directionLbl.setText("Rerouting!");
               //  mRouteTracker.addTrackingStatusChangedListener(getTrackingStatusChangedListener());
               //  mRouteTracker.addNewVoiceGuidanceListener(getNewVoiceGuidanceListener());
            });
        }

        // for demo
        // set up a simulated location data source which simulates movement along the route
        RouteTrackerLocationDataSource routeTrackerLocationDataSource;
        if (simulatedRoute != null) {
            SimulatedLocationDataSource mSimulatedLocationDataSource = new SimulatedLocationDataSource();
            SimulationParameters simulationParameters = new SimulationParameters(Calendar.getInstance(), 35, 5, 5);
            Polyline routeGeometry_ = simulatedRoute.getRoutes().get(0).getRouteGeometry();
            mSimulatedLocationDataSource.setLocations(routeGeometry_, simulationParameters);
            // create a route tracker location data source to snap the location display to the route
            routeTrackerLocationDataSource = new RouteTrackerLocationDataSource(mRouteTracker, mSimulatedLocationDataSource);
        } else {
            // for production use device location data source
            routeTrackerLocationDataSource = new RouteTrackerLocationDataSource(getApplicationContext(), mRouteTracker);
        }
        // set the route tracker location data source as the location data source for this app
        mLocationDisplay.setLocationDataSource(routeTrackerLocationDataSource);

        // if the user navigates the map view away from the location display, activate the recenter button
        mLocationDisplay.addAutoPanModeChangedListener(autoPanModeChangedEvent -> {
            recenterButton.setEnabled(true);
        });

        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);

        //mRouteTracker.addTrackingStatusChangedListener(getTrackingStatusChangedListener());
        mLocationDisplay.addLocationChangedListener(locationChangedEvent -> {
           // mRouteTracker.trackLocationAsync(locationChangedEvent.getLocation());

            TrackingStatus trackingStatus = mRouteTracker.getTrackingStatus();
            // set geometries for the route ahead and the remaining route
            if (trackingStatus == null)
                return;

            if (trackingStatus.getRouteProgress() != null) {
                mRouteAheadGraphic.setGeometry(trackingStatus.getRouteProgress().getRemainingGeometry());
                mRouteTraveledGraphic.setGeometry(trackingStatus.getRouteProgress().getTraversedGeometry());
            }

            TrackingStatus.Distance remainingDistance = trackingStatus.getDestinationProgress().getRemainingDistance();
            // covert remaining minutes to hours:minutes:seconds
            String remainingTimeString = DateUtils.formatElapsedTime((long) (trackingStatus.getDestinationProgress().getRemainingTime() * 60));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    timeLbl.setText(getString(R.string.time_remaining, remainingTimeString));
                    distanceLbl.setText(getString(R.string.distance_remaining, remainingDistance.getDisplayText(),
                            remainingDistance.getDisplayTextUnits().getPluralDisplayName()));
                }
            });

            if (trackingStatus.getDestinationStatus() == DestinationStatus.REACHED) {
                // if there are more destinations to visit. Greater than 1 because the start point is considered a "stop"
                if (mRouteTracker.getTrackingStatus().getRemainingDestinationCount() > 1) {
                    // switch to the next destination
                    mRouteTracker.switchToNextDestinationAsync();
                    Toast.makeText(getApplicationContext(), "Navigating to the second stop.", Toast.LENGTH_LONG).show();
                } else {
                    mMapView.getLocationDisplay().stop();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    //Toast.makeText(getApplicationContext(), "Arrived at the final destination.", Toast.LENGTH_LONG).show();
                }
            }
        });

        mLocationDisplay.startAsync();
        //Toast.makeText(getApplicationContext(), "Navigating to the first stop.", Toast.LENGTH_SHORT ).show();

    } // end start navigation
    //private RouteResult routeResult_;
    private void getSimulatedRoute(RouteTask routeTask, RouteResult routeResult, RouteParameters routeParameters){
        ListenableFuture<RouteParameters> routeParametersFuture = routeTask.createDefaultParametersAsync();
        routeParametersFuture.addDoneListener(() -> {

            try {
                RouteParameters routeParameters_ = routeParametersFuture.get();
                routeParameters_.setStops(getSimulatedStops());
                routeParameters_.setReturnDirections(true);
                routeParameters_.setReturnStops(true);
                routeParameters_.setReturnRoutes(true);
                ListenableFuture<RouteResult> routeResultFuture_ = routeTask.solveRouteAsync(routeParameters_);

                routeResultFuture_.addDoneListener(() -> {

                    try {

                        RouteResult routeResult_ = routeResultFuture_.get();
                        Polyline routeGeometry_ = routeResult_.getRoutes().get(0).getRouteGeometry();
                        Graphic routeGraphic_ = new Graphic(routeGeometry_, new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, Color.BLUE, 5f));

                        GraphicsOverlay go = new GraphicsOverlay();
                        mMapView.getGraphicsOverlays().add(go);
                        go.getGraphics().add(routeGraphic_);
                        //mGraphicsOverlay.getGraphics().add(routeGraphic_);

                        Polyline routeGeometry = routeResult.getRoutes().get(0).getRouteGeometry();
                        Graphic routeGraphic = new Graphic(routeGeometry, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 5f));

                        Envelope e = com.esri.arcgisruntime.geometry.GeometryEngine.combineExtents(routeGraphic.getGeometry(), routeGraphic_.getGeometry());
                        mMapView.setViewpointGeometryAsync(e, 30);
                        Button navigateRouteButton = findViewById(R.id.navigationButton);
                        navigateRouteButton.setOnClickListener(v -> startNavigation(routeTask, routeParameters, routeResult, routeResult_));

                    } catch (ExecutionException | InterruptedException e) {
                        String error = "Error creating default route parameters: " + e.getMessage();
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        Log.e("case02665010", error);
                    }
                });
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    // not used

    private RouteTracker.TrackingStatusChangedListener getTrackingStatusChangedListener() {
        myTrackingStatusChangedListener = new RouteTracker.TrackingStatusChangedListener() {
            @Override
            public void onTrackingStatusChanged(RouteTracker.TrackingStatusChangedEvent trackingStatusChangedEvent) {
                // get the route's tracking status
                TrackingStatus trackingStatus = trackingStatusChangedEvent.getTrackingStatus();

                // set geometries for the route ahead and the remaining route
                mRouteAheadGraphic.setGeometry(trackingStatus.getRouteProgress().getRemainingGeometry());
                Geometry traversed = trackingStatus.getRouteProgress().getTraversedGeometry();
                mRouteTraveledGraphic.setGeometry(traversed);

                // get remaining distance information
                TrackingStatus.Distance remainingDistance = trackingStatus.getDestinationProgress().getRemainingDistance();
                // covert remaining minutes to hours:minutes:seconds
                String remainingTimeString = DateUtils.formatElapsedTime((long) (trackingStatus.getDestinationProgress().getRemainingTime() * 60));

                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      timeLbl.setText(getString(R.string.time_remaining, remainingTimeString));
                                      distanceLbl.setText(getString(R.string.distance_remaining, remainingDistance.getDisplayText(),
                                              remainingDistance.getDisplayTextUnits().getPluralDisplayName()));
                                  }
                              });

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
        ImageView turnImage = findViewById(R.id.imageView);
        myNewVoiceGuidanceListener = new RouteTracker.NewVoiceGuidanceListener() {
            @Override
            public void onNewVoiceGuidance(RouteTracker.NewVoiceGuidanceEvent newVoiceGuidanceEvent) {
                // use Android's text to speech to speak the voice guidance
                String txt = newVoiceGuidanceEvent.getVoiceGuidance().getText();
                Log.d(TAG, "Voice command: " + txt);
                if (txt.isEmpty())
                    return;
                speakVoiceGuidance(txt);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        directionLbl.setText(getString(R.string.next_direction, txt));
                        turnImage.setImageResource(getDirectionDrawable(txt));
                    }
                });
            }
        };
        return myNewVoiceGuidanceListener;
    }

    /**
     * Uses Android's text to speak to say the latest voice guidance from the RouteTracker out loud.
     */
    private void speakVoiceGuidance(String voiceGuidanceText) {
        if (mIsTextToSpeechInitialized && !mTextToSpeech.isSpeaking()) {
            mTextToSpeech.speak(voiceGuidanceText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private List<Stop> getStops() {
        List<Stop> stops =   new ArrayList<Stop>(2);
        if (mCurrentLocation == null || mCurrentLocation.getX() == 0.0){
            mMapView.getLocationDisplay().startAsync();
            mCurrentLocation = mLocationDisplay.getMapLocation();
            if (mCurrentLocation.getX() == 0.0){
                // wildwood community center.
                double x = -82.02357333333333;
                double y = 28.852696666666663;
                mCurrentLocation = new Point(x, y, SpatialReferences.getWgs84());
            }
        }
        Stop start = new Stop(mCurrentLocation);
        start.setName("Current Location");
        stops.add(start);
        stops.add(mDestination);
        return stops;
    }
    private List<Stop> getSimulatedStops(){
        List<Stop> stops = new ArrayList<Stop>(3);
        Stop start = new Stop(mCurrentLocation);
        stops.add(start);
        Point p2 = new Point( -82.01578185304507,  28.84526306058272, SpatialReferences.getWgs84());
        stops.add(new Stop(p2));
        Point p3 = new Point( -82.01515941942515,  28.84760574373517, SpatialReferences.getWgs84());
        stops.add(new Stop(p3));
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
        super.onDestroy();
        mMapView.dispose();
    }

    @Override
    protected void onStop() {
        // clean up
        if (mMapView != null) {
            mLocationDisplay.stop();
            mLocationDisplay = null;
            //mRouteTracker.removeNewVoiceGuidanceListener(myNewVoiceGuidanceListener);
            mRouteTracker = null;
            mTextToSpeech.stop();
            mMapView.dispose();
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public Dialog onCreateDialog(Bundle savedInstanceState){
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        String[] directions = new String[mDirections.size()];
        for (int i=0; i<mDirections.size(); i++){
            directions[i] = mDirections.get(i).getDirectionText();
        }

        builder.setTitle("Directions")
                .setItems(directions, null);

        return builder.create();
    }

    // Show how to use SimpleAdapter to customize list item in android alert dialog.
    private void showSimpleAdapterAlertDialog()
    {

       // for (int i=0; i<mDirections.size(); i++){
       //     directions[i] = mDirections.get(i).getDirectionText();
       // }

        // Each image in array will be displayed at each item beginning.
        FontDrawable drawable = new FontDrawable(this, R.string.fa_directions_solid, true, false);
        drawable.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        int[] imageIdArr = {R.drawable.ic_destination, R.drawable.ic_navigate, R.drawable.ic_source};
        // Each item text.
        String[] listItemArr = new String[mDirections.size()];


        // Image and text item data's key.
        final String CUSTOM_ADAPTER_IMAGE = "image";
        final String CUSTOM_ADAPTER_TEXT = "text";



        // Create SimpleAdapter list data.
        List<Map<String, Object>> dialogItemList = new ArrayList<Map<String, Object>>();
        int listItemLen = mDirections.size();
        for(int i=0; i<listItemLen; i++)
        {
            Map<String, Object> itemMap = new HashMap<String, Object>();
            String d = mDirections.get(i).getDirectionText();
            itemMap.put(CUSTOM_ADAPTER_IMAGE, getDirectionDrawable(d));
            itemMap.put(CUSTOM_ADAPTER_TEXT, d);

            dialogItemList.add(itemMap);
        }

        // Create SimpleAdapter object.
        SimpleAdapter simpleAdapter = new SimpleAdapter(MapNavActivity.this, dialogItemList,
                R.layout.direction_dialog,
                new String[]{CUSTOM_ADAPTER_IMAGE, CUSTOM_ADAPTER_TEXT},
                new int[]{R.id.alertItemImageView,R.id.alertItemTextView});
        // Create a alert dialog builder.
        AlertDialog.Builder builder = new AlertDialog.Builder(MapNavActivity.this);

        builder.setIcon(drawable)
            .setTitle("Directions")
            .setAdapter(simpleAdapter, null)
            .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        //builder.setCancelable(false);
        builder.create();
        builder.show();
    }

    private int getDirectionDrawable(String fromDirection){
        int i = R.drawable.north;
        if (fromDirection.contains("Start"))
            return R.drawable.location;
        if (fromDirection.contains("Turn left"))
            return R.drawable.west;
        if (fromDirection.contains("Turn right"))
            return R.drawable.east;
        if (fromDirection.contains("Continue"))
            return R.drawable.north;
        if (fromDirection.contains("Go north"))
            return R.drawable.north;
        if (fromDirection.contains("Go south"))
            return R.drawable.south;
        if (fromDirection.contains("Go west"))
            return R.drawable.west;
        if (fromDirection.contains("Go east"))
            return R.drawable.east;
        if (fromDirection.contains("southwest"))
            return R.drawable.south_west;
        if (fromDirection.contains("Go northeast"))
            return R.drawable.north_east;
        if (fromDirection.contains("Go northwest"))
            return R.drawable.north_west;
        if (fromDirection.contains("Bear right"))
            return R.drawable.north_east;
        if (fromDirection.contains("Bear left"))
            return R.drawable.north_west;
        if (fromDirection.contains("Finish"))
            return R.drawable.flag;
        return i;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
           mLocationDisplay.startAsync();
        } else {
            Toast.makeText(this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
        }
    }
}



package com.thevillages.myfirstapp;

import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
//import android.support.v4.widget.SimpleCursorAdapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;
import androidx.cursoradapter.widget.SimpleCursorAdapter;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleFillSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;
import com.esri.arcgisruntime.layers.FeatureLayer;

import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.thevillages.maplibrary.MapViewModel;
import com.thevillages.maplibrary.TravelType;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private int LOCATION_ACCESS_CODE = 1;
    private final String TAG = MainActivity.class.getSimpleName();
    private final String COLUMN_NAME_ADDRESS = "address";
    private final String[] mColumnNames = { BaseColumns._ID, COLUMN_NAME_ADDRESS };
    private SearchView mAddressSearchView;

    // passed values from bundle
    Double mGISLat = Double.valueOf(0);
    Double mGISLong = Double.valueOf(0);
    String mAddress = null;

    // *** ADD ***
    private MapView mMapView;
    private LocatorTask mLocatorTask;
    private GraphicsOverlay mGraphicsOverlay;
    private GeocodeParameters mAddressGeocodeParameters;
    private PictureMarkerSymbol mPinSourceSymbol;
    private Callout mCallout;

    private SceneView mSceneView;
    private FeatureLayer mFeatureLayer;
    private Point mLocation;
    private MapViewModel vm;
    private TravelType mTravelType = TravelType.CAR;

    public MainActivity(){
        vm = new MapViewModel();
        ArcGISRuntimeEnvironment.setLicense("runtimebasic,1000,rud000252796,none,MJJ47AZ7G349NERL1216");
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            //test gps
            Toast.makeText(this, "GPS access is granted", Toast.LENGTH_SHORT).show();

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            LocationProvider gpsProvider =  lm.getProvider(LocationManager.GPS_PROVIDER);
            final boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

            if (!gpsEnabled){
                //create intent and open activity
                Intent settingsIntent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
                startActivity(settingsIntent);
            }
        } else {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                //Toast.makeText(this, "Location permission required for map functions.", Toast.LENGTH_SHORT).show();
                new AlertDialog.Builder(this)
                        .setTitle("permission needed")
                        .setMessage("Location permission needed for map function")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_ACCESS_CODE);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_ACCESS_CODE);
            }

        }

        setContentView(R.layout.activity_main);

        // inflate address search view
        mAddressSearchView = (SearchView) findViewById(R.id.addressSearchView);
        mAddressSearchView.setIconified(false);
        mAddressSearchView.setFocusable(false);
        mAddressSearchView.setQueryHint(getResources().getString(R.string.address_search_hint));


        // define pin drawable
        BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        try {
            mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Picture Marker Symbol error: " + e.getMessage());
            Toast.makeText(getApplicationContext(), "Failed to load pin drawable.", Toast.LENGTH_LONG).show();
        }
        // set pin to half of native size
        mPinSourceSymbol.setWidth(19f);
        mPinSourceSymbol.setHeight(72f);

        // create a LocatorTask from an online service
        //mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");
        mLocatorTask = new LocatorTask("https://arc7.thevillages.com/arcgis/rest/services/TSGLOCATE2/GeocodeServer");

        // *** ADD ***
        mMapView = findViewById(R.id.mapView);

        setupMap();
        //setupSceneMap();

        //final ArcGISMap map = new ArcGISMap(Basemap.createStreetsVector());
        // set the map to be displayed in this view
        //mMapView.setMap(map);
        // set the map viewpoint to start over North America
        //mMapView.setViewpoint(new Viewpoint(40, -100, 100000000));

        // add listener to handle screen taps
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                identifyGraphic(motionEvent);
                return true;
            }
        });

        // define the graphics overlay
        mGraphicsOverlay = new GraphicsOverlay();

        setupAddressSearchView();

        //fab navBtn click handler
        FloatingActionButton navBtn = findViewById(R.id.navBtn);
        navBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                // create intent and go to mapnavactivity
                /* testing to find activities
                PackageManager pm = getApplicationContext().getPackageManager();
                List<PackageInfo> plist = pm.getInstalledPackages(pm.GET_ACTIVITIES);
                PackageInfo pinfo = null;
                for (PackageInfo item : plist) {
                    if (item.packageName.equals("com.thevillages.myfirstapp"))
                        pinfo = item;
                }
                ActivityInfo[] aarray = pinfo.activities;
                */

                //Intent intent = new Intent(getApplicationContext(), Class.forName("MapViewActivity"));
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String actvitityClassName = "com.thevillages.maplibrary.MapNavActivity"; //"com.thevillages.maplibrary.MapViewActivity"
                intent.setClassName(getApplicationContext(), actvitityClassName );
                //Intent intent = new Intent(MainActivity.this, NavigationActivity.class );
                intent.putExtra("GISLONG", mLocation.getX());
                intent.putExtra("GISLAT", mLocation.getY());
                intent.putExtra("TRAVEL_TYPE", mTravelType.getValue());
                intent.putExtra("ADDRESS", mAddressSearchView.getQuery().toString());
                startActivity(intent);
            }
        });

        // values pass from the villages mobile app
        if (this.getIntent().getExtras() != null) {
            mTravelType = TravelType.valueOf( getIntent().getIntExtra("TRAVEL_TYPE", 0));
            /*
            mAddress = getIntent().getStringExtra("ADDRESS");
            if (mAddress != null) {
                mGISLat = getIntent().getDoubleExtra("GISLAT",0 );
                mGISLong = getIntent().getDoubleExtra("GISLONG", 0);
                mAddressSearchView.setQuery(mAddress, true);
            }

           */
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_ACCESS_CODE){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "permission NOT granted", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up the address SearchView. Uses MatrixCursor to show suggestions to the user as the user inputs text.
     */
    private void setupAddressSearchView() {

        mAddressGeocodeParameters = new GeocodeParameters();
        // get place name and address attributes
        mAddressGeocodeParameters.getResultAttributeNames().add("score");
        //mAddressGeocodeParameters.getResultAttributeNames().add("address");
        // return only the closest result
        mAddressGeocodeParameters.setMaxResults(1);
        mAddressSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String address) {
                // geocode typed address
                geoCodeTypedAddress(address);
                // clear focus from search views
                mAddressSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // as long as newText isn't empty, get suggestions from the locatorTask
                if (!newText.equals("")) {
                    final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask.suggestAsync(newText);
                    suggestionsFuture.addDoneListener(new Runnable() {

                        @Override public void run() {
                            try {
                                // get the results of the async operation
                                List<SuggestResult> suggestResults = suggestionsFuture.get();
                                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                                int key = 0;
                                // add each address suggestion to a new row
                                for (SuggestResult result : suggestResults) {
                                    suggestionsCursor.addRow(new Object[] { key++, result.getLabel() });
                                }
                                // define SimpleCursorAdapter
                                String[] cols = new String[] { COLUMN_NAME_ADDRESS };
                                int[] to = new int[] { R.id.suggestion_address };
                                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(MainActivity.this,
                                        R.layout.suggestion, suggestionsCursor, cols, to, 0);
                                mAddressSearchView.setSuggestionsAdapter(suggestionAdapter);
                                // handle an address suggestion being chosen
                                mAddressSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                                    @Override public boolean onSuggestionSelect(int position) {
                                        return false;
                                    }

                                    @Override public boolean onSuggestionClick(int position) {
                                        // get the selected row
                                        MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                        // get the row's index
                                        int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                        // get the string from the row at index
                                        String address = selectedRow.getString(selectedCursorIndex);
                                        // use clicked suggestion as query
                                        mAddressSearchView.setQuery(address, true);
                                        return true;
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Geocode suggestion error: " + e.getMessage());
                            }
                        }
                    });
                }
                return true;
            }
        });
    }

    /**
     * Identifies the Graphic at the tapped point.
     *
     * @param motionEvent containing a tapped screen point
     */
    private void identifyGraphic(MotionEvent motionEvent) {
        // get the screen point
        android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()), Math.round(motionEvent.getY()));
        // from the graphics overlay, get graphics near the tapped location
        final ListenableFuture<IdentifyGraphicsOverlayResult> identifyResultsFuture = mMapView
                .identifyGraphicsOverlayAsync(mGraphicsOverlay, screenPoint, 10, false);
        identifyResultsFuture.addDoneListener(new Runnable() {
            @Override public void run() {
                try {
                    IdentifyGraphicsOverlayResult identifyGraphicsOverlayResult = identifyResultsFuture.get();
                    List<Graphic> graphics = identifyGraphicsOverlayResult.getGraphics();
                    // if a graphic has been identified
                    if (graphics.size() > 0) {
                        //get the first graphic identified
                        Graphic identifiedGraphic = graphics.get(0);
                        showCallout(identifiedGraphic);
                    } else {
                        // if no graphic identified
                        mCallout.dismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Identify error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Geocode an address passed in by the user.
     *
     * @param address read in from searchViews
     */
    private void geoCodeTypedAddress(final String address) {
        // check that address isn't null
        if (address != null) {

            // Execute async task to find the address
            mLocatorTask.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                        // Call geocodeAsync passing in an address
                        final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                                .geocodeAsync(address, mAddressGeocodeParameters);

                        geocodeResultListenableFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // Get the results of the async operation
                                    List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                                    if (geocodeResults.size() > 0) {
                                        displaySearchResult(geocodeResults.get(0));
                                    } else {
                                        Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                                                Toast.LENGTH_LONG).show();
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    Log.e(TAG, "Geocode error: " + e.getMessage());
                                    Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error), Toast.LENGTH_LONG)
                                         .show();
                                }
                            }
                        });

                    } else {
                        Log.i(TAG, "Trying to reload locator task");
                        mLocatorTask.retryLoadAsync();
                    }
                }
            });

            mLocatorTask.loadAsync();
        }
    }

    /**
     * Turns a GeocodeResult into a Point and adds it to a GraphicOverlay which is then drawn on the map.
     *
     * @param geocodeResult a single geocode result
     */
    private void displaySearchResult(GeocodeResult geocodeResult) {
        // dismiss any callout
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }
        // clear map of existing graphics
        mMapView.getGraphicsOverlays().clear();
        mGraphicsOverlay.getGraphics().clear();
        // create graphic object for resulting location
        Point resultPoint = geocodeResult.getDisplayLocation();
        mLocation = resultPoint;
        Graphic resultLocGraphic = new Graphic(resultPoint, geocodeResult.getAttributes(), mPinSourceSymbol);
        // add graphic to location layer
        mGraphicsOverlay.getGraphics().add(resultLocGraphic);
        // zoom map to result over 3 seconds
        //Viewpoint  vp = new Viewpoint(geocodeResult.getExtent(), 3); thows exception
        Viewpoint  vp = new Viewpoint(resultPoint , 10000);

        // set the graphics overlay to the map
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
        //showCallout(resultLocGraphic);
        mMapView.setViewpointAsync(vp);
    }

    /**
     * Shows the Graphic's attributes as a Callout.
     *
     * @param graphic containing attributes
     */
    private void showCallout(final Graphic graphic) {
        // create a TextView for the Callout
        TextView calloutContent = new TextView(getApplicationContext());
        calloutContent.setTextColor(Color.BLACK);
        // set the text of the Callout to graphic's attributes
        if (graphic.getAttributes().size() < 2)
            return;
        String address = graphic.getAttributes().get("PlaceName").toString() + "\n" + graphic.getAttributes().get("StAddr").toString();

        calloutContent.setText(address);
        // get Callout
        mCallout = mMapView.getCallout();
        // set Callout options: animateCallout: true, recenterMap: false, animateRecenter: false
        mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
        mCallout.setContent(calloutContent);

        // set the leader position and show the callout
        Point calloutLocation = graphic.computeCalloutLocation(graphic.getGeometry().getExtent().getCenter(), mMapView);
        mCallout.setGeoElement(graphic, calloutLocation);
        mCallout.show();
    }

    private void createGraphicsOverlay() {
        mGraphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
    }

    private void createPointGraphics() {
        Point point = new Point(vm.getLatX(), vm.getLongY(), SpatialReferences.getWgs84());
        SimpleMarkerSymbol pointSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.rgb(226, 119, 40), 10.0f);
        pointSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 2.0f));
        Graphic pointGraphic = new Graphic(point, pointSymbol);
        mGraphicsOverlay.getGraphics().add(pointGraphic);
    }

    private void createPolylineGraphics() {
        PointCollection polylinePoints = new PointCollection(SpatialReferences.getWgs84());
        polylinePoints.add(new Point(-118.67999016098526, 34.035828839974684));
        polylinePoints.add(new Point(-118.65702911071331, 34.07649252525452));
        Polyline polyline = new Polyline(polylinePoints);
        SimpleLineSymbol polylineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3.0f);
        Graphic polylineGraphic = new Graphic(polyline, polylineSymbol);
        mGraphicsOverlay.getGraphics().add(polylineGraphic);
    }

    private void createPolygonGraphics() {
        PointCollection polygonPoints = new PointCollection(SpatialReferences.getWgs84());
        polygonPoints.add(new Point(-118.70372100524446, 34.03519536420519));
        polygonPoints.add(new Point(-118.71766916267414, 34.03505116445459));
        polygonPoints.add(new Point(-118.71923322580597, 34.04919407570509));
        polygonPoints.add(new Point(-118.71631129436038, 34.04915962906471));
        polygonPoints.add(new Point(-118.71526020370266, 34.059921300916244));
        polygonPoints.add(new Point(-118.71153226844807, 34.06035488360282));
        polygonPoints.add(new Point(-118.70803735010169, 34.05014385296186));
        polygonPoints.add(new Point(-118.69877903513455, 34.045182336992816));
        polygonPoints.add(new Point(-118.6979656552508, 34.040267760924316));
        polygonPoints.add(new Point(-118.70259112469694, 34.038800278306674));
        polygonPoints.add(new Point(-118.70372100524446, 34.03519536420519));
        Polygon polygon = new Polygon(polygonPoints);
        SimpleFillSymbol polygonSymbol = new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, Color.rgb(226, 119, 40),
                new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 2.0f));
        Graphic polygonGraphic = new Graphic(polygon, polygonSymbol);
        mGraphicsOverlay.getGraphics().add(polygonGraphic);
    }

    private void createGraphics() {
        createGraphicsOverlay();
        createPointGraphics();
        createPolylineGraphics();
        createPolygonGraphics();
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

    private void setupSceneMap() {
        if (mSceneView != null) {
            // santa monica CA
            double latitude = 33.8210;
            double longitude = -118.6778;
            // the villages
            //double latitude = 28.907124539990157;
            //double longitude = -81.97479891040035;

            double altitude = 44000.0;
            double heading = 0.1;
            double pitch = 30.0;
            double roll = 0.0;

            ArcGISScene scene = new ArcGISScene();
            scene.setBasemap(Basemap.createStreets());
            mSceneView.setScene(scene);
            Camera camera = new Camera(latitude, longitude, altitude, heading, pitch, roll);
            mSceneView.setViewpointCamera(camera);
        }
    }

    private void setupMap() {
        if (mMapView != null) {
            Basemap.Type basemapType = Basemap.Type.TOPOGRAPHIC;
            // santa monica CA
            double latitude = 33.8210;
            double longitude = -118.6778;
            int levelOfDetail =11;
            ArcGISMap map = new ArcGISMap(basemapType, latitude, longitude, levelOfDetail);

            vm.loadmap();

            mMapView.setMap(vm.getMap());

           // addLayer(map);
        }
    }
    private void addLayer(final ArcGISMap map){
        String id = "2e4b3df6ba4b44969a3bc9827de746b3";

        Portal portal = new Portal("http://www.arcgis.com");
        final PortalItem portalItem = new PortalItem(portal, id);

        mFeatureLayer = new FeatureLayer(portalItem,0);
        mFeatureLayer.addDoneLoadingListener(new Runnable() {

            @Override public void run() {
                if (mFeatureLayer.getLoadStatus() == LoadStatus.LOADED) {
                    map.getOperationalLayers().add(mFeatureLayer);
                }
            }
        });

        mFeatureLayer.loadAsync();
    }
}

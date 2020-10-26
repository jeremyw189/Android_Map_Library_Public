package com.thevillages.maplibrary;

import android.graphics.Color;
import android.util.Log;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.navigation.RouteTracker;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MapViewModel {
    private static final String TAG = "MapViewModel";

    private Point mStart = null;
    private Point mEnd = null;

    private GraphicsOverlay mGraphicsOverlay;

    // the villages center point.
    protected static double latitude = 28.907124539990157;
    protected static double longitude = -81.97479891040035;

    public double _theVillagesScale = 124762.7156655228955;
    // Create and set initial map area
    public Envelope _theVillagesEnvelope = new Envelope(-82.121556, 28.690528, -81.887883, 29.001611, SpatialReferences.getWgs84());
    // Create central point where map is centered
    public Point _theVillagesCentralPoint = new Point(-82.0047195, 28.8460695, SpatialReferences.getWgs84());

    // Create and set initial map
    private ArcGISMap _map =  null; // new Map();
    private MapView mMapView;

    private RouteTracker mRouteTracker;
    private RouteParameters mRouteParameters;
    private RouteResult mRouteResult;
    private RouteTask mRouteTask;

    public double getLatX(){
        return MapViewModel.latitude;
    }
    public double getLongY(){
        return MapViewModel.longitude;
    }

    public Point getStart() {return mStart; }
    public void setStart(Point start) { mStart = start; }
    public Point getEnd() {return mEnd; }
    public void setEnd(Point end) { mEnd = end; }

    public ArcGISMap getMap(){
        final ArcGISMap map = _map;
        return map;
    }
    // constructor
    public MapViewModel(MapView mapView){
        mMapView = mapView;
    }
    public MapViewModel(){
       // mMapView = mapView;
    }

    public void loadmap(){

        String  mapService = "https://arc5.thevillages.com/arcgis/rest/services/PUBLICMAP26/MapServer/";
        ArcGISTiledLayer layer = new ArcGISTiledLayer(mapService);
        Basemap map = new Basemap(layer);

        _map = new ArcGISMap(map);
        Viewpoint startPt = new Viewpoint(_theVillagesCentralPoint, 200000);
        _map.setInitialViewpoint(startPt);

        _map.loadAsync();

        if (mMapView != null)
            mMapView.setMap(_map);
    }

    public void loadRouteService (android.content.Context context, String routeTaskServiceURI){
        // create route task from San Diego service

        mRouteTask = new RouteTask(context, routeTaskServiceURI);
        mRouteTask.loadAsync();

        mRouteTask.addDoneLoadingListener(() -> {
            if (mRouteTask.getLoadError() == null && mRouteTask.getLoadStatus() == LoadStatus.LOADED) {
                final ListenableFuture<RouteParameters> routeParamsFuture = mRouteTask.createDefaultParametersAsync();
                routeParamsFuture.addDoneListener(() -> {
                    try {
                        mRouteParameters = routeParamsFuture.get();
                        List<Stop> stops = new ArrayList<>();
                        stops.add(new Stop(mStart));
                        stops.add(new Stop(mEnd));
                        mRouteParameters.setStops(stops);
                        // Code from the next step 15
                        final ListenableFuture<RouteResult> routeResultFuture = mRouteTask.solveRouteAsync(mRouteParameters);
                        routeResultFuture.addDoneListener(() -> {
                            try {
                                mRouteResult = routeResultFuture.get();
                                Route firstRoute = mRouteResult.getRoutes().get(0);
                                // Code from the next step 16
                                Polyline routePolyline = firstRoute.getRouteGeometry();
                                SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3.0f);
                                Graphic routeGraphic = new Graphic(routePolyline, routeSymbol);
                                mGraphicsOverlay.getGraphics().add(routeGraphic);

                            } catch (InterruptedException | ExecutionException e) {
                                Log.e(TAG, "Solve RouteTask failed " + e.getMessage());
                            }
                        });

                    } catch (InterruptedException | ExecutionException e) {
                        Log.e(TAG, "Cannot create RouteTask parameters " + e.getMessage());
                        //showError("Cannot create RouteTask parameters " + e.getMessage());
                    }
                });
            } else {
                Log.e( TAG, "Unable to load RouteTask " + mRouteTask.getLoadStatus().toString());
            }
        });

    }

    public void createGraphicsOverlay() {
        mGraphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
    }

    public void setMapMarker(Point location, SimpleMarkerSymbol.Style style, int markerColor, int outlineColor) {
        float markerSize = 8.0f;
        float markerOutlineThickness = 2.0f;
        SimpleMarkerSymbol pointSymbol = new SimpleMarkerSymbol(style, markerColor, markerSize);
        pointSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, outlineColor, markerOutlineThickness));
        Graphic pointGraphic = new Graphic(location, pointSymbol);
        mGraphicsOverlay.getGraphics().add(pointGraphic);
    }

    public void setStartMarker(Point location) {
        mGraphicsOverlay.getGraphics().clear();
        setMapMarker(location, SimpleMarkerSymbol.Style.DIAMOND, Color.rgb(226, 119, 40), Color.BLUE);
        mStart = location;
        mEnd = null;
    }

    public void setEndMarker(Point location) {
        setMapMarker(location, SimpleMarkerSymbol.Style.SQUARE, Color.rgb(40, 119, 226), Color.RED);
        mEnd = location;
        // findRoute();
    }

    private void findRoute() {
        // Code from the next step goes here
    }

    // add navigation
    public RouteTracker createRouteTracker(android.content.Context context){
        //1 create tracker
        mRouteTracker = new RouteTracker(context, mRouteResult, 0);
       //2 enable rerouting
        mRouteTracker.enableReroutingAsync(mRouteTask, mRouteParameters, RouteTracker.ReroutingStrategy.TO_NEXT_STOP, false );
        //3 scribe to tracker.

        // i'm flying by the seat of my pants.
        return mRouteTracker;

    }
}

package com.thevillages.maplibrary;

import com.esri.arcgisruntime.location.LocationDataSource;
import com.esri.arcgisruntime.navigation.RouteTracker;

public class RouteTrackerDisplayLocationDataSource extends LocationDataSource {
    private LocationDataSource mLocationDataSource;
    private RouteTracker mRouteTracker;

    public RouteTrackerDisplayLocationDataSource(RouteTracker routeTracker, LocationDataSource dataSource){
        mLocationDataSource = dataSource;
        mRouteTracker = routeTracker;

        mLocationDataSource.addLocationChangedListener( locationChangedEvent -> {
            mRouteTracker.trackLocationAsync(locationChangedEvent.getLocation());
        });

        mRouteTracker.addTrackingStatusChangedListener(trackingStatusChangedEvent -> {
           if (trackingStatusChangedEvent.getTrackingStatus().getDisplayLocation() != null){
               setLastKnownLocation(trackingStatusChangedEvent.getTrackingStatus().getDisplayLocation());
           }
        });
    }

    @Override
    protected void onStart() {
        mLocationDataSource.startAsync();
    }

    @Override
    protected void onStop() {
        mLocationDataSource.stop();
    }
}

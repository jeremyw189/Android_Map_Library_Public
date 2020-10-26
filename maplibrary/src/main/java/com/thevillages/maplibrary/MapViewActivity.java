package com.thevillages.maplibrary;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.esri.arcgisruntime.mapping.view.MapView;

public class MapViewActivity extends AppCompatActivity {

    private MapView pMapView;
    private MapViewModel vm = new MapViewModel();

    public MapViewActivity(){

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_view);

        pMapView = findViewById(R.id.mapView);
        vm.loadmap();
        pMapView.setMap(vm.getMap());
    }

    @Override
    protected void onPause() {
        if (pMapView != null) {
            pMapView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pMapView != null) {
            pMapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (pMapView != null) {
            pMapView.dispose();
        }
        super.onDestroy();
    }
}

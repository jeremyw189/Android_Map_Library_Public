package com.thevillages.maplibrary;

import androidx.appcompat.app.AppCompatActivity;

import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SceneView;
import android.os.Bundle;

public class NavSceneActivity extends AppCompatActivity {

    private SceneView mSceneView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nav_scene);

        mSceneView = findViewById(R.id.sceneView);

        setupScene();
    }

    private void setupScene(){
        if( mSceneView == null)
            return;

        Basemap.Type basemapType = Basemap.Type.IMAGERY_WITH_LABELS;
        ArcGISScene scene = new ArcGISScene(basemapType);
        mSceneView.setScene(scene);

        addFeatureLayer();

        setElevationSource((scene));
    }

    private void addFeatureLayer() {
        String url = "https://services3.arcgis.com/GVgbJbqm8hXASVYi/arcgis/rest/services/Trails/FeatureServer/0";
        String mapurl = getResources().getString(R.string.map_service);
        final ServiceFeatureTable serviceFeatureTable = new ServiceFeatureTable(url);
        FeatureLayer featureLayer = new FeatureLayer(serviceFeatureTable);

        //ArcGISTiledLayer layer = new ArcGISTiledLayer(mapurl);
        //Basemap bm = new Basemap(layer);
        //ArcGISScene scene = new ArcGISScene(bm);
        //mSceneView.setScene(scene);

        mSceneView.getScene().getOperationalLayers().add(featureLayer);

        Camera camera = new Camera(
                28.907124,
                -81.974798,
                50.0,
                0.0,
                50.0,
                0);
        mSceneView.setViewpointCamera(camera);
    }

    private void setElevationSource(ArcGISScene scene) {
        ArcGISTiledElevationSource elevationSource = new ArcGISTiledElevationSource(
                "http://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer");
        scene.getBaseSurface().getElevationSources().add(elevationSource);
    }
}

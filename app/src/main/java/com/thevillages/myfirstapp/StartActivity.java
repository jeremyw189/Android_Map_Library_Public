package com.thevillages.myfirstapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.thevillages.maplibrary.TravelType;

public class StartActivity extends AppCompatActivity {
    private int LOCATION_ACCESS_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        setLocationPremissions();

        Button searchBtn = findViewById(R.id.searchBtn);
        searchBtn.setOnClickListener(new View.OnClickListener() {
                     @Override
                     public void onClick(View v) {
                         Intent intent = new Intent(StartActivity.this, MainActivity.class );
                         intent.putExtra("TRAVEL_TYPE", TravelType.CAR.getValue());
                         startActivity(intent);
                     }
                 });

        Button navLibBtn = findViewById(R.id.navLibBtn);
        navLibBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, NavigationActivity.class );
                startActivity(intent);
            }
        });

        Button navMapBtn = findViewById(R.id.navMapBtn);
        navMapBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                //Intent intent = new Intent(getApplicationContext(), Class.forName("MapViewActivity"));
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String activityClassName = "com.thevillages.maplibrary.MapNavActivity"; //"com.thevillages.maplibrary.MapViewActivity"
                intent.setClassName(getApplicationContext(), activityClassName );
                //cane gardens -81.992341d 28.892343d
                // lake shore pool 28.910041, -81.971360
                intent.putExtra("ADDRESS", "Lake Shore pool");
                intent.putExtra("GISLONG", -81.971360d );
                intent.putExtra("GISLAT", 28.910041d);
                intent.putExtra("TRAVEL_TYPE", TravelType.CAR.getValue()); // 0 CAR 1 GOLF_CART
                //Intent intent = new Intent(MainActivity.this, NavigationActivity.class );

                startActivity(intent);
            }
        });

        Button navSceneBtn = findViewById(R.id.navSceneBtn);
        navSceneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String activityClassName = "com.thevillages.maplibrary.NavSceneActivity";
                intent.setClassName(getApplicationContext(), activityClassName );
                //cane gardens -81.992341d 28.892343d
                // lake shore pool 28.910041, -81.971360
                intent.putExtra("GISLONG", -81.971360d );
                intent.putExtra("GISLAT", 28.910041d);
                intent.putExtra("TRAVEL_TYPE", TravelType.GOLF_CART.getValue()); // 0 CAR 1 GOLF_CART

                startActivity(intent);
            }
        });
        Button   golfCartBtn = findViewById(R.id.golfCartBtn);
        golfCartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, MainActivity.class );
                intent.putExtra("TRAVEL_TYPE", TravelType.GOLF_CART.getValue());
                startActivity(intent);
            }
        });

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

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    private void setLocationPremissions() {
        if (ContextCompat.checkSelfPermission(StartActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
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
                                ActivityCompat.requestPermissions(StartActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_ACCESS_CODE);
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
    }
}

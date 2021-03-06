package me.jhoughton.multidrop;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.internal.IPolylineDelegate;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locMgr;
    static int GPS_ALLOWED = 0;
    LatLng currentLoc;
    PolylineOptions poly;
    public boolean justStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        justStarted = getIntent().getBooleanExtra("js",true);
        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
        ArrayList<String> locations = getIntent().getStringArrayListExtra("locations");
        if(locations == null) return;
        for(int i=1;i<locations.size();i++) {
            String l = locations.get(i);
            double lat = Double.parseDouble(l.substring(0,l.indexOf(',')));
            double lon = Double.parseDouble(l.substring(l.indexOf(',') + 1));
            // Toast.makeText(getApplicationContext(),l,Toast.LENGTH_SHORT).show();
            mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)));
        }
        if(getIntent().getStringExtra("polyline") == null) return;
        poly = new PolylineOptions();
        for(LatLng l : decodePoly(getIntent().getStringExtra("polyline"))) {
            // Log.d("UGH","LAT: " + l.latitude + " LON: " + l.longitude);
            poly.add(l);
        }
        poly.width(20).color(Color.RED);
        // Toast.makeText(getApplicationContext(), poly.toString(), Toast.LENGTH_LONG).show();
        if(getIntent().getStringExtra("bounds") == null) return;
        String bounds = getIntent().getStringExtra("bounds");
        String nes = bounds.substring(0, bounds.indexOf(';'));
        String sws = bounds.substring(bounds.indexOf(';') + 1);
        int swd = sws.indexOf(',');
        int ned = nes.indexOf(',');
        LatLng sw = new LatLng(Double.parseDouble(sws.substring(0,swd)), Double.parseDouble(sws.substring(swd+1)));
        LatLng ne = new LatLng(Double.parseDouble(nes.substring(0,ned)), Double.parseDouble(nes.substring(ned+1)));
        mMap.addPolyline(poly);
        // Toast.makeText(getApplicationContext(),"SW: " + sw.toString(),Toast.LENGTH_SHORT).show();
        // Toast.makeText(getApplicationContext(),"NE: " + ne.toString(),Toast.LENGTH_SHORT).show();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds(sw,ne), width, height, 50));
        // mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(zoomOnCoords(locations), 50));
    }

    private ArrayList<LatLng> decodePoly(String encoded) {

        ArrayList<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }

    public void bringDownPane(View v, int height) {
        v.animate().translationY(0);
        return;
    }

    public void bringUpPane(View v, int height) {
        v.animate().translationY(-1 * (height - v.findViewById(R.id.directions).getHeight()));
        return;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        requestUpdates();
    }
    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        @Override
        public void onMyLocationChange(Location location) {
            final double[] locationArray = {location.getLatitude(),location.getLongitude()};
            LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
            // Toast.makeText(getApplicationContext(), "Changed " + location.getLatitude() + " : " + location.getLongitude(), Toast.LENGTH_SHORT).show();
            final Intent i = new Intent(getApplicationContext(), DirectionsActivity.class);
            i.putExtra("location", locationArray);
            if(currentLoc == null) {
                if (mMap != null) {
                    if(justStarted) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 16.0f));
                        final RelativeLayout rl = (RelativeLayout) findViewById(R.id.bottomPane);
                        rl.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startActivity(i);
                            }
                        });
                    } else {
                        final RelativeLayout rl = (RelativeLayout) findViewById(R.id.bottomPane);
                        rl.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                finish();
                            }
                        });
                    }
                }
            }
            currentLoc = loc;
        }
    };

    private GoogleMap.OnMyLocationButtonClickListener myLocationButtonListener = new GoogleMap.OnMyLocationButtonClickListener() {
        @Override
        public boolean onMyLocationButtonClick() {
            try {
                Location ploc = mMap.getMyLocation();
                LatLng loc = new LatLng(ploc.getLatitude(),ploc.getLongitude());
                if(mMap != null){
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 16.0f));
                }
                return true;

            } catch(Exception e) {
                Toast.makeText(getApplicationContext(),"Waiting for location...",Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    };

    private LatLngBounds zoomOnCoords(ArrayList<String> al) {
        // Intent i = getIntent();
        double lat, lon;
        int in;
        // String str;
        LatLngBounds bounds = null;
        for(String cs: al) {
            Toast.makeText(getApplicationContext(),cs,Toast.LENGTH_SHORT).show();
            // str = cs.toString();
            in = cs.indexOf(',');
            lat = Double.parseDouble(cs.substring(0, in));
            lon = Double.parseDouble(cs.substring(in + 1));
            LatLng loc = new LatLng(lat,lon);
            bounds = bounds == null ? new LatLngBounds(loc,loc): bounds.including(loc);
            // Toast.makeText(getApplicationContext(),lat+" "+lon,Toast.LENGTH_SHORT).show();
            // mMap.addMarker(new MarkerOptions().position(loc));
        }
        return bounds;
        // mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng((maxlat + minlat) / 2.0, (minlon + maxlon) / 2.0)));
    }

    protected android.location.LocationListener locationListener = new android.location.LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            myLocationChangeListener.onMyLocationChange(location);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle bundle) {
            // Toast.makeText(getApplicationContext(),"status changed",Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onProviderDisabled(String provider) {
            if(provider.equals(LocationManager.GPS_PROVIDER)) {
                GPS_ALLOWED = 0;
            }
        }
        @Override
        public void onProviderEnabled(String provider) {
            if(provider.equals(LocationManager.GPS_PROVIDER)) {
                requestUpdates();
            }
        }
    };

    void requestUpdates() {
        try {
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
                locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 0f, locationListener);
                locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 0f, locationListener);
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),"GPS permission denied!",Toast.LENGTH_LONG).show();
        }
    }
    void getPermission() {
        // Assume thisActivity is the current activity
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(),"Requesting GPS permission",Toast.LENGTH_SHORT).show();
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        GPS_ALLOWED);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        getPermission();
        mMap.setTrafficEnabled(true);
        /*
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
        */
        locMgr = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        requestUpdates();
        mMap.setOnMyLocationChangeListener(myLocationChangeListener);
        mMap.setOnMyLocationButtonClickListener(myLocationButtonListener);
        mMap.setMyLocationEnabled(true);
    }
}

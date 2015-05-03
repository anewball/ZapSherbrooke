package hack.com.zap;

import android.app.Activity;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class MapsActivity extends Activity implements OnMapReadyCallback, LocationListener
{
    private GoogleMap mMap;
    private LatLng mCurrentLocation;
    private Marker mEndMarker;
    private String mEndMarkerTittle;

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mMap = mapFragment.getMap();
    }

    @Override
    public void onMapReady(GoogleMap map)
    {
        try
        {
            InputStream inputStream = getResources().openRawResource(getResources().getIdentifier("raw/nodes", "raw", getPackageName()));

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line = bufferedReader.readLine();
            while ((line = bufferedReader.readLine()) != null)
            {
                String split[] = line.split(",");

                String str1 = split[11].toString().replace("\"", "").trim();
                String str2 = split[12].toString().replace("\"", "").trim();


                if (str1.matches("((-|\\+)?[0-9]+(\\.[0-9]+)?)+") && str2.matches("((-|\\+)?[0-9]+(\\.[0-9]+)?)+"))
                {
                    double lat = Double.parseDouble(split[11].toString().replace("\"", "").trim());
                    double lon = Double.parseDouble(split[12].toString().replace("\"", "").trim());
                    loadWifi(map, split[1], lat, lon);
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {}


    }

    @Override
    public void onLocationChanged(Location location)
    {
        mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());

    }


    private void loadWifi(GoogleMap map, String name, double lat, double lon)
    {
        LatLng location = new LatLng(lat, lon);

        map.setMyLocationEnabled(true);

        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();

        // Getting the name of the best provider
        String provider = locationManager.getBestProvider(criteria, true);

        // Getting Current Location
        Location loc = locationManager.getLastKnownLocation(provider);

        if(location!=null){
            onLocationChanged(loc);
        }
        locationManager.requestLocationUpdates(provider, 20000, 0, this);

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 13));

        map.addMarker(new MarkerOptions()
                .title(name)
                .position(location));



        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener()
        {
            @Override
            public boolean onMarkerClick(Marker marker)
            {
                marker.showInfoWindow();
                mEndMarker = marker;
                String url = getMapsApiDirectionsUrl(marker.getPosition());
                ReadTask downloadTask = new ReadTask();
                downloadTask.execute(url);

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation,13));

                return true;
            }
        });
    }


    private String getMapsApiDirectionsUrl(LatLng loc)
    {
        String url = "http://maps.googleapis.com/maps/api/directions/json?";

        List<NameValuePair> params = new LinkedList<NameValuePair>();
        params.add(new BasicNameValuePair("origin", mCurrentLocation.latitude + "," + mCurrentLocation.longitude));
        params.add(new BasicNameValuePair("destination", loc.latitude + "," + loc.longitude));
        params.add(new BasicNameValuePair("sensor", "false"));

        String paramString = URLEncodedUtils.format(params, "utf-8");
        url += paramString;

        return url;
    }

    private class ReadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try {
                HttpConnection http = new HttpConnection();
                data = http.readUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            new ParserTask().execute(result);
        }
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try
            {
                jObject = new JSONObject(jsonData[0]);
                PathJSONParser parser = new PathJSONParser();
                routes = parser.parse(jObject);
                mEndMarkerTittle = parser.getEndAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> routes) {
            ArrayList<LatLng> points = null;
            PolylineOptions polyLineOptions = null;

            // traversing through routes
            for (int i = 0; i < routes.size(); i++)
            {
                points = new ArrayList<LatLng>();
                polyLineOptions = new PolylineOptions();
                List<HashMap<String, String>> path = routes.get(i);

                for (int j = 0; j < path.size(); j++)
                {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                polyLineOptions.addAll(points);
                polyLineOptions.width(5);
                polyLineOptions.color(Color.RED);
            }

            mEndMarker.setTitle(mEndMarkerTittle);
            mMap.addPolyline(polyLineOptions);
        }
    }
}

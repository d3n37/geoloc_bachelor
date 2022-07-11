package com.example.myapplication;

import android.graphics.PointF;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;
import com.lemmingapex.trilateration.LinearLeastSquaresSolver;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKit;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Circle;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.location.FilteringMode;
import com.yandex.mapkit.location.Location;
import com.yandex.mapkit.location.LocationListener;
import com.yandex.mapkit.location.LocationManager;
import com.yandex.mapkit.location.LocationStatus;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.CompositeIcon;
import com.yandex.mapkit.map.IconStyle;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.runtime.image.ImageProvider;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.RealVector;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements UserLocationObjectListener {
    private static final double DESIRED_ACCURACY = 0;
    private static final long MINIMAL_TIME = 10000; //каждые 10 секунд (10k ms) идет обновление данных о местоположении
    private static final double MINIMAL_DISTANCE = 10;
    private static final boolean USE_IN_BACKGROUND = false; //Приложение не получает данных, когда приложение не активно
    public static final int COMFORTABLE_ZOOM_LEVEL = 16;
    private MapView mapView;
    private MapObjectCollection mapObjColl;
    private UserLocationLayer userLocationLayer;
    private LocationManager locationManager;
    private LocationListener myLocationListener;
    private Point myLocation;
    private EditText rst1;
    private TextView clc_result;
    double calcul_lat; double calcul_lon;
    List<Coordinate<Double, Double, Double>> cord = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapKitFactory.setApiKey("2e906027-0614-49f7-8c60-a44ba2b1c6ee");
        MapKitFactory.initialize(this);
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapview);
        mapView.getMap().setRotateGesturesEnabled(true);
        mapView.getMap().move(new CameraPosition(new Point(0, 0), 8.0f, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 3), null);
        locationManager = MapKitFactory.getInstance().createLocationManager();
        myLocationListener = new LocationListener() {
            @Override
            public void onLocationUpdated(Location location) {
                if (myLocation == null) {
                    moveCamera(location.getPosition(), COMFORTABLE_ZOOM_LEVEL);
                }
                myLocation = location.getPosition();
            }
            @Override
            public void onLocationStatusUpdated(@NonNull LocationStatus locationStatus) {
                if (locationStatus == LocationStatus.NOT_AVAILABLE) {
                    System.out.println("Ошибка получения данных о местоположении!");
                }
            }
        };
        MapKit mapKit = MapKitFactory.getInstance();
        userLocationLayer = mapKit.createUserLocationLayer(mapView.getMapWindow());
        mapObjColl = mapView.getMap().getMapObjects().addCollection();
        userLocationLayer.setVisible(true);
        userLocationLayer.setHeadingEnabled(true);
        userLocationLayer.setObjectListener(this);
        mapObjColl.addCollection();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        mapView.onStart();
        subscribeToLocationUpdate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MapKitFactory.getInstance().onStop();
        locationManager.unsubscribe(myLocationListener);
        mapView.onStop();
    }

    public void onBtnClick(View view) {
        rst1 = findViewById(R.id.rasst1);
        String srst1 = rst1.getText().toString();
        clc_result = findViewById(R.id.calcul_result);
        if (TextUtils.isEmpty(srst1)) {
            clc_result.setText("Введите не пустую строку с расстоянием!");
        }
        else  {
            moveCamera(myLocation, COMFORTABLE_ZOOM_LEVEL);
            Double rs = Double.parseDouble(rst1.getText().toString());
            cord.add(new Coordinate<>(myLocation.getLatitude(), myLocation.getLongitude(), rs));
            double g_lat = myLocation.getLatitude(); //Широта
            double g_lon = myLocation.getLongitude(); //Долгота
            Toast.makeText(getApplicationContext(), "#" + cord.size() + "| Geopos: " + g_lat + ", " + g_lon + ", " + rs, Toast.LENGTH_SHORT).show();
        }
    }

    public Point cir_center;
    public void onFinishClick(View view){
        clc_result = findViewById(R.id.calcul_result);
        if (cord.size()<3) {
            clc_result.setText("Проведите как минимум 3 замера!");
        }
        else {
            MapKitFactory.initialize(this);
            //TestCords();
            CreateCircles();
            ResCalc();
            PinOnCalc();
        }
    }

    public void onClearClick(View view) {
        for (int i = 0; i < cord.size(); i++) {
            mapObjColl.clear();
            cord.clear();
        }
        clc_result = findViewById(R.id.calcul_result);
        clc_result.setText("Данные удалены!");
    }

    private void CreateCircles(){
        for (int i = 0; i < cord.size(); i++) {;
            float g_lon = cord.get(i).getLon().floatValue();
            float g_lat = cord.get(i).getLat().floatValue();
            float g_dst = cord.get(i).getDst().floatValue();
            cir_center = new Point(g_lon, g_lat);
            mapView = findViewById(R.id.mapview);
            mapObjColl = mapView.getMap().getMapObjects();
            Circle circle = new Circle(new Point(g_lat * 1.0, g_lon * 1.0), g_dst * 1);
            mapObjColl.addCircle(circle, -1666667, 2, 663399);
            System.out.println("Added circle with: " + g_lat + " " + g_lon + "; rad: " +  g_dst);
        }
    }

    private void subscribeToLocationUpdate() {
        if (locationManager != null && myLocationListener != null) {
            locationManager.subscribeForLocationUpdates(DESIRED_ACCURACY, MINIMAL_TIME, MINIMAL_DISTANCE, USE_IN_BACKGROUND, FilteringMode.OFF, myLocationListener);
        }
    }

    private void moveCamera(Point point, float zoom) {
        mapView.getMap().move(
                new CameraPosition(point, zoom, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1),
                null);
    }

    @Override
    public void onObjectAdded(UserLocationView userLocationView) {
        //Устаналвивает центр карты в середине экрана телефона
        userLocationLayer.setAnchor(
                new PointF((float)(mapView.getWidth() * 0.5), (float)(mapView.getHeight() * 0.5)),
                new PointF((float)(mapView.getWidth() * 0.5), (float)(mapView.getHeight() * 0.5)));

        //Добавляет зеленый кружочек на месте нахождения пользователя
        userLocationView.getArrow().setIcon(ImageProvider.fromResource(
                this, R.drawable.user_arrow));

        CompositeIcon pinIcon = userLocationView.getPin().useCompositeIcon();

        pinIcon.setIcon(
                "pin",
                ImageProvider.fromResource(this, R.drawable.search_result),
                new IconStyle().setAnchor(new PointF(0.5f, 0.5f))
                        .setZIndex(1f)
                        .setScale(0.5f)
        );
    }

    @Override
    public void onObjectRemoved(UserLocationView view) {
    }

    @Override
    public void onObjectUpdated(UserLocationView view, ObjectEvent event) {
    }

    public void PinOnCalc(){
        PlacemarkMapObject mark = mapObjColl.addPlacemark(new Point(calcul_lat * 1.0, calcul_lon * 1.0));
        mark.setOpacity(0.5f);
        mark.setIcon(ImageProvider.fromResource(this, R.drawable.clc_rslt));
    }

    public void ResCalc(){
        double[][] positions = new double[cord.size()][2];
        double[] distances = new double[cord.size()];
        for (int i = 0; i < cord.size(); i++) {
            positions[i][0] = cord.get(i).getLat();
            positions[i][1] = cord.get(i).getLon();
            distances[i] = cord.get(i).getDst();
        }
        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();
        double[] ccentroid = optimum.getPoint().toArray();
        calcul_lat = ccentroid[0];
        calcul_lon = ccentroid[1];
        clc_result.setText("Широта: " + ccentroid[0] + ", Долгота: " + ccentroid[1]);
        //double[] standardDeviation = optimum.getSigma(0);
    }

    public void TestCords(){
        cord.clear();
        //Тестовый набор данных - 19 позиций. Тверской район, ЦАО, г.Москва
        //Фактический центр - выход из м. "Пушкинская" на Тверскую ул. (здание "Известия")
        cord.add(new Coordinate<>(55.768754, 37.595647, 1000.0));
        cord.add(new Coordinate<>(55.770457, 37.599104, 1000.0));
        cord.add(new Coordinate<>(55.771893, 37.603471, 1000.0));
        cord.add(new Coordinate<>(55.770772, 37.605252, 1000.0));
        cord.add(new Coordinate<>(55.767890, 37.606328, 500.0));
        cord.add(new Coordinate<>(55.767447, 37.609329, 500.0));
        cord.add(new Coordinate<>(55.765638, 37.610323, 500.0));
        cord.add(new Coordinate<>(55.762685, 37.611668, 500.0));
        cord.add(new Coordinate<>(55.761308, 37.607653, 500.0));
        cord.add(new Coordinate<>(55.759709, 37.605603, 1000.0));
        cord.add(new Coordinate<>(55.758916, 37.602457, 1000.0));
        cord.add(new Coordinate<>(55.759231, 37.597427, 1000.0));
        cord.add(new Coordinate<>(55.762604, 37.595088, 1000.0));
        cord.add(new Coordinate<>(55.765661, 37.590803, 1000.0));
        cord.add(new Coordinate<>(55.768236, 37.594681, 1000.0));
        cord.add(new Coordinate<>(55.768737, 37.597724, 1000.0));
        cord.add(new Coordinate<>(55.767777, 37.599427, 500.0));
        cord.add(new Coordinate<>(55.765772, 37.599638, 500.0));
        cord.add(new Coordinate<>(55.764243, 37.602004, 500.0));
    }
}

class Coordinate<T1, T2, T3> {
    private T1 lat;
    private T2 lon;
    private T3 dst;

    public Coordinate(T1 lat, T2 lon, T3 dst) {
        this.lat = lat;
        this.lon = lon;
        this.dst = dst;
    }

    public T1 getLat() { return lat; }
    public T2 getLon() { return lon; }
    public T3 getDst() { return dst; }
}

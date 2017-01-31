package agency.tango.viking.map.views;

import android.databinding.ViewDataBinding;
import android.os.Bundle;
import android.support.annotation.LayoutRes;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;

import agency.tango.viking.mvvm.ActivityView;
import agency.tango.viking.mvvm.ViewModel;

public abstract class MapAwareActivityView<VM extends ViewModel, VD extends ViewDataBinding> extends
    ActivityView<VM, VD> {

  private static final String MAPVIEW_BUNDLE_KEY = "mapview_bundle_key";

  public MapAwareActivityView(@LayoutRes int layoutIdRes) {
    super(layoutIdRes);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    MapsInitializer.initialize(this);

    Bundle mapViewBundle;
    if (savedInstanceState != null) {
      mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
    } else {
      mapViewBundle = new Bundle();
    }
    mapView().onCreate(mapViewBundle);
  }

  public abstract MapView mapView();

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
    if (mapViewBundle == null) {
      mapViewBundle = new Bundle();
      outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
    }

    mapView().onSaveInstanceState(mapViewBundle);
  }

  @Override
  public void onResume() {
    super.onResume();
    mapView().onResume();
  }

  @Override
  public void onStart() {
    super.onStart();
    mapView().onStart();
  }

  @Override
  public void onStop() {
    super.onStop();
    mapView().onStop();
  }

  @Override
  public void onPause() {
    mapView().onPause();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    mapView().onDestroy();
    super.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView().onLowMemory();
  }
}
package agency.tango.viking.mvvm;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;

import javax.inject.Inject;
import javax.inject.Provider;

public class GenericViewModelFactory<T extends ViewModel> implements ViewModelProvider.Factory {

  private final Provider<T> viewModelProvider;

  @Inject
  public GenericViewModelFactory(Provider<T> viewModelProvider) {
    this.viewModelProvider = viewModelProvider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends ViewModel> T create(Class<T> modelClass) {
    try {
      return (T) viewModelProvider.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
package com.frostchein.atlant.activities.send;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.widget.EditText;
import butterknife.BindView;
import butterknife.OnClick;
import com.frostchein.atlant.R;
import com.frostchein.atlant.activities.base.BaseActivity;
import com.frostchein.atlant.activities.camera.CameraActivity;
import com.frostchein.atlant.dagger2.component.AppComponent;
import com.frostchein.atlant.dagger2.component.DaggerSendActivityComponent;
import com.frostchein.atlant.dagger2.component.SendActivityComponent;
import com.frostchein.atlant.dagger2.modules.SendActivityModule;
import com.frostchein.atlant.model.Balance;
import com.frostchein.atlant.model.Transactions;
import com.frostchein.atlant.utils.ConnectivityUtil;
import com.frostchein.atlant.utils.DecimalDigitsInputFilter;
import com.frostchein.atlant.utils.DialogUtil;
import com.frostchein.atlant.utils.IntentUtil;
import com.frostchein.atlant.utils.IntentUtil.EXTRA_STRING;
import com.frostchein.atlant.views.AtlToolbarView;
import com.frostchein.atlant.views.BaseCustomView;
import javax.inject.Inject;
import org.greenrobot.eventbus.EventBus;

public class SendActivity extends BaseActivity implements SendView {

  @Inject
  SendPresenter sendPresenter;
  @Inject
  AtlToolbarView atlToolbarView;

  @BindView(R.id.send_address_edit)
  EditText send_address_edit;
  @BindView(R.id.send_value)
  EditText send_value_edit;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_send);
    setToolbarTitle(R.string.send_title);
    atlToolbarView.removeTitle();
    sendPresenter.onCreate(getIntent().getStringExtra(EXTRA_STRING.ADDRESS));
    EventBus.getDefault().register(sendPresenter);
  }

  @Override
  public void initUI() {
    send_value_edit.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(100, 18)});
  }

  @Override
  protected void onDestroy() {
    EventBus.getDefault().unregister(sendPresenter);
    super.onDestroy();
  }

  @Override
  protected BaseCustomView getCustomToolbar() {
    return atlToolbarView;
  }

  @Override
  protected void setupComponent(AppComponent appComponent) {
    SendActivityComponent component = DaggerSendActivityComponent.builder()
        .appComponent(appComponent)
        .sendActivityModule(new SendActivityModule(this))
        .build();
    component.inject(this);
  }

  @Override
  public boolean useToolbar() {
    return true;
  }

  @Override
  public boolean useDrawer() {
    return false;
  }

  @Override
  public boolean useToolbarActionHome() {
    return true;
  }

  @Override
  public boolean useToolbarActionQRCode() {
    return true;
  }

  @Override
  public boolean useCustomToolbar() {
    return true;
  }

  @Override
  public boolean useSwipeRefresh() {
    return false;
  }

  @Override
  public boolean timerLogOut() {
    return true;
  }

  @Override
  protected void onToolbarQR() {
    super.onToolbarQR();
    goToCameraActivity(false, CameraActivity.TAG_FROM_SEND);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == BaseActivity.REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
      sendPresenter.onCreate(data.getStringExtra(IntentUtil.EXTRA_STRING.ADDRESS));
    }
  }

  @OnClick(R.id.bt_close)
  void OnClick() {
    sendPresenter.onValidate();
  }

  @Override
  public void setAddress(String address) {
    send_address_edit.setText(address);
  }

  @Override
  public void setValue(String SWValue) {
    send_value_edit.setText(SWValue);
  }

  @Override
  public void setBalance(Balance balance) {
    atlToolbarView.setContent(balance);
  }

  @Override
  public String getAddress() {
    return send_address_edit.getText().toString();
  }

  @Override
  public String getValue() {
    return send_value_edit.getText().toString();
  }

  @Override
  public void onError(String message) {
    onScreenError(message);
  }

  @Override
  public void onTimeout() {
    onScreenError(getString(R.string.system_timeout));
  }

  @Override
  public void onSuccessfulSend(Transactions transactions) {
    showMessage(getString(R.string.send_successful_send));
    setResult(RESULT_OK);
    finish();
  }

  @Override
  public void dialogConfirmTransaction() {
    String text = String.format(getResources().getString(R.string.send_dialog_text_warning),
        send_value_edit.getText(), send_address_edit.getText());
    DialogUtil.openDialogChoice(getContext(), R.string.app_name, text, R.string.bt_dialog_continue,
        R.string.bt_dialog_back, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            DialogUtil.hideDialog();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
              @Override
              public void run() {
                if (!ConnectivityUtil.isNetworkOnline(getContext())) {
                  onNoInternetConnection();
                } else {
                  sendPresenter.onSendTransaction();
                }
              }
            }, 50);
          }
        });
  }

  @Override
  public void onNoMoney() {
    onScreenError(getString(R.string.send_no_money));
  }

  @Override
  public void onFormatMoney() {
    onScreenError(getString(R.string.send_no_correct_money));
  }

  @Override
  public void onInvalidAddress() {
    onScreenError(getString(R.string.send_invalid_data));
  }

}

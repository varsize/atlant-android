package com.frostchein.atlant.activities.send;

import com.frostchein.atlant.Config;
import com.frostchein.atlant.R;
import com.frostchein.atlant.activities.base.BaseActivity;
import com.frostchein.atlant.activities.base.BasePresenter;
import com.frostchein.atlant.events.network.OnStatusError;
import com.frostchein.atlant.events.network.OnStatusSuccess;
import com.frostchein.atlant.events.network.OnStatusTimeOut;
import com.frostchein.atlant.model.Balance;
import com.frostchein.atlant.model.Transactions;
import com.frostchein.atlant.rest.AtlantApi;
import com.frostchein.atlant.rest.AtlantClient;
import com.frostchein.atlant.rest.NetModule;
import com.frostchein.atlant.rest.TransactionRestHandler;
import com.frostchein.atlant.utils.CredentialHolder;
import com.frostchein.atlant.utils.DigitsUtils;
import com.frostchein.atlant.utils.ParserDataFromQr;
import java.math.BigInteger;
import javax.inject.Inject;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.web3j.crypto.WalletUtils;

public class SendPresenterImpl implements SendPresenter, BasePresenter {

  private SendView view;
  private AtlantClient atlantClient;
  private boolean isPrepare = true;
  private Balance balance;
  private Transactions transactions;

  @Inject
  SendPresenterImpl(SendView view) {
    this.view = view;
    AtlantApi atlantApi = NetModule.getRetrofit(Config.ENDPOINT_URL).create(AtlantApi.class);
    atlantClient = new AtlantClient(atlantApi);
  }


  @Override
  public void onCreate(String line) {
    if (view != null) {
      balance = CredentialHolder.getWalletBalance(view.getContext());
      transactions = CredentialHolder.getWalletTransaction(view.getContext());
      ParserDataFromQr parserDataFromQr = new ParserDataFromQr(line);
      if (parserDataFromQr.isCorrect()) {
        view.setAddress(parserDataFromQr.getAddress());
        view.setValue(parserDataFromQr.getAmount());
      }
      view.setBalance(balance);
    }
  }

  @Override
  public void onValidate() {
    if (view != null) {

      String address = view.getAddress();
      if (!WalletUtils.isValidAddress(address)) {
        view.onInvalidAddress();
        return;
      }

      double value;
      try {
        value = Double.parseDouble(view.getValue());
        if (value < 0.000000000000000001) {
          view.onFormatMoney();
          return;
        }
      } catch (Exception e) {
        view.onFormatMoney();
        return;
      }

      if (value > Double.parseDouble(DigitsUtils.ATLtoString(new BigInteger(balance.getResult())))) {
        view.onNoMoney();
        return;
      }

      view.dialogConfirmTransaction();
    }
  }

  @Override
  public void onSendTransaction() {
    if (view != null) {
      isPrepare = true;
      view.showProgressDialog(view.getContext().getString(R.string.send_preparing));
      TransactionRestHandler.preparationTransaction(atlantClient);
    }
  }


  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onError(OnStatusError onStatusError) {
    if (view != null) {
      if (onStatusError.getRequest() != BaseActivity.REQUEST_CODE_SEND) {
        return;
      }
      view.hideProgressDialog();
      if (onStatusError.getMessage() == null) {
        view.onError(view.getContext().getString(R.string.send_error_send));
      } else {
        view.onError(onStatusError.getMessage());
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onTimeout(OnStatusTimeOut onStatusTimeOut) {
    if (view != null) {
      if (onStatusTimeOut.getRequest() != BaseActivity.REQUEST_CODE_SEND) {
        return;
      }
      view.hideProgressDialog();
      view.onTimeout();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onSuccess(final OnStatusSuccess onStatusSuccess) {
    if (view != null) {
      if (onStatusSuccess.getRequest() != BaseActivity.REQUEST_CODE_SEND) {
        return;
      }
      view.hideProgressDialog();

      if (isPrepare) {
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            isPrepare = false;
            view.showProgressDialog(view.getContext().getString(R.string.send_process));
            TransactionRestHandler
                .sendTransaction(atlantClient, onStatusSuccess.getNonce(), onStatusSuccess.getGasPrice(),
                    view.getAddress(), view.getValue());
          }
        }, 100);
      } else {
        //  balance.setResult(balance.getResult() - value);
        CredentialHolder.saveWalletInfo(view.getContext(), balance, transactions);
        view.onSuccessfulSend(onStatusSuccess.getTransactions());
      }
    }
  }
}

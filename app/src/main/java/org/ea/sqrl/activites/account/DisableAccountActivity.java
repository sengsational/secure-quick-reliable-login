package org.ea.sqrl.activites.account;

import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

import org.ea.sqrl.R;
import org.ea.sqrl.activites.base.BaseActivity;
import org.ea.sqrl.processors.CommunicationFlowHandler;
import org.ea.sqrl.processors.SQRLStorage;
import org.ea.sqrl.utils.Utils;

public class DisableAccountActivity extends BaseActivity {
    protected CommunicationFlowHandler communicationFlowHandler = CommunicationFlowHandler.getInstance(this, handler);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disable_account);

        communicationFlowHandler.setupAskPopupWindow(getLayoutInflater(), handler);
        communicationFlowHandler.setupErrorPopupWindow(getLayoutInflater());
        setupProgressPopupWindow(getLayoutInflater());
        setupErrorPopupWindow(getLayoutInflater());

        SQRLStorage storage = SQRLStorage.getInstance(DisableAccountActivity.this.getApplicationContext());

        final EditText txtDisablePassword = findViewById(R.id.txtDisablePassword);
        findViewById(R.id.btnDisableAccount).setOnClickListener(v -> {
            handler.post(() -> showProgressPopup());

            new Thread(() -> {
                boolean decryptionOk = storage.decryptIdentityKey(txtDisablePassword.getText().toString(), entropyHarvester, false);
                if(decryptionOk) {
                    Utils.clearQuickPassDelayed(DisableAccountActivity.this);
                } else {
                    showErrorMessage(R.string.decrypt_identity_fail);
                    storage.clearQuickPass();
                    storage.clear();
                    handler.post(() -> {
                        txtDisablePassword.setText("");
                        hideProgressPopup();
                    });
                    return;
                }

                handler.post(() -> txtDisablePassword.setText(""));

                if(communicationFlowHandler.isUrlBasedLogin()) {
                    communicationFlowHandler.addAction(CommunicationFlowHandler.Action.QUERY_WITHOUT_SUK);
                    communicationFlowHandler.addAction(CommunicationFlowHandler.Action.LOCK_ACCOUNT_CPS);
                } else {
                    communicationFlowHandler.addAction(CommunicationFlowHandler.Action.QUERY_WITHOUT_SUK_QRCODE);
                    communicationFlowHandler.addAction(CommunicationFlowHandler.Action.LOCK_ACCOUNT);
                }

                communicationFlowHandler.setDoneAction(() -> {
                    storage.clear();
                    handler.post(() -> {
                        hideProgressPopup();
                        showInfoMessage(
                                R.string.disable_account_title,
                                R.string.disable_account_successful,
                                () -> closeActivity()
                        );
                    });
                });

                communicationFlowHandler.setErrorAction(() -> {
                    storage.clear();
                    handler.post(() -> hideProgressPopup());
                });

                communicationFlowHandler.handleNextAction();

            }).start();
        });
    }

    protected void closeActivity() {
        if(communicationFlowHandler.isUrlBasedLogin()) {
            DisableAccountActivity.this.finishAffinity();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                DisableAccountActivity.this.finishAndRemoveTask();
            }
        } else {
            DisableAccountActivity.this.finish();
        }
    }
}

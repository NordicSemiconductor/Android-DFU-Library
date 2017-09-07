package no.nordicsemi.android.dfu;


import android.app.Activity;

public class DfuServiceImpl extends DfuBaseService {

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return DfuActivity.class;
    }

    @Override
    protected boolean isDebug() {
        return true;
    }
}

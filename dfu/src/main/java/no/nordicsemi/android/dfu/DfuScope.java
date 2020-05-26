package no.nordicsemi.android.dfu;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings("WeakerAccess")
@Retention(RetentionPolicy.SOURCE)
@IntDef(value = {
            DfuServiceInitiator.SCOPE_SYSTEM_COMPONENTS,
            DfuServiceInitiator.SCOPE_APPLICATION
        },
        flag = true)
public @interface DfuScope {}

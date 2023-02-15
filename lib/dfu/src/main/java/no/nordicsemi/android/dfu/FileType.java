package no.nordicsemi.android.dfu;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings("WeakerAccess")
@Retention(RetentionPolicy.SOURCE)
@IntDef(value = {
            DfuBaseService.TYPE_SOFT_DEVICE,
            DfuBaseService.TYPE_BOOTLOADER,
            DfuBaseService.TYPE_APPLICATION
        },
        flag = true)
public @interface FileType {}


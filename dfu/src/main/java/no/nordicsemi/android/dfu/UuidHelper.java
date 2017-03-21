package no.nordicsemi.android.dfu;

import android.content.Intent;
import android.os.ParcelUuid;
import android.os.Parcelable;

/* package */ class UuidHelper {

	/* package */ static void assignCustomUuids(final Intent intent) {
		Parcelable[] uuids = intent.getParcelableArrayExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_LEGACY_DFU);
		if (uuids != null && uuids.length == 4) {
			LegacyDfuImpl.DFU_SERVICE_UUID       = uuids[0] != null ? ((ParcelUuid) uuids[0]).getUuid() : LegacyDfuImpl.DEFAULT_DFU_SERVICE_UUID;
			LegacyDfuImpl.DFU_CONTROL_POINT_UUID = uuids[1] != null ? ((ParcelUuid) uuids[1]).getUuid() : LegacyDfuImpl.DEFAULT_DFU_CONTROL_POINT_UUID;
			LegacyDfuImpl.DFU_PACKET_UUID        = uuids[2] != null ? ((ParcelUuid) uuids[2]).getUuid() : LegacyDfuImpl.DEFAULT_DFU_PACKET_UUID;
			LegacyDfuImpl.DFU_VERSION_UUID       = uuids[3] != null ? ((ParcelUuid) uuids[3]).getUuid() : LegacyDfuImpl.DEFAULT_DFU_VERSION_UUID;
		} else {
			LegacyDfuImpl.DFU_SERVICE_UUID       = LegacyDfuImpl.DEFAULT_DFU_SERVICE_UUID;
			LegacyDfuImpl.DFU_CONTROL_POINT_UUID = LegacyDfuImpl.DEFAULT_DFU_CONTROL_POINT_UUID;
			LegacyDfuImpl.DFU_PACKET_UUID        = LegacyDfuImpl.DEFAULT_DFU_PACKET_UUID;
			LegacyDfuImpl.DFU_VERSION_UUID       = LegacyDfuImpl.DEFAULT_DFU_VERSION_UUID;
		}

		uuids = intent.getParcelableArrayExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_SECURE_DFU);
		if (uuids != null && uuids.length == 3) {
			SecureDfuImpl.DFU_SERVICE_UUID       = uuids[0] != null ? ((ParcelUuid) uuids[0]).getUuid() : SecureDfuImpl.DEFAULT_DFU_SERVICE_UUID;
			SecureDfuImpl.DFU_CONTROL_POINT_UUID = uuids[1] != null ? ((ParcelUuid) uuids[1]).getUuid() : SecureDfuImpl.DEFAULT_DFU_CONTROL_POINT_UUID;
			SecureDfuImpl.DFU_PACKET_UUID        = uuids[2] != null ? ((ParcelUuid) uuids[2]).getUuid() : SecureDfuImpl.DEFAULT_DFU_PACKET_UUID;
		} else {
			SecureDfuImpl.DFU_SERVICE_UUID       = SecureDfuImpl.DEFAULT_DFU_SERVICE_UUID;
			SecureDfuImpl.DFU_CONTROL_POINT_UUID = SecureDfuImpl.DEFAULT_DFU_CONTROL_POINT_UUID;
			SecureDfuImpl.DFU_PACKET_UUID        = SecureDfuImpl.DEFAULT_DFU_PACKET_UUID;
		}

		uuids = intent.getParcelableArrayExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_EXPERIMENTAL_BUTTONLESS_DFU);
		if (uuids != null && uuids.length == 2) {
			ExperimentalButtonlessDfuImpl.EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = uuids[0] != null ? ((ParcelUuid) uuids[0]).getUuid() : ExperimentalButtonlessDfuImpl.DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID;
			ExperimentalButtonlessDfuImpl.EXPERIMENTAL_BUTTONLESS_DFU_UUID         = uuids[1] != null ? ((ParcelUuid) uuids[1]).getUuid() : ExperimentalButtonlessDfuImpl.DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID;
		} else {
			ExperimentalButtonlessDfuImpl.EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = ExperimentalButtonlessDfuImpl.DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID;
			ExperimentalButtonlessDfuImpl.EXPERIMENTAL_BUTTONLESS_DFU_UUID         = ExperimentalButtonlessDfuImpl.DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID;
		}
	}
}

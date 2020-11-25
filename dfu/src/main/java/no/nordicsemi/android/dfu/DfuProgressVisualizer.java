package no.nordicsemi.android.dfu;

// This class has been extracted from DfuProgressInfo in 1.11.0 to allow Xamarin bindings.
// See: https://github.com/NordicSemiconductor/Android-DFU-Library/issues/221

/**
 * This is an internal DFU interface. Applications should not use it.
 * It has public access modifier to allow Xamarin bindings.
 * @since 1.11.0
 */
public interface DfuProgressVisualizer {
	/**
	 * Creates or updates the notification in the Notification Manager.
	 * Sends broadcast with given progress state to the activity.
	 */
	void updateProgressNotification();
}

package no.nordicsemi.android.dfu;

/* package */ public interface DfuController {

	/**
	 * Pauses the DFU operation. Call {@link #resume()} to resume, or {@link #abort()} to cancel.
	 * This method does nothing if DFU operation was already paused.
	 */
	void pause();

	/**
	 * Resumes a previously paused DFU operation.
	 * @see #pause()
	 */
	void resume();

	/**
	 * Aborts the DFU operation after it has started.
	 */
	void abort();
}

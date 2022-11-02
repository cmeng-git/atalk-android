/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import javax.media.Controls;

import timber.log.Timber;

/**
 * Provides an abstract implementation of <code>Controls</code> which facilitates implementers by
 * requiring them to only implement {@link Controls#getControls()}.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractControls implements Controls
{
	/**
	 * Implements {@link Controls#getControl(String)}. Invokes {@link #getControls()} and then
	 * looks for a control of the specified type in the returned array of controls.
	 *
	 * @param controlType
	 * 		a <code>String</code> value naming the type of the control of this instance to be
	 * 		retrieved
	 * @return an <code>Object</code> which represents the control of this instance with the specified
	 * type
	 */
	public Object getControl(String controlType)
	{
		return getControl(this, controlType);
	}

	/**
	 * Gets the control of a specific <code>Controls</code> implementation of a specific type if such a
	 * control is made available through {@link Controls#getControls()}; otherwise, returns
	 * <code>null</code>.
	 *
	 * @param controlsImpl
	 * 		the implementation of <code>Controls</code> which is to be queried for its list of
	 * 		controls so that the control of the specified type can be looked for
	 * @param controlType
	 * 		a <code>String</code> value which names the type of the control to be retrieved
	 * @return an <code>Object</code> which represents the control of <code>controlsImpl</code> of the
	 * specified <code>controlType</code> if such a control is made available through
	 * <code>Controls#getControls()</code>; otherwise, <code>null</code>
	 */
	public static Object getControl(Controls controlsImpl, String controlType)
	{
		Object[] controls = controlsImpl.getControls();

		if ((controls != null) && (controls.length > 0)) {
			Class<?> controlClass;

			try {
				controlClass = Class.forName(controlType);
			}
			catch (ClassNotFoundException cnfe) {
				controlClass = null;
				Timber.w(cnfe, "Failed to find control class %s", controlType);
			}
			if (controlClass != null) {
				for (Object control : controls) {
					if (controlClass.isInstance(control))
						return control;
				}
			}
		}
		return null;
	}

	/**
	 * Returns an instance of a specific <code>Class</code> which is either a control of a specific
	 * <code>Controls</code> implementation or the <code>Controls</code> implementation itself if it is an
	 * instance of the specified <code>Class</code>. The method is similar to
	 * {@link #getControl(Controls, String)} in querying the specified <code>Controls</code>
	 * implementation about a control of the specified <code>Class</code> but is different in
	 * looking at the type hierarchy of the <code>Controls</code> implementation for the specified
	 * <code>Class</code>.
	 *
	 * @param controlsImpl
	 * 		the <code>Controls</code> implementation to query
	 * @param controlType
	 * 		the runtime type of the instance to be returned
	 * @return an instance of the specified <code>controlType</code> if such an instance can be found
	 * among the controls of the specified <code>controlsImpl</code> or <code>controlsImpl</code> is
	 * an instance of the specified <code>controlType</code>; otherwise, <code>null</code>
	 */
	@SuppressWarnings("unchecked")
	public static <T> T queryInterface(Controls controlsImpl, Class<T> controlType)
	{
		T control;
		if (controlsImpl == null) {
			control = null;
		}
		else {
			control = (T) controlsImpl.getControl(controlType.getName());
			if ((control == null) && controlType.isInstance(controlsImpl))
				control = (T) controlsImpl;
		}
		return control;
	}

	/**
	 * Returns an instance of a specific <code>Class</code> which is either a control of a specific
	 * <code>Controls</code> implementation or the <code>Controls</code> implementation itself if it is an
	 * instance of the specified <code>Class</code>. The method is similar to
	 * {@link #getControl(Controls, String)} in querying the specified <code>Controls</code>
	 * implementation about a control of the specified <code>Class</code> but is different in looking at
	 * the type hierarchy of the <code>Controls</code> implementation for the specified <code>Class</code>.
	 *
	 * @param controlsImpl
	 * 		the <code>Controls</code> implementation to query
	 * @param controlType
	 * 		the runtime type of the instance to be returned
	 * @return an instance of the specified <code>controlType</code> if such an instance can be found
	 * among the controls of the specified <code>controlsImpl</code> or <code>controlsImpl</code> is
	 * an instance of the specified <code>controlType</code>; otherwise, <code>null</code>
	 */
	public static Object queryInterface(Controls controlsImpl, String controlType)
	{
		Object control;
		if (controlsImpl == null) {
			control = null;
		}
		else {
			control = controlsImpl.getControl(controlType);
			if (control == null) {
				Class<?> controlClass;

				try {
					controlClass = Class.forName(controlType);
				}
				catch (ClassNotFoundException cnfe) {
					controlClass = null;
					Timber.w(cnfe, "Failed to find control class %s", controlType);
				}
				if ((controlClass != null) && controlClass.isInstance(controlsImpl)) {
					control = controlsImpl;
				}
			}
		}
		return control;
	}
}

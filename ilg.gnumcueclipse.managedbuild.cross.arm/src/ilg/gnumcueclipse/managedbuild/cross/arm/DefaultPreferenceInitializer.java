/*******************************************************************************
 * Copyright (c) 2015 Liviu Ionescu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Liviu Ionescu - initial version
 *******************************************************************************/

package ilg.gnumcueclipse.managedbuild.cross.arm;

import ilg.gnumcueclipse.managedbuild.cross.preferences.DefaultPreferences;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.INodeChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.NodeChangeEvent;
import org.osgi.service.prefs.Preferences;

/**
 * Initialisations are executed in two different moments: as the first step
 * during bundle inits and after all defaults are loaded from all possible
 * sources
 * 
 */
public class DefaultPreferenceInitializer extends AbstractPreferenceInitializer {

	// ------------------------------------------------------------------------

	/**
	 * Early inits. Preferences set here might be overridden by plug-in
	 * preferences.ini, product .ini or command line option.
	 */
	@Override
	public void initializeDefaultPreferences() {

		if (Activator.getInstance().isDebugging()) {
			System.out.println("DefaultPreferenceInitializer.initializeDefaultPreferences()");
		}

		DefaultPreferences defaultPreferences = new DefaultPreferences(Activator.PLUGIN_ID);
		// Default toolchain name
		String toolchainName = ToolchainDefinition.DEFAULT_TOOLCHAIN_NAME;
		defaultPreferences.putToolchainName(toolchainName);

		// When the 'ilg.gnuarmeclipse.managedbuild.cross' node is completely
		// added to /default, a NodeChangeEvent is raised.
		// This is the moment when all final default values are in, possibly
		// set by product or command line.

		Preferences prefs = Platform.getPreferencesService().getRootNode().node(DefaultScope.SCOPE);
		if (prefs instanceof IEclipsePreferences) {
			((IEclipsePreferences) prefs).addNodeChangeListener(new LateInitializer());
		}
	}

	/**
	 * INodeChangeListener for late initialisations.
	 */
	private class LateInitializer implements INodeChangeListener {

		@Override
		public void added(NodeChangeEvent event) {

			if (Activator.getInstance().isDebugging()) {
				System.out.println("LateInitializer.added() " + event + " " + event.getChild().name());
			}

			if (Activator.PLUGIN_ID.equals(event.getChild().name())) {

				finalizeInitializationsDefaultPreferences();

				// We're done, de-register listener.
				((IEclipsePreferences) (event.getSource())).removeNodeChangeListener(this);
			}
		}

		@Override
		public void removed(NodeChangeEvent event) {

			if (Activator.getInstance().isDebugging()) {
				System.out.println("LateInitializer.removed() " + event);
			}
		}

		/**
		 * The second step of defaults initialisation.
		 */
		public void finalizeInitializationsDefaultPreferences() {

			String value;

			DefaultPreferences commonDefaultPreferences = new DefaultPreferences(
					ilg.gnumcueclipse.managedbuild.cross.Activator.PLUGIN_ID);
			DefaultPreferences defaultPreferences = new DefaultPreferences(Activator.PLUGIN_ID);
			DefaultPreferences deprecatedDefaultPreferences = new DefaultPreferences("ilg.gnuarmeclipse.managedbuild.cross");

			// Try to get the build tools path from the common store.
			value = commonDefaultPreferences.getBuildToolsPath();
			if (value.isEmpty()) {
				// If not there, try to get it from the GNU MCU ARM Eclipse store.
				value = defaultPreferences.getBuildToolsPath();
				if (value.isEmpty()) {
					// If not there, try to get it from the GNU ARM Eclipse store.
					value = deprecatedDefaultPreferences.getBuildToolsPath();
					if (value.isEmpty()) {
						// If not defined elsewhere, discover build tools.
						value = DefaultPreferences.discoverBuildToolsPath();
					}
				}
				if (!value.isEmpty()) {
					// Copy from old store to new store.
					commonDefaultPreferences.putBuildToolsPath(value);
				}
			}

			// Toolchains paths
			for (ToolchainDefinition toolchain : ToolchainDefinition.getList()) {

				String toolchainName = toolchain.getName();

				// Check if the toolchain path is explictly defined in the
				// default preferences.
				String path = defaultPreferences.getToolchainPath(toolchainName);
				if (path.isEmpty()) {
					path = deprecatedDefaultPreferences.getToolchainPath(toolchainName);
					if (!path.isEmpty()) {
						// Copy from deprecated store to new store.
						defaultPreferences.putToolchainPath(toolchainName, path);
					}
				}
				if (!path.isEmpty()) {
					continue; // Already defined, use it as is.
				}

				// Check if the search path is defined in the default
				// preferences.
				String searchPath = defaultPreferences.getToolchainSearchPath(toolchainName);
				if (searchPath.isEmpty()) {

					// If not defined, get the OS Specific default
					// from preferences.ini.
					searchPath = defaultPreferences.getToolchainSearchPathOs(toolchainName);
					if (!searchPath.isEmpty()) {
						// Store the search path in the preferences
						defaultPreferences.putToolchainSearchPath(toolchainName, searchPath);
					}
				}

				if (!searchPath.isEmpty()) {
					// If the search path is known, discover toolchain.
					value = DefaultPreferences.discoverToolchainPath(toolchainName, searchPath);
					if (value != null && !value.isEmpty()) {
						// If the toolchain path was finally discovered, store
						// it in the preferences.
						defaultPreferences.putToolchainPath(toolchainName, value);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------
}
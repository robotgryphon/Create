package com.simibubi.create.infrastructure.ponder;

public class PonderIndex {

	public static final boolean REGISTER_DEBUG_SCENES = true;

	public static void register() {
		if (REGISTER_DEBUG_SCENES)
			DebugScenes.registerAll();
	}

	public static boolean editingModeActive() {
		return false;
	}

}

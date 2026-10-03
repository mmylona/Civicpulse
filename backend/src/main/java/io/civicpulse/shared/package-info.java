/**
 * Shared kernel: the error model, request ids, time and web configuration used by
 * every module.
 *
 * <p>This module is <em>open</em>: other modules may use all of its types. In return it
 * must never depend on any other module.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package io.civicpulse.shared;

import org.springframework.modulith.ApplicationModule;

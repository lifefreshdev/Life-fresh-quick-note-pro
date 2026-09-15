package com.example.config

/**
 * Global compile-time feature flags for production releases.
 *
 * Set AI_FEATURES_ENABLED to false for the non-AI public freeze release.
 */
object ReleaseFeatureFlags {
    const val AI_FEATURES_ENABLED: Boolean = true
}

package com.translatelab.backend.translation.service;

public record DocumentCleanupResult(
        int abandonedJobs,
        int deletedSources,
        int deletedResults,
        int deletedOrphanUploads,
        int failures
) {

    public int processed() {
        return abandonedJobs
                + deletedSources
                + deletedResults
                + deletedOrphanUploads
                + failures;
    }
}

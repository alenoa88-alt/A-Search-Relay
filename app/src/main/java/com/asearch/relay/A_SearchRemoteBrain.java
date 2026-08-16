package com.asearch.relay;

import com.asearch.relay.data.Entities;

import java.util.List;

/**
 * Integration boundary for the future remote Â Search brain.
 * v0.4A intentionally leaves it disconnected and uses LocalCandidateBrain.
 */
public final class A_SearchRemoteBrain implements ManagerBrain {
    @Override
    public ManagerCandidate analyze(
            Entities.MessageEntity current,
            List<Entities.MessageEntity> recentContext,
            Entities.RelationshipProfileEntity relationship
    ) {
        throw new UnsupportedOperationException(
                "Remote Â Search reasoning is not connected in the strict read-only v0.4A build."
        );
    }
}


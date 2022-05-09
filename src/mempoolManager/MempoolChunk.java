package mempoolManager;

import ledger.AppContent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toSet;

public class MempoolChunk {

    private final UUID stateID;

    private final Set<UUID> previous;

    private final Set<UUID> usedIds;

    private final List<AppContent> addedContent;

    private final int weight;

    public MempoolChunk(UUID stateID, Set<UUID> previous, List<AppContent> addedContent) {
        this.stateID = stateID;
        this.previous = previous;
        this.usedIds = addedContent.stream().map(AppContent::getId).collect(toSet());
        this.addedContent = addedContent;
        this.weight = 1;
    }

    public UUID getId() {
        return stateID;
    }

    public Set<UUID> getPreviousChunksIds() {
        return previous;
    }

    public Set<UUID> getUsedIds() {
        return usedIds;
    }

    public List<AppContent> getAddedContent() {
        return addedContent;
    }

    public int getInherentWeight() {
        return weight;
    }

}

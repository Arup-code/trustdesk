package app.dexcode.trustdesk.dto;

public record AdminResetSummary(
    int ticketsReset,
    int toolActionsDeleted,
    int approvalsDeleted,
    int draftRepliesDeleted,
    int agentRunTracesDeleted,
    int evalRunsDeleted
) {}

package com.asearch.relay;

public final class FutureMessaging {
    private FutureMessaging() {}

    public enum State {
        DRAFTED,
        READY_FOR_APPROVAL,
        APPROVED,
        AUTO_SEND_ALLOWED,
        SENT,
        FAILED,
        CANCELLED
    }

    public static final class MessageDraft {
        public String id;
        public String contactId;
        public String roomId;
        public String text;
        public String language;
        public double relationshipConfidence;
        public double identityConfidence;
        public double styleConfidence;
        public double intentConfidence;
        public State state = State.DRAFTED;
    }

    public static final class MessageApproval {
        public String draftId;
        public boolean approved;
        public String approvedBy;
        public long approvedAt;
        public String policyReason;
    }

    public static final class MessageAction {
        public String draftId;
        public State requestedState;
        public boolean financialCommitment;
        public boolean contractualCommitment;
        public boolean performanceCommitment;
        public boolean sensitiveNegotiation;
        public boolean humanApprovalRequired = true;
    }

    public static final class MessageExecutionResult {
        public String actionId;
        public State state;
        public String externalMessageId;
        public String failureReason;
        public long completedAt;
    }
}


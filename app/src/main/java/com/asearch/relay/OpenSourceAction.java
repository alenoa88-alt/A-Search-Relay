package com.asearch.relay;

import android.content.Context;

public interface OpenSourceAction {
    Result open(Context context, Evidence evidence);

    final class Evidence {
        public String source;
        public String roomId;
        public String title;
        public String relevantMessageId;
        public long timestamp;
        public String sourceUrl;
    }

    final class Result {
        public final boolean exactTargetOpened;
        public final String explanation;

        public Result(boolean exactTargetOpened, String explanation) {
            this.exactTargetOpened = exactTargetOpened;
            this.explanation = explanation;
        }
    }
}

package com.enterprise.techdebt.safety;

import java.util.List;

public interface SafetySignal {
    String name();

    SignalResult evaluate(List<FileChange> changes);
}

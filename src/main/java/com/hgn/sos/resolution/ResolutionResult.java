package com.hgn.sos.resolution;

import com.hgn.sos.order.Order;

public class ResolutionResult {
    private final Order order;
    private final boolean resolvedViaGraceWindow;
    private final boolean ambiguous;

    private ResolutionResult(Order order, boolean grace, boolean ambiguous) {
        this.order = order;
        this.resolvedViaGraceWindow = grace;
        this.ambiguous = ambiguous;
    }

    public static ResolutionResult resolved(Order order, boolean grace) {
        return new ResolutionResult(order, grace, false);
    }

    public static ResolutionResult ambiguous() {
        return new ResolutionResult(null, false, true);
    }

    public boolean isAmbiguous() { return ambiguous; }
    public boolean isResolvedViaGraceWindow() { return resolvedViaGraceWindow; }
    public Order getOrder() { return order; }
}
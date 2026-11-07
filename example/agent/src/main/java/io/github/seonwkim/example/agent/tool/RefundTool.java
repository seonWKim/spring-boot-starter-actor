package io.github.seonwkim.example.agent.tool;

import org.springframework.stereotype.Component;

/** A tool with a side effect: the agent asks a human before it runs. */
@Component
public class RefundTool implements Tool {

    @Override
    public String name() {
        return "refund";
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public String invoke(String orderId) {
        return "refund issued for order " + orderId;
    }
}

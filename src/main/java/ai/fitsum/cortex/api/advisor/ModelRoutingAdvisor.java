package ai.fitsum.cortex.api.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes to local model for sensitive content, cloud model otherwise.
 * Privacy-first routing based on SensitivityGuard classification.
 */
@Component
public class ModelRoutingAdvisor implements CallAroundAdvisor {
    
    private static final Logger log = LoggerFactory.getLogger(ModelRoutingAdvisor.class);
    
    public ModelRoutingAdvisor() {}
    
    @Override
    public String getName() {
        return "ModelRouting";
    }
    
    @Override
    public int getOrder() {
        return 400;  // After retrieval
    }
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        String sensitivity = (String) advisedRequest.adviseContext().getOrDefault("sensitivity", "NONE");
        
        // Route based on sensitivity
        // Single provider mode (LM Studio via OpenAI protocol)
        String provider = "LM_STUDIO";
        log.info("Routing to LM Studio (sensitivity: {})", sensitivity);
        
        // Store provider choice for telemetry
        Map<String, Object> context = new HashMap<>(advisedRequest.adviseContext());
        context.put("provider", provider);
        
        AdvisedRequest updatedRequest = AdvisedRequest.from(advisedRequest)
            .withAdviseContext(context)
            .build();
        
        return chain.nextAroundCall(updatedRequest);
    }
}


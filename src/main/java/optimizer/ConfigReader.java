package optimizer;

import java.util.Arrays;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.apache.logging.log4j.Logger;

public class ConfigReader {
    private Logger logger;

    public ConfigReader(Logger logger) {
        this.logger = logger;
    }

    public Config get() throws Exception {
        Config config = new Config();
        try {
            ApiClient client = io.kubernetes.client.util.Config.defaultClient();
            CoreV1Api api = new CoreV1Api(client);
            String optimizerCmName = System.getenv("OPTIMIZER_CM_NAME");
            String optimizerCmNamespace = System.getenv("OPTIMIZER_CM_NAMESPACE");
            logger.info("going to read config from {}/{}", optimizerCmNamespace, optimizerCmName);
            V1ConfigMap appConfig = api.readNamespacedConfigMap(optimizerCmName, optimizerCmNamespace, "");
            Map<String, String> appConfigData = appConfig.getData();
            config.authenticate = Boolean.parseBoolean(appConfigData.get("authenticate"));
            config.redhatRecommendationsUrl = appConfigData.get("redhatRecommendationsUrl");
            config.redhatTokenUrl = appConfigData.get("redhatTokenUrl");
            config.clientId = appConfigData.get("clientId");
            config.clientSecret = appConfigData.get("clientSecret");
            config.pollIntervalSeconds = Long.parseLong(appConfigData.get("pollIntervalSeconds"));
            config.workflowUrl = appConfigData.get("workflowUrl");
            String clusterNames = appConfigData.get("clusterName");
            if (clusterNames != null && !clusterNames.isEmpty()) {
                config.clusterNames = Arrays.asList(clusterNames.split(","));
            }
            config.term = appConfigData.get("term");
        } catch (ApiException e) {
            logger.error(String.format("Code: %d. Body: %s",e.getCode(), e.getResponseBody()));
            throw e;
        }
        return config;
    }
}

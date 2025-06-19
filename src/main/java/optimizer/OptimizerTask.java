package optimizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.Logger;

public class OptimizerTask extends TimerTask {
    private Logger logger;
    private Config config;
    private String lastReported = "";
    OptimizerTask(Logger logger, Config config) {
        this.logger = logger;
        this.config = config;
    }
    @Override
    public void run() {
        try {
            logger.info("started OptimizerTask");
            // get token for accessing RedHat Insights
            logger.info("creating token");
            String token = getToken();
            // fetch recommendations
            logger.info("fetching recommendations");
            byte[] jsonRecommendations = fetchRecommendations(token);
            // parse recommendations and prepare input for workflow
            logger.info("parsing and filtering recommendations");
            List<ContainerRecommendation> recommendations = parseRecommendations(jsonRecommendations);
            // call workflow
            applyRecommendations(recommendations);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void applyRecommendations(List<ContainerRecommendation> recommendations) throws Exception {
        for (ContainerRecommendation r: recommendations) {
            // create workflow input
            WorkflowInput wi = new WorkflowInput();
            wi.clusterName = r.cluster_alias;
            wi.containerName = r.container;
            wi.resourceNamespace = r.project;
            wi.resourceType = r.workloadType;
            wi.resourceName = r.workloadName;
            wi.containerResources.limits.memory = r.memoryLimit;
            wi.containerResources.limits.cpu = r.cpuLimit;
            wi.containerResources.requests.memory = r.memoryRequest;
            wi.containerResources.requests.cpu = r. cpuRequest;
            // execute workflow
            executeWorkflowRegular(wi);
            // log
            logger.info("applied recommendation "+r.id);
            // save last report time
            lastReported = r.last_reported;
        }
    }

    private void executeWorkflowRegular(WorkflowInput wi) throws Exception {
        String jsonBody = new ObjectMapper().writeValueAsString(wi);
        logger.debug("posting json body {}", jsonBody);
        HttpPost request = new HttpPost(config.workflowUrl);
        request.setEntity(new StringEntity(jsonBody, ContentType.create("application/json")));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client
                     .execute(request)) {

            final int statusCode = response.getStatusLine().getStatusCode();
            String id = "";
            JsonNode nodeRoot = new ObjectMapper().readTree(response.getEntity().getContent());
            JsonNode nodeId = nodeRoot.get("id");
            if (nodeId != null) {
                id = nodeId.asText();
                logger.info("executed workflow instance {}", id);
            }
            if (statusCode < 200 || statusCode > 299) {
                throw new Exception("Error executing workflow. Received: " + response.getStatusLine());
            }
        }
    }
    private void executeWorkflowWithCloudEvent(WorkflowInput wi) throws Exception {
        // create cloud event HTTP request
        CloudEvent cloudEvent = new CloudEvent();
        cloudEvent.data = wi;
        String jsonBody = new ObjectMapper().writeValueAsString(cloudEvent);
        // send HTTP request
        logger.debug("posting json body {}", jsonBody);
        HttpPost request = new HttpPost(config.workflowUrl);
        request.setEntity(new StringEntity(jsonBody, ContentType.create("application/cloudevents+json")));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client
                     .execute(request)) {

            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new Exception("Error executing workflow. Received: " + response.getStatusLine());
            }
        }
    }

    private List<ContainerRecommendation> parseRecommendations(byte[] jsonRecommendations) throws IOException {
        List<ContainerRecommendation> result = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonRecommendations);
        Iterator<JsonNode> iterator = rootNode.withArray("data").elements();
        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            ContainerRecommendation r = new ContainerRecommendation();
            r.cluster_alias = node.get("cluster_alias").asText();
            if (config.clusterNames.size() > 0 && !config.clusterNames.contains(r.cluster_alias)) {
                continue;
            }
            r.cluster_uuid = node.get("cluster_uuid").asText();
            r.last_reported = node.get("last_reported").asText();
            r.id = node.get("id").asText();
            r.project = node.get("project").asText();
            r.container = node.get("container").asText();
            r.workloadType = node.get("workload_type").asText();
            r.workloadName = node.get("workload").asText();

            if (this.lastReported.compareTo(r.last_reported) >= 0) {
                continue;
            }

            JsonNode resources = node.at(String.format("/recommendations/recommendation_terms/%s/recommendation_engines/performance/config", config.term));
            if ( resources == null ) {
                continue;
            }

            JsonNode cpuLimit = resources.at("/limits/cpu/amount");
            if (cpuLimit != null) {
                r.cpuLimit = cpuLimit.asDouble();
            }
            JsonNode cpuRequest = resources.at("/requests/cpu/amount");
            if (cpuRequest != null) {
                r.cpuRequest = cpuRequest.asDouble();
            }
            JsonNode memoryLimit = resources.at("/limits/memory/amount");
            if (memoryLimit != null) {
                r.memoryLimit = memoryLimit.asLong();
            }
            JsonNode memoryRequest = resources.at("/requests/memory/amount");
            if (memoryRequest != null) {
                r.memoryRequest = memoryRequest.asLong();
            }

            result.add(r);
        }

        logger.info("parsed "+result.size()+" recommendations");
        return result;
    }

    private byte[] fetchRecommendations(String token) throws Exception {
        String fetchMsg = "fetching recommendations";
       URIBuilder uriBuilder = new URIBuilder(config.redhatRecommendationsUrl)
               .addParameter("order_how", "ASC")
               .addParameter("limit", Integer.toString(Integer.MAX_VALUE));

       if (config.clusterName != null) {
           uriBuilder.addParameter("cluster", config.clusterName);
           fetchMsg = String.format("%s for cluster %s", fetchMsg, config.clusterName);
       }

        if ( !lastReported.isEmpty() ) {
            String startDate = lastReported.substring(0,10);
            uriBuilder.addParameter("start_date", startDate);
            fetchMsg = String.format("%s newer than %s", fetchMsg, lastReported);
        }

        logger.info(fetchMsg);

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.addHeader("Accept", "application/json");
        if (!token.isEmpty()) {
            httpGet.addHeader("Authorization", "Bearer "+token);
        }
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client
                     .execute(httpGet)) {

            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new Exception("Error getting recommendations from " + config.redhatRecommendationsUrl + ". Received: " + response.getStatusLine());
            }

            byte[] jsonBytes = response.getEntity().getContent().readAllBytes();
            return jsonBytes;
        }
    }

    private String getToken() throws Exception {
        if (!config.authenticate) {
            return "";
        }
        String token;

        ArrayList<NameValuePair> formData = new ArrayList<NameValuePair>();
        formData.add(new BasicNameValuePair("grant_type", "client_credentials"));
        formData.add(new BasicNameValuePair("scope", "openid api.iam.service_accounts"));
        formData.add(new BasicNameValuePair("client_id", config.clientId));
        formData.add(new BasicNameValuePair("client_secret", config.clientSecret));

        HttpPost httpPost = new HttpPost(config.redhatTokenUrl);
        httpPost.setEntity(new UrlEncodedFormEntity(formData, StandardCharsets.UTF_8));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client
                     .execute(httpPost)) {

            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new Exception("Error getting access token from " + config.redhatTokenUrl + ". Received: " + response.getStatusLine());
            }

            byte[] jsonBytes = response.getEntity().getContent().readAllBytes();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonBytes);
            token = jsonNode.get("access_token").asText();
        }

        return token;
    }
}
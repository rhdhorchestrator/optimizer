package optimizer;

public class Config {
    /**
     * How often to fetch recommendations
     */
    public long pollIntervalSeconds;
    /**
     * credentials for accessing RedHat Insights information
     */
    public String clientId;
    public String clientSecret;
    /**
     * RedHat URLs. Configuration enables proxying and testing in dev envs
     */
    public String redhatRecommendationsUrl;
    public String redhatTokenUrl;
    /**
     * The URL for the OSL (SonataFlow) workflow. E.g. http://patch-k8s-resource.sonataflow-infra/patch-k8s-resource
     */
    public String workflowUrl;
    /**
     * Optional. If empty then we read all recommendations and apply them to the single cluster we have access to. Otherwise, fetch only recommendations for this cluster     */
    public String clusterName;
    /**
     * short_term | medium_term | long_term
     */
    public String term;
    /**
     * do not authenticate when fetching recommendations. Used for development
     */
    public boolean authenticate = true;
}

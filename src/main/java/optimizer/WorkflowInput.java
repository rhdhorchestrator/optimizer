package optimizer;

public class WorkflowInput {
    public class Resources {
        public double cpu;
        public long memory;
    }
    public class ContainerResources {
        public Resources limits = new Resources();
        public Resources requests = new Resources();
    }
    public String clusterName;
    public String resourceType;
    public String resourceNamespace;
    public String resourceName;
    public String containerName;
    public ContainerResources containerResources = new ContainerResources();;
}

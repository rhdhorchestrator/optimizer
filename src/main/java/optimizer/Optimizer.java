package optimizer;

import java.util.Timer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Optimizer {

    //private static final Logger logger = LogManager.getLogger();
    public static void main(String[] args) throws Exception {
        Logger logger = LogManager.getLogger(Optimizer.class);
        // read config
        logger.info("Reading configuration from ConfigMap");
        Config config = new ConfigReader(logger).get();
        // set timer and timer task
        logger.info("setting up OptimizerTask");
        OptimizerTask optimizerTask = new OptimizerTask(logger, config);
        Timer timer = new Timer("Optimizer");
        logger.info("Starting timer");
        timer.scheduleAtFixedRate(optimizerTask, 0, config.pollIntervalSeconds*1000);
    }
}

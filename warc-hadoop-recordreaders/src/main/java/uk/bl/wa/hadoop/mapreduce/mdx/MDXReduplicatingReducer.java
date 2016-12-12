package uk.bl.wa.hadoop.mapreduce.mdx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONException;

/**
 * 
 * This reducer takes MDX entries ordered by hash, and re-populates
 * empty/deduplicated MDX records.
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
@SuppressWarnings({ "deprecation" })
public class MDXReduplicatingReducer extends MapReduceBase implements
        Reducer<Text, Text, Text, Text> {

    private static Log log = LogFactory.getLog(MDXReduplicatingReducer.class);

    static enum MyCounters {
        NUM_RECORDS, NUM_REVISITS, NUM_ERRORS, NUM_DROPPED_RECORDS, NUM_UNRESOLVED_REVISITS, NUM_RESOLVED_REVISITS
    }

    private static final Text revisit = new Text("revisit");
    private static final Text response = new Text("response");

    public MDXReduplicatingReducer() {
        try {
            Properties props = new Properties();
            props.load(getClass()
                    .getResourceAsStream("/log4j-override.properties"));
            PropertyConfigurator.configure(props);
        } catch (IOException e1) {
            log.error("Failed to load log4j config from properties file.");
        }
    }

    /**
     */
    @Override
    public void configure(JobConf job) {
        log.info("Initialisation complete.");
    }

    @Override
    public void reduce(Text key, Iterator<Text> values,
            OutputCollector<Text, Text> output, Reporter reporter)
            throws IOException {

        try {
            long noValues = 0;
            String json;
            MDX mdx;
            String exemplar = null;
            List<MDX> toReduplicate = new ArrayList<MDX>();
            while (values.hasNext()) {
                json = values.next().toString();
                mdx = new MDX(json);
                noValues++;

                // Collect the revisit records:
                if (revisit.equals(mdx.getRecordType())) {
                    // Add this revisit record to the stack:
                    reporter.incrCounter(MyCounters.NUM_REVISITS, 1);
                    toReduplicate.add(mdx);
                } else {
                    // Record a response record:
                    if (exemplar == null
                            && response.equals(mdx.getRecordType())) {
                        exemplar = json;
                    }
                    // Collect complete records:
                    output.collect(key, new Text(mdx.toString()));
                }

                // Report:
                reporter.incrCounter(MyCounters.NUM_RECORDS, 1);

                // Occasionally update status report:
                if ((noValues % 1000) == 0) {
                    reporter.setStatus("Processed " + noValues + ", of which "
                            + reporter.getCounter(MyCounters.NUM_REVISITS)
                                    .getValue()
                            + " records need reduplication.");
                }

            }

            // Mis-reduce status:
            log.info("Mid-reduce: Processed " + noValues + ", of which "
                    + reporter.getCounter(MyCounters.NUM_REVISITS).getValue()
                    + " records need reduplication.");

            // Now fix up revisits:
            for (MDX rmdxw : toReduplicate) {
                // Set outKey based on hash:
                // Text outKey = new rmdxw.getHash();
                // Handle merge:
                if (exemplar != null) {
                    // Modify record type and and merge the properties:
                    MDX rmdx = new MDX(exemplar);
                    @SuppressWarnings("unchecked")
                    Iterator<String> keys = rmdxw.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        rmdx.put(k, rmdxw.get(k));
                    }
                    rmdx.setRecordType("reduplicated");
                    reporter.incrCounter(MyCounters.NUM_RESOLVED_REVISITS, 1);
                    // Collect resolved records:
                    output.collect(key, new Text(rmdx.toString()));
                } else {
                    reporter.incrCounter(MyCounters.NUM_UNRESOLVED_REVISITS, 1);
                    // Collect unresolved records:
                    output.collect(key, new Text(rmdxw.toString()));
                }
            }
        } catch (JSONException e) {
            log.error("Exception in MDX reducer.", e);
            e.printStackTrace();
            reporter.incrCounter(MyCounters.NUM_ERRORS, 1);
        }

    }

}
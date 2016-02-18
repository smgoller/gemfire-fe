package io.pivotal.bds.gemfire.r.server.function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Declarable;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.execute.Function;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.execute.FunctionException;
import com.gemstone.gemfire.cache.execute.ResultSender;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.cache.query.Struct;
import com.gemstone.gemfire.cache.query.types.CollectionType;
import com.gemstone.gemfire.cache.query.types.ObjectType;

import io.pivotal.bds.gemfire.ml.Model;
import io.pivotal.bds.gemfire.r.common.ModelKey;
import io.pivotal.bds.gemfire.r.common.AdhocPrediction;
import io.pivotal.bds.gemfire.r.common.AdhocPredictionRequest;
import io.pivotal.bds.gemfire.r.common.AdhocPredictionResponse;
import io.pivotal.bds.gemfire.r.server.util.Utils;
import io.pivotal.bds.gemfire.util.RegionHelper;

@SuppressWarnings("serial")
public class AdhocPredictionFunction implements Function, Declarable {

    private static final Logger LOG = LoggerFactory.getLogger(AdhocPredictionFunction.class);

    @Override
    public void execute(FunctionContext ctx) {
        try {
            ResultSender<AdhocPredictionResponse> sender = ctx.getResultSender();
            AdhocPredictionRequest req = (AdhocPredictionRequest) ctx.getArguments();
            LOG.debug("execute: req={}", req);

            Region<ModelKey, Model<?, ?, double[], Number>> modelRegion = RegionHelper.getRegion("model");

            ModelKey modelKey = req.getModelKey();
            Model<?, ?, double[], Number> model = modelRegion.get(modelKey);
            Assert.notNull(model, "Model " + modelKey.getModelId() + " not found");

            String queryId = req.getQueryId();

            Region<String, String> queryRegion = RegionHelper.getRegion("queries");
            String query = queryRegion.get(queryId);
            Assert.notNull(query, "Query " + queryId + " not found");

            List<Object> queryArgs = req.getQueryArgs();

            LOG.debug("execute: query={}", query);
            QueryService qs = CacheFactory.getAnyInstance().getQueryService();
            Query q = qs.newQuery(query);

            Object ores = queryArgs == null || queryArgs.isEmpty() ? q.execute() : q.execute(queryArgs.toArray());
            LOG.debug("execute: ores={}", ores);

            SelectResults<?> sres = (SelectResults<?>) ores;

            if (sres.isEmpty()) {
                throw new IllegalArgumentException("Query did not return any results");
            }

            CollectionType ctype = sres.getCollectionType();
            ObjectType otype = ctype.getElementType();

            if (!otype.isStructType()) {
                throw new IllegalArgumentException("Query did not return structs");
            }

            @SuppressWarnings("unchecked")
            List<Struct> lres = ((SelectResults<Struct>) ores).asList();
            List<AdhocPrediction> predicts = new ArrayList<>();

            for (Struct st : lres) {
                double[] x = Utils.extractX(st);
                Number p = model.predict(x);
                LOG.debug("execute: x={}, p={}", Arrays.toString(x), p);

                AdhocPrediction pred = new AdhocPrediction(x, p);
                predicts.add(pred);
            }

            AdhocPredictionResponse resp = new AdhocPredictionResponse(predicts);
            LOG.debug("execute: resp={}", resp);

            sender.lastResult(resp);
        } catch (Exception x) {
            throw new FunctionException(x.toString(), x);
        }
    }

    @Override
    public String getId() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean hasResult() {
        return true;
    }

    @Override
    public boolean isHA() {
        return false;
    }

    @Override
    public boolean optimizeForWrite() {
        return false;
    }

    @Override
    public void init(Properties props) {
    }

}

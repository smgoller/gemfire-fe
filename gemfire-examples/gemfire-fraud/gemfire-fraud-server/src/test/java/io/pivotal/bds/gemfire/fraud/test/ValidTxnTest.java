package io.pivotal.bds.gemfire.fraud.test;

import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.FunctionService;

import io.pivotal.bds.gemfire.fraud.common.data.ProcessTransactionRequest;

public class ValidTxnTest extends BaseTest {

    private static final Random random = new Random();

    @Override
    protected void go() throws Exception {
        for (int i = 0; i < 100; ++i) {
            double amt = 2.0 + (random.nextDouble() * 8.0); // $2 to $10
            doTxn(amt);
        }
    }

    private void doTxn(double amt) {
        ProcessTransactionRequest req = new ProcessTransactionRequest();

        req.setCardNumber("1234567890000012");
        req.setNameOnCard("Bill Smith");
        req.setCcv("123");
        req.setAmount(amt);
        req.setCardPresent(true);
        req.setExpiration(201705);
        req.setVendorId("vendor-0");
        req.setCreatedOn(new Date());

        Set<ProcessTransactionRequest> filter = new HashSet<>();
        filter.add(req);

        LOG.info("go: executing: amt={}", amt);
        Execution exec = FunctionService.onRegion(transactionRoutingRegion).withFilter(filter);
        Object res = exec.execute("ProcessTransactionFunction").getResult();
        LOG.info("req={}, res={}", req, res);
    }

    public static void main(String[] args) throws Exception {
        new ValidTxnTest().run(args);
    }
}

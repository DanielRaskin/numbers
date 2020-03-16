package numbers;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.locks.StampedLock;

/**
 * REST API
 */
class RestAPI {
    private Javalin javalin;
    private StampedLock lock = new StampedLock();

    private volatile BigDecimal min;
    private volatile BigDecimal max;
    private volatile BigDecimal avg;
    private volatile BigDecimal sum = new BigDecimal(BigInteger.ZERO, 4);
    private volatile int numberOfNumbers = 0;

    RestAPI() {
        javalin = Javalin.create()
                .post("/", this::addNumber)
                .get("/", this::getData)
                // return status 400 in case of exception
                .exception(Exception.class, (e, ctx) -> {
                    ctx.status(400);
                    var message = e.getMessage();
                    ctx.result((message == null) ? "server error" : message);
                });
    }

    // start server
    void start(int port) {
        javalin.start(port);
    }

    // stop server
    void stop() {
        javalin.stop();
    }

    // receive new number
    private void addNumber(Context context) {
        // get new number from request body
        var number = new BigDecimal(context.body().trim());
        var lockStamp = lock.writeLock();
        // modify values
        try {
            if ((numberOfNumbers == 0) || (number.compareTo(min) < 0)) {
                min = number;
            }
            if ((numberOfNumbers == 0) || (number.compareTo(max) > 0)) {
                max = number;
            }
            sum = sum.add(number);
            avg = sum.divide(new BigDecimal(numberOfNumbers + 1), RoundingMode.HALF_UP);
            // numberOfNumbers is modified last, so if it is greater than zero we are sure we have correct data
            numberOfNumbers++;
        } finally {
            lock.unlock(lockStamp);
        }
    }

    // get values
    private void getData(Context context) {
        if (numberOfNumbers == 0) {
            context.result("no data");
        } else {
            // try optimistic read
            var lockStamp = lock.tryOptimisticRead();
            var minimum = min;
            var maximum = max;
            var average = avg;
            if (! lock.validate(lockStamp)) {
                // if optimistic read failed, use read lock
                lockStamp = lock.readLock();
                try {
                    minimum = min;
                    maximum = max;
                    average = avg;
                } finally {
                    lock.unlock(lockStamp);
                }
            }
            context.result("minimum = " + minimum.toString() + "\n"
                    + "maximum = " + maximum.toString() + "\n"
                    + "average = " + average.toString());
        }
    }
}

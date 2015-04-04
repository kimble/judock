package com.developerb.judock;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.concurrent.TimeUnit;

/**
 * @author Kim A. Betti
 */
public interface ReadyPredicate {


    Result isReady(Context context);


    public static class Result {

        private final boolean kill;
        private final boolean success;
        private final Duration graceTime;

        private final String message;

        private Result(boolean kill, boolean success, Duration graceTime, String message) {
            this.kill = kill;
            this.success = success;
            this.graceTime = graceTime;
            this.message = message;
        }

        public static Result kill(String message) {
            return new Result(true, false, null, message);
        }

        public static Result tryAgain(String message) {
            return new Result(true, false, Duration.standardSeconds(5), message);
        }

        public static Result tryAgain(long val, TimeUnit unit, String message) {
            Duration graceTime = new Duration(unit.toMillis(val));
            return new Result(false, false, graceTime, message);
        }

        public static Result success(String message) {
            return new Result(false, true, null, message);
        }

        boolean shouldBeKilled() {
            return kill;
        }

        boolean wasSuccessful() {
            return success;
        }

        void sleep() throws InterruptedException {
            if (!shouldBeKilled() && graceTime != null) {
                Thread.sleep(graceTime.getMillis());
            }
        }

        @Override
        public String toString() {
            if (kill) {
                return "to be killed, " + message;
            }
            else if (success) {
                return "successful, " + message;
            }
            else {
                return "trying again in " + graceTime.getStandardSeconds() + " seconds, " + message;
            }
        }
    }


    public static class Context {

        private final DateTime started;
        private final String ipAdress;

        public Context(String ipAddress) throws Exception {
            this.started = DateTime.now();
            this.ipAdress = ipAddress;
        }

        public boolean runningForMoreThen(long val, TimeUnit unit) {
            Duration other = new Duration(unit.toMillis(val));
            Duration sinceStartup = new Duration(started, DateTime.now());
            return sinceStartup.isLongerThan(other);
        }

        public String ipAddress() {
            return ipAdress;
        }

    }


}
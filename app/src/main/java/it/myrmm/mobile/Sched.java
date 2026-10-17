package it.myrmm.mobile;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Pianifica il check-in periodico (sopravvive al riavvio) e uno immediato. */
final class Sched {
    private static final int PERIODIC = 1;
    private static final int NOW = 2;

    private Sched() {}

    static void start(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName cn = new ComponentName(c, CheckinJob.class);
        js.cancel(PERIODIC);
        JobInfo periodic = new JobInfo.Builder(PERIODIC, cn)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(Config.every(c) * 60L * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(periodic);
        now(c);
    }

    static void now(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        ComponentName cn = new ComponentName(c, CheckinJob.class);
        JobInfo one = new JobInfo.Builder(NOW, cn)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(60L * 1000L)
                .build();
        js.schedule(one);
    }

    static void stop(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancelAll();
    }
}

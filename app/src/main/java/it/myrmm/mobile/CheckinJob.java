package it.myrmm.mobile;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class CheckinJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Checkin.run(getApplicationContext());
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
